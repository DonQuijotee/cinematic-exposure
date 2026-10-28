# Cinematic Exposure

Minecraft 1.21.1 / NeoForge 独立附属。**v0.2.0-alpha 改为场景操作，替换 v0.1 的“原 GUI 加双手背景”方案。尚未进行游戏内测试。**

依赖 Java 21、NeoForge 21.1.255、Cinematic Interactions 0.18.12、Exposure 1.9.19。依赖不打包。

## 暗房

Lightroom 使用 Cinematic 正式 `AddonStations` / `AddonScenes` / `AddonMenuStation` API，参照宿主 SmithingStation 和官方附属示例：由宿主处理走近工作台、抵达后俯身、相机与双手过渡、库存抽屉、退出及服务器会话。镜头目标为台面前方 0.70 格、上方 0.62 格，俯角 40°；不再受旧独立适配器的 0.35 格位移限制。

从宿主库存抽屉选择物品，再点击台面标注的胶卷、相纸、染料位置放入；点击成品拿取照片。台面后排提供冲印、上一帧、下一帧、流程切换。Shift 点击冲印使用 Exposure 原有创造冲印请求，权限和材料仍由 Exposure 校验。照片预览由 Exposure 原有图像系统渲染在画面右下方。

胶卷装卸、实际冲印开始和照片取出跟随真实容器状态，冲印时右手按压、左手扶纸。新状态可中断旧动作。照片堆数量使用 Exposure 的组件数据读取。

**多人服务器需要同时更新本附属和依赖。** Lightroom 的场景注册在两端执行。原方块没有标准 getMenuProvider 开放入口，且客户端菜单构造需要方块坐标，因此增加一个仅针对 Lightroom 的 MenuProvider 适配器：委托原方块实体 createMenu，并通过 NeoForge 标准菜单附加数据写入原坐标。没有替换照片生成、配方、消耗或容器协议。

蹲下使用、关闭暗房场景、无法抵达工作面时，按 Cinematic 原有规则回退到 Exposure 原窗口。这是宿主的安全回退，不是正常场景显示方式。

## 相机改装

打开手持相机改装后显示透明场景，不显示 Exposure 原改装面板。左手托住原相机模型；从下方库存区拿起物品，点击机身上的胶卷、闪光灯、镜头、滤镜标记操作。右手跟随目标、持有附件，镜头安装包含对准与旋转。Shift 点击快速移动，Esc 或背包键退出。

库存区与场景点击均发送原版容器点击，使用 Exposure 原 Menu 的槽位规则。该界面不实现原版 GUI 的拖拽分配、双击收集及数字键换槽；使用普通左右键和 Shift 点击。相机支架不在范围内。

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

代理云环境可用 `python3 tools/cloud-build.py build --stacktrace`，保留 TLS 校验。产物为 `build/libs/cinematic-exposure-1.21.1-neoforge-0.2.0-alpha.jar`。

GitHub Actions 支持 push、PR 和手动构建。手动启用 release 后，仅在构建与打包检查成功时发布 v0.2.0-alpha Pre-release，并上传同次构建的 JAR。不会覆盖 v0.1.0-alpha。

没有启动 Minecraft 客户端或服务器。需要玩家验证台面拾取范围、镜头构图、手部/物品穿模、预览与库存抽屉布局、光影及多人同步。默认皮肤/模型之外的资源包可能需要进一步调位。

源码分析见 [docs/source-analysis.md](docs/source-analysis.md)，许可证见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
