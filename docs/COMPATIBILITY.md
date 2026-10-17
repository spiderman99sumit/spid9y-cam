# Compatibility and validation

| Device / platform | Status |
|---|---|
| Redmi Note 9 Pro, Android 12 / API 31 | Original pipeline physically tested with front and back 1080p camera enumeration |
| Windows x64 + OBS Studio | Original pipeline physically tested; normal Media Source and OBS Virtual Camera capture validated |
| Other Android 8+ devices / Windows PCs | Not yet physically validated; please report results |
| iOS, macOS, Linux | Not supported by this release |

The native prototype's real MPEG-TS stream was inspected with FFprobe: H.264, 1920 × 1080, square pixels, display aspect ratio 16:9, frame-rate header 30/1. Fresh short samples measured approximately 30 fps before and during AE lock, with zero phone transport-queue drops. The sensor reported AE state 3 (locked), and exposure time/ISO stayed stable in the lock window.

The optional webcam was captured through DirectShow as raw NV12 at 1920 × 1080 / 30 fps. A native mirrored-output sample measured about 30 fresh fps and approximately 2% total machine CPU for the Python + FFmpeg bridge. These are short measurements on one setup, not a thermal soak test or a guarantee across devices, games, lighting, cables, and OBS plugins.

On the tested front lens, AE/AWB lock, digital zoom, EV, and stabilization are exposed. Focus lock and torch are not exposed and are disabled. A standard scene-mode capability does not establish equivalence to Xiaomi Camera's proprietary processing.

The first branded APK, public ZIP setup, and isolated receiver tests have separate validation in the release notes. Do not treat a passing generated-input test as proof of a phone's hardware compatibility.
