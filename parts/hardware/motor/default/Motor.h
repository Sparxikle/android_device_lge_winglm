#pragma once

#include <aidl/vendor/wing/hardware/motor/BnMotor.h>
#include <android-base/logging.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <unistd.h>

namespace aidl {
namespace vendor {
namespace wing {
namespace hardware {
namespace motor {

class Motor : public BnMotor {
  public:
    Motor();
    ndk::ScopedAStatus moveUp() override;
    ndk::ScopedAStatus moveDown() override;
    ndk::ScopedAStatus stop() override;
    ndk::ScopedAStatus getStatus(int32_t* _aidl_return) override;

  private:
    bool executeIoctl(uint32_t cmd);
    void initializeHardware();
    
    bool mIsUp;
    bool mInitialized;
};

}  // namespace motor
}  // namespace hardware
}  // namespace wing
}  // namespace vendor
}  // namespace aidl
