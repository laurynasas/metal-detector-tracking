/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.pointtopoint;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.tango.depthinterpolation.TangoDepthInterpolation;
import com.google.tango.support.TangoPointCloudManager;
import com.google.tango.support.TangoSupport;
import com.google.tango.transformhelpers.TangoTransformHelper;

import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.view.SurfaceView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An example showing how to build a very simple point-to-point measurement app
 * in Java. It uses the Tango Support Library to do depth calculations using
 * the point cloud data. Whenever the user clicks on the camera display, a point
 * is recorded from the point cloud data closest to the point of the touch;
 * consecutive touches are used as the two points for a distance measurement.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION
 * configuration parameter in order to achieve best results synchronizing the
 * Rajawali virtual world with the RGB camera.
 * <p/>
 * For more details on the augmented reality effects, including color camera texture rendering,
 * see java_augmented_reality_example or java_hello_video_example.
 */
public class PointToPointActivity extends Activity implements View.OnTouchListener {


    private class MeasuredPoint {
        public double mTimestamp;
        public float[] mDepthTPoint;


        public MeasuredPoint(double timestamp, float[] depthTPoint) {
            mTimestamp = timestamp;
            mDepthTPoint = depthTPoint;
        }
    }

    private static final String TAG = PointToPointActivity.class.getSimpleName();

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    // The interval at which we'll update our UI debug text in milliseconds.
    // This is the rate at which we query for distance data.
    private static final int UPDATE_UI_INTERVAL_MS = 100;

    private static final int INVALID_TEXTURE_ID = 0;

    private SurfaceView mSurfaceView;
    private PointToPointRenderer mRenderer;
    private TangoPointCloudManager mPointCloudManager;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;
    private TextView mDistanceTextview;
    private CheckBox mBilateralBox;
    private volatile TangoImageBuffer mCurrentImageBuffer;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private boolean mPointSwitch = true;

    // Two measured points in Depth Camera space.
    private MeasuredPoint[] mMeasuredPoints = new MeasuredPoint[2];

    // Two measured points in OpenGL space, we used a stack to hold the data is because rajawalli
    // LineRenderer expects a stack of points to be passed in. This is render ready data format from
    // Rajawalli's perspective.
    private Stack<Vector3> mMeasurePoitnsInOpenGLSpace = new Stack<Vector3>();
    private float mMeasuredDistance = 0.0f;

    // Handles the debug text UI update loop.
    private Handler mHandler = new Handler();
    private ImageView mCrosshair;

    private int mDisplayRotation = 0;

    private Handler handler;
    private int handlerDelay;
    private BufferedWriter writer;
    private File file;
    private FileWriter writeFile;
//    --------------------- Audio Recording ---------------------------------


    //    private static String mFileName = "/home/laurynas/workspace/coin-detection/audio_cache/test.3gp";
    private static String mFileName;
    private ArrayList<MeasuredPoint> allMeasuredPoints = new ArrayList<>();

    private static final String LOG_TAG = "AudioRecordTest";
    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private Button mRecordButton;

    private MediaRecorder mRecorder = null;

