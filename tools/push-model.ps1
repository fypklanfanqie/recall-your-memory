# 把本地已下载的语音模型推到设备，省掉 228MB 首次下载
#
#   powershell -ExecutionPolicy Bypass -File tools\push-model.ps1
#   powershell -ExecutionPolicy Bypass -File tools\push-model.ps1 -Package com.echo.recall
#
param(
    [string]$Package = "com.echo.recall.debug",
    [string]$Adb = "C:\Users\Lfq06\AppData\Local\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modelDir = Join-Path $root "tools\models\sense-voice"

$model = Join-Path $modelDir "model.int8.onnx"
$tokens = Join-Path $modelDir "tokens.txt"

foreach ($f in @($model, $tokens)) {
    if (-not (Test-Path $f)) {
        throw "缺少文件: $f`n先下载：java tools\Download.java https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx $f proxy"
    }
}

Write-Host "[echo] 目标包名: $Package"
$devices = & $Adb devices | Select-String "device$"
if (-not $devices) { throw "没有检测到设备（adb devices 为空）" }

# 1) 推到公共临时目录
& $Adb push $model /data/local/tmp/echo-model.int8.onnx
& $Adb push $tokens /data/local/tmp/echo-tokens.txt

# 2) 用 run-as 放进应用私有目录（仅 debuggable 包可用）
$target = "files/models/sense-voice"
& $Adb shell run-as $Package mkdir -p $target
& $Adb shell run-as $Package cp /data/local/tmp/echo-model.int8.onnx "$target/model.int8.onnx"
& $Adb shell run-as $Package cp /data/local/tmp/echo-tokens.txt "$target/tokens.txt"

# 3) 校验
Write-Host "[echo] 设备上的文件："
& $Adb shell run-as $Package ls -l $target

& $Adb shell rm -f /data/local/tmp/echo-model.int8.onnx /data/local/tmp/echo-tokens.txt
Write-Host "[echo] 完成。打开 App → 设置 → 本地语音模型，应显示「SenseVoice 已就绪」。"
