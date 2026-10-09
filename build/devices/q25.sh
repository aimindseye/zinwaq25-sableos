#!/usr/bin/env bash
# Zinwa Q25 device adapter.
# shellcheck disable=SC2034 # variables are read by build/sable.sh and docs
#
# Same fail-closed capability flags as the other SableOS device adapters. A flag
# moves to YES only after the matching gate in docs/QUALIFICATION.md has
# evidence. Flashing stays manual (docs/INSTALL.md): there is no flash function.

SABLE_DEVICE_CANONICAL=q25
SABLE_DEVICE_CODENAME=Q25
SABLE_DEVICE_SOC=mt6789
SABLE_DEVICE_ARTIFACT_KIND=full-device-images
SABLE_DEVICE_BASE=lineage-23.2

SABLE_DEVICE_BUILD_SUPPORTED=YES
SABLE_DEVICE_BUILD_TESTED=NO
SABLE_DEVICE_BOOT_QUALIFIED=NO
SABLE_DEVICE_FLASH_SUPPORTED=NO
SABLE_DEVICE_FLASH_TRANSPORT=manual-fastboot-and-recovery-sideload
SABLE_DEVICE_PRESERVED_DATA_UPDATE_SUPPORTED=NO
SABLE_DEVICE_RELEASE_SIGNING_SUPPORTED=NO

# Partition facts from the LineageOS device tree (BoardConfig.mk, fastboot-info.txt).
SABLE_DEVICE_VIRTUAL_AB=YES
SABLE_DEVICE_DYNAMIC_PARTITIONS=YES
SABLE_DEVICE_BOOT_HEADER_VERSION=4
SABLE_DEVICE_RECOVERY_PARTITION=vendor_boot
SABLE_DEVICE_INSTALL_IMAGES="boot dtbo vbmeta vendor_boot"
