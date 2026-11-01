# 更改签名密码（Windows / PowerShell）

> 目的：把签名密钥的保护密码换成一个你自己知道的强密码。
> **换密码不会改变签名指纹**，所以已安装的 App 仍可正常覆盖更新（不需要卸载）。

## 前置

- 密钥文件：`C:/project/project.jks`（PKCS12 格式，store 密码与 key 密码相同）
- 配置：仓库根目录 `keystore.properties`（已被 `.gitignore` 忽略，不会进 Git）

## 用法

在仓库根目录打开 PowerShell 执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\change-signing-password.ps1
```

脚本会：

1. 先把 `project.jks` 备份成 `project.jks.bak-<时间戳>`
2. 隐藏输入方式让你输两遍新密码（不会显示、不会写进本文件或日志）
3. 调用 `keytool -storepasswd` 修改密码
4. 同步更新 `keystore.properties` 里的 `storePassword` / `keyPassword`
5. 重新构建 release，并用 `apksigner --print-certs` 打印签名指纹供你比对

## 换完必须核对签名指纹

签名指纹应保持为（大小写不敏感）：

```text
SHA256: 0E:13:E1:B5:27:EC:65:36:59:21:2D:2C:02:5E:B1:4A:83:3C:DD:20:50:43:4E:EC:A9:88:31:FE:93:DA:45:B9
```

只要这个指纹不变，说明只是换了密码、密钥没变，已安装的 App 可以直接升级。

## 备份

换密码前已备份一份到：

```text
C:\Users\86159\Desktop\CycleAlarm-keystore-backup-20261101\
  project.jks
  keystore.properties
```

**请把整个文件夹复制到 U 盘 / 加密云盘**。密钥是唯一「丢了就永远无法覆盖更新」的东西；
如果以后丢失，只能换新密钥，用户必须卸载重装（本地数据清空，需先导出备份）。
