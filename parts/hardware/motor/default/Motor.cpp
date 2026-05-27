#include "Motor.h"
#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/looper.h>
#include <cmath>

#define MOTOR_UP          0xDC01
#define MOTOR_DOWN        0xDC02
#define MOTOR_STOP        0xDC05
#define MOTOR_GET_MAX_PPS 0xDC06
#define DEVICE_NODE       "/dev/stdrv"
#define STATE_PROP        "persist.vendor.wing.motor.state"
#define DIR_SYSFS         "/sys/devices/platform/soc/soc:lge,motor-stspin220/motor_dir"

#define FREEFALL_THRESHOLD 2.5f
#define FALL_TICKS_REQUIRED 2

namespace aidl {
namespace vendor {
namespace wing {
namespace hardware {
namespace motor {

Motor::Motor() : mIsUp(false), mInitialized(false), mSensorThreadRunning(false), 
                 mSensorManager(nullptr), mAccelSensor(nullptr), mSensorEventQueue(nullptr) {
    initializeHardware();
    startSensorThread();
}

Motor::~Motor() {
    mSensorThreadRunning = false;
    if (mSensorThread.joinable()) {
        mSensorThread.join();
    }
}

void Motor::startSensorThread() {
    mSensorManager = ASensorManager_getInstanceForPackage("vendor.wing.hardware.motor");
    if (!mSensorManager) {
        LOG(ERROR) << "Failed to get ASensorManager instance";
        return;
    }

    mAccelSensor = ASensorManager_getDefaultSensor(mSensorManager, ASENSOR_TYPE_ACCELEROMETER);
    if (!mAccelSensor) {
        LOG(ERROR) << "Failed to get Accelerometer sensor";
        return;
    }

    mSensorThreadRunning = true;
    mSensorThread = std::thread(&Motor::sensorLoop, this);
}

void Motor::sensorLoop() {
    ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    mSensorEventQueue = ASensorManager_createEventQueue(mSensorManager, looper, 0, nullptr, nullptr);
    ASensorEventQueue_enableSensor(mSensorEventQueue, mAccelSensor);
    ASensorEventQueue_setEventRate(mSensorEventQueue, mAccelSensor, 10000); // 100Hz

    ASensorEvent event;
    int fall_ticks = 0;

    while (mSensorThreadRunning) {
        if (ALooper_pollOnce(500, nullptr, nullptr, nullptr) == ALOOPER_POLL_WAKE) continue;

        while (ASensorEventQueue_getEvents(mSensorEventQueue, &event, 1) > 0) {
            float x = event.acceleration.x;
            float y = event.acceleration.y;
            float z = event.acceleration.z;
            float g = std::sqrt(x*x + y*y + z*z);

            if (g < FREEFALL_THRESHOLD) {
                fall_ticks++;
                if (fall_ticks >= FALL_TICKS_REQUIRED && mIsUp) {
                    LOG(ERROR) << "NATIVE FALL DETECTED! Emergency Retract!";
                    executeIoctl(MOTOR_DOWN);
                    mIsUp = false;
                    android::base::SetProperty(STATE_PROP, "down");
                    
                    std::lock_guard<std::mutex> lock(mCallbackLock);
                    if (mCallback) {
                        mCallback->onNotifyFall();
                    }
                    fall_ticks = 0;
                }
            } else {
                fall_ticks = 0;
            }
        }
    }
    
    ASensorEventQueue_disableSensor(mSensorEventQueue, mAccelSensor);
    ASensorManager_destroyEventQueue(mSensorManager, mSensorEventQueue);
}

void Motor::initializeHardware() {
    int retry_count = 0;
    int fd = -1;

    LOG(INFO) << "Initializing Motor HAL hardware...";

    while (retry_count < 20) {
        fd = open(DEVICE_NODE, O_RDWR);
        if (fd >= 0) break;
        usleep(500000);
        retry_count++;
    }

    if (fd < 0) return;

    int32_t max_pps = 0;
    if (ioctl(fd, MOTOR_GET_MAX_PPS, &max_pps) >= 0) {
        LOG(INFO) << "!! SAFETY !! Performing forced retraction on startup.";
        if (ioctl(fd, MOTOR_DOWN, 0) >= 0) {
            struct pollfd pfd = {.fd = fd, .events = POLLIN};
            if (poll(&pfd, 1, 5000) > 0) {
                mIsUp = false;
                android::base::SetProperty(STATE_PROP, "down");
            }
        }
        mInitialized = true;
    }
    close(fd);
}

bool Motor::executeIoctl(uint32_t cmd) {
    int fd = open(DEVICE_NODE, O_RDWR);
    if (fd < 0) return false;

    if (ioctl(fd, cmd, 0) < 0) {
        close(fd);
        return false;
    }

    struct pollfd pfd = {.fd = fd, .events = POLLIN};
    if (poll(&pfd, 1, 5000) > 0) {
        char buf[16];
        read(fd, buf, sizeof(buf));
        close(fd);
        return true;
    }

    close(fd);
    return false;
}

ndk::ScopedAStatus Motor::moveUp() {
    if (!mInitialized) initializeHardware();
    if (mIsUp) return ndk::ScopedAStatus::ok();

    if (executeIoctl(MOTOR_UP)) {
        mIsUp = true;
        android::base::SetProperty(STATE_PROP, "up");
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Motor::moveDown() {
    if (!mInitialized) initializeHardware();
    if (!mIsUp) return ndk::ScopedAStatus::ok();

    if (executeIoctl(MOTOR_DOWN)) {
        mIsUp = false;
        android::base::SetProperty(STATE_PROP, "down");
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

ndk::ScopedAStatus Motor::registerCallback(const std::shared_ptr<IMotorCallback>& callback) {
    std::lock_guard<std::mutex> lock(mCallbackLock);
    mCallback = callback;
    return ndk::ScopedAStatus::ok();
}

}  // namespace motor
}  // namespace hardware
}  // namespace wing
}  // namespace vendor
}  // namespace aidl
