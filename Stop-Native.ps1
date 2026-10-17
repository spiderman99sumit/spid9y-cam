$ErrorActionPreference = 'Stop'
$webcamStop = Join-Path $PSScriptRoot 'Stop-Webcam.ps1'
if (Test-Path -LiteralPath $webcamStop) { & $webcamStop }
$runtimePath = Join-Path $PSScriptRoot 'native-runtime.json'
if (-not (Test-Path -LiteralPath $runtimePath)) { Write-Host 'Native receiver is not running.'; return }
$runtime = Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json
$receiverProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($runtime.pid)"
$listener = Get-NetTCPConnection -LocalPort $runtime.port -State Listen -ErrorAction SilentlyContinue
if ($receiverProcess.Name -eq 'node.exe' -and $receiverProcess.CommandLine -match 'native-server\.mjs' -and $listener.OwningProcess -contains $runtime.pid) {
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $($runtime.pid)"
    foreach ($child in $children) {
        if ($child.Name -eq 'ffmpeg.exe' -and $child.CommandLine -match 'tcp://127\.0\.0\.1:8876') { Stop-Process -Id $child.ProcessId -ErrorAction SilentlyContinue }
    }
    Stop-Process -Id $runtime.pid -ErrorAction SilentlyContinue
    Write-Host 'Native receiver stopped. Stop camera capture on your phone too.'
} else { Write-Host 'Saved process is no longer this receiver. No process was stopped.' }
