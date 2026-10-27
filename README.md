# Cinematic Exposure

为 **Minecraft 1.21.1 / NeoForge** 提供 Exposure 相机改装与 Lightroom 第一人称视觉交互的独立附属模组。

**Alpha，尚未经游戏内测试。** 编译通过不代表游戏内效果或兼容性已经验证。本项目没有启动 Minecraft 客户端、服务器或游戏内自动化测试。

## 安装

需要 Java 21，以及以下模组：

| 依赖 | 本项目构建基线 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.255 |
| [Cinematic Interactions](https://github.com/dcvke/CinematicInteractions/releases/tag/v0.18.12-neoforge-1.21.1) | 0.18.12，NeoForge 1.21.1 版本 |
| [Exposure](https://modrinth.com/mod/exposure/version/iBvaR6AK) | 1.9.19，NeoForge 1.21.1 版本 |

将上述依赖和 `cinematic-exposure-1.21.1-neoforge-0.1.0-alpha.jar` 放入游戏 `mods/`。依赖不内嵌。本附属的动作与渲染仅在客户端运行，元数据按 Cinematic API 文档将两个原模组声明为双方必需依赖。其他补丁版本未验证。

## 已编写的功能

- **Lightroom 优先接入**：打开原界面时平滑靠近工作区，双手落到台面；胶卷装卸、同步冲印开始时的按压与扶纸动作、成品照片取出动作；识别照片堆组件内的数量变化。
- **手持相机改装**：原改装窗口打开时显示 Exposure 原相机模型，左手托机、右手操作；支持实际存在的胶卷、闪光灯、镜头、滤镜。镜头动作包含拿起、对准、旋转、松手，拆卸反向播放。
- 复用 Cinematic 的玩家皮肤与袖层手臂绘制，使用缓入缓出和手部平滑跟随；镜头位移受距离限制与碰撞射线约束。
- 关闭、预览/帮助子窗口、切换容器、配置关闭、无效相机、方块移除、切换世界或视角等情况可以中断。正常退出平滑恢复；死亡、离开世界或宿主场景接管时立即释放。
- Exposure 原容器、全部按钮、快捷移动、配方、照片预览、消耗和网络路径继续工作。缩放后宽度至少 440 时，默认将完整原窗口及其按钮一起移到右边，左边显示动画；保留原槽位与鼠标命中计算。窄屏保持原窗口居中。

不包含举放相机、快门、取景器、相机支架、自拍倒计时或其他工作站。第三人称下保留原界面，不启动第一人称场景。

## 状态同步与边界

动画观察原 GUI 使用的真实 Menu 状态，包括原版客户端预测以及 Exposure/原版收到的同步数据。它不发送、延迟、重放任何点击，不执行附件写回、配方、照片生成或物品消耗。普通冲印跟随 `LightroomMenu.isPrinting()` 的同步状态，瞬时/创造冲印跟随结果增加；单纯点击未获准的冲印按钮不制造成功动作。

模型使用独立 `ItemStack.copy()` 快照。为避免安装时镜头同时出现在机身与右手中，**只在渲染副本上**暂时隐藏/显示相应模型组件；此副本不会交给容器或网络，不改变实际附件数据或数量。

初始内容建立基线，不回放已有物品；整包同步纠正会清除未完成动作并重建基线。同一槽位的新变化可替换旧视觉动作，手部平滑回接。动画队列有上限；高频操作优先跟随最新状态，而不积压过时动作。动画不阻塞真实 GUI 操作。

## 配置

游戏首次加载生成 `config/cinematic_exposure-client.toml`，也可通过 NeoForge 模组配置界面修改。示例见 [config/cinematic_exposure-client.example.toml](config/cinematic_exposure-client.example.toml)。

```toml
[animations]
camera = true
lightroom = true
speed = 1.0
# 宽屏时原窗口右置，下次界面初始化时生效。
dockGui = true
```

`speed` 范围为 0.25–3.0，同时影响手部动作和进出镜头速度。提供简体中文、英语配置语言文件。

## 构建

完整 **JDK 21**（包含 javac）和联网环境：

```sh
./gradlew build --stacktrace
```

Windows 使用 `gradlew.bat build --stacktrace`。Wrapper 固定 Gradle 8.8，校验官方发行包 SHA-256。构建通过官方 Maven 仓库获得 NeoForge，通过 Modrinth 版本 ID 固定 Exposure，并自动下载和校验 Cinematic 0.18.12 官方发布 JAR 的 SHA-256。无需手动收集依赖，不使用伪造依赖，不禁用 TLS。

若云环境只有 HTTP(S) 代理、Java 不自动读取代理变量，可使用随附助手（仍验证 TLS）：

```sh
python3 tools/cloud-build.py build --stacktrace
```

助手只读取代理配置，并在存在时使用系统 Java 信任库，不保存或输出访问令牌。当前云环境构建应选择完整 JDK，而非宿主不完整的默认编译器；例如将 `JAVA_HOME` 指向已安装的 JDK 21。

可安装产物：`build/libs/cinematic-exposure-1.21.1-neoforge-0.1.0-alpha.jar`。工程未配置游戏运行任务；开发与 CI 均只做构建。

## GitHub Actions 与发布

[构建工作流](.github/workflows/build.yml) 使用 Java 21 与 Gradle Wrapper，支持 push、PR 和手动触发；构建成功才上传上述唯一可安装 JAR，日志总会保留，不上传 sources/dev JAR。

手动触发 `Build Cinematic Exposure`，选中 `release`：构建任务成功后，发布任务才创建 `v0.1.0-alpha` 标签和 Pre-release，并上传**该次 Actions 的构建产物**。发布说明为中文。创建已存在标签/Release 会失败，不自动覆盖旧发布。普通 push/PR 不发布。

## 尚未验证与已知限制

- 没有游戏内验证：实际镜头构图、袖层/手部位置、附件接口对齐、穿模、冲印节奏，需要玩家测试调整。碰撞约束是尽量减少穿模，不能保证任意地形下无穿模。
- 缩放后宽度小于 440，或关闭窗口右置时，原窗口可能覆盖部分背景动画；原窗口的交互保持可用。
- 使用世界空间渲染与宿主包内手臂方法的精准 Invoker。宿主更新、其他相机/玩家模型模组、光影、资源包、GUI 布局模组可能需要额外适配。
- 高延迟/服务器纠正可能中止已经开始的预测视觉。只保证不产生额外物品操作；多人环境效果未验证。自动化移走成品也会表现为结果槽减少。
- Lightroom 的其他菜单仍使用原 GUI；照片预览子窗口打开时动画安全退出，返回时重新接入。
- 不广播附属动画给旁观者，不新增任何服务端协议。

具体源码核对与兼容层说明见 [docs/source-analysis.md](docs/source-analysis.md)，编译记录见 [docs/build-report.md](docs/build-report.md)。许可证见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
