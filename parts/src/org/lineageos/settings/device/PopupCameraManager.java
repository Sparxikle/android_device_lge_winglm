package org.lineageos.settings.device;

import android.app.AlertDialog;
import android.content.Context;
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

public class PopupCameraManager implements MotorNative.FallListener {
    private static final String TAG = "WingParts-PopupCam";
    private static final String FRONT_CAMERA_ID = "1";
    private static final String STATE_PROP = "persist.vendor.wing.motor.state";
    
    private static final int DEBOUNCE_DELAY_MS = 100;

    private static final String SOUND_UP_PATH = "/product/media/audio/ui/popup_up.ogg";
    private static final String SOUND_DOWN_PATH = "/product/media/audio/ui/popup_down.ogg";
    private static final float SOUND_VOLUME = 0.5f; 

    private final Context mContext;
    private final CameraManager mCameraManager;
    private final Handler mHandler;
    
    private final ExecutorService mHardwareExecutor = Executors.newSingleThreadExecutor();
    
    private final SoundPool mSoundPool;
    
    private int mSoundIdUp;
    private int mSoundIdDown;
    private boolean mIsCameraUp = false;
    private boolean mIsShuttingDown = false;

    private final Runnable mRetractRunnable = () -> {
        Log.i(TAG, "Debounce timer expired. Retracting motor.");
        retractCamera();
    };

    public PopupCameraManager(Context context) {
        mContext = context;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());

        // Sync initial state
        mIsCameraUp = SystemProperties.get(STATE_PROP, "down").equals("up");

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

        // Register for native fall events
        MotorNative.setFallListener(this);
    }

    public void start() {
        mCameraManager.registerAvailabilityCallback(mAvailabilityCallback, mHandler);
        Log.i(TAG, "PopupCameraManager started.");
    }

    public void shutdown() {
        mIsShuttingDown = true;
        mHandler.removeCallbacks(mRetractRunnable);
        mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
        
        if (mIsCameraUp) {
            retractCamera();
        }
        
        mHardwareExecutor.shutdown();
    }

    @Override
    public void onFallDetected() {
        Log.e(TAG, "Emergency: Native Free Fall Detected!");
        mIsCameraUp = false; // HAL already retracted it
        mHandler.removeCallbacks(mRetractRunnable);

        mHandler.post(() -> {
            AlertDialog dialog = new AlertDialog.Builder(mContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Drop Detected")
                .setMessage("The front camera has closed to protect the lens.")
                .setCancelable(false) 
                .setPositiveButton("OK", (d, which) -> {
                    Log.i(TAG, "User acknowledged drop.");
                })
                .create();

            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            dialog.show();
        });
    }

    private void retractCamera() {
        if (!mIsCameraUp) return;
        mIsCameraUp = false;
        
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
                        mSoundPool.play(mSoundIdUp, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1.0f);
                        MotorNative.moveUp();
                    });
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
