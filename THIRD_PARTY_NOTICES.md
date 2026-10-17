# Third-party software

The Windows release ZIP distributes Spid9y Cam's own source/scripts and APK. It does **not** redistribute the runtime executables, Python packages, or Android SDK tools. `Setup-Runtime.ps1` downloads the exact artifacts pinned in `dependencies.json` directly from their publishers, checks their hashes, and preserves their included notices on the user's PC.

| Component | Source / publisher | License or terms | Use |
|---|---|---|---|
| Node.js | [nodejs.org](https://nodejs.org/) / [source](https://github.com/nodejs/node) | MIT and bundled notices | Local control/receiver server |
| FFmpeg Windows build | [Gyan builds](https://www.gyan.dev/ffmpeg/builds/) / [FFmpeg source](https://ffmpeg.org/download.html) | This pinned essentials build is GPL-enabled; consult its included license and build notices | H.264 remux, monitor decode, native flip |
| Android platform-tools | [Google](https://developer.android.com/tools/releases/platform-tools) | Android SDK terms and package notices | ADB USB connection |
| Python | [python.org](https://www.python.org/) / [source](https://github.com/python/cpython) | PSF License and bundled notices | Optional virtual-camera bridge |
| NumPy | [PyPI](https://pypi.org/project/numpy/) / [source](https://github.com/numpy/numpy) | BSD-3-Clause and wheel notices | NV12 frame views |
| pyvirtualcam | [PyPI](https://pypi.org/project/pyvirtualcam/) / [source](https://github.com/letmaik/pyvirtualcam) | GPL-2.0, including its upstream notices | Existing OBS virtual-camera backend |
| OBS Studio | [obsproject.com](https://obsproject.com/) / [source](https://github.com/obsproject/obs-studio) | GPL-2.0 and component notices | Optional camera device; installed separately by user |

The APK uses Android platform APIs and does not bundle the Android SDK, Google services, or the runtime tools listed above. Spid9y Cam is not affiliated with Google, OBS, FFmpeg, Iriun, DroidCam, or Xiaomi.

If you redistribute a bundle that includes third-party binaries yourself, you must separately satisfy those components' license and corresponding-source obligations. The lightweight release format here is designed to avoid silently repackaging the maintainer's development tools.
