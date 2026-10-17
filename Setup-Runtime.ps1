# SPDX-License-Identifier: GPL-2.0-only
param([switch]$SkipWebcam)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem
$manifest = Get-Content -LiteralPath "$PSScriptRoot\dependencies.json" -Raw | ConvertFrom-Json
$tools = Join-Path $PSScriptRoot 'tools'
$cache = Join-Path $tools 'downloads'
New-Item -ItemType Directory -Path $cache -Force | Out-Null
function Get-VerifiedArchive($Dependency, [string]$Name) {
    $file = Join-Path $cache $Name
    $algorithm = if ($Dependency.sha256) { 'SHA256' } else { 'SHA1' }
    $expected = if ($Dependency.sha256) { $Dependency.sha256 } else { $Dependency.sha1 }
    if (-not (Test-Path -LiteralPath $file) -or (Get-FileHash -LiteralPath $file -Algorithm $algorithm).Hash.ToLowerInvariant() -ne $expected) {
        Write-Host "Downloading $Name from $(([Uri]$Dependency.url).Host)..."
        Invoke-WebRequest -UseBasicParsing -Uri $Dependency.url -OutFile $file -TimeoutSec 600
    }
    if ((Get-FileHash -LiteralPath $file -Algorithm $algorithm).Hash.ToLowerInvariant() -ne $expected) { throw "Checksum mismatch for $Name. Nothing from this download will run." }
    return $file
}
function Expand-Verified([string]$File, [string]$Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    # Each archive has its own checksum-keyed folder, so a previous incomplete
    # extraction can safely be overwritten without removing unrelated files.
    $zip = [IO.Compression.ZipFile]::OpenRead($File)
    try {
        $base = [IO.Path]::GetFullPath($Destination).TrimEnd('\') + '\'
        foreach ($entry in $zip.Entries) {
            $target = [IO.Path]::GetFullPath((Join-Path $Destination $entry.FullName))
            if (-not $target.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) { throw 'Archive contains an unsafe path.' }
            if (-not $entry.Name) { New-Item -ItemType Directory -Path $target -Force | Out-Null; continue }
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
    } finally { $zip.Dispose() }
}
Write-Host 'Spid9y Cam runtime setup. Downloads are pinned in dependencies.json.'
Write-Host 'Publisher licenses and notices remain with the downloaded tools.'
$archive = Get-VerifiedArchive $manifest.node 'node.zip'
$nodeExtract = Join-Path $cache "node-$($manifest.node.version)"
Expand-Verified $archive $nodeExtract
$nodeRoot = (Get-ChildItem -LiteralPath $nodeExtract -Directory | Select-Object -First 1).FullName
Copy-Item -LiteralPath "$nodeRoot\node.exe" -Destination "$tools\node.exe" -Force
Copy-Item -LiteralPath "$nodeRoot\LICENSE" -Destination "$tools\Node-LICENSE.txt" -Force
$archive = Get-VerifiedArchive $manifest.adb 'platform-tools.zip'
Expand-Verified $archive $tools
$archive = Get-VerifiedArchive $manifest.ffmpeg 'ffmpeg.zip'
$ffmpegExtract = Join-Path $cache "ffmpeg-$($manifest.ffmpeg.version)"
Expand-Verified $archive $ffmpegExtract
$ffmpegRoot = (Get-ChildItem -LiteralPath $ffmpegExtract -Directory | Select-Object -First 1).FullName
foreach ($name in @('ffmpeg.exe','ffprobe.exe')) { Copy-Item -LiteralPath "$ffmpegRoot\bin\$name" -Destination "$tools\$name" -Force }
New-Item -ItemType Directory -Path "$tools\ffmpeg-notices" -Force | Out-Null
Get-ChildItem -LiteralPath $ffmpegRoot -File | Where-Object { $_.Name -match 'LICENSE|README' } | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination "$tools\ffmpeg-notices" -Force }
if (-not $SkipWebcam) {
    $archive = Get-VerifiedArchive $manifest.python 'python.zip'
    Expand-Verified $archive "$tools\python"
    foreach ($wheel in $manifest.wheels) {
        $archive = Get-VerifiedArchive $wheel "$($wheel.name).whl"
        Expand-Verified $archive "$tools\virtualcam-deps"
    }
    [IO.File]::WriteAllText("$tools\python\python312._pth", "python312.zip`r`n.`r`n..\virtualcam-deps`r`nimport site`r`n", [Text.Encoding]::ASCII)
    & "$tools\python\python.exe" -c 'import numpy,pyvirtualcam; print(numpy.__version__,pyvirtualcam.__version__)'
    if ($LASTEXITCODE -ne 0) { throw 'Webcam Python runtime verification failed.' }
}
& "$tools\node.exe" --version
& "$tools\ffmpeg.exe" -version | Select-Object -First 1
Write-Host 'Setup complete. Connect your Android phone and run Start Native Camera.cmd.'
