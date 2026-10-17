# Setup and troubleshooting

## The launcher says a PowerShell file is missing

Extract the whole Windows ZIP into a normal folder. Run its launcher from that folder, or use Create Desktop Shortcuts.cmd. Do not copy just a CMD file to your Desktop. A copied launcher needs its companion scripts.

## No phone / unauthorized / USB command timed out

Unlock the phone. Use a data cable and a direct USB port. Enable USB debugging yourself and accept the phone's authorization prompt for this PC. Try reconnecting the cable and selecting File Transfer if necessary. Connect only one Android device for this release. The launcher waits briefly for initial ADB discovery and puts time limits on commands.

USB debugging is a phone setting. Spid9y Cam does not turn it on or approve authorization prompts automatically. If Windows lacks your phone's USB driver, obtain it from the phone manufacturer or Google's official OEM driver documentation.

## Android or Xiaomi rejects APK installation

Install Spid9y-Cam-Android.apk manually from the phone's Downloads/Files app. If Android asks you to permit installation from that app, review that setting yourself. You may choose to enable Install via USB in Developer options; the project does not change it for you. Do not disable unrelated device protections. GitHub release APKs are signed by the maintainer but are not Play Store installations.

## No preview / camera error

Allow Camera permission. Close another app that is using the camera, keep Spid9y Cam in the foreground, unlock the phone, and tap Start camera. Check the page's status message and selected lens. A lens must expose 1920 × 1080 video and a suitable hardware encoder; the release does not silently advertise an upscaled lower-resolution image as native 1080p.

## OBS Media Source is blank

Confirm the web page says Camera live. Use http://127.0.0.1:8780/live.ts in a Media Source with Local File off and input format mpegts. Try disabling hardware decoding for that source. Allow a moment for a keyframe after reconnecting. Keep the phone app visible. Network buffering trades startup/latency for resilience; start at 1 MB.

## It is not in the Video Capture Device dropdown

Click Start webcam output on the page and select OBS Virtual Camera. OBS Studio's virtual-camera component must be installed. Close/reopen source properties after starting it. Leave OBS's own Start Virtual Camera output off because the two outputs share the same device. The direct Media Source works without this optional webcam bridge.

## Camera looks mirrored / not mirrored

Mirror webcam output changes the optional webcam device and the web monitor. It does not alter the direct Media Source. For the direct source, use OBS's own Transform → Flip Horizontal if you want a mirrored scene. Both preserve the 1920 × 1080 frame geometry; neither makes processing literally free.

## Stops when screen locks or the phone app is backgrounded

That is expected for this release. Keep the app visible and the screen unlocked. The app requests the screen stay awake while visible; Android/OEM policies and system actions can still interrupt capture.

## Reinstalling/upgrading

Use signed APKs from the same release repository. A development APK may use a different signing key and cannot always replace a release APK. This public app uses a different Android package and different local ports from the private Pocket Cam prototype, so it can be installed separately. Stop old companion services when you are no longer using them.
