# 发布维护

## 本地令牌

两平台的访问令牌保存在 `%LOCALAPPDATA%\SealedWordOrbPublishing\credentials.json`，使用 Windows 当前用户的 DPAPI 加密。源码、发布资料和 JAR 都不包含令牌。

发布脚本在运行时解密并通过 HTTPS 请求头发送给对应平台，不将令牌放入 URL 或日志。只能在同一 Windows 用户环境直接使用；复制加密文件到别的账户/机器不保证能解密。

## 版本编号

- `0.1.0`：原始可重复使用版，旧开发编号 `1.0.0`。仅修改 JAR 的 mods.toml 和 MANIFEST.MF 版本字段，保留原始类文件。
- `1.0.0`：当前一次性版，旧开发编号 `1.1.0`。已从当前源码重新构建。
- 旧编号产物备份保存在 `build/publishing/original-artifacts/`，不要上传或安装这些备份。

英文描述和各版更新日志均位于此目录。`modrinth-state.json` 记录远端项目身份和最近验证状态；它不是凭据文件。

## 后续发布

更新源码版本、构建并验证新 JAR，准备相应的 `CHANGELOG-<版本>.en.md`。发布新版本：

```powershell
.\publishing\Publish-Modrinth.ps1 -Versions '1.0.1'
.\publishing\Publish-CurseForge.ps1 -ProjectId 1706061 -Versions '1.0.1'
```

Modrinth 脚本更新项目英文介绍，并在上传后验证服务端 SHA-512。同版本号存在不同文件时拒绝覆盖。首次创建的草稿可以加 `-SubmitForReview` 提交审核。

CurseForge 项目 ID 为 1706061。脚本自动查找正确的 Minecraft 1.20.1、Forge、Java 17、Client 和 Server 标签；成功后记录文件 ID，避免正常重跑重复上传。若网络中断导致结果不确定，先到作者后台核实，不能盲目重试。项目介绍需要网站编辑。

当前两个版本已被上传接口接收：0.1.0 文件 ID 为 8940480，1.0.0 文件 ID 为 8940481。审核状态及主页正文尚未通过网页验证；浏览器连接仍不可用。

没有创建后台定时任务；后续更新可按请求复用这些脚本同步上传。
