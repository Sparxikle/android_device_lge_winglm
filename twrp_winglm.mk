#
# Copyright (C) 2025 The TWRP Open Source Project
#
# SPDX-License-Identifier: Apache-2.0
#

$(call inherit-product, device/lge/sm7250-common/sm7250-common.mk)
$(call inherit-product, vendor/twrp/config/common.mk)

PRODUCT_NAME := twrp_winglm
PRODUCT_DEVICE := winglm
PRODUCT_MANUFACTURER := LGE
PRODUCT_BRAND := lge
PRODUCT_MODEL := LM-F100

PRODUCT_GMS_CLIENTID_BASE := android-lge
