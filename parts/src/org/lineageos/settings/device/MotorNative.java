package org.lineageos.settings.device;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import vendor.wing.hardware.motor.IMotor;
import vendor.wing.hardware.motor.IMotorCallback;

public class MotorNative {
    private static final String TAG = "WingParts-MotorNative";
    private static final String SERVICE_NAME = "vendor.wing.hardware.motor.IMotor/default";
    private static IMotor sMotorService;
    private static MotorCallback sCallback;

    public interface FallListener {
        void onFallDetected();
    }

    private static FallListener sFallListener;

    private static class MotorCallback extends IMotorCallback.Stub {
        @Override
        public void onNotifyFall() {
            Log.e(TAG, "Native fall callback received!");
            if (sFallListener != null) {
                sFallListener.onFallDetected();
            }
        }

        @Override
        public String getInterfaceHash() {
            return IMotorCallback.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IMotorCallback.VERSION;
        }
    }

    private static synchronized IMotor getService() {
        if (sMotorService == null) {
            IBinder binder = ServiceManager.getService(SERVICE_NAME);
            if (binder != null) {
                sMotorService = IMotor.Stub.asInterface(binder);
                try {
                    sCallback = new MotorCallback();
                    sMotorService.registerCallback(sCallback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to register callback", e);
                }
            } else {
                Log.e(TAG, "Failed to get motor service");
            }
        }
        return sMotorService;
    }

    public static void setFallListener(FallListener listener) {
        sFallListener = listener;
        getService(); // Ensure service and callback are initialized
    }

    public static void moveUp() {
        IMotor service = getService();
        if (service != null) {
            try {
                service.moveUp();
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in moveUp", e);
                sMotorService = null;
            }
        }
    }

    public static void moveDown() {
        IMotor service = getService();
        if (service != null) {
            try {
                service.moveDown();
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in moveDown", e);
                sMotorService = null;
            }
        }
    }
}
