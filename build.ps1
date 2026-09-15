# Echo - local build script.
#
# This environment denies writes outside the repo, so every mutable path
# (Gradle home, Android user home, even user.home) is redirected inside it.
#
#   powershell -File build.ps1 assembleDebug
#   powershell -File build.ps1 testDebugUnitTest
#   powershell -File build.ps1 clean assembleRelease
#
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tasks)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "C:\Users\Lfq06\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $root ".gradle-home"
# NOTE: set ANDROID_USER_HOME only. Adding the legacy ANDROID_PREFS_ROOT makes AGP fail with
# "Several environment variables ... contain different paths to the Android Preferences folder"
# (it rejects multiple injection methods even when both point at the same directory).
$env:ANDROID_USER_HOME = Join-Path $root ".android-home"
$homeDir = (Join-Path $root ".home") -replace '\\', '/'

foreach ($d in @($env:ANDROID_USER_HOME, (Join-Path $root ".home"), (Join-Path $root ".tmp"))) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}
$env:TEMP = Join-Path $root ".tmp"
$env:TMP = $env:TEMP

$gradle = Join-Path $env:GRADLE_USER_HOME "dist\gradle-9.7.1\bin\gradle.bat"
if (-not (Test-Path $gradle)) { throw "Gradle distribution not found: $gradle" }

if (-not $Tasks -or $Tasks.Count -eq 0) { $Tasks = @("assembleDebug") }

Write-Host "[echo] JAVA_HOME        = $env:JAVA_HOME"
Write-Host "[echo] ANDROID_HOME     = $env:ANDROID_HOME"
Write-Host "[echo] GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "[echo] user.home        = $homeDir"
Write-Host "[echo] tasks            = $($Tasks -join ' ')"

& $gradle @Tasks --console=plain "-Duser.home=$homeDir"
exit $LASTEXITCODE