    private Button mPlayButton;
    private MediaPlayer mPlayer = null;
    private boolean mStartPlaying = true;
    private boolean mStartRecording = true;

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
        try {
            mPlayer.start();
        } catch (Exception e) {
            Log.e(LOG_TAG, "start() failed");
        }

    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }

    private void onPlay(boolean start) {
        if (start) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }


    private void onRecord(boolean start) {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();

        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
        try {
            mRecorder.start();

        } catch (Exception e) {
            Log.e(LOG_TAG, "start() failed");
        }
    }

    private void stopRecording() {
        try {
            mRecorder.stop();

        } catch (Exception e) {
            Log.e(LOG_TAG, "stop() failed");
        }
        try {
            mRecorder.release();

        } catch (Exception e) {
            Log.e(LOG_TAG, "release() failed");
        }

        mRecorder = null;
    }

    public synchronized void onPlayClick(View view) {

        mStartPlaying = true;
        onPlay(mStartPlaying);
        if (mStartPlaying) {
            mPlayButton.setText("Stop playing");
        } else {
            mPlayButton.setText("Start playing");
        }
        mStartPlaying = !mStartPlaying;
    }

    public void onRecordClick(View view) {

        onRecord(mStartRecording);
        if (mStartRecording) {
            mRecordButton.setText("Stop recording");


            handler = new Handler();
            handlerDelay = 100; //milliseconds

            handler.postDelayed(new Runnable() {
                public void run() {
                    Calendar cal = Calendar.getInstance();
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

                    MeasuredPoint point = getMeasuredPoint();

                    if (point != null) {

                        allMeasuredPoints.add(point);
                        float[] openGLPoint = toOpenGLSpace(point);

                        if (openGLPoint != null) {
                            Log.e("WRITER", writer.toString());
                            Log.e("COORDINATES", "(" + Float.toString(openGLPoint[0]) + "; " + Float.toString(openGLPoint[1]) + "; " + Float.toString(openGLPoint[2]) + ") at time: " + point.mTimestamp);

                            try {
                                writer.write(Float.toString(openGLPoint[0]) + "; " + Float.toString(openGLPoint[1]) + "; " + Float.toString(openGLPoint[2]) + "; " + point.mTimestamp);
                                writer.newLine();
                            } catch (IOException e) {
                                Log.e("WRITER", "Cannot write to file", e);
                            }
                        } else {
                            Log.e("==", "OpenGL point is null....");

                        }
                    } else {
                        Log.e("==", "Point is null....");
                    }
                    Log.e("==", sdf.format(cal.getTime()));

                    handler.postDelayed(this, handlerDelay);
                }
            }, handlerDelay);


        } else {
            handler.removeCallbacksAndMessages(null);
            try {
                writer.close();

            } catch (IOException e) {
                Log.e("WRITER", e.toString());
            }
            mRecordButton.setText("Start recording");
        }
        mStartRecording = !mStartRecording;
    }


//    -----------------------------------------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


//        -------------- Audio recording -------------------------------------
        mFileName = "/storage/sdcard0/Music";
        mFileName += "/audiorecordtest.3gp";

        // Record to the external cache directory for visibility
//        mFileName = getExternalCacheDir().getAbsolutePath();
//        mFileName += "/audiorecordtest.3gp";

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        Log.e("==", "will try to init writer");

        try {
            writeFile = new FileWriter("/storage/sdcard0/Music/coordinates.txt");
            writer = new BufferedWriter(writeFile);
            Log.e("==", "initialised writer");
        } catch (IOException msg) {
            Log.e("FILE EXCEPTION", msg.toString());
        }
        mRecordButton = (Button) findViewById(R.id.record_button);
        Log.e("DEEEEEEEEEEEEEEE", mFileName);

        mPlayButton = (Button) findViewById(R.id.play_button);


