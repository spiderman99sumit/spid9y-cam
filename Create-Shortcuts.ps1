# SPDX-License-Identifier: GPL-2.0-only
$ErrorActionPreference = 'Stop'
$desktop = [Environment]::GetFolderPath('Desktop')
$shell = New-Object -ComObject WScript.Shell
foreach ($entry in @(@{Name='Spid9y Cam - Start';File='Start Native Camera.cmd'},@{Name='Spid9y Cam - Stop';File='Stop Native Camera.cmd'})) {
    $shortcut = $shell.CreateShortcut((Join-Path $desktop ($entry.Name+'.lnk')))
    $shortcut.TargetPath = Join-Path $PSScriptRoot $entry.File
    $shortcut.WorkingDirectory = $PSScriptRoot
    $shortcut.Description = $entry.Name
    $shortcut.Save()
}
Write-Host 'Spid9y Cam shortcuts created. Keep the extracted app folder in this location.'
