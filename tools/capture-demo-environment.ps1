param(
    [switch]$ApplyDemoDisplay,
    [string]$OutputPath = "pleos-evidence/environment.txt"
)
$ErrorActionPreference = "Stop"
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (!(Test-Path $adb)) { $adb = (Get-Command adb -ErrorAction Stop).Source }
& $adb -e get-state
if ($LASTEXITCODE -ne 0) { throw "Start exactly one Pleos emulator before running this script." }
if ($ApplyDemoDisplay) {
    & $adb -e shell wm size 1920x1080
    & $adb -e shell wm density 160
    if ($LASTEXITCODE -ne 0) { throw "Display configuration failed." }
}
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("VPE demo environment - $(Get-Date -Format o)")
$lines.Add("Host: $([System.Environment]::OSVersion.VersionString)")
$lines.Add("AVD: $((& $adb -e emu avd name | Where-Object { $_ -ne 'OK' }) -join ' ')")
foreach ($property in @("ro.build.version.release", "ro.build.version.sdk", "ro.product.cpu.abi", "ro.build.fingerprint")) {
    $lines.Add("$property = $((& $adb -e shell getprop $property) -join ' ')")
}
$lines.Add("Screen size: $((& $adb -e shell wm size) -join '; ')")
$lines.Add("Screen density: $((& $adb -e shell wm density) -join '; ')")
$lines.Add("Automotive: $((& $adb -e shell pm list features | Select-String 'android.hardware.type.automotive') -join ' ')")
$lines.Add("App: $((& $adb -e shell dumpsys package com.vpe.hyundai | Select-String 'versionCode=|versionName=') -join '; ')")
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"
if (Test-Path $emulator) { $lines.Add("Emulator binary: $((& $emulator -version 2>&1 | Select-Object -First 3) -join '; ')") }
$parent = Split-Path $OutputPath -Parent
if ($parent -and !(Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$lines | Set-Content -Encoding UTF8 $OutputPath
$lines | ForEach-Object { Write-Host $_ }
Write-Host "Saved: $OutputPath"