// -------------------------------------------------------------------------------------

        mSurfaceView = (SurfaceView) findViewById(R.id.ar_view);
        mRenderer = new PointToPointRenderer(this);
        mSurfaceView.setSurfaceRenderer(mRenderer);
        mSurfaceView.setOnTouchListener(this);
        mPointCloudManager = new TangoPointCloudManager();
        mDistanceTextview = (TextView) findViewById(R.id.distance_textview);
        mBilateralBox = (CheckBox) findViewById(R.id.check_box);
        mCrosshair = (ImageView) findViewById(R.id.crosshair);
        mCrosshair.setColorFilter(getResources().getColor(R.color.crosshair_ready));

        mMeasuredPoints[0] = null;
        mMeasuredPoints[1] = null;

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check and request camera permission at run time.
        if (checkAndRequestPermissions()) {
            bindTangoService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        clearLine();


        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.
        synchronized (this) {
            try {
                mRenderer.getCurrentScene().clearFrameCallbacks();
                if (mTango != null) {
                    mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                    mTango.disconnect();
                }
                // We need to invalidate the connected texture ID so that we cause a
                // re-connection in the OpenGL thread after resume.
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mIsConnected = false;
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Initialize Tango Service as a normal Android Service.
     */
    private void bindTangoService() {
        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        mTango = new Tango(PointToPointActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the OpenGL
                // thread or in the UI thread.
                synchronized (PointToPointActivity.this) {
                    try {
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                        TangoSupport.initialize(mTango);
                        connectRenderer();
                        mIsConnected = true;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
        mHandler.post(mUpdateUiLoopRunnable);
    }

    /**
     * Sets up the Tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service (motion tracking), plus low latency
        // IMU integration, color camera, depth and drift correction.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // NOTE: Low latency integration is necessary to achieve a
        // precise alignment of virtual objects with the RBG image and
        // produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        // Drift correction allows motion tracking to recover after it loses tracking.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DRIFT_CORRECTION, true);

        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the RGB camera and point cloud.
     */
    private void startupTango() {
        // No need to add any coordinate frame pairs since we are not
        // using pose data. So just initialize.
        ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using OnPoseAvailable for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Mark a camera frame as available for rendering in the OpenGL thread.
                    mIsFrameAvailableTangoThread.set(true);
                    mSurfaceView.requestRender();
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                // Save the cloud and point data for later use.
                mPointCloudManager.updatePointCloud(pointCloud);
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using OnPoseAvailable for this app.
            }
        });
        mTango.experimentalConnectOnFrameListener(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                new Tango.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(TangoImageBuffer tangoImageBuffer, int i) {
                        mCurrentImageBuffer = copyImageBuffer(tangoImageBuffer);
                    }

                    TangoImageBuffer copyImageBuffer(TangoImageBuffer imageBuffer) {
                        ByteBuffer clone = ByteBuffer.allocateDirect(imageBuffer.data.capacity());
                        imageBuffer.data.rewind();
                        clone.put(imageBuffer.data);
                        imageBuffer.data.rewind();
                        clone.flip();
                        return new TangoImageBuffer(imageBuffer.width, imageBuffer.height,
                                imageBuffer.stride, imageBuffer.frameNumber,
                                imageBuffer.timestamp, imageBuffer.format, clone,
                                imageBuffer.exposureDurationNs);
                    }
                });
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void connectRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks have a chance to run and before scene objects are rendered
                // into the scene.

                try {
                    // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the
                    // Tango callback thread and service disconnection from an onPause event.
                    synchronized (PointToPointActivity.this) {
                        // Don't execute any tango API actions if we're not connected to the
                        // service.
                        if (!mIsConnected) {
                            return;
                        }

                        // Set up scene camera projection to match RGB camera intrinsics.
                        if (!mRenderer.isSceneCameraConfigured()) {
                            TangoCameraIntrinsics intrinsics =
                                    TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            mDisplayRotation);
                            mRenderer.setProjectionMatrix(
                                    projectionMatrixFromCameraIntrinsics(intrinsics));
                        }

                        // Connect the camera texture to the OpenGL Texture if necessary
                        // NOTE: When the OpenGL context is recycled, Rajawali may regenerate the
                        // texture with a different ID.
                        if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                            mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mRenderer.getTextureId());
                            mConnectedTextureIdGlThread = mRenderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                        }

                        // If there is a new RGB camera frame available, update the texture with
                        // it.
                        if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                            mRgbTimestampGlThread =
                                    mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                        }

                        // If a new RGB frame has been rendered, update the camera pose to match.
                        if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            //
                            // When drift correction mode is enabled in config file, we need
                            // to query the device with respect to Area Description pose in
                            // order to use the drift-corrected pose.
                            //
                            // Note that if you don't want to use the drift corrected pose, the
                            // normal device with respect to start of service pose is still
                            // available.
                            //
                            // Also, we used mColorCameraToDipslayRotation to rotate the
                            // transformation to align with the display frame. The reason we use
                            // color camera instead depth camera frame is because the
                            // getDepthAtPointNearestNeighbor transformed depth point to camera
                            // frame.
                            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                    mRgbTimestampGlThread,
                                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.ENGINE_OPENGL,
                                    TangoSupport.ENGINE_OPENGL,
                                    mDisplayRotation);
                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                                // Update the camera pose from the renderer.
                                mRenderer.updateRenderCameraPose(lastFramePose);
                                mCameraPoseTimestamp = lastFramePose.timestamp;
                            } else {
                                // When the pose status is not valid, it indicates the tracking has
                                // been lost. In this case, we simply stop rendering.
                                //
                                // This is also the place to display UI to suggest the user walk
                                // to recover tracking.
                                Log.w(TAG, "Can't get device pose at time: " +
                                        mRgbTimestampGlThread);
                            }

                            // If both points have been measured, we transform the points to OpenGL
                            // space, and send it to mRenderer to render.
                            if (mMeasuredPoints[0] != null && mMeasuredPoints[1] != null) {
                                // To make sure drift correct pose is also applied to virtual
                                // object (measured points).
                                // We need to re-query the Start of Service to Depth camera
                                // pose every frame. Note that you will need to use the timestamp
                                // at the time when the points were measured to query the pose.
                                TangoSupport.MatrixTransformData openglTDepthArr0 =
                                        TangoSupport.getMatrixTransformAtTime(
                                                mMeasuredPoints[0].mTimestamp,
                                                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                                TangoSupport.ENGINE_OPENGL,
                                                TangoSupport.ENGINE_TANGO,
                                                TangoSupport.ROTATION_IGNORED);

                                TangoSupport.MatrixTransformData openglTDepthArr1 =
                                        TangoSupport.getMatrixTransformAtTime(
                                                mMeasuredPoints[1].mTimestamp,
                                                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                                                TangoSupport.ENGINE_OPENGL,
                                                TangoSupport.ENGINE_TANGO,
                                                TangoSupport.ROTATION_IGNORED);

                                if (openglTDepthArr0.statusCode == TangoPoseData.POSE_VALID &&
                                        openglTDepthArr1.statusCode == TangoPoseData.POSE_VALID) {
                                    mMeasurePoitnsInOpenGLSpace.clear();
                                    float[] p0 = TangoTransformHelper.transformPoint(
                                            openglTDepthArr0.matrix,
                                            mMeasuredPoints[0].mDepthTPoint);
                                    float[] p1 = TangoTransformHelper.transformPoint(
                                            openglTDepthArr1.matrix,
                                            mMeasuredPoints[1].mDepthTPoint);

                                    Log.e("COORDINATES", "(" + Float.toString(p0[0]) + "; " + Float.toString(p0[1]) + "; " + Float.toString(p0[2]) + ")");
                                    Log.e("COORDINATES", "(" + Float.toString(p1[0]) + "; " + Float.toString(p1[1]) + "; " + Float.toString(p1[2]) + ")");


                                    mMeasurePoitnsInOpenGLSpace.push(
                                            new Vector3(p0[0], p0[1], p0[2]));
                                    mMeasurePoitnsInOpenGLSpace.push(
                                            new Vector3(p1[0], p1[1], p1[2]));

                                    mMeasuredDistance = (float) Math.sqrt(
                                            Math.pow(p0[0] - p1[0], 2) +
                                                    Math.pow(p0[1] - p1[1], 2) +
                                                    Math.pow(p0[2] - p1[2], 2));
                                }
                            }

                            mRenderer.setLine(mMeasurePoitnsInOpenGLSpace);
                        }
                    }
                    // Avoid crashing the application due to unhandled exceptions.
                } catch (TangoErrorException e) {
                    Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
                } catch (Throwable t) {
                    Log.e(TAG, "Exception on the OpenGL thread", t);
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });
    }

    /**
     * Use Tango camera intrinsics to calculate the projection Matrix for the Rajawali scene.
     */
    private static float[] projectionMatrixFromCameraIntrinsics(TangoCameraIntrinsics intrinsics) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100;

        double cx = intrinsics.cx;
        double cy = intrinsics.cy;
        double width = intrinsics.width;
        double height = intrinsics.height;
        double fx = intrinsics.fx;
        double fy = intrinsics.fy;

        double xscale = near / fx;
        double yscale = near / fy;

        double xoffset = (cx - (width / 2.0)) * xscale;
        // Color camera's coordinates has y pointing downwards so we negate this term.
        double yoffset = -(cy - (height / 2.0)) * yscale;

        float m[] = new float[16];
        Matrix.frustumM(m, 0,
                (float) (xscale * -width / 2.0 - xoffset),
                (float) (xscale * width / 2.0 - xoffset),
                (float) (yscale * -height / 2.0 - yoffset),
                (float) (yscale * height / 2.0 - yoffset), near, far);
        return m;
    }

    public MeasuredPoint getMeasuredPoint() {
        // Calculate click location in u,v (0;1) coordinates.
//        float u = motionEvent.getX() / view.getWidth();
//        float v = motionEvent.getY() / view.getHeight();
        float u = 0.5f;
        float v = 0.5f;

        try {
            // Place point near the clicked point using the latest point cloud data.
            // Synchronize against concurrent access to the RGB timestamp in the OpenGL thread
            // and a possible service disconnection due to an onPause event.
            MeasuredPoint newMeasuredPoint;
            synchronized (this) {
                newMeasuredPoint = getDepthAtTouchPosition(u, v);
            }
            if (newMeasuredPoint != null) {
                // Update a line endpoint to the touch location.
                // This update is made thread-safe by the renderer.
//                updateLine(newMeasuredPoint);
                return newMeasuredPoint;

            } else {
                Log.w(TAG, "Point was null.");
            }

        } catch (TangoException t) {
            Toast.makeText(getApplicationContext(),
                    R.string.failed_measurement,
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, getString(R.string.failed_measurement), t);
        } catch (SecurityException t) {
            Toast.makeText(getApplicationContext(),
                    R.string.failed_permissions,
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, getString(R.string.failed_permissions), t);
        }
        return null;
    }

    public float[] toOpenGLSpace(MeasuredPoint measuredPoint) {
        // To make sure drift correct pose is also applied to virtual
        // object (measured points).
        // We need to re-query the Start of Service to Depth camera
        // pose every frame. Note that you will need to use the timestamp
        // at the time when the points were measured to query the pose.
        float[] p0 = null;
        TangoSupport.MatrixTransformData openglTDepthArr0 =
                TangoSupport.getMatrixTransformAtTime(
                        measuredPoint.mTimestamp,
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                        TangoSupport.ENGINE_OPENGL,
                        TangoSupport.ENGINE_TANGO,
                        TangoSupport.ROTATION_IGNORED);

        if (openglTDepthArr0.statusCode == TangoPoseData.POSE_VALID) {
            p0 = TangoTransformHelper.transformPoint(
                    openglTDepthArr0.matrix,
                    measuredPoint.mDepthTPoint);
        }
        return p0;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            // Calculate click location in u,v (0;1) coordinates.
            float u = motionEvent.getX() / view.getWidth();
            float v = motionEvent.getY() / view.getHeight();

            try {
                // Place point near the clicked point using the latest point cloud data.
                // Synchronize against concurrent access to the RGB timestamp in the OpenGL thread
                // and a possible service disconnection due to an onPause event.
                MeasuredPoint newMeasuredPoint;
                synchronized (this) {
                    newMeasuredPoint = getDepthAtTouchPosition(u, v);
                }
                if (newMeasuredPoint != null) {
                    // Update a line endpoint to the touch location.
                    // This update is made thread-safe by the renderer.
                    updateLine(newMeasuredPoint);
                } else {
                    Log.w(TAG, "Point was null.");
                }

            } catch (TangoException t) {
                Toast.makeText(getApplicationContext(),
                        R.string.failed_measurement,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_measurement), t);
            } catch (SecurityException t) {
                Toast.makeText(getApplicationContext(),
                        R.string.failed_permissions,
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, getString(R.string.failed_permissions), t);
            }
        }
        return true;
    }

    /**
     * Use the Tango Support Library with point cloud data to calculate the depth
     * of the point closest to where the user touches the screen. It returns a
     * Vector3 in OpenGL world space.
     */
    private MeasuredPoint getDepthAtTouchPosition(float u, float v) {
        TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
        if (pointCloud == null) {
            return null;
        }

        double rgbTimestamp;
        TangoImageBuffer imageBuffer = mCurrentImageBuffer;
        if (mBilateralBox.isChecked()) {
            rgbTimestamp = imageBuffer.timestamp; // CPU.
        } else {
            rgbTimestamp = mRgbTimestampGlThread; // GPU.
        }

        TangoPoseData depthlTcolorPose = TangoSupport.getPoseAtTime(
                rgbTimestamp,
                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                TangoSupport.ENGINE_TANGO,
                TangoSupport.ENGINE_TANGO,
                TangoSupport.ROTATION_IGNORED);
        if (depthlTcolorPose.statusCode != TangoPoseData.POSE_VALID) {
            Log.w(TAG, "Could not get color camera transform at time "
                    + rgbTimestamp);
            return null;
        }

        float[] depthPoint;
        if (mBilateralBox.isChecked()) {
            depthPoint = TangoDepthInterpolation.getDepthAtPointBilateral(
                    pointCloud,
                    new double[]{0.0, 0.0, 0.0},
                    new double[]{0.0, 0.0, 0.0, 1.0},
                    imageBuffer,
                    u, v,
                    mDisplayRotation,
                    depthlTcolorPose.translation,
                    depthlTcolorPose.rotation);
        } else {
            depthPoint = TangoDepthInterpolation.getDepthAtPointNearestNeighbor(
                    pointCloud,
                    new double[]{0.0, 0.0, 0.0},
                    new double[]{0.0, 0.0, 0.0, 1.0},
                    u, v,
                    mDisplayRotation,
                    depthlTcolorPose.translation,
                    depthlTcolorPose.rotation);
        }

        if (depthPoint == null) {
            return null;
        }

        return new MeasuredPoint(rgbTimestamp, depthPoint);
    }

    /**
     * Update the oldest line endpoint to the value passed into this function.
     * This will also flag the line for update on the next render pass.
     */
    private synchronized void updateLine(MeasuredPoint newPoint) {
        if (mPointSwitch) {
            mPointSwitch = !mPointSwitch;
            mMeasuredPoints[0] = newPoint;
            return;
        }
        mPointSwitch = !mPointSwitch;
        mMeasuredPoints[1] = newPoint;
    }

    /*
     * Remove all the points from the Scene.
     */
    private synchronized void clearLine() {
        mMeasuredPoints[0] = null;
        mMeasuredPoints[1] = null;
        mPointSwitch = true;
        mRenderer.setLine(null);
    }

    // Debug text UI update loop, updating at 10Hz.
    private Runnable mUpdateUiLoopRunnable = new Runnable() {
        public void run() {
            try {
                mDistanceTextview.setText(String.format("%.2f", mMeasuredDistance) + " meters");
            } catch (Exception e) {
                e.printStackTrace();
            }
            mHandler.postDelayed(this, UPDATE_UI_INTERVAL_MS);
        }
    };

    /**
     * Set the color camera background texture rotation and save the display rotation.
     */
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (mIsConnected) {
                    mRenderer.updateColorCameraTextureUvGlThread(mDisplayRotation);
                }
            }
        });
    }

    /**
     * Check to see if we have the necessary permissions for this app; ask for them if we don't.
     *
     * @return True if we have the necessary permissions, false if we don't.
     */
    private boolean checkAndRequestPermissions() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return false;
        }
        return true;
    }

    /**
     * Check to see if we have the necessary permissions for this app.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain that the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Java Point to point Example requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(PointToPointActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PointToPointActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();


        if (hasCameraPermission()) {
            bindTangoService();
        } else {
            Toast.makeText(this, "Java Point to point Example requires camera permission",
                    Toast.LENGTH_LONG).show();
        }
    }
}
