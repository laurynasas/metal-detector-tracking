package com.projecttango.examples.java.utils;

import java.io.File;
import com.projecttango.examples.java.utils.RecordListener;

public class AudioRecordHelper implements AudioRecordThread.OnErrorListener {

    private AudioRecordThread mAudioRecordThread;

    public AudioRecordThread getmAudioRecordThread(){
        return mAudioRecordThread;
    }

    private RecordListener mListener;

    public void setAudioRecordListener(RecordListener mediaRecordListener) {
        this.mListener = mediaRecordListener;
    }

    public void startRecord(File file) {
        if (mAudioRecordThread == null) {
            mAudioRecordThread = new AudioRecordThread(file);
            mAudioRecordThread.setOnErrorListener(this);
            mAudioRecordThread.start();

            if (mListener != null) {
                mListener.onRecordStart();
            }
            //} else {
            //    mAudioRecordThread.onResume();
        }
    }

    //public void pause() {
    //    if (mAudioRecordThread == null) {
    //        return;
    //    }
    //    //mAudioRecordThread.onPause();
    //}

    public void stopRecord() {
        if (mAudioRecordThread != null) {
            mAudioRecordThread.onRelease();
            mAudioRecordThread = null;
            if (mListener != null) {
                mListener.onRecordStop();
            }
        }
    }

    @Override public void onError() {
        stopRecord();
    }
}