# Spid9y Cam

**A free, open-source USB camera for Android + Windows, built for OBS.**

Use a compatible Android phone as a **1920 × 1080, 16:9 webcam**, with exposure and white-balance locks, camera switching, zoom, and other supported controls from a local browser page. Camera frames go directly to Android's hardware H.264 encoder; video stays on your devices.

[Download the latest release](https://github.com/spiderman99sumit/spid9y-cam/releases) · [Setup and troubleshooting](docs/TROUBLESHOOTING.md) · [Tested compatibility](docs/COMPATIBILITY.md) · [Report a bug](https://github.com/spiderman99sumit/spid9y-cam/issues/new/choose)

> **First public beta.** The original capture pipeline was tested on a Redmi Note 9 Pro running Android 12. Targets of 1080p / 30 fps depend on your phone, lighting, heat, and USB connection. This is not a universal-device or zero-latency guarantee.

## What you get

- Native Android capture and hardware H.264 encoding at a **30 fps / 12 Mbps target**.
- Front camera by default, where a front lens exposes 1080p video.
- A local web panel for camera selection, start/stop, AE/AWB/focus locks, EV, zoom, torch, and stabilization. Unsupported controls are disabled.
- A direct **OBS Media Source** without another video encoding step.
- Optional **OBS Virtual Camera** output for the normal webcam dropdown.
- Optional native horizontal mirroring for the webcam output, without resizing or re-encoding. Mirroring has a processing cost; the direct Media Source stays unmirrored.
- No account, cloud relay, ads, watermark, recording, or paid feature gate in this release.

## Quick start

1. Download and extract **Spid9y-Cam-Windows.zip** from [Releases](https://github.com/spiderman99sumit/spid9y-cam/releases). Keep the entire folder together.
2. Double-click **Setup Runtime.cmd** once. It downloads version-pinned Node.js, FFmpeg, Google platform-tools, Python, NumPy, and pyvirtualcam from their publishers and verifies their hashes. Internet is needed for this step; capture afterwards is local.
3. Connect one Android phone with a **data-capable USB cable**. Unlock it, enable USB debugging, and accept its authorization prompt for your PC.
4. Run **Start Native Camera.cmd**. It installs the included Android APK if needed, opens the phone app, creates local USB forwards, and opens **http://localhost:8780**. If Android blocks USB installation, install **Spid9y-Cam-Android.apk** manually on the phone.
5. Allow Camera on the phone. Keep Spid9y Cam visible and the screen unlocked. Turn the phone sideways. Use your usual microphone in OBS; this version sends video only.

Use **Create Desktop Shortcuts.cmd** if you want shortcuts. Copying just a launcher file to the Desktop will not copy its companion scripts.

### In OBS: direct stream

Add **Media Source**, untick **Local File**, and enter:

```text
http://127.0.0.1:8780/live.ts
```

Set Input Format to `mpegts` if OBS does not detect it. Start with 1 MB network buffering. If you get a black picture, confirm the phone is sending video and try turning hardware decoding off for this source.

### In OBS or another app: webcam dropdown

Click **Start webcam output** on the local web panel, then select **OBS Virtual Camera** as a **Video Capture Device**, at **1920 × 1080 / 30 fps**. OBS Studio and its virtual-camera component must already be installed.

Do **not** also start OBS's own virtual-camera output: both use the same device. This release does not install a separately named Spid9y camera driver. If you need OBS's virtual camera for another output simultaneously, use the direct Media Source for input instead.

### Camera locks and mirroring

Let the image settle before enabling **Exposure lock** or **White balance lock**. The front lens on the tested Redmi does not support focus lock or torch; those options are correctly disabled for it. Digital zoom and stabilization may crop the image. Changing EV can change exposure even while AE lock is enabled.

**Mirror webcam output** affects OBS Virtual Camera and the web monitor, not the direct stream. It flips decoded pixels with FFmpeg's native filter and briefly reconnects when changed. In a short prototype test, mirrored output maintained about 30 fresh fps with approximately 2% total machine CPU for the optional webcam bridge. That is one test, not a zero-overhead promise.

## Stop

Run **Stop Native Camera.cmd** to stop the PC receiver and optional webcam output. Tap **Stop camera** on the page or phone to stop capture. Closing the web page alone does not stop capture. The phone app stops its camera when it leaves the foreground.

## Requirements and limits

Windows 10/11 x64; Android 8+ with a Camera2 camera and hardware AVC encoder exposing 1080p; USB debugging; a working data cable. Only the device in the compatibility table has a physical test recorded so far.

No iPhone, macOS/Linux client support, phone audio, background/locked-screen capture, proprietary Xiaomi HDR/Night/AI/beauty modes, or dedicated virtual-camera driver is claimed. Setup downloads third-party tools rather than redistributing their binaries in our release ZIP. See [third-party notices](THIRD_PARTY_NOTICES.md).

## Build and test

No Node package installation is needed; the receiver uses the Node standard library.

```powershell
npm test
```

Tests require FFmpeg on PATH and verify real H.264 → MPEG-TS decoding, preview output, control forwarding, origin restrictions, and 1080p geometry. See [development and release builds](docs/DEVELOPMENT.md) for Android SDK/JDK requirements, APK signing, and packaging.

## Support the project

Star the repository, test on another phone, report reproducible bugs, or improve the code. Donations are optional; a maintainer-owned payment link has not been configured yet. Features do not depend on donating.

## License

Spid9y Cam source is **GPL-2.0-only**, copyright 2026 spiderman99sumit. Runtime dependencies retain their own licenses. Source code for this project is included in the repository; downloaded third-party components and their notices remain under `tools/` on the user's PC and are not committed here.
