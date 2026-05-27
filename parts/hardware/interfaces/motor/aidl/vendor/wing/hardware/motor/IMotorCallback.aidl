package vendor.wing.hardware.motor;

/**
 * Callback interface for motor events.
 */
@VintfStability
interface IMotorCallback {
    /**
     * Triggered when a free-fall event is detected by the hardware HAL.
     */
    void onNotifyFall();
}
