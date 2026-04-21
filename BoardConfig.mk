#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from common device tree
include device/lge/sm7250-common/BoardConfigCommon.mk

# Kernel
BOARD_KERNEL_CMDLINE += androidboot.hardware=winglm
TARGET_KERNEL_CONFIG := vendor/lineageos_wing_defconfig

# Properties
TARGET_SYSTEM_PROP += $(DEVICE_PATH)/system.prop
TARGET_VENDOR_PROP += $(DEVICE_PATH)/vendor.prop

# Inherit vendor BoardConfig
include vendor/lge/winglm/BoardConfigVendor.mk
