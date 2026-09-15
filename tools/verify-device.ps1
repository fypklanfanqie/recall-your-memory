# 真机验收自动化：安装 / 权限 / 保活 / 电量 / 崩溃恢复
#
#   powershell -ExecutionPolicy Bypass -File tools\verify-device.ps1            # 全部检查
#   powershell -ExecutionPolicy Bypass -File tools\verify-device.ps1 -SoakHours 8
#
param(
    [string]$Adb = "C:\Users\Lfq06\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.echo.recall.debug",
    [string]$Apk = "",
    [int]$SoakHours = 0
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
if (-not $Apk) { $Apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk" }

function Section($title) { Write-Host "`n===== $title =====" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "  [OK]   $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "  [WARN] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red }

Section "设备"
$devices = & $Adb devices | Select-String "device$"
if (-not $devices) { Fail "未检测到设备，请插线并开启 USB 调试"; exit 1 }
& $Adb devices -l
$sdk = (& $Adb shell getprop ro.build.version.sdk).Trim()
$model = (& $Adb shell getprop ro.product.model).Trim()
$brand = (& $Adb shell getprop ro.product.manufacturer).Trim()
$battery = (& $Adb shell dumpsys battery | Select-String "level:").ToString().Trim()
Ok "机型 $brand $model · API $sdk · $battery"

Section "安装"
if (Test-Path $Apk) {
    & $Adb install -r $Apk | Out-Host
    Ok "已安装 $Apk"
} else {
    Warn "未找到 APK：$Apk（先运行 build.ps1 assembleDebug）"
}

Section "权限"
& $Adb shell pm grant $Package android.permission.RECORD_AUDIO 2>$null
& $Adb shell pm grant $Package android.permission.POST_NOTIFICATIONS 2>$null
$granted = (& $Adb shell dumpsys package $Package | Select-String "RECORD_AUDIO: granted")
if ($granted) { Ok "RECORD_AUDIO 已授予" } else { Warn "RECORD_AUDIO 未授予（可在 App 内授权）" }

Section "启动与前台服务"
& $Adb shell am start -n "$Package/com.echo.recall.MainActivity" | Out-Host
Start-Sleep -Seconds 4
$svc = & $Adb shell dumpsys activity services $Package | Select-String "RecorderService"
if ($svc) { Ok "前台服务已存在" } else { Warn "服务未运行（需在 App 内点「开启聆听」）" }

$notif = & $Adb shell dumpsys notification --noredact | Select-String "echo_listening"
if ($notif) { Ok "常驻通知存在" } else { Warn "未看到常驻通知" }

Section "保活状态"
$power = & $Adb shell dumpsys deviceidle whitelist | Select-String $Package
if ($power) { Ok "已在电池优化白名单" } else { Warn "未加入白名单：设置 → 后台保活引导" }

Section "内存（PSS）"
& $Adb shell dumpsys meminfo $Package | Select-String "TOTAL PSS|TOTAL:" | Select-Object -First 3

Section "崩溃恢复"
& $Adb shell am crash $Package 2>$null | Out-Null
Start-Sleep -Seconds 6
$after = & $Adb shell pidof $Package
if ($after) { Ok "进程已自动恢复 (pid $after)" } else { Warn "进程未自动恢复（START_STICKY 取决于系统策略）" }

Section "回溯与库"
$db = & $Adb shell run-as $Package ls files/memories 2>$null
if ($db) { Ok "记忆音频目录：`n$db" } else { Warn "还没有记忆（先在 App 里说几句话再点回溯）" }
$modelFiles = & $Adb shell run-as $Package ls files/models/sense-voice 2>$null
if ($modelFiles) { Ok "模型文件：`n$modelFiles" } else { Warn "未安装转写模型：可用 tools\push-model.ps1 推送" }

if ($SoakHours -gt 0) {
    Section "过夜 soak（$SoakHours 小时）"
    $log = Join-Path $root "tools\out\soak-$(Get-Date -Format yyyyMMdd-HHmm).log"
    New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
    "time,level,pss_kb,service" | Out-File $log -Encoding utf8
    $end = (Get-Date).AddHours($SoakHours)
    while ((Get-Date) -lt $end) {
        $level = (& $Adb shell dumpsys battery | Select-String "level:").ToString() -replace "[^0-9]", ""
        $pss = (& $Adb shell dumpsys meminfo $Package | Select-String "TOTAL PSS" | Select-Object -First 1).ToString() -replace "[^0-9]", ""
        $alive = if (& $Adb shell dumpsys activity services $Package | Select-String "RecorderService") { 1 } else { 0 }
        "$(Get-Date -Format 'HH:mm:ss'),$level,$pss,$alive" | Add-Content $log
        if ($alive -eq 0) { Warn "服务已中断（$(Get-Date -Format 'HH:mm:ss')）" }
        Start-Sleep -Seconds 600
    }
    Ok "soak 日志：$log（用 Excel 打开可看电量曲线）"
}

Write-Host "`n完成。逐项对照 docs\VERIFY.md 勾选即可。" -ForegroundColor Cyan
