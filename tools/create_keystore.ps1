# 一键生成正式签名密钥库（.jks）并写入 keystore.properties
# 用法：在项目根目录运行  .\tools\create_keystore.ps1
# 依赖：keytool（JDK 自带，Android Studio 的 JBR 也包含）

param(
    [string]$KeystorePath = "C:\secure\cycle_alarm.jks",
    [string]$Alias = "cycle_alarm"
)

$ErrorActionPreference = "Stop"

$dir = Split-Path $KeystorePath -Parent
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}

$storePass = Read-Host "请输入密钥库密码（至少 6 位，务必牢记）" -AsSecureString
$keyPass = Read-Host "请再次输入同一密码（用作密钥密码）" -AsSecureString

$name = Read-Host "姓名/姓名缩写（可随意填写）"
$org = Read-Host "组织名（可随意填写）"
$city = Read-Host "城市"

$storePlain = [System.Net.NetworkCredential]::new("", $storePass).Password
$keyPlain = [System.Net.NetworkCredential]::new("", $keyPass).Password

$keytool = "keytool"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\keytool.exe"))) {
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
}

& $keytool -genkeypair -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA -keysize 2048 `
    -validity 36500 `
    -storepass $storePlain -keypass $keyPlain `
    -dname "CN=$name, OU=$org, O=$org, L=$city, ST=$city, C=CN"

if ($LASTEXITCODE -ne 0) {
    throw "keytool 生成失败，请检查 JDK/JBR 是否可用"
}

$props = @"
storeFile=$($KeystorePath.Replace('\', '/'))
storePassword=$storePlain
keyAlias=$Alias
keyPassword=$keyPlain
"@
Set-Content -Path (Join-Path $PSScriptRoot "..\keystore.properties") -Value $props -Encoding UTF8

Write-Host ""
Write-Host "已生成：$KeystorePath"
Write-Host "已写入：keystore.properties"
Write-Host "!!! 立即备份密钥库文件，并把密码存到密码管理器。丢失密钥库 = 无法再更新此应用 !!!"
