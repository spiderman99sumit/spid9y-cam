# SPDX-License-Identifier: GPL-2.0-only
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$apk = Join-Path $root 'android\build\Spid9y-Cam-Android.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build a release-signed Android APK first; see docs/DEVELOPMENT.md.' }
$dist = Join-Path $root 'dist'
$staging = Join-Path $dist 'Spid9y-Cam-Windows'
New-Item -ItemType Directory -Path "$staging\public","$staging\docs" -Force | Out-Null
$files = @('native-server.mjs','webcam-output.py','Common.ps1','Setup-Runtime.ps1','Setup Runtime.cmd','dependencies.json','Start-Native.ps1','Start Native Camera.cmd','Stop-Native.ps1','Stop Native Camera.cmd','Start-Webcam.ps1','Start Webcam Output.cmd','Stop-Webcam.ps1','Stop Webcam Output.cmd','Create-Shortcuts.ps1','Create Desktop Shortcuts.cmd','README.md','LICENSE','PRIVACY.md','THIRD_PARTY_NOTICES.md')
foreach ($name in $files) { Copy-Item -LiteralPath (Join-Path $root $name) -Destination $staging -Force }
foreach ($name in @('native.html','native.css','native.js')) { Copy-Item -LiteralPath "$root\public\$name" -Destination "$staging\public" -Force }
foreach ($name in @('TROUBLESHOOTING.md','COMPATIBILITY.md')) { Copy-Item -LiteralPath "$root\docs\$name" -Destination "$staging\docs" -Force }
Copy-Item -LiteralPath $apk -Destination "$staging\Spid9y-Cam-Android.apk" -Force
Copy-Item -LiteralPath $apk -Destination "$dist\Spid9y-Cam-Android.apk" -Force
$unexpected = Get-ChildItem -LiteralPath $staging -Recurse -File | Where-Object { $_.Name -match '\.keystore$|\.jks$|\.log$|runtime\.json$|\.xml$' }
if ($unexpected) { throw 'Unexpected private or runtime file in release staging.' }
$zip = Join-Path $dist 'Spid9y-Cam-Windows.zip'
Compress-Archive -LiteralPath $staging -DestinationPath $zip -Force
$lines = foreach ($name in @('Spid9y-Cam-Android.apk','Spid9y-Cam-Windows.zip')) { $hash = Get-FileHash -LiteralPath "$dist\$name" -Algorithm SHA256; "$($hash.Hash.ToLowerInvariant())  $name" }
[IO.File]::WriteAllLines("$dist\SHA256SUMS.txt", $lines, [Text.Encoding]::ASCII)
Write-Host 'Release files built in dist/. No runtime executables or signing keys were packaged.'
