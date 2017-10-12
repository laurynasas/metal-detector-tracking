package com.projecttango.examples.java.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.File;
import java.io.IOException;
import com.projecttango.examples.java.utils.BufferedRandomAccessFile;
import com.projecttango.examples.java.utils.FileUtil;

public class AudioRecordThread extends Thread {

    public static int getBitsPerSample() {
        return BITS_PER_SAMPLE;
    }

    public static int getCHANNELS() {

        return CHANNELS;
    }

    public static int getFormatTag() {
        return FORMAT_TAG;
    }

    public static int getAvgBytesPerSec() {
        return AVG_BYTES_PER_SEC;
    }

    public static int getBlockAlign() {
        return BLOCK_ALIGN;
    }

    public static int getSamplesPerSec() {

        return SAMPLES_PER_SEC;
    }

    /**

     * Sample rate
     */
    private final static int SAMPLES_PER_SEC = 16000;


    /**
     * BITS_PER_SAMPLE
     * 8 = ENCODING_PCM_8BIT, 16 = ENCODING_PCM_16BIT
     */
    private final static int BITS_PER_SAMPLE = 16;

    /**
     * CHANNELS
     * 1 = CHANNEL_IN_MONO, 2 = CHANNEL_IN_STEREO
     */
    private final static int CHANNELS = 1;

    /**
     * FORMAT_TAG
     * 1 = PCM
     */
    private final static int FORMAT_TAG = 1;

    /**
     * AVG_BYTES_PER_SEC
     * SAMPLES_PER_SEC * CHANNELS *BITS_PER_SAMPLE / 8
     */
    private final static int AVG_BYTES_PER_SEC = SAMPLES_PER_SEC * CHANNELS * BITS_PER_SAMPLE / 8;

    /**
     * BLOCK_ALIGN
     * CHANNELS *BITS_PER_SAMPLE / 8
     */
    private final static int BLOCK_ALIGN = CHANNELS * BITS_PER_SAMPLE / 8;

    //private final Object mPauseLock = new Object();
    //private boolean mPauseFlag;
    //private boolean mExitFlag;
    private AudioRecord mAudioRecord;
    private boolean mRecordFlag;
    private int mMiniBuffer = FileUtil.IO_BUFFER_SIZE;
    private File mFile;
    private BufferedRandomAccessFile bufferedRandomAccessFile;
    private OnErrorListener mOnErrorListener;

    public void setOnErrorListener(OnErrorListener onErrorListener) {
        this.mOnErrorListener = onErrorListener;
    }

    public AudioRecord getmAudioRecord(){
        return mAudioRecord;
    }

    public AudioRecordThread(File file) {
        super();
        mFile = file;
        mMiniBuffer = AudioRecord.getMinBufferSize(SAMPLES_PER_SEC, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
    }

    //public void onPause() {
    //    synchronized (mPauseLock) {
    //        mPauseFlag = true;
    //    }
    //}
    //public void onResume() {
    //    synchronized (mPauseLock) {
    //        mPauseFlag = false;
    //        mPauseLock.notify();
    //    }
    //}
    public void onRelease() {
        mRecordFlag = false;
        //mExitFlag = true;
        //synchronized (mPauseLock) {
        //    if (mPauseFlag) {
        //        mPauseFlag = false;
        //        mPauseLock.notify();
        //    }
        //}
    }

    //private void pauseLock() throws InterruptedException {
    //    synchronized (mPauseLock) {
    //        if (mPauseFlag) {
    //            mPauseLock.wait();
    //        }
    //    }
    //}

    private void createAudioRecord() {
        if (mAudioRecord == null) {
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLES_PER_SEC,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mMiniBuffer);
        }
    }

    private void releaseAudioRecord() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    private BufferedRandomAccessFile createEmptyWaveFile(File file) throws IOException {
        BufferedRandomAccessFile bufferedRandomAccessFile =
                new BufferedRandomAccessFile(mFile, "rw");
        bufferedRandomAccessFile.setLength(0);
        bufferedRandomAccessFile.write(
                makeWaveHeader(0, CHANNELS, BITS_PER_SAMPLE, FORMAT_TAG, SAMPLES_PER_SEC,
                        AVG_BYTES_PER_SEC, BLOCK_ALIGN));
        return bufferedRandomAccessFile;
    }

