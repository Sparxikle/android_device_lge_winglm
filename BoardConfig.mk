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
TARGET_KERNEL_NO_GCC := true

# Properties
TARGET_VENDOR_PROP += $(DEVICE_PATH)/vendor.prop
TARGET_PRODUCT_PROP += $(DEVICE_PATH)/product.prop

# Inherit vendor BoardConfig
include vendor/lge/winglm/BoardConfigVendor.mk
