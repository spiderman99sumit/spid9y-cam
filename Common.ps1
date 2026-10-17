# SPDX-License-Identifier: GPL-2.0-only
function Invoke-CheckedTool {
    param([string]$File, [string[]]$Arguments, [int]$TimeoutSeconds = 20)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $File
    $quoted = foreach ($argument in $Arguments) { if ($argument -match '[\s"]') { '"' + ($argument -replace '(\\+)$','$1$1') + '"' } else { $argument } }
    $info.Arguments = $quoted -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $task = [Diagnostics.Process]::Start($info)
    $output = $task.StandardOutput.ReadToEndAsync()
    $errors = $task.StandardError.ReadToEndAsync()
    if (-not $task.WaitForExit($TimeoutSeconds * 1000)) { $task.Kill(); throw "Tool timed out: $([IO.Path]::GetFileName($File)). Check the phone and USB cable." }
    $text = $output.GetAwaiter().GetResult()
    $errorText = $errors.GetAwaiter().GetResult()
    if ($task.ExitCode -ne 0) { throw "Tool failed: $errorText $text" }
    return $text
}