    private void write(BufferedRandomAccessFile bufferedRandomAccessFile) throws IOException {
        byte[] data = new byte[mMiniBuffer];
        int readSize = mAudioRecord.read(data, 0, mMiniBuffer);
        if (readSize > 0) {
            bufferedRandomAccessFile.write(data);
        }
    }

    private BufferedRandomAccessFile rewriteWaveHeader(
            BufferedRandomAccessFile bufferedRandomAccessFile) throws IOException {
        int pcmSize = (int) (bufferedRandomAccessFile.length() - 44);
        if (pcmSize > 0) {
            bufferedRandomAccessFile.seek(0);
            bufferedRandomAccessFile.write(
                    makeWaveHeader(pcmSize, CHANNELS, BITS_PER_SAMPLE, FORMAT_TAG, SAMPLES_PER_SEC,
                            AVG_BYTES_PER_SEC, BLOCK_ALIGN));
            bufferedRandomAccessFile.close();
        }

        return bufferedRandomAccessFile;
    }

    public BufferedRandomAccessFile getBufferedRandomAccessFile(){
        return bufferedRandomAccessFile;
    }


    @Override public void run() {
        try {
            //while (!mExitFlag) {
            createAudioRecord();
            mAudioRecord.startRecording();
            mRecordFlag = true;
            bufferedRandomAccessFile = createEmptyWaveFile(mFile);
            while (mRecordFlag) {
                write(bufferedRandomAccessFile);
            }
            rewriteWaveHeader(bufferedRandomAccessFile);

            //pauseLock();
            //}

        } catch (Exception e) {
            e.printStackTrace();
            //interrupt();

            if (mOnErrorListener != null) {
                mOnErrorListener.onError();
            }
        } finally {
            releaseAudioRecord();
            FileUtil.closeSilently(bufferedRandomAccessFile);
        }
    }

    public interface OnErrorListener {
        void onError();
    }

    private byte[] makeWaveHeader(int dataCkSize, int nChannels, int wBitsPerSample, int wFormatTag,
                                  int nSamplesPerSec, int nAvgBytesPerSec, int nBlockAlign) {

        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (dataCkSize + 36 & 0xff); // add rest 36 size
        header[5] = (byte) ((dataCkSize + 36 >> 8) & 0xff);
        header[6] = (byte) ((dataCkSize + 36 >> 16) & 0xff);
        header[7] = (byte) ((dataCkSize + 36 >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = (byte) (wBitsPerSample & 0xff); // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = (byte) (wFormatTag & 0xff);
        header[21] = 0;
        header[22] = (byte) (nChannels & 0xff);
        header[23] = 0;
        header[24] = (byte) (nSamplesPerSec & 0xff);
        header[25] = (byte) ((nSamplesPerSec >> 8) & 0xff);
        header[26] = (byte) ((nSamplesPerSec >> 16) & 0xff);
        header[27] = (byte) ((nSamplesPerSec >> 24) & 0xff);
        header[28] = (byte) (nAvgBytesPerSec & 0xff);
        header[29] = (byte) ((nAvgBytesPerSec >> 8) & 0xff);
        header[30] = (byte) ((nAvgBytesPerSec >> 16) & 0xff);
        header[31] = (byte) ((nAvgBytesPerSec >> 24) & 0xff);
        header[32] = (byte) (nBlockAlign & 0xff); // block align
        header[33] = 0;
        header[34] = (byte) (wBitsPerSample & 0xff); // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (dataCkSize & 0xff);
        header[41] = (byte) ((dataCkSize >> 8) & 0xff);
        header[42] = (byte) ((dataCkSize >> 16) & 0xff);
        header[43] = (byte) ((dataCkSize >> 24) & 0xff);

        return header;
    }
}

