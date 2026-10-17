$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$runtime = Join-Path $PSScriptRoot 'webcam-runtime.json'
if (Test-Path -LiteralPath $runtime) {
    try {
        $state = Get-Content -LiteralPath $runtime -Raw | ConvertFrom-Json
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.pid)"
        if ($existing.CommandLine -match 'webcam-output\.py') { Write-Host 'Webcam output is already running as OBS Virtual Camera.'; return }
    } catch {}
}
try { $status = Invoke-RestMethod 'http://127.0.0.1:8780/api/status' -TimeoutSec 2 } catch { throw 'Start Spid9y Cam first, then run Start Webcam Output.' }
$python = Join-Path $PSScriptRoot 'tools\python\python.exe'
$script = Join-Path $PSScriptRoot 'webcam-output.py'
if (-not (Test-Path -LiteralPath $python)) { throw 'Run Setup Runtime.cmd to install the optional webcam runtime.' }
$worker = Start-Process -FilePath $python -ArgumentList ('"{0}"' -f $script) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput "$PSScriptRoot\webcam-output.log" -RedirectStandardError "$PSScriptRoot\webcam-error.log" -PassThru
$ready = $false
for ($attempt=0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 200
    if ($worker.HasExited) { break }
    try { $current=Get-Content -LiteralPath $runtime -Raw|ConvertFrom-Json; if($current.pid -eq $worker.Id -and $current.running){$ready=$true;break} }catch{}
}
if (-not $ready) { $detail=Get-Content "$PSScriptRoot\webcam-error.log" -Raw -ErrorAction SilentlyContinue;throw "Webcam output did not start. Install OBS Studio's virtual camera component and keep OBS's own virtual-camera output off. $detail" }
Write-Host 'Webcam output started. In OBS: Video Capture Device -> OBS Virtual Camera.'
Write-Host 'Resolution: 1920x1080, FPS: 30. Do not also start OBS own Virtual Camera output.'
Write-Host 'If the device is not listed yet, close and reopen the Video Capture Device properties.'
