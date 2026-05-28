package org.lineageos.settings.device;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import vendor.wing.hardware.motor.IMotor;

public class MotorNative {
    private static final String TAG = "WingParts-MotorNative";
    private static final String SERVICE_NAME = "vendor.wing.hardware.motor.IMotor/default";
    private static IMotor sMotorService;

    private static synchronized IMotor getService() {
        if (sMotorService == null) {
            IBinder binder = ServiceManager.getService(SERVICE_NAME);
            if (binder == null) {
                // Fallback: Try without the /default suffix just in case
                binder = ServiceManager.getService("vendor.wing.hardware.motor.IMotor");
            }
            
            if (binder != null) {
                try {
                    binder.linkToDeath(() -> sMotorService = null, 0);
                } catch (RemoteException e) {
                    // Ignore
                }
                sMotorService = IMotor.Stub.asInterface(binder);
            } else {
                Log.e(TAG, "Failed to get motor service: " + SERVICE_NAME);
            }
        }
        return sMotorService;
    }

    public static void moveUp() {
        IMotor service = getService();
        if (service != null) {
            try {
                service.moveUp();
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in moveUp", e);
                sMotorService = null; // Reset for retry
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
                sMotorService = null; // Reset for retry
            }
        }
    }
}
