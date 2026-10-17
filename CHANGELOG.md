# Changelog

## 0.1.0-beta.1

First public beta, extracted from the working native prototype and rebranded as Spid9y Cam.

- Native Camera2 → hardware AVC 1080p30 target over local USB-forwarded sockets.
- Front camera default and web controls for supported camera settings.
- Direct OBS MPEG-TS stream and optional existing OBS virtual-camera output.
- Optional native horizontal mirroring without another encoding step.
- Publisher downloads with pinned hashes instead of redistributing the development machine's runtime bundle.
- Separate public Android package, private release signing, safe release-file allowlist, documentation, and setup troubleshooting.
- Bounded ADB commands, initial device-discovery retries, webcam-start readiness checks, local-only browser-origin restrictions, and receiver request-error handling.

This release does not claim support for untested devices, iOS, background/locked-screen capture, phone audio, a separate Spid9y camera driver, or proprietary OEM camera processing.
