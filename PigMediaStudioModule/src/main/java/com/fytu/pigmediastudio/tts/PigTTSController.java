package com.fytu.pigmediastudio.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

public class PigTTSController {

    private PigTTSController instance;
    private static final String TAG = "PigTTSController";
    private static final String TTS_READ_FLAG = "TtsForReadUtterance";

    private HashMap<String, TextToSpeech> ttsMap = new HashMap<>();

    //复用池
    private List<String> freePool = new ArrayList<>();

    //复用池大小
    private int freePoolMaxSize = 3;

    private Context applicationContext;

    private TextToSpeech ttsForRead;
    private boolean isReadFinish = true;
    private boolean readyRead = false;
    private Thread thread_read;
    private boolean isValid = true;


    private TextToSpeech ttsForWav;
    private boolean isIniting = false;


    //需要线程中管理这个pig

    private PigBlockQueue pigReadBlockQueue;

    public PigTTSController(Context context) {
        this.applicationContext = context;
        instance = this;
    }

    public boolean isReadyRead(){
        return readyRead;
    }

    public void initRead() {
        if (!readyRead) {
            readyRead = true;

            pigReadBlockQueue = new PigBlockQueue();//无限长

            ttsForRead = new TextToSpeech(applicationContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == 0){
                        Log.d(TAG,"tts init Success");
                    }else{
                        Log.d(TAG,"tts init Failed");
                    }
                    HashMap<String, String> map = new HashMap<>();
                    map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TTS_READ_FLAG);
                    isReadFinish = false;
                    ttsForRead.speak("", TextToSpeech.QUEUE_FLUSH, map);
                }
            });

            ttsForRead.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TTS_READ_FLAG,"utter onstart");
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TTS_READ_FLAG,"utter ondone");
                    isReadFinish = true;
                }

                @Override
                public void onError(String utteranceId) {
                    Log.d(TTS_READ_FLAG,"utter onError！！！");
                    isReadFinish = true;
                }
            });

            isValid = true;
            thread_read = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isValid) {
                        while (isReadFinish) {//当它读完了，再给它安排任务
                            try {
                                String textForRead = pigReadBlockQueue.take();//阻塞式
                                readText(textForRead);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            thread_read.start();
        }
    }

    public void removeElementByContains(final String innerStr){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                pigReadBlockQueue.removeElementByContains(innerStr);
            }
        });
        t.setPriority(3);
        t.start();

    }

    public void removeElementByEqual(final String equalStr){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                pigReadBlockQueue.removeElementByEqual(equalStr);
            }
        });
        t.setPriority(3);
        t.start();
    }

    public void removeElementByIndex(final int index){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                pigReadBlockQueue.removeElementByIndex(index);
            }
        });

        t.setPriority(3);
        t.start();
    }

    public void removeElementByCountIndex(final int countIndex){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                pigReadBlockQueue.removeElementByCountIndex(countIndex);
            }
        });
        t.setPriority(3);
        t.start();
    }

    public void removeAllElement(){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                pigReadBlockQueue.removeAllElement();
            }
        });
        t.setPriority(3);
        t.start();
    }

    public void readNow(String txtForReadNow){
        isReadFinish = false;
        HashMap<String, String> map = new HashMap<>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TTS_READ_FLAG);
        Log.d(TTS_READ_FLAG,"call readNow()");
        ttsForRead.speak(txtForReadNow,TextToSpeech.QUEUE_FLUSH,map);
    }

    private void readText(String txtForRead) {
        isReadFinish = false;
        HashMap<String, String> map = new HashMap<>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TTS_READ_FLAG);
        Log.d(TTS_READ_FLAG,"call readText()");
        ttsForRead.speak(txtForRead,TextToSpeech.QUEUE_FLUSH,map);
    }

    public void loadReadTTS(final String strForRead){
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pigReadBlockQueue.put(strForRead);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        t.setPriority(5);
        t.start();
    }

    public PigTTSController setSpeed(float rate){
        int result = ttsForRead.setSpeechRate(rate);
        if (result == 0){
            Log.d(TAG,"setSpeed success");
        }else{
            Log.d(TAG,"setSpeed failed");
        }
        return instance;
    }

    public PigTTSController setPitch(float pitch){
        int result = ttsForRead.setPitch(pitch);
        if (result == 0){
            Log.d(TAG,"setPitch success");
        }else{
            Log.d(TAG,"setPitch failed");
        }
        return instance;
    }

    public PigTTSController setLanguage(Locale loc){
        int result = ttsForRead.setLanguage(loc);
        if (result == 0){
            Log.d(TAG,"setPitch success");
        }else{
            Log.d(TAG,"setPitch failed");
        }
        return instance;
    }


    /**
     * 在onDestroy等地方调用，释放资源
     */
    public void release(){
        if (ttsForRead != null){
            //停止播报
            ttsForRead.stop();
            //关闭资源
            ttsForRead.shutdown();
            //结束线程循环
            isValid = false;
            //结束block
            pigReadBlockQueue.release();
            pigReadBlockQueue = null;

        }
    }

    /**
     * 暂停播放
     */
    public void stop(){
        if (ttsForRead != null){
            ttsForRead.stop();
        }
    }




}
