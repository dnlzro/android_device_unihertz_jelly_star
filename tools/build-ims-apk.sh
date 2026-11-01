#!/usr/bin/env bash
# Build device/unihertz/jelly_star/ims/ from the extracted stock system_ext.
# usage: build-ims-apk.sh STOCK_SYSTEM_EXT_DIR
#
# - merges mediatek-ims-base, mediatek-common and a TelephonyMetrics stub into the APK
#   (stock loads the jars from the boot classpath, AOSP r4 lacks the class)
# - targetSdkVersion 33: WfcHandler registers receivers without the export flag
# - patches the app so IMS recovers after airplane mode and Wi-Fi reconnects (patch_ims_smali.py)
# The build re-signs the APK with the platform key.
set -euo pipefail
stock=$1
here=$(cd "$(dirname "$0")" && pwd)
ims=$here/../device/unihertz/jelly_star/ims
work=$(mktemp -d)
mkdir -p "$ims/lib64"

apktool_jar=$(nix eval --raw nixpkgs#apktool.outPath)/libexec/apktool/apktool.jar
nix build --no-link nixpkgs#apktool
nix shell nixpkgs#jre_headless -c java -cp "$apktool_jar" "$here/stub/Asm.java" \
  "$here/stub/TelephonyMetrics.smali" "$work/classes.dex"
(cd "$work" && python3 -I -c "import zipfile; z = zipfile.ZipFile('stub.jar', 'w'); z.write('classes.dex'); z.close()")

unzip -q -p "$stock/priv-app/ImsService/ImsService.apk" AndroidManifest.xml > "$work/AndroidManifest.in"
python3 -I "$here/set_target_sdk.py" "$work/AndroidManifest.in" "$work/AndroidManifest.xml" 33
python3 -I "$here/repack.py" "$stock/priv-app/ImsService/ImsService.apk" "$ims/ImsService.apk" \
  --manifest "$work/AndroidManifest.xml" \
  "$stock/framework/mediatek-ims-base.jar" "$stock/framework/mediatek-common.jar" "$work/stub.jar"

nix shell nixpkgs#apktool nixpkgs#jre_headless -c apktool d -f -r -o "$work/decoded" "$ims/ImsService.apk"
python3 -I "$here/patch_ims_smali.py" "$work/decoded"
nix shell nixpkgs#apktool nixpkgs#jre_headless -c apktool b -f "$work/decoded" -o "$work/rebuilt.apk"
unzip -q -p "$work/rebuilt.apk" classes.dex > "$work/classes.dex"
python3 -I "$here/replace_dex.py" "$ims/ImsService.apk" "$work/patched.apk" classes.dex "$work/classes.dex"
mv "$work/patched.apk" "$ims/ImsService.apk"

for lib in libmtk_vt_wrapper libvcodec_cap libvcodec_capenc \
  vendor.mediatek.hardware.videotelephony-V1-ndk vendor.mediatek.hardware.videotelephony@1.0; do
  cp "$stock/lib64/$lib.so" "$ims/lib64/"
done
chmod 644 "$ims"/lib64/*.so "$ims/ImsService.apk"

cp "$stock/etc/sysconfig/com.mediatek.ims.config.xml" "$ims/"
{
  echo '<?xml version="1.0" encoding="utf-8"?>'
  echo '<permissions>'
  awk '/privapp-permissions package="com.mediatek.ims"/,/<\/privapp-permissions>/' \
    "$stock/etc/permissions/system-ext-permissions-mediatek.xml"
  echo '</permissions>'
} > "$ims/privapp-permissions-mtkims.xml"
