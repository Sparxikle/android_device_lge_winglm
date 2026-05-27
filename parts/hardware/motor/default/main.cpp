#include "Motor.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using aidl::vendor::wing::hardware::motor::Motor;

int main(int argc, char** argv) {
    android::base::InitLogging(argv, android::base::LogdLogger());
    
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    std::shared_ptr<Motor> motor = ndk::SharedRefBase::make<Motor>();

    const std::string instance = std::string() + Motor::descriptor + "/default";
    binder_status_t status = AServiceManager_addService(motor->asBinder().get(), instance.c_str());
    CHECK_EQ(status, STATUS_OK);

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach here
}
