#include <jni.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <poll.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include <string.h>

#define LOG_TAG "WingParts-MotorJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MOTOR_UP          0xDC01
#define MOTOR_DOWN        0xDC02
#define MOTOR_GET_MAX_PPS 0xDC06
#define DEVICE_NODE       "/dev/stdrv"
#define STATE_PROP        "persist.vendor.wing.motor.state"

// Hardware Safety State Tracking
static bool g_is_initialized = false;
static bool g_is_up = false;

// Internal function to execute the raw ioctl safely
bool executeMotorIoctl(int cmd) {
    int fd = open(DEVICE_NODE, O_RDWR);
    if (fd < 0) {
        LOGE("Failed to open %s. Check SELinux or permissions.", DEVICE_NODE);
        return false;
    }

    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;

    LOGI("Sending ioctl command: 0x%X", cmd);
    int ret = ioctl(fd, cmd, 0);
    
    if (ret < 0) {
        LOGE("ioctl failed with return code: %d", ret);
        close(fd);
        return false;
    }

    // Wait up to 5 seconds for the physical movement to complete
    int poll_ret = poll(&pfd, 1, 5000);
    if (poll_ret > 0) {
        LOGI("Driver signaled completion successfully.");
        char buf[16];
        read(fd, buf, sizeof(buf)); // Clear pending interrupt
        close(fd);
        return true;
    } else {
        LOGE("Driver poll timeout (5s) or error. Motor may be stuck!");
        close(fd);
        return false;
    }
}

// Ensure the driver is awake and clear faults on boot
void initializeHardwareIfNeeded() {
    if (g_is_initialized) return;

    int fd = open(DEVICE_NODE, O_RDWR);
    if (fd >= 0) {
        int max_pps = 0;
        // 0xDC06 is a mandatory handshake to clear 'move_status' in the kernel driver
        if (ioctl(fd, MOTOR_GET_MAX_PPS, &max_pps) >= 0) {
            LOGI("Hardware Handshake successful. Max PPS: %d", max_pps);
            
            // Restore state from property to prevent redundant movements on service restart
            char prop_value[PROP_VALUE_MAX];
            if (__system_property_get(STATE_PROP, prop_value) > 0) {
                g_is_up = (strcmp(prop_value, "up") == 0);
                LOGI("Restored motor state from property: %s", g_is_up ? "UP" : "DOWN");
            } else {
                LOGI("No state property found. Defaulting to DOWN.");
                g_is_up = false;
                __system_property_set(STATE_PROP, "down");
            }
            
            g_is_initialized = true;
        } else {
            LOGE("Hardware Handshake failed.");
        }
        close(fd);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_lineageos_settings_device_MotorNative_moveUp(JNIEnv* env, jclass clazz) {
    initializeHardwareIfNeeded();

    if (g_is_up) {
        LOGI("Safety Check: Motor is already UP. Ignoring command.");
        return;
    }

    if (executeMotorIoctl(MOTOR_UP)) {
        g_is_up = true;
        __system_property_set(STATE_PROP, "up");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_lineageos_settings_device_MotorNative_moveDown(JNIEnv* env, jclass clazz) {
    initializeHardwareIfNeeded();

    if (!g_is_up) {
        LOGI("Safety Check: Motor is already DOWN. Ignoring command.");
        return;
    }

    if (executeMotorIoctl(MOTOR_DOWN)) {
        g_is_up = false;
        __system_property_set(STATE_PROP, "down");
    }
}
