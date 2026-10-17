# SPDX-License-Identifier: GPL-2.0-only
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
. "$PSScriptRoot\Common.ps1"
foreach ($required in @('tools\node.exe','tools\ffmpeg.exe','tools\platform-tools\adb.exe')) { if (-not (Test-Path -LiteralPath "$PSScriptRoot\$required")) { throw 'Run Setup Runtime.cmd first. Keep the whole app folder together.' } }
$adb = "$PSScriptRoot\tools\platform-tools\adb.exe"
Invoke-CheckedTool -File $adb -Arguments @('start-server') | Write-Host
$online = @()
for ($attempt = 0; $attempt -lt 6; $attempt++) {
    $devices = Invoke-CheckedTool -File $adb -Arguments @('devices')
    $online = @($devices -split "`n" | Where-Object { $_ -match '^\S+\s+device\s*$' })
    if ($online.Count -gt 0) { break }
    Start-Sleep -Milliseconds 500
}
if ($online.Count -ne 1) { throw 'Connect exactly one Android phone, unlock it, enable USB debugging, and accept Allow USB debugging on the phone.' }
$package = 'io.github.spiderman99sumit.spid9ycam'
$installed = Invoke-CheckedTool -File $adb -Arguments @('shell','pm','list','packages',$package)
if ($installed -notmatch [regex]::Escape($package)) {
    $apk = "$PSScriptRoot\Spid9y-Cam-Android.apk"
    if (-not (Test-Path -LiteralPath $apk)) { $apk = "$PSScriptRoot\android\build\Spid9y-Cam-Android.apk" }
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Download Spid9y-Cam-Android.apk from this release and place it beside this launcher.' }
    Write-Host 'Installing Spid9y Cam. Unlock the phone and approve its installation prompt.'
    try { Invoke-CheckedTool -File $adb -Arguments @('install','--no-streaming','-r',$apk) -TimeoutSeconds 120 | Write-Host }
    catch { throw 'Android blocked installation. Install the APK manually on the phone, then run this launcher again. See docs/TROUBLESHOOTING.md.' }
}
Invoke-CheckedTool -File $adb -Arguments @('forward','tcp:8876','tcp:8876') | Write-Host
Invoke-CheckedTool -File $adb -Arguments @('forward','tcp:8877','tcp:8877') | Write-Host
Invoke-CheckedTool -File $adb -Arguments @('shell','am','start','-n',"$package/.CameraActivity") | Write-Host
try { $status = Invoke-RestMethod 'http://127.0.0.1:8780/api/status' -TimeoutSec 2 } catch { $status = $null }
if ($status -and $status.app.id -ne 'spid9y-cam') { throw 'Another application is using port 8780. Close it before starting Spid9y Cam.' }
if (-not $status) {
    Start-Process -FilePath "$PSScriptRoot\tools\node.exe" -ArgumentList ('"{0}"' -f "$PSScriptRoot\native-server.mjs") -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput "$PSScriptRoot\native-server.log" -RedirectStandardError "$PSScriptRoot\native-server-error.log"
    for ($attempt=0; $attempt -lt 15; $attempt++) { Start-Sleep -Milliseconds 300; try { $status=Invoke-RestMethod 'http://127.0.0.1:8780/api/status' -TimeoutSec 1; break } catch {} }
    if (-not $status) { throw 'Receiver did not start. See native-server-error.log in this folder.' }
}
Start-Process 'http://localhost:8780'
Write-Host 'On the phone, allow Camera. Keep Spid9y Cam visible and the phone unlocked.'
Write-Host 'OBS Media Source: http://127.0.0.1:8780/live.ts (input format: mpegts).'
Write-Host 'For a webcam dropdown, click Start webcam output on the control page.'
