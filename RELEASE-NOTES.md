# Spid9y Cam 0.1.0-beta.1

First public beta of the free, open-source Android → Windows USB camera tool for OBS.

## Downloads

- **Spid9y-Cam-Windows.zip** — extract the entire folder, run Setup Runtime.cmd once, then Start Native Camera.cmd. Includes the signed APK, own code/scripts, and docs; runtime dependencies download separately from their publishers with hash checks.
- **Spid9y-Cam-Android.apk** — signed Android app for manual installation if USB installation is blocked.
- **SHA256SUMS.txt** — checksums for both assets.

## Features

1080p / 16:9 / 30 fps target, native hardware H.264, front camera default, local browser controls, direct OBS Media Source, optional OBS Virtual Camera output, and optional native mirroring for that webcam output. No account, cloud relay, ads, watermark, or donation gate in this release.

## Validation and limits

The underlying native prototype was physically tested on a Redmi Note 9 Pro / Android 12 and Windows x64, including camera locks, 16:9 stream metadata, DirectShow output, and about 30 fps in short tests. Fresh runtime setup and generated-input receiver tests pass for the public code. The exact branded release APK needs additional device validation; see the compatibility table and issues for results.

This is a prerelease, not a production compatibility guarantee. The app must stay visible with the phone unlocked. Video only. No iPhone support, proprietary Xiaomi processing, or separate named Spid9y virtual-camera driver. Optional webcam output shares OBS's virtual-camera device; do not run OBS's own virtual-camera output simultaneously. Native mirror processing preserves pixels but is not zero-overhead.

## Support

Bug reports and additional phone tests are welcome. Donations are not configured yet; no payment account is linked. Please read the setup guide before adding an OBS source.
