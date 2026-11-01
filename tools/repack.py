#!/usr/bin/env python3
"""Copy an APK without its v1 signature, appending dex files from jars as classesN.dex.

Stored entries are padded to 4-byte alignment (zipalign's job), dex is stored uncompressed.
usage: repack.py IN.apk OUT.apk [--manifest FILE] JAR...
"""
import sys
import zipfile


def add(out, name, data, stored):
    zi = zipfile.ZipInfo(name, date_time=(2008, 1, 1, 0, 0, 0))
    zi.external_attr = 0o644 << 16
    if stored:
        zi.compress_type = zipfile.ZIP_STORED
        data_start = out.fp.tell() + 30 + len(name.encode())
        zi.extra = b"\0" * ((4 - data_start % 4) % 4)
    else:
        zi.compress_type = zipfile.ZIP_DEFLATED
    out.writestr(zi, data)


args = sys.argv[1:]
manifest = None
if "--manifest" in args:
    i = args.index("--manifest")
    manifest = open(args[i + 1], "rb").read()
    del args[i : i + 2]
src, dst, *jars = args

with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as out:
    n = 1
    for info in zin.infolist():
        name = info.filename
        if name.startswith("META-INF/") and name.rsplit(".", 1)[-1] in ("RSA", "SF", "MF", "DSA", "EC"):
            continue
        is_dex = name.startswith("classes") and name.endswith(".dex")
        n += is_dex and name != "classes.dex"
        data = manifest if manifest is not None and name == "AndroidManifest.xml" else zin.read(name)
        add(out, name, data, is_dex or info.compress_type == zipfile.ZIP_STORED)
    for jar in jars:
        with zipfile.ZipFile(jar) as zj:
            for name in sorted(x for x in zj.namelist() if x.startswith("classes") and x.endswith(".dex")):
                n += 1
                add(out, f"classes{n}.dex", zj.read(name), True)
                print(f"classes{n}.dex <- {jar.rsplit('/', 1)[-1]}:{name}")
