# v0.2.9-alpha 构建记录

2026-10-30（Asia/Hong_Kong）。Java 21 / Gradle 8.8 / NeoForge 21.1.255。负片渲染与光照条移除后 compileJava --stacktrace 成功（8 秒）；最终通过云代理/CA 助手执行实际 `./gradlew build --stacktrace`，BUILD SUCCESSFUL（6 秒）。TLS 校验保持开启。

`tools/verify-artifact.py` 通过 Java 21 字节码、元数据、Mixin、语言和宿主接口检查，既有相机几何回归 238356 个采样通过。源码核对 Exposure 的 NEGATIVE / ModifiedImage 只读效果、稳定图片变体标识、原图渲染器的透明混合、客户端菜单进度及成品/照片堆/混色相纸的帧读取 API。git diff --check 通过，旧光照拾取点与旧 BE 进度读取均已移除。

产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.2.9-alpha.jar`。Actions 仅在完整构建和检查成功后发布同次产物。

未启动游戏或服务器，未进行游戏内或画面测试。待玩家验证负片到成片的实际观感、透明层叠、混色流程、取走成品/切换帧以及光影资源包兼容；编译检查不能验证这些视觉与运行时行为。

以下保留旧版本记录。

# v0.2.8-alpha 构建记录

2026-10-30（Asia/Hong_Kong）。Java 21 / Gradle 8.8 / NeoForge 21.1.255。通过云代理/CA 助手执行实际 `./gradlew build --stacktrace`，BUILD SUCCESSFUL（9 秒），TLS 校验保持开启。

`tools/verify-artifact.py` 打包和宿主接口检查通过，既有相机几何回归 238356 个采样通过。额外用 javap 核对真实 Exposure iBvaR6AK JAR：LightroomMenu.fromBuffer 创建独立 SimpleContainerData，getData 返回该菜单字段，证实上一版使用 BE 进度的错误；修正为与原版 LightroomScreen 相同的数据来源。

产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.2.8-alpha.jar`。Actions 仅在完整构建和检查成功后发布同次产物。

未运行游戏、客户端或服务器。静态检查不代表显影效果已经游戏内验证；图片渐入、完成/取消切换以及光影/资源包兼容仍需玩家实测。

以下保留旧版本记录。

# v0.2.7-alpha 构建记录

2026-10-30（Asia/Shanghai）。Java 21 / Gradle 8.8 / NeoForge 21.1.255。通过云代理/CA 助手执行实际 `./gradlew build --stacktrace`，BUILD SUCCESSFUL（8 秒）；TLS 校验保持开启。

`tools/verify-artifact.py` 通过：Java 21 字节码、元数据、语言、Mixin 类及宿主接口检查；既有相机几何回归检查 238356 个采样通过。本次仅修改暗房中央图像的渲染颜色透明度，用当前原菜单同步的冲印进度驱动，不更改游戏逻辑或相机动作。

产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.2.7-alpha.jar`。Actions 在成功构建和检查后发布同次产物，远程结果以实际工作流日志为准。

未启动 Minecraft、客户端或服务器，未进行游戏内或画面测试。待验证透明混合、相纸上的显影观感、光影与资源包兼容。旁观者不接收原菜单的持续进度，保持完整预览；创造模式瞬时冲印即时显示。

以下保留旧版本记录。

# v0.2.6-alpha 构建记录

2026-10-29（Asia/Hong_Kong）。Java 21 / Gradle 8.8 / NeoForge 21.1.255。分阶段 compileJava 成功，最终通过云代理/CA 助手执行实际 `./gradlew build --stacktrace`，BUILD SUCCESSFUL（8 秒）。TLS 校验保持开启。

`tools/verify-artifact.py` 打包/宿主接口检查通过；成品 JAR 的独立 CameraAssembly 数学检查通过。新增侧向镜头、胶卷位置、左右支撑断言；238356 个胶卷路径采样，另检查左手镜头端点、变换可逆、初末连续性、可达距离及原模型闪光灯间距。测试仅加载附属的纯 Java 几何类，没有 Minecraft 或依赖运行类路径。数学检查不能验证动作观感，上一版用户实测反馈正说明这一限制。

产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.2.6-alpha.jar`。Actions 继续在完整构建和检查通过后才发布同次产物。

未启动游戏、客户端或服务器，未进行画面/游戏内测试。仍需验证侧向构图、两手操作及支撑的实际观感、快速换物/中断、缩放与替换模型资源包。暗房代码本次未修改。

以下保留旧版本记录。

# v0.2.5-alpha 构建与几何检查

2026-10-29（Asia/Hong_Kong）。Java 21 / Gradle 8.8 / NeoForge 21.1.255。通过 cloud-build.py 配置云代理/CA 执行实际 `./gradlew build --stacktrace` 成功；未关闭 TLS 校验。

