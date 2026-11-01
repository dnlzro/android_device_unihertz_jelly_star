#!/usr/bin/env python3
"""Set android:targetSdkVersion in a binary AndroidManifest.xml.

usage: set_target_sdk.py MANIFEST_IN MANIFEST_OUT SDK
"""
import struct
import sys

TARGET_SDK_ATTR = 0x01010270
RES_XML_RESOURCE_MAP = 0x0180
RES_XML_START_ELEMENT = 0x0102

src, dst, sdk = sys.argv[1], sys.argv[2], int(sys.argv[3])
b = bytearray(open(src, "rb").read())
offset = struct.unpack_from("<H", b, 2)[0]
attr_index = None
patched = 0
while offset < len(b):
    ctype, header, size = struct.unpack_from("<HHI", b, offset)
    if ctype == RES_XML_RESOURCE_MAP:
        count = (size - header) // 4
        ids = struct.unpack_from(f"<{count}I", b, offset + header)
        attr_index = ids.index(TARGET_SDK_ATTR)
    elif ctype == RES_XML_START_ELEMENT:
        attr_start, attr_size, attr_count = struct.unpack_from("<HHH", b, offset + 16 + 8)
        first = offset + 16 + attr_start
        for i in range(attr_count):
            a = first + i * attr_size
            if struct.unpack_from("<I", b, a + 4)[0] == attr_index:
                old = struct.unpack_from("<I", b, a + 16)[0]
                struct.pack_into("<I", b, a + 16, sdk)
                print(f"targetSdkVersion {old} -> {sdk}")
                patched += 1
    offset += size
if patched != 1:
    sys.exit(f"expected one targetSdkVersion attribute, patched {patched}")
open(dst, "wb").write(b)
