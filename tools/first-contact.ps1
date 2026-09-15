# 首次接触：一条命令完成 安装 → 权限 → 推模型 → 启动 → 截屏
#
#   powershell -ExecutionPolicy Bypass -File tools\first-contact.ps1
#   powershell -ExecutionPolicy Bypass -File tools\first-contact.ps1 -SkipInstall -SkipModel
#
param(
    [string]$Adb = "C:\Users\Lfq06\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.echo.recall.debug",
    [string]$Apk = "",
    [switch]$SkipInstall,
    [switch]$SkipModel,
    [switch]$Release
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
if ($Release) {
    $Package = "com.echo.recall"
    if (-not $Apk) { $Apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk" }
}
if (-not $Apk) { $Apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk" }

$shots = Join-Path $root "tools\out\shots"
New-Item -ItemType Directory -Force $shots | Out-Null

function Shot([string]$name) {
    $remote = "/sdcard/echo_shot.png"
    $local = Join-Path $shots "$name.png"
    & $Adb shell screencap -p $remote 2>&1 | Out-Null
    & $Adb pull $remote $local 2>&1 | Out-Null
    & $Adb shell rm -f $remote 2>&1 | Out-Null
    if (Test-Path $local) { Write-Host "  [shot] $local" -ForegroundColor DarkGray }
}

Write-Host "`n[1/6] 连接检查" -ForegroundColor Cyan
& $Adb start-server 2>&1 | Out-Null
$devices = (& $Adb devices) -join "`n"
if ($devices -notmatch "\sdevice\s*`r?`n?" -or $devices -match "unauthorized") {
    Write-Host "  设备未授权！请在手机上点「允许 USB 调试」→「始终允许」后重跑本脚本。" -ForegroundColor Red
    Write-Host $devices
    exit 1
}
Write-Host ("  " + ((& $Adb shell getprop ro.product.model).Trim()) + " / API " + ((& $Adb shell getprop ro.build.version.sdk).Trim())) -ForegroundColor Green

if (-not $SkipInstall) {
    Write-Host "`n[2/6] 安装 $Apk" -ForegroundColor Cyan
    & $Adb install -r -g $Apk 2>&1 | Select-Object -Last 3
}

Write-Host "`n[3/6] 授权麦克风与通知" -ForegroundColor Cyan
& $Adb shell pm grant $Package android.permission.RECORD_AUDIO 2>&1 | Out-Null
& $Adb shell pm grant $Package android.permission.POST_NOTIFICATIONS 2>&1 | Out-Null
$perm = (& $Adb shell dumpsys package $Package | Select-String "RECORD_AUDIO: granted") -join ""
if ($perm) { Write-Host "  RECORD_AUDIO = granted" -ForegroundColor Green } else { Write-Host "  权限未授予（可在 App 内手动允许）" -ForegroundColor Yellow }

if (-not $SkipModel) {
    Write-Host "`n[4/6] 推送本地语音模型（228MB，跳过 App 内下载）" -ForegroundColor Cyan
    $model = Join-Path $root "tools\models\sense-voice\model.int8.onnx"
    $tokens = Join-Path $root "tools\models\sense-voice\tokens.txt"
    if ((Test-Path $model) -and (Test-Path $tokens)) {
        & $Adb push $model /data/local/tmp/echo-model.onnx 2>&1 | Select-Object -Last 1
        & $Adb push $tokens /data/local/tmp/echo-tokens.txt 2>&1 | Select-Object -Last 1
        $target = "files/models/sense-voice"
        & $Adb shell run-as $Package mkdir -p $target 2>&1 | Out-Null
        & $Adb shell run-as $Package cp /data/local/tmp/echo-model.onnx "$target/model.int8.onnx" 2>&1 | Out-Null
        & $Adb shell run-as $Package cp /data/local/tmp/echo-tokens.txt "$target/tokens.txt" 2>&1 | Out-Null
        & $Adb shell rm -f /data/local/tmp/echo-model.onnx /data/local/tmp/echo-tokens.txt 2>&1 | Out-Null
        $ls = (& $Adb shell run-as $Package ls -l $target) -join "`n"
        if ($ls -match "model.int8.onnx") { Write-Host "  模型已就位：`n$ls" -ForegroundColor Green } else { Write-Host "  推送失败：$ls" -ForegroundColor Yellow }
    } else {
        Write-Host "  本地没有模型文件，跳过（可在 App 内下载）" -ForegroundColor Yellow
    }
}

Write-Host "`n[5/6] 清日志并启动 App" -ForegroundColor Cyan
& $Adb logcat -c 2>&1 | Out-Null
& $Adb shell am start -n "$Package/com.echo.recall.MainActivity" 2>&1 | Select-Object -First 3
Start-Sleep -Seconds 6

Write-Host "`n[6/6] 截屏与日志" -ForegroundColor Cyan
Shot "01-home"
& $Adb shell dumpsys window | Select-String "mCurrentFocus" | Select-Object -First 1
Write-Host "  --- 崩溃/错误日志 ---" -ForegroundColor DarkGray
& $Adb logcat -d -t 120 2>&1 | Select-String -Pattern "FATAL|AndroidRuntime|E echo|RecorderEngine|VadEngine|EchoApp" | Select-Object -Last 15

Write-Host "`n完成。截图在 tools\out\shots\" -ForegroundColor Cyan