`tools/verify-artifact.py` 检查 Java 21 字节码、版本/依赖、Mixin、语言键、许可证和宿主私有接口，并用 JDK 运行 `tools/CheckCameraAssembly.java`。该独立程序的类路径只有附属 JAR，只加载其中纯 Java CameraAssembly，不加载 Minecraft 类或依赖、不开窗口、不启动客户端/服务器。

最终 278856 个路径采样通过，包括连续性、变换可逆、可达距离、掌/袖包络及胶卷道具与原版闪光灯间距。涵盖 9 个初始构图、3 个初始手位、装卸/手持状态、30/60/144 FPS、.25/1/3 动画速度和平滑后的轨迹。过程中发现外侧转弯及袖子下缘过近，已扩大绕行范围并调整握持倾角到 26°；最终采样最小间距约 .0093 格。它是指定原模型的数学回归检查，不是游戏渲染/碰撞引擎测试。

产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.2.5-alpha.jar`。GitHub Actions 在构建和上述检查均成功后才发布同次 Artifact。没有进行游戏内或多人测试，资源包改模、异常视角、快速中断及实际手指贴合仍待玩家验证。

以下保留旧版本记录。

# v0.2.4-alpha 构建记录

2026-10-29（Asia/Shanghai）。Java 21.0.9 / Gradle 8.8 / NeoForge 21.1.255。分阶段 compileJava 成功，最终通过云代理/CA 助手执行实际 `./gradlew build --stacktrace`，结果 BUILD SUCCESSFUL（首轮 10 秒，最终修正后 7 秒）。没有关闭 TLS 校验。

`tools/verify-artifact.py` 通过：Java 21 字节码、版本/依赖、Mixin 类、语言键、许可证、无嵌套依赖，以及官方 Cinematic 0.18.12 JAR 的抽屉接口和 Hands.position/drawArm 签名。使用 javap 解析字节码，不执行游戏类。`git diff --check` 通过。

产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.2.4-alpha.jar`。Actions 配置继续在独立成功构建后才发布同次 JAR，实际远程状态以 Actions 日志为准。

未启动 Minecraft、未做游戏内、服务端、Mixin 实际应用或视觉验证。Gradle test 为 NO-SOURCE，不宣称动画测试通过。需实测重点：左右手一致性、可达距离与接口贴合、附件旋转收尾、库存/潜行两种打开方式、拖拽预览、快速换物与中断时掏取。极端缩放/侧身可能因限距而使手指略离物品。

以下保留旧版本记录。

# v0.2.3-alpha 构建记录

Java 21 / Gradle 8.8，执行 `./gradlew build --stacktrace`（通过 cloud-build.py 提供云代理/CA）成功。JAR 静态检查通过；额外以 javap 检查官方 EMI 1.1.22 与 1.1.24 NeoForge JAR 中目标方法及 null-screen 行为。EMI 不作为必需依赖，不打包其 JAR。

未启动 Minecraft、未进行游戏内或服务器测试。Actions 只在成功构建后发布同次产物。以下为旧版本记录。

# v0.2.2-alpha 构建记录

Java 21.0.9 / Gradle 8.8 / NeoForge 21.1.255。相机与抽屉阶段 compileJava 通过，暗房状态阶段 compileJava 通过，完整 `./gradlew build --stacktrace`（由 cloud-build.py 配置代理与 CA）通过。

静态检查包括 Java 21 字节码、元数据、语言键、Mixin 类文件，以及使用 javap 检查实际 Cinematic 0.18.12 发布 JAR 中抽屉适配所需的字段、方法和 GUI 调用描述符。javap 只解析类文件，不加载游戏。运行结果以本次 Actions 日志为准。

未启动 Minecraft，未执行 runClient/runServer，未做游戏内动画、Mixin 应用或输入验证。旧版本记录如下。

# v0.2.1-alpha 修复构建记录

环境：Java 21.0.9、Gradle 8.8、NeoForge 21.1.255；依赖版本未改变。

分阶段 compileJava --stacktrace：持久物品、世界照片、冲印动作均通过；相机部分发现一次变量作用域编译错误，已修复并再次通过。最终执行 `./gradlew build --stacktrace`（通过 tools/cloud-build.py 注入云代理与信任库）及 tools/verify-artifact.py，完整构建 BUILD SUCCESSFUL（9 秒），打包检查 PASS。Actions 在推送后独立构建并仅在成功后发布。

未启动 Minecraft，未执行 runClient/runServer，未进行游戏内或图形测试。编译检查不能证明 BER、手部姿态、GUI 拖拽和网络场景在游戏中的实际表现。

以下保留旧版本历史构建记录。

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
