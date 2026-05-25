#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/lge/winglm

DEVICE_NAME := winglm

# Inherit from the common device configuration.
$(call inherit-product, device/lge/sm7250-common/sm7250-common.mk)

# Audio
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/audio/audio_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_platform_info.xml \
    $(LOCAL_PATH)/audio/audio_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_platform_info_intcodec.xml \
    $(LOCAL_PATH)/audio/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    $(LOCAL_PATH)/audio/mixer_paths.xml:$(TARGET_COPY_OUT_VENDOR)/etc/mixer_paths.xml \

# Fingerprint
PRODUCT_PACKAGES += \
    android.hardware.biometrics.fingerprint@2.3-service.lge \
    sensors.lge

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/sensors/hals.conf:$(TARGET_COPY_OUT_VENDOR)/etc/sensors/hals.conf

# Device State
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/devicestate/device_state_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/devicestate/device_state_configuration.xml

# Display layout
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/display/display_layout_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_layout_configuration.xml \
    $(LOCAL_PATH)/configs/display/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml

$(call soong_config_set_bool,stagefright,target_disable_thumbnail_block_model,true)

# Init Script
PRODUCT_COPY_FILES += \
    device/lge/winglm/configs/init/vendor.lge.hardware.sensors.devicecontext@1.0-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.lge.hardware.sensors.devicecontext@1.0-service.rc

# Custom Wing fixes
PRODUCT_PACKAGES += \
		WingParts

PRODUCT_COPY_FILES += \
    device/lge/winglm/audio/cam_popup_open.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/cam_popup_open.ogg \
    device/lge/winglm/audio/cam_popup_close.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/cam_popup_close.ogg


# Input
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/keylayout/gpio-keys.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/gpio-keys.kl \
    $(LOCAL_PATH)/configs/keylayout/gpio-keys.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/gpio-keys.idc \
    $(LOCAL_PATH)/configs/idc/touch_sub_dev.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/touch_sub_dev.idc \
    $(LOCAL_PATH)/configs/idc/touch_sub_dex_dev.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/touch_sub_dex_dev.idc

# Input port associations
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/input/input-port-associations.xml:$(TARGET_COPY_OUT_VENDOR)/etc/input-port-associations.xml

$(call soong_config_set,LGE_FINGERPRINT_HAL,TARGET_HAS_EGISTEC_UDFPS,true)

# Overlays
PRODUCT_PACKAGES += \
    ApertureOverlayWinglm \
    FrameworksResOverlayWinglm \
    SettingsOverlayWinglm \
    SystemUIOverlayWinglm

# Soong namespace
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Inherit from vendor makefiles.
$(call inherit-product, vendor/lge/winglm/winglm-vendor.mk)

PRODUCT_SYSTEM_PROPERTIES += \
    ro.recovery.ui.brightness_file=/sys/class/backlight/panel0-backlight/brightness \
    ro.recovery.ui.max_brightness_file=/sys/class/backlight/panel0-backlight/max_brightness \
    ro.recovery.ui.brightness_normal_percent=50 \
    ro.recovery.ui.brightness_dimmed_percent=25


