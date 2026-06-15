package org.lineageos.settings.device;

import android.app.AlertDialog;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.view.WindowManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PopupCameraManager {
    private static final String TAG = "WingParts-PopupCam";
    private static final String FRONT_CAMERA_ID = "1";
    private static final String STATE_PROP = "persist.vendor.wing.motor.state";
    
    private static final int DEBOUNCE_DELAY_MS = 100;
    private static final double FREEFALL_G_THRESHOLD = 2.5;
    private static final int FREEFALL_TICKS_REQUIRED = 2;

    private static final String SOUND_UP_PATH = "/product/media/audio/ui/popup_up.ogg";
    private static final String SOUND_DOWN_PATH = "/product/media/audio/ui/popup_down.ogg";
    private static final float SOUND_VOLUME = 0.5f; 

    private final Context mContext;
    private final CameraManager mCameraManager;
    private final SensorManager mSensorManager;
    private final Handler mHandler;
    
    // --- The Hardware Thread ---
    private final ExecutorService mHardwareExecutor = Executors.newSingleThreadExecutor();
    
    private final SoundPool mSoundPool;
    private final Sensor mAccelSensor;
    
    private int mSoundIdUp;
    private int mSoundIdDown;
    private boolean mIsCameraUp = false;
    private boolean mIsShuttingDown = false;
    private int mFreefallTicks = 0;

    private final SensorEventListener mAccelListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mIsShuttingDown) return;
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double gVector = Math.sqrt(x*x + y*y + z*z);
            
            if (gVector < FREEFALL_G_THRESHOLD) { 
                mFreefallTicks++;
                if (mFreefallTicks >= FREEFALL_TICKS_REQUIRED) { 
                    Log.e(TAG, "EMERGENCY: Accelerometer Free Fall! Retracting!");
                    triggerEmergencyRetract();
                }
            } else {
                mFreefallTicks = 0; 
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final Runnable mRetractRunnable = () -> {
        Log.i(TAG, "Debounce timer expired. Retracting motor.");
        retractCamera();
    };

    public PopupCameraManager(Context context) {
        mContext = context;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());

        mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Sync initial state with the persisted property
        mIsCameraUp = SystemProperties.get(STATE_PROP, "down").equals("up");
        Log.i(TAG, "Initial motor state: " + (mIsCameraUp ? "UP" : "DOWN"));

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attrs)
                .build();

        mSoundIdUp = mSoundPool.load(SOUND_UP_PATH, 1);
        mSoundIdDown = mSoundPool.load(SOUND_DOWN_PATH, 1);
    }

    public void start() {
        mCameraManager.registerAvailabilityCallback(mAvailabilityCallback, mHandler);
        Log.i(TAG, "PopupCameraManager started.");
    }

    public void shutdown() {
        Log.i(TAG, "Shutdown signal received. Cleaning up.");
        mIsShuttingDown = true;
        mHandler.removeCallbacks(mRetractRunnable);
        mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
        
        // Ensure motor is retracted during shutdown
        if (mIsCameraUp) {
            retractCamera();
        }
        
        mHardwareExecutor.shutdown();
    }

    private void triggerEmergencyRetract() {
        if (mIsShuttingDown) return;
        mHandler.removeCallbacks(mRetractRunnable); 
        retractCamera();

        mHandler.post(() -> {
            AlertDialog dialog = new AlertDialog.Builder(mContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Drop Detected")
                .setMessage("The front camera has closed to protect the lens.")
                .setCancelable(false) 
                .setPositiveButton("OK", (d, which) -> {
                    if (mIsShuttingDown) return;
                    Log.i(TAG, "User acknowledged drop. Re-opening motor.");
                    if (!mIsCameraUp) {
                        mIsCameraUp = true;
                        
                        mHardwareExecutor.execute(() -> {
                            if (mIsShuttingDown) return;
                            mSoundPool.play(mSoundIdUp, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1.0f);
                            MotorNative.moveUp();
                        });
                        
                        if (mAccelSensor != null) {
                            mSensorManager.registerListener(mAccelListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
                        }
                    }
                })
                .create();

            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            dialog.show();
        });
    }

    private void retractCamera() {
        if (!mIsCameraUp) return;
        mIsCameraUp = false;
        mFreefallTicks = 0; 
        
        if (mAccelSensor != null) {
            mSensorManager.unregisterListener(mAccelListener);
        }

        mHardwareExecutor.execute(() -> {
            mSoundPool.play(mSoundIdDown, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1.0f);
            MotorNative.moveDown();
        });
    }

    private final CameraManager.AvailabilityCallback mAvailabilityCallback =
            new CameraManager.AvailabilityCallback() {

        @Override
        public void onCameraUnavailable(String cameraId) {
            if (mIsShuttingDown) return;
            if (FRONT_CAMERA_ID.equals(cameraId)) {
                Log.i(TAG, "Front camera opened. Moving UP.");
                mHandler.removeCallbacks(mRetractRunnable);
                
                if (!mIsCameraUp) {
                    mIsCameraUp = true;

                    mHardwareExecutor.execute(() -> {
                        if (mIsShuttingDown) return;
                        mSoundPool.play(mSoundIdUp, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1.0f);
                        MotorNative.moveUp();
                    });
                }

                if (mAccelSensor != null) {
                    mSensorManager.registerListener(mAccelListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
                }
            }
        }

        @Override
        public void onCameraAvailable(String cameraId) {
            if (mIsShuttingDown) return;
            if (FRONT_CAMERA_ID.equals(cameraId)) {
                Log.i(TAG, "Front camera closed. Starting debounce timer.");
                mHandler.postDelayed(mRetractRunnable, DEBOUNCE_DELAY_MS);
            }
        }
    };
}
