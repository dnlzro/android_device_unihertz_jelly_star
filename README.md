# Jelly Star AOSP device tree

A from-scratch AOSP 16 (`android-16.0.0_r4`) device tree for the Unihertz Jelly Star (MT6789), on
the stock vendor partitions and kernel. It's a base to build on: a plain AOSP system with the
hardware working, without bundled user-facing apps.

## Contents

| Path | What it is |
|---|---|
| `device/unihertz/jelly_star/` | The device tree. Goes in `device/unihertz/jelly_star` in AOSP |
| `patches/<project>/` | Changes to AOSP projects, applied with `git apply` in that project |
| `tools/` | Scripts that generate inputs in the device tree from stock firmware |

## Patches

| Project | Why |
|---|---|
| `frameworks/av` | Lets the audio HAL accept analog wired devices, without which the headphone jack fails |
| `packages/apps/Settings` | Face enrollment: the face HAL reads camera frames from a file, which Settings must write |
| `packages/apps/Settings` | Face setup: hides the text and icon rows AOSP leaves blank for manufacturers to fill in |
| `packages/apps/Settings` | Face setup: plays AOSP's animation, since its video is an empty file |

## Files that aren't in Git

`ims/ImsService.apk` and `ims/com.mediatek.ims.config.xml` come from stock firmware, so they stay out
of Git. **The ROM doesn't build without them.** To put them back:

```bash
tools/restore-stock-files.sh SYSTEM_EXT_IMAGE
```

The image is stock's `system_ext` logical partition, in EROFS. The script builds `ImsService.apk` from
stock's, merging in two stock framework jars and a stub for a class AOSP dropped. The results are
byte-identical from run to run.

## Building

```bash
source build/envsetup.sh
lunch aosp_jelly_star-bp4a-userdebug
m
```

Only `system.img` is flashed. The rest of the phone stays stock.
