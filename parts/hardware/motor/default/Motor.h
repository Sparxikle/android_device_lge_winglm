#pragma once

#include <aidl/vendor/wing/hardware/motor/BnMotor.h>
#include <aidl/vendor/wing/hardware/motor/IMotorCallback.h>
#include <android-base/logging.h>
#include <android/sensor.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <thread>
#include <mutex>

namespace aidl {
namespace vendor {
namespace wing {
namespace hardware {
namespace motor {

class Motor : public BnMotor {
  public:
    Motor();
    ~Motor();
    ndk::ScopedAStatus moveUp() override;
    ndk::ScopedAStatus moveDown() override;
    ndk::ScopedAStatus stop() override;
    ndk::ScopedAStatus getStatus(int32_t* _aidl_return) override;
    ndk::ScopedAStatus registerCallback(const std::shared_ptr<IMotorCallback>& callback) override;

  private:
    bool executeIoctl(uint32_t cmd);
    void initializeHardware();
    
    // Sensor management
    void startSensorThread();
    void sensorLoop();
    
    bool mIsUp;
    bool mInitialized;
    bool mSensorThreadRunning;
    
    std::shared_ptr<IMotorCallback> mCallback;
    std::mutex mCallbackLock;
    
    std::thread mSensorThread;

    ASensorManager* mSensorManager;
    const ASensor* mAccelSensor;
    ASensorEventQueue* mSensorEventQueue;
};

}  // namespace motor
}  // namespace hardware
}  // namespace wing
}  // namespace vendor
}  // namespace aidl
