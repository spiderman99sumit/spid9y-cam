# Development and releases

## Receiver tests

Install Node.js 22+ and FFmpeg, then run `npm test`. The code has no npm runtime dependencies. `Setup Runtime.cmd` provides a pinned Windows runtime if desired; for tests add its tools folder to PATH.

The integration test generates a real 1080p H.264 stream, supplies a simulated phone-control socket, reads the receiver's MJPEG and MPEG-TS endpoints, decodes the resulting stream, verifies controls, and checks cross-origin restrictions. It does not open a physical camera.

## Android build

Install JDK 17+ and Android SDK Platform 35 + Build Tools 35.0.0 from their official tools. Set JAVA_HOME and ANDROID_HOME. On Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android/Build-Apk.ps1
```

The default creates a development-signed APK. A release maintainer can pass `-Release` to create/use a separate signing key. Release signing material lives in `.private/`, which is ignored and excluded from packages. Its password is stored using Windows user-bound protection and passed to tools through a temporary environment variable. Back up signing material privately if you maintain updates; it must never be committed or uploaded as an asset.

The public app package is `io.github.spiderman99sumit.spid9ycam`. Native phone/ADB ports are 8876 and 8877; the default PC server is loopback port 8780. Tests can override receiver ports with NATIVE_PORT, NATIVE_VIDEO_PORT, and NATIVE_CONTROL_PORT.

## Windows ZIP

After building a release APK, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File Build-Release.ps1
```

This packages an explicit allowlist of source, launchers, docs, notices, and the signed APK into dist/. It does not copy tools/, private keys, logs, runtime files, development SDKs, or another project. Setup downloads dependencies using the pinned URL/hash manifest; it does not execute an unverified archive.

## Before tagging

Run tests and script syntax checks, test a fresh runtime setup, verify APK signing, inspect ZIP contents and hashes, scan staged files for secrets and local paths, and document the exact physical devices tested. Do not use generated-input results to claim universal 30 fps performance. Update CHANGELOG and release notes. Tags are public source snapshots; use a prerelease until broader device testing is available.
