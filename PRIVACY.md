# Privacy

Spid9y Cam sends phone video through ADB-forwarded local TCP sockets to the Windows receiver. The control page and stream server bind to `127.0.0.1`; the phone's video/control servers also bind to loopback. The app requests **Camera** and **Internet** permissions; Internet is needed for these sockets.

The project has no account system, analytics, advertising, cloud video relay, built-in recording, audio capture, or automatic screenshot upload. The phone stops camera capture when its activity leaves the foreground. The optional webcam output switches to black when fresh frames stop arriving.

Runtime setup contacts the publishers listed in `dependencies.json` to download tools. Those downloads are separate from camera capture. Your usual browser, OBS plugins, antivirus, and downstream video applications have their own behavior and policies; this statement covers Spid9y Cam's code.

Local logs and runtime JSON files can include process IDs, camera IDs, errors, and performance counters. They are ignored by Git and excluded from the release ZIP. Do not attach private camera images or full device/ADB dumps to a public issue.
