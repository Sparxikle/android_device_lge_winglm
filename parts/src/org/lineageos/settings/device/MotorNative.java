package org.lineageos.settings.device;

public class MotorNative {
    static {
        System.loadLibrary("wingmotor_jni");
    }

    public static native void moveUp();
    public static native void moveDown();
}
