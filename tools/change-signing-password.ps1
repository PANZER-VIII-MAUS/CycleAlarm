<#
.SYNOPSIS
    更改 CycleAlarm release 签名密钥的密码（不改密钥本身，签名指纹不变）。

.DESCRIPTION
    1. 备份密钥文件
    2. 隐藏输入新密码两遍
    3. keytool -storepasswd 修改密码
    4. 同步更新 keystore.properties
    5. 重新构建 release 并打印签名指纹供核对

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\tools\change-signing-password.ps1
#>
[CmdletBinding()]
param(
    [string]$PropertiesPath = (Join-Path $PSScriptRoot '..\keystore.properties'),
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

function Read-PlainProperty([string]$path, [string]$key) {
    $line = Get-Content -Path $path -Encoding UTF8 | Where-Object { $_ -match "^\s*$([regex]::Escape($key))\s*=" } | Select-Object -First 1
    if (-not $line) { throw "未在 $path 中找到 $key" }
    return ($line -replace "^\s*$([regex]::Escape($key))\s*=\s*", '').Trim()
}

function Write-PlainProperty([string]$path, [hashtable]$values) {
    $lines = Get-Content -Path $path -Encoding UTF8
    $out = foreach ($line in $lines) {
        $matched = $false
        foreach ($k in $values.Keys) {
            if ($line -match "^\s*$([regex]::Escape($k))\s*=") {
                "$k=$($values[$k])"
                $matched = $true
                break
            }
        }
        if (-not $matched) { $line }
    }
    Set-Content -Path $path -Value $out -Encoding UTF8
}

Write-Host '=== CycleAlarm 签名密码修改 ===' -ForegroundColor Cyan

$propertiesPath = (Resolve-Path $PropertiesPath).Path
$storeFile = Read-PlainProperty $propertiesPath 'storeFile'
$oldStorePass = Read-PlainProperty $propertiesPath 'storePassword'
$oldKeyPass = Read-PlainProperty $propertiesPath 'keyPassword'
$keyAlias = Read-PlainProperty $propertiesPath 'keyAlias'

if (-not (Test-Path $storeFile)) { throw "找不到密钥文件：$storeFile" }
Write-Host "密钥文件：$storeFile"
Write-Host "别名：$keyAlias"

# 1) 备份
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupPath = "$storeFile.bak-$stamp"
Copy-Item -Path $storeFile -Destination $backupPath -Force
Write-Host "已备份：$backupPath" -ForegroundColor Green

# 2) 输入新密码（隐藏）
$secure1 = Read-Host -AsSecureString '请输入新密码'
$secure2 = Read-Host -AsSecureString '请再输入一次'
$bstr1 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure1)
$bstr2 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure2)
try {
    $newPass1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr1)
    $newPass2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr2)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr1) | Out-Null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr2) | Out-Null
}

if ($newPass1 -ne $newPass2) { throw '两次输入的密码不一致' }
if ($newPass1.Length -lt 8) { throw '新密码至少 8 位' }
if ($newPass1 -eq $oldStorePass) { throw '新密码与旧密码相同，未做修改' }

# 3) 定位 keytool
$keytool = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\keytool.exe') }
$candidates += @(
    'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe',
    'C:\Program Files\Java\jdk-21.0.12\bin\keytool.exe',
    'C:\Program Files\Java\jdk-17\bin\keytool.exe'
)
foreach ($c in $candidates) { if (Test-Path $c) { $keytool = $c; break } }
if (-not $keytool) {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { $keytool = $cmd.Source }
}
if (-not $keytool) { throw '找不到 keytool，请确认已安装 JDK 或 Android Studio' }
Write-Host "keytool：$keytool"

# 4) 修改密码（PKCS12：store 与 key 使用同一密码）
Write-Host '正在修改密钥库密码...' -ForegroundColor Yellow
& $keytool -storepasswd -keystore $storeFile -storepass $oldStorePass -new $newPass1
if ($LASTEXITCODE -ne 0) { throw "keytool 修改密码失败（退出码 $LASTEXITCODE）" }

# 5) 更新 keystore.properties
Write-PlainProperty $propertiesPath @{
    storePassword = $newPass1
    keyPassword   = $newPass1
}
Write-Host '已更新 keystore.properties' -ForegroundColor Green

# 6) 校验：能重新打开密钥库
& $keytool -list -keystore $storeFile -storepass $newPass1 -alias $keyAlias | Out-Null
if ($LASTEXITCODE -ne 0) { throw '新密码校验失败，请用备份还原' }
Write-Host '新密码校验通过' -ForegroundColor Green

# 7) 重新构建并打印签名指纹
if (-not $SkipBuild) {
    Write-Host '正在重新构建 release...' -ForegroundColor Yellow
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        & .\gradlew.bat -q assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "构建失败（退出码 $LASTEXITCODE）" }
    } finally {
        Pop-Location
    }

    $apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release.apk'
    $apksigner = 'C:\Users\86159\AppData\Local\Android\Sdk\build-tools\34.0.0\apksigner.bat'
    if (Test-Path $apksigner) {
        Write-Host '=== 签名指纹（应与换密码前一致）===' -ForegroundColor Cyan
        & $apksigner verify --print-certs $apk | Select-String 'SHA-256'
    } else {
        Write-Host '未找到 apksigner，跳过指纹核对' -ForegroundColor Yellow
    }
}

Write-Host ''
Write-Host '完成。请把新密码记到密码管理器里；旧密码已失效。' -ForegroundColor Green
Write-Host "密钥备份：$backupPath（确认新密码可用后可删，但建议保留一份）" -ForegroundColor Green
