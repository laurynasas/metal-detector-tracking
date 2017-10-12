package com.projecttango.examples.java.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class FileUtil {

    public static final int IO_BUFFER_SIZE = 8 * 1024;
    public static final String AUDIO_DIR_NAME = "audio";

    public static void closeSilently(Closeable c) {
        if (c == null) {
            return;
        }

        try {
            c.close();
        } catch (Throwable t) {
            // do nothing
        }
    }

    public static boolean deleteFile(File file) {
        try {
            if (file.exists()) {
                return file.delete();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static void deleteDir(final File fileDir) {
        new Thread(new Runnable() {
            @Override public void run() {
                File[] files;
                try {
                    if (fileDir.exists()) {
                        files = fileDir.listFiles();
                        for (File child : files) {
                            child.delete();
                        }
                        fileDir.delete();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static File getCacheDirectory(Context context, boolean preferExternal)
            throws IOException {
        File appCacheDir = null;

        if (preferExternal && Environment.MEDIA_MOUNTED.equals(
                Environment.getExternalStorageState())) {
            appCacheDir = getExternalDirectory(context);
        }

        if (appCacheDir == null) {
            appCacheDir = context.getCacheDir();
        }

        if (appCacheDir == null) {
            String cacheDirPath = "/data/data/" + context.getPackageName() + "/cache/";
            Log.d(FileUtil.class.getName(),
                    "Can't define system cache directory! use " + cacheDirPath);
            appCacheDir = new File(cacheDirPath);
        }

        return appCacheDir;
    }

    private static File getExternalDirectory(Context context) throws IOException {
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir != null && !cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                return null;
            }

            try {
                new File(cacheDir, ".nomedia").createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return cacheDir;
    }

    public static File getAudioCacheDirectory(Context context) throws IOException {
        File fileDir = getCacheDirectory(context, true);
        File audioCacheDir = new File(fileDir.getPath() + File.separator + AUDIO_DIR_NAME);
        if (!audioCacheDir.exists()) {
            if (!audioCacheDir.mkdirs()) {
                return null;
            }

            try {
                new File(audioCacheDir, ".nomedia").createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return audioCacheDir;
    }
}
