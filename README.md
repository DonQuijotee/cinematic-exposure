# Cinematic Exposure

Minecraft 1.21.1 / NeoForge 独立附属。**v0.2.1-alpha 修复暗房物品持久显示、台面照片预览和相机物品栏。尚未进行游戏内测试。**

依赖 Java 21、NeoForge 21.1.255、Cinematic Interactions 0.18.12、Exposure 1.9.19。依赖不打包。

## 暗房

Lightroom 使用 Cinematic 正式 `AddonStations` / `AddonScenes` / `AddonMenuStation` API，参照宿主 SmithingStation 和官方附属示例：由宿主处理走近工作台、抵达后俯身、相机与双手过渡、库存抽屉、退出及服务器会话。镜头目标为台面前方 0.70 格、上方 0.62 格，俯角 40°；不再受旧独立适配器的 0.35 格位移限制。

从宿主库存抽屉选择物品，再点击台面标注的胶卷、相纸、染料位置放入；点击成品拿取照片。台面后排提供冲印、上一帧、下一帧、流程切换。Shift 点击冲印使用 Exposure 原有创造冲印请求，权限和材料仍由 Exposure 校验。照片预览使用 Exposure 原有图像系统，平放在方块中央，保留宽高比、图像内容与贴图效果，随方块朝向旋转。可点击中央照片区域发起冲印，也可点击台面后排的冲印控制。预览存在时，双手在照片两侧工作，边缘指示灯跟随原冲印状态，不覆盖图片。

静置物品由独立 `LightroomRenderer` 方块实体渲染器绘制，读取 Exposure 已同步的实际库存。关闭场景、重进区块和重新接近时仍可显示；物品取走或方块移除后不保留副本。不生成掉落物实体、不另存库存、不新增网络包。中央是胶卷选中帧的预览；没有胶卷时可展示成品堆顶部照片，成品槽仍位于右侧。关闭暗房模块也会关闭这些附属渲染。

胶卷装卸、实际冲印开始和照片取出跟随真实容器状态，冲印时双手贴近中央照片两侧，右手沿纸边操作、左手扶纸。新状态可中断旧动作。照片堆数量使用 Exposure 的组件数据读取。

**多人服务器需要同时更新本附属和依赖。** Lightroom 的场景注册在两端执行。原方块没有标准 getMenuProvider 开放入口，且客户端菜单构造需要方块坐标，因此增加一个仅针对 Lightroom 的 MenuProvider 适配器：委托原方块实体 createMenu，并通过 NeoForge 标准菜单附加数据写入原坐标。没有替换照片生成、配方、消耗或容器协议。

蹲下使用、关闭暗房场景、无法抵达工作面时，按 Cinematic 原有规则回退到 Exposure 原窗口。这是宿主的安全回退，不是正常场景显示方式。

## 相机改装

打开手持相机改装后显示透明场景，不显示 Exposure 原改装面板。左手托住原相机模型；按 Tab 或点击右下方按钮展开库存，从库存区拿起物品，点击机身上的胶卷、闪光灯、镜头、滤镜标记操作。右手跟随目标、持有附件，镜头安装包含对准与旋转。Shift 点击快速移动，Esc 或背包键退出。

库存使用 Exposure 原有 18 像素槽位间距、快捷栏间隔与库存贴图，未修改 Menu 槽位坐标。`AbstractContainerScreen` 负责普通点击、拖拽分配、Shift 快速转移、双击归集、数字键换槽及服务端交互规则。相机保留在库存左侧区域，库存展开/收起不移动附件目标；缩放或窗口变化后重算布局。新开界面默认收起，调整窗口尺寸保留展开状态。原版容器的丢弃键仍有效，点击场景空白处不会意外丢弃光标物品。第三人称打开时保留 Exposure 原窗口。

这些输入路径已接入原版实现，但尚未做游戏内鼠标/拖拽测试。相机支架不在范围内。

## 配置

`config/cinematic_exposure-client.toml`，也可用 NeoForge 配置界面：

```toml
[animations]
camera = true
lightroom = true
speed = 1.0
```

速度范围 0.25–3.0，控制附件/冲印手部动作和相机改装进出速度。暗房的走近与镜头过渡遵循 Cinematic 宿主设置。旧 `dockGui` 配置已移除。

## 构建与发布

完整 JDK 21：

```sh
./gradlew build --stacktrace
python3 tools/verify-artifact.py
```

代理云环境可用 `python3 tools/cloud-build.py build --stacktrace`，保留 TLS 校验。产物为 `build/libs/cinematic-exposure-1.21.1-neoforge-0.2.1-alpha.jar`。

GitHub Actions 支持 push、PR 和手动构建。手动启用 release 后，仅在构建与打包检查成功时发布 v0.2.1-alpha Pre-release，并上传同次构建的 JAR。不会覆盖旧版 Release。

没有启动 Minecraft 客户端或服务器。需要玩家验证台面拾取范围、镜头构图、手部/物品穿模、预览与库存抽屉布局、光影及多人同步。默认皮肤/模型之外的资源包可能需要进一步调位。

源码分析见 [docs/source-analysis.md](docs/source-analysis.md)，许可证见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
