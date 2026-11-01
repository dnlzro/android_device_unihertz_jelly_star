# Generic arm64 board for now: same partitions and flash procedure as aosp_arm64.
# Only system.img is flashed; stock vendor, dlkm and kernel stay on the phone.
include build/make/target/board/generic_arm64/BoardConfig.mk

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += device/unihertz/jelly_star/sepolicy/private
