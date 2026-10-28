# v0.2.0-alpha 编译记录

本次在完整 Java 21 环境运行 Gradle Wrapper build --stacktrace，通过云代理助手保留 TLS 校验。结果 BUILD SUCCESSFUL；随后 tools/verify-artifact.py 静态打包检查通过。

新增的 LightroomStation、CameraSceneScreen、AddonAvailabilityMixin 与公共 LightroomMenuProviderMixin 均编译通过。未运行 Minecraft、客户端、服务器、游戏内或多人测试。公共 Mixin 只适配原菜单 Provider 与 BlockPos；其他渲染兼容 Mixin 保持客户端加载。

GitHub Actions 保存实际构建日志，发布任务只上传成功构建产物。

---
以下为 v0.1.0-alpha 的历史记录（原 GUI 保留方案已在 v0.2 替换）：

# 编译与静态检查记录

日期：2026-10-28（Asia/Shanghai）。

## 已完成

- 实际使用完整 Temurin JDK 21.0.9、Gradle Wrapper 8.8、ModDevGradle 2.0.148、NeoForge 21.1.255 编译。
- 通过代理助手执行的实际 Wrapper 命令为 `./gradlew build --stacktrace`，另传入云端代理和系统信任库 JVM 参数；构建结果为 **BUILD SUCCESSFUL**。
- `tools/verify-artifact.py` 静态检查通过：Java 21 class 主版本号 65、mod ID/版本、依赖声明、仅客户端 Mixin 列表及对应 class、中英语言键、许可证、没有嵌入依赖 JAR。
- 使用 javap 直接读取实际 NeoForge 和 Cinematic JAR 核对 Camera 三参数 setRotation、原点击/容器包处理方法、透明背景方法及 Hands.drawArm 的实际描述符。此步骤不加载游戏。
- Git diff 空白检查与源码敏感令牌模式扫描通过。
- 输出 `build/libs/cinematic-exposure-1.21.1-neoforge-0.1.0-alpha.jar`。

## 真实遇到并解决的环境问题

1. 首次原始 Wrapper 下载 `https://services.gradle.org/distributions/gradle-9.2.1-bin.zip` 报 `java.net.ConnectException: Connection refused`。Java 未自动使用环境 HTTPS 代理。最终 Wrapper 固定为官方 Gradle 8.8，并通过 `tools/cloud-build.py` 设置代理，保留 TLS 校验和系统证书。
2. 默认宿主 Java 在 NeoForm 重编译步骤报 `error: release version 21 not supported`。改用工作区已有的完整 Temurin JDK 21 后，Minecraft/NeoForge 开发制品重编译与附属编译均成功。
3. Cinematic 官方 JAR 已真实下载并通过 SHA-256：`f8593eff597be7616d109572d880da43c9699684964dba166b97f30bd1c52340`。Exposure 使用官方 Modrinth 固定版本 ID `iBvaR6AK`，没有替代依赖或伪造 JAR。

## 检查范围

没有运行 Minecraft、客户端、服务器、游戏内自动化、多人测试或游戏画面验证。Gradle 的 test 任务没有游戏测试，编译日志中的 `test NO-SOURCE` 不表示测试通过。Mixin 方法存在性检查不等于运行时注入验证。

GitHub Actions 的实际状态以仓库 [Actions 页面](https://github.com/DonQuijotee/cinematic-exposure/actions) 为准。工作流仅在成功构建后上传安装 JAR；手动启用 release 时，发布任务消费同次成功构建的 Artifact，失败不发布。
