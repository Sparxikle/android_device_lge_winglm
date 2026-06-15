#include "Motor.h"
#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/strings.h>

#define MOTOR_UP          0xDC01
#define MOTOR_DOWN        0xDC02
#define MOTOR_STOP        0xDC05
#define MOTOR_GET_MAX_PPS 0xDC06
#define DEVICE_NODE       "/dev/stdrv"
#define STATE_PROP        "persist.vendor.wing.motor.state"
#define DIR_SYSFS         "/sys/devices/platform/soc/soc:lge,motor-stspin220/motor_dir"

namespace aidl {
namespace vendor {
namespace wing {
namespace hardware {
namespace motor {

Motor::Motor() : mIsUp(false), mInitialized(false) {
    initializeHardware();
}

void Motor::initializeHardware() {
    int retry_count = 0;
    int fd = -1;

    LOG(INFO) << "Initializing Motor HAL hardware...";

    // Wait for the device node to become available (up to 10 seconds)
    while (retry_count < 20) {
        fd = open(DEVICE_NODE, O_RDWR);
        if (fd >= 0) break;

        LOG(WARNING) << "Failed to open " << DEVICE_NODE << " (Attempt " << retry_count + 1 << "/20). Retrying in 500ms...";
        usleep(500000);
        retry_count++;
    }

    if (fd < 0) {
        LOG(ERROR) << "Could not open " << DEVICE_NODE << " after 10 seconds. Hardware might be missing.";
        return;
    }

    int32_t max_pps = 0;
    if (ioctl(fd, MOTOR_GET_MAX_PPS, &max_pps) >= 0) {
        LOG(INFO) << "Hardware Handshake successful. max_pps=" << max_pps;

        // Only retract if the system thinks the camera is currently UP.
        // This prevents annoying motor movement on every normal boot.
        std::string state = android::base::GetProperty(STATE_PROP, "down");
        if (state == "up") {
            LOG(INFO) << "!! SAFETY !! persist.state is 'up'. Performing safety retraction.";
            // Update property immediately to prevent repeated retractions if this is interrupted
            android::base::SetProperty(STATE_PROP, "down");
            if (ioctl(fd, MOTOR_DOWN, 0) >= 0) {
                struct pollfd pfd = {.fd = fd, .events = POLLIN};
                int poll_ret = poll(&pfd, 1, 5000);
                if (poll_ret > 0) {
                    LOG(INFO) << "Safety retraction successful.";
                    mIsUp = false;
                }
            }
        } else {
            LOG(INFO) << "Motor state is already 'down' in persist. Skipping startup retraction.";
            mIsUp = false;
        }

        mInitialized = true;
    } else {
        LOG(ERROR) << "Hardware Handshake failed.";
    }
    close(fd);
}


bool Motor::executeIoctl(uint32_t cmd) {
    int fd = open(DEVICE_NODE, O_RDWR);
    if (fd < 0) {
        LOG(ERROR) << "Failed to open " << DEVICE_NODE;
        return false;
    }

    if (ioctl(fd, cmd, 0) < 0) {
        PLOG(ERROR) << "ioctl 0x" << std::hex << cmd << " failed";
        close(fd);
        return false;
    }

    struct pollfd pfd = {.fd = fd, .events = POLLIN};
    if (poll(&pfd, 1, 5000) > 0) {
        LOG(INFO) << "Motor movement completed.";
        char buf[16];
        read(fd, buf, sizeof(buf));
        close(fd);
        return true;
    }

    LOG(ERROR) << "Motor movement timeout!";
    close(fd);
    return false;
}

ndk::ScopedAStatus Motor::moveUp() {
    if (!mInitialized) initializeHardware();
    if (mIsUp) return ndk::ScopedAStatus::ok();

    android::base::SetProperty(STATE_PROP, "up");
    if (executeIoctl(MOTOR_UP)) {
        mIsUp = true;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Motor::moveDown() {
    if (!mInitialized) initializeHardware();
    if (!mIsUp) return ndk::ScopedAStatus::ok();

    android::base::SetProperty(STATE_PROP, "down");
    if (executeIoctl(MOTOR_DOWN)) {
        mIsUp = false;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Motor::stop() {
    executeIoctl(MOTOR_STOP);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Motor::getStatus(int32_t* _aidl_return) {
    int fd = open(DEVICE_NODE, O_RDWR);
    if (fd < 0) {
        *_aidl_return = -1;
        return ndk::ScopedAStatus::fromServiceSpecificError(-1);
    }
    ioctl(fd, MOTOR_GET_MAX_PPS, _aidl_return);
    close(fd);
    return ndk::ScopedAStatus::ok();
}

}  // namespace motor
}  // namespace hardware
}  // namespace wing
}  // namespace vendor
}  // namespace aidl
