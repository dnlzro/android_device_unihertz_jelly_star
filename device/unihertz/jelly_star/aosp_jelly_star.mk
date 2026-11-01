$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)

# aosp_arm64.mk only applies these when TARGET_PRODUCT is exactly aosp_arm64. They give
# us skip_mount.cfg and the VNDK apexes the stock vendor (API 31) needs. Its
# USE_SOONG_DEFINED_SYSTEM_IMAGE stays off so PRODUCT_PACKAGES below reach the image.
MODULE_BUILD_FROM_SOURCE ?= true
$(call inherit-product, $(SRC_TARGET_DIR)/product/gsi_release.mk)

PRODUCT_NAME := aosp_jelly_star
PRODUCT_DEVICE := jelly_star
PRODUCT_BRAND := Unihertz
PRODUCT_MANUFACTURER := Unihertz
PRODUCT_MODEL := Jelly Star

PRODUCT_PACKAGE_OVERLAYS += device/unihertz/jelly_star/overlay

# Board settings stock keeps on its system partition: the thermal shutdown command and RGB LED ownership.
PRODUCT_COPY_FILES += \
    device/unihertz/jelly_star/init/init.jelly_star.rc:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/init/init.jelly_star.rc

# Bluetooth profiles, as stock enables them. Without these it can scan and pair, but not connect.
PRODUCT_SYSTEM_PROPERTIES += \
    bluetooth.profile.a2dp.source.enabled=true \
    bluetooth.profile.asha.central.enabled=true \
    bluetooth.profile.avrcp.target.enabled=true \
    bluetooth.profile.bas.client.enabled=true \
    bluetooth.profile.gatt.enabled=true \
    bluetooth.profile.hfp.ag.enabled=true \
    bluetooth.profile.hid.device.enabled=true \
    bluetooth.profile.hid.host.enabled=true \
    bluetooth.profile.map.server.enabled=true \
    bluetooth.profile.opp.enabled=true \
    bluetooth.profile.pan.nap.enabled=true \
    bluetooth.profile.pan.panu.enabled=true \
    bluetooth.profile.pbap.server.enabled=true

# NFC: the stack and vendor HAL are present, Android just needs the feature files.
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.nfc.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/android.hardware.nfc.xml \
    frameworks/native/data/etc/android.hardware.nfc.hce.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/android.hardware.nfc.hce.xml \
    frameworks/native/data/etc/android.hardware.nfc.uicc.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/android.hardware.nfc.uicc.xml \
    frameworks/native/data/etc/com.nxp.mifare.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/com.nxp.mifare.xml

# IMS: MediaTek's ImsService from the stock system_ext, re-signed by this build.
PRODUCT_PACKAGES += \
    JellyIwlan \
    MtkImsService \
    libmtk_vt_wrapper \
    libvcodec_cap \
    libvcodec_capenc \
    vendor.mediatek.hardware.videotelephony-V1-ndk \
    vendor.mediatek.hardware.videotelephony@1.0

PRODUCT_COPY_FILES += \
    device/unihertz/jelly_star/ims/privapp-permissions-mtkims.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-mtkims.xml \
    device/unihertz/jelly_star/ims/com.mediatek.ims.config.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/sysconfig/com.mediatek.ims.config.xml
