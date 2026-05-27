package vendor.wing.hardware.motor;

import vendor.wing.hardware.motor.IMotorCallback;

@VintfStability
interface IMotor {
    /**
     * Move the motor to the UP position (fully extended).
     */
    void moveUp();

    /**
     * Move the motor to the DOWN position (fully retracted).
     */
    void moveDown();

    /**
     * Stop the motor immediately.
     */
    void stop();

    /**
     * Get the current motor status/max PPS.
     */
    int getStatus();

    /**
     * Register a callback for motor events (like free-fall).
     */
    void registerCallback(in IMotorCallback callback);
}
