package vendor.wing.hardware.motor;

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
     * Useful for the 0xDC06 handshake.
     */
    int getStatus();
}
