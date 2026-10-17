# SPDX-License-Identifier: GPL-2.0-only
param([string]$AndroidSdkRoot, [string]$JavaHome, [switch]$Release)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $AndroidSdkRoot) { $AndroidSdkRoot = $env:ANDROID_HOME }
if (-not $AndroidSdkRoot) { $AndroidSdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $AndroidSdkRoot -or -not $JavaHome) { throw 'Set ANDROID_HOME and JAVA_HOME (JDK 17+), or pass -AndroidSdkRoot and -JavaHome.' }
$platform = Join-Path $AndroidSdkRoot 'platforms\android-35\android.jar'
if (-not (Test-Path -LiteralPath $platform)) { $platform = Join-Path $AndroidSdkRoot 'android-35\android.jar' }
$buildTools = Join-Path $AndroidSdkRoot 'build-tools\35.0.0'
if (-not (Test-Path -LiteralPath $buildTools)) { $buildTools = Join-Path $AndroidSdkRoot 'android-15' }
foreach ($required in @($platform,"$buildTools\aapt2.exe","$JavaHome\bin\javac.exe")) { if (-not (Test-Path -LiteralPath $required)) { throw "Required build tool is missing: $required" } }
$output = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path "$output\classes","$output\dex","$root\.private" -Force | Out-Null
function Assert-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command exited with $LASTEXITCODE" } }
& "$JavaHome\bin\javac.exe" --release 8 -classpath $platform -d "$output\classes" "$PSScriptRoot\CameraActivity.java"
Assert-Exit
& "$JavaHome\bin\jar.exe" cf "$output\classes.jar" -C "$output\classes" .
Assert-Exit
& "$JavaHome\bin\java.exe" -cp "$buildTools\lib\d8.jar" com.android.tools.r8.D8 --min-api 26 --lib $platform --output "$output\dex" "$output\classes.jar"
Assert-Exit
& "$buildTools\aapt2.exe" link -o "$output\unsigned.apk" --manifest "$PSScriptRoot\AndroidManifest.xml" -I $platform
Assert-Exit
& "$JavaHome\bin\jar.exe" uf "$output\unsigned.apk" -C "$output\dex" classes.dex
Assert-Exit
& "$buildTools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
Assert-Exit
if ($Release) {
    $secretFile = "$root\.private\signing-password.xml"
    if (-not (Test-Path -LiteralPath $secretFile)) {
        $random = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($random) } finally { $rng.Dispose() }
        ConvertTo-SecureString ([Convert]::ToBase64String($random)) -AsPlainText -Force | Export-Clixml -LiteralPath $secretFile
    }
    $secure = Import-Clixml -LiteralPath $secretFile
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:SPID9Y_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    $keystore = "$root\.private\release.keystore"
} else { $env:SPID9Y_SIGNING_PASSWORD = 'android'; $keystore = "$root\.private\development.keystore" }
try {
    if (-not (Test-Path -LiteralPath $keystore)) {
        & "$JavaHome\bin\keytool.exe" -genkeypair -keystore $keystore -storepass:env SPID9Y_SIGNING_PASSWORD -keypass:env SPID9Y_SIGNING_PASSWORD -alias spid9ycam -dname 'CN=Spid9y Cam' -keyalg RSA -keysize 3072 -validity 10000
        Assert-Exit
    }
    $apk = Join-Path $output $(if ($Release) { 'Spid9y-Cam-Android.apk' } else { 'Spid9y-Cam-Development.apk' })
    & "$JavaHome\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" sign --ks $keystore --ks-key-alias spid9ycam --ks-pass env:SPID9Y_SIGNING_PASSWORD --key-pass env:SPID9Y_SIGNING_PASSWORD --out $apk "$output\aligned.apk"
    Assert-Exit
    & "$JavaHome\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" verify --print-certs $apk
    Assert-Exit
    Write-Output "APK built and verified: $apk"
} finally { Remove-Item Env:\SPID9Y_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
