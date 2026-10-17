$ErrorActionPreference = 'Stop'
$runtime = Join-Path $PSScriptRoot 'webcam-runtime.json'
if (-not (Test-Path -LiteralPath $runtime)) { Write-Host 'Webcam output is not running.'; return }
$state = Get-Content -LiteralPath $runtime -Raw | ConvertFrom-Json
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.pid)"
if ($process.Name -eq 'python.exe' -and $process.CommandLine -match 'webcam-output\.py') {
    foreach ($child in (Get-CimInstance Win32_Process -Filter "ParentProcessId = $($state.pid)")) {
        if ($child.Name -eq 'ffmpeg.exe' -and $child.CommandLine -match '127\.0\.0\.1:8780/live\.ts') { Stop-Process -Id $child.ProcessId -ErrorAction SilentlyContinue }
    }
    Stop-Process -Id $state.pid -ErrorAction SilentlyContinue
    Write-Host 'Optional webcam output stopped.'
} else { Write-Host 'Saved process is no longer webcam output. No process was stopped.' }
