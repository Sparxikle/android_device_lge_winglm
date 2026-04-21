#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)
from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
)

import extract_utils.tools

namespace_imports = [
    'vendor/lge/sm7250-common',
    'vendor/qcom/opensource/display',
]


blob_fixups: blob_fixups_user_type = {
#    'vendor/lib64/liblgdnnsnpe.so': blob_fixup()
#        .replace_needed('libstdc++.so', 'libstdc++_vendor.so'),
#    'vendor/lib64/vendor.qti.hardware.camera.postproc@1.0-service-impl.so': blob_fixup()
#        .sig_replace('80 00 80 52 21 00 80 52 e7 09 00 94', '1f 20 03 d5 1f 20 03 d5 1f 20 03 d5'),
}  # fmt: skip

module = ExtractUtilsModule(
    'winglm',
    'lge',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sm7250-common', module.vendor
    )
    utils.run()
