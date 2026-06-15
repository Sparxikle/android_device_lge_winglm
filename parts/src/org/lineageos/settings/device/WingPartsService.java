package org.lineageos.settings.device;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class WingPartsService extends Service {
    private static final String TAG = "WingPartsService";
    private PopupCameraManager mPopupCameraManager;
    private SecondaryScreenMediaManager mSecondaryScreenMediaManager;

    private final BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SHUTDOWN.equals(action) || Intent.ACTION_REBOOT.equals(action)) {
                Log.i(TAG, "Device is " + (Intent.ACTION_SHUTDOWN.equals(action) ? "shutting down" : "rebooting") + ". Stopping modules.");
                if (mPopupCameraManager != null) {
                    mPopupCameraManager.shutdown();
                }
                if (mSecondaryScreenMediaManager != null) {
                    mSecondaryScreenMediaManager.shutdown();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Initializing WingParts Modules...");
        
        mPopupCameraManager = new PopupCameraManager(this);
        mPopupCameraManager.start();

        mSecondaryScreenMediaManager = new SecondaryScreenMediaManager(this);
        mSecondaryScreenMediaManager.start();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_REBOOT);
        registerReceiver(mShutdownReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mShutdownReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; 
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Headless, no UI binding needed
    }
}
