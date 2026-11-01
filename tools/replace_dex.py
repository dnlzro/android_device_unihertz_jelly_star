#!/usr/bin/env python3
"""Copy a zip, replacing one stored dex entry and keeping 4-byte alignment.

usage: replace_dex.py IN.apk OUT.apk ENTRY NEW_FILE
"""
import sys
import zipfile

src, dst, entry, new = sys.argv[1:]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as out:
    for info in zin.infolist():
        data = open(new, "rb").read() if info.filename == entry else zin.read(info.filename)
        zi = zipfile.ZipInfo(info.filename, date_time=(2008, 1, 1, 0, 0, 0))
        zi.external_attr = 0o644 << 16
        zi.compress_type = info.compress_type
        if zi.compress_type == zipfile.ZIP_STORED:
            start = out.fp.tell() + 30 + len(info.filename.encode())
            zi.extra = b"\0" * ((4 - start % 4) % 4)
        out.writestr(zi, data)
