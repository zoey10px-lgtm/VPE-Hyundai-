<#
.SYNOPSIS
  Proof-of-reception test for real Pleos Vehicle Service on a running Pleos Connect Emulator.
.DESCRIPTION
  This script never generates synthetic vehicle readings. It installs the actual VPE APK,
  taps the app's actual Pleos connect button, captures SDK callbacks via ADB and checks
  for numeric Odometer/Fuel/EV results. Raw VIN is never collected.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\verify-pleos.ps1 -ApkPath ".\app\build\outputs\apk\debug\app-debug.apk" -WatchSeconds 60
#>
param(
  [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk",
  [string]$DeviceSerial = "",
  [int]$WatchSeconds = 0,
  [string]$OutputDir = "pleos-evidence"
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  throw "adb not found. Start Android Studio, install Platform Tools, and add platform-tools to PATH."
}

$adbs = @()
if ($DeviceSerial) { $adbs = @("-s", $DeviceSerial) }
function RunAdb {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
  $res = & adb @adbs @AdbArgs 2>&1
  if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ') -- $res" }
  return $res
}
if (-not (Test-Path $ApkPath)) {
  throw "APK not found: $ApkPath (build with gradle assembleDebug or provide -ApkPath)"
}

New-Item -Force -ItemType Directory -Path $OutputDir | Out-Null
$features = (RunAdb shell pm list features) -join "`n"
Set-Content -Path (Join-Path $OutputDir "device-features.txt") -Value $features -Encoding UTF8
if (-not $features.Contains("android.hardware.type.automotive")) {
  throw "Connected device is not Android Automotive. Boot the official Pleos Connect API 34 AVD instead."
}

$boot = ((RunAdb shell getprop sys.boot_completed) -join "").Trim()
if ($boot -ne "1") { throw "Pleos Emulator boot incomplete. Cold Boot the AVD, then retry." }
$packages = (RunAdb shell pm list packages) -join "`n"
Set-Content -Path (Join-Path $OutputDir "installed-packages.txt") -Value $packages -Encoding UTF8

Write-Host "Installing VPE onto the actual Automotive emulator..."
RunAdb install -r $ApkPath | Out-Host
$permissionDump = (RunAdb shell dumpsys package com.vpe.hyundai) -join "`n"
Set-Content (Join-Path $OutputDir "package-permissions.txt") $permissionDump -Encoding UTF8
RunAdb logcat -c | Out-Null
RunAdb shell am force-stop com.vpe.hyundai | Out-Null
RunAdb shell am start -W -n com.vpe.hyundai/.MainActivity | Out-Host
Start-Sleep -Seconds 4

RunAdb shell uiautomator dump /sdcard/vpe_ui.xml | Out-Null
$xmlRaw = (RunAdb shell cat /sdcard/vpe_ui.xml) -join "`n"
Set-Content (Join-Path $OutputDir "vpe-ui.xml") $xmlRaw -Encoding UTF8
[xml]$hierarchy = $xmlRaw

$button = $null
foreach ($node in $hierarchy.SelectNodes("//node")) {
  $value = [string]$node.GetAttribute("text")
  if ($value.Contains("Pleos 차량 연결하기") -or $value.Contains("차량 데이터 새로고침")) {
    $button = $node
    break
  }
}
if (-not $button) {
  throw "Real Pleos connect action is missing from UI. Check the saved vpe-ui.xml and package-permissions.txt."
}

$bounds = [string]$button.GetAttribute("bounds")
$matches = [regex]::Matches($bounds,"[0-9]+")
if ($matches.Count -ne 4) { throw "Cannot parse connect button bounds: $bounds" }
$x = [int](([int]$matches[0].Value + [int]$matches[2].Value) / 2)
$y = [int](([int]$matches[1].Value + [int]$matches[3].Value) / 2)
Write-Host "Tapping live SDK connection at $x,$y"
RunAdb shell input tap $x $y | Out-Null

$evidencePath = Join-Path $OutputDir "sdk-callbacks.txt"
$seconds = 25
for ($i=0; $i -lt $seconds; $i+=2) {
  Start-Sleep -Seconds 2
  $logs = (RunAdb logcat -d -s VPE_PLEOS:I) -join "`n"
  Set-Content $evidencePath $logs -Encoding UTF8
  if ($logs -match "ODO_OK: km=[0-9]") { break }
}
$raw = Get-Content $evidencePath -Raw
Write-Host ""
Write-Host "========== Pleos live SDK callback evidence =========="
$raw -split "`n" | Select-Object -Last 45 | ForEach-Object { Write-Host $_ }

$distanceSuccess = $raw -match "ODO_OK: km=[0-9]"
$fuelSuccess = $raw -match "(FUEL_OK|EV_OK): percent=[0-9]"
$identified = $raw -match "(VIN_OK|CRN_OK)"
Write-Host "Odometer numeric callback: $distanceSuccess"
Write-Host "Fuel / EV numeric callback: $fuelSuccess"
Write-Host "Vehicle identity callback: $identified"
Write-Host "Logs saved to $((Resolve-Path $OutputDir).Path)"

if ($WatchSeconds -gt 0) {
  Write-Host "Opening Pleos Virtual Console at http://127.0.0.1:8088/"
  RunAdb forward tcp:8088 tcp:8088 | Out-Null
  Write-Host "Use Motion / Energy cards to change a real emulator vehicle property."
  Write-Host "Watching SDK listener callbacks for $WatchSeconds seconds..."
  Start-Sleep -Seconds $WatchSeconds
  $raw = (RunAdb logcat -d -s VPE_PLEOS:I) -join "`n"
  Set-Content $evidencePath $raw -Encoding UTF8
  $changed = $raw -match "(ODO_UPDATE: km=|FUEL_UPDATE: percent=|EV_SOC_UPDATE: percent=)"
  Write-Host "SDK listener update observed: $changed"
}
if (-not $distanceSuccess) {
  throw "No numeric odometer callback. Inspect pleos-evidence/sdk-callbacks.txt for actual Service errors."
}
Write-Host "PASS: Real Pleos Vehicle Service returned an odometer value."
