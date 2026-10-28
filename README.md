# Cinematic Exposure

Minecraft 1.21.1 / NeoForge 独立附属。**v0.2.2-alpha 打磨相机取出与附件动作、统一 Cinematic 物品栏，并增加暗房工艺和光照说明。尚未进行游戏内测试。**

依赖 Java 21、NeoForge 21.1.255、Cinematic Interactions 0.18.12、Exposure 1.9.19。依赖不打包。

## 暗房

Lightroom 使用 Cinematic 正式 `AddonStations` / `AddonScenes` / `AddonMenuStation` API，参照宿主 SmithingStation 和官方附属示例：由宿主处理走近工作台、抵达后俯身、相机与双手过渡、库存抽屉、退出及服务器会话。镜头目标为台面前方 0.70 格、上方 0.62 格，俯角 40°；不再受旧独立适配器的 0.35 格位移限制。

从宿主库存抽屉选择物品，再点击台面标注的胶卷、相纸、染料位置放入；点击成品拿取照片。台面后排提供冲印、上一帧、下一帧、工艺切换。当前工艺显示“常规／混色”；只有 Exposure 判定为折射状态时可切换，否则显示禁用原因。Shift 点击冲印使用 Exposure 原有创造冲印请求，权限和材料仍由 Exposure 校验。照片预览使用 Exposure 原有图像系统，平放在方块中央，保留宽高比、图像内容与贴图效果，随方块朝向旋转。可点击中央照片区域发起冲印，也可点击台面后排的冲印控制。预览存在时，双手在照片两侧工作，边缘指示灯跟随原冲印状态，不覆盖图片。

静置物品由独立 `LightroomRenderer` 方块实体渲染器绘制，读取 Exposure 已同步的实际库存。关闭场景、重进区块和重新接近时仍可显示；物品取走或方块移除后不保留副本。不生成掉落物实体、不另存库存、不新增网络包。中央是胶卷选中帧的预览；没有胶卷时可展示成品堆顶部照片，成品槽仍位于右侧。关闭暗房模块也会关闭这些附属渲染。

胶卷装卸、实际冲印开始和照片取出跟随真实容器状态，冲印时双手贴近中央照片两侧，右手沿纸边操作、左手扶纸。新状态可中断旧动作。照片堆数量使用 Exposure 的组件数据读取。

**多人服务器需要同时更新本附属和依赖。** Lightroom 的场景注册在两端执行。原方块没有标准 getMenuProvider 开放入口，且客户端菜单构造需要方块坐标，因此增加一个仅针对 Lightroom 的 MenuProvider 适配器：委托原方块实体 createMenu，并通过 NeoForge 标准菜单附加数据写入原坐标。没有替换照片生成、配方、消耗或容器协议。

蹲下使用、关闭暗房场景、无法抵达工作面时，按 Cinematic 原有规则回退到 Exposure 原窗口。这是宿主的安全回退，不是正常场景显示方式。

台面前缘显示绿色“光照充足”或黄色“光照不足”。悬停可查看检测亮度与 Exposure 配置要求，折射时使用原模组规定的五个采样位置。光照达标不代表材料齐全：悬停冲印目标会显示缺胶卷、相纸不适用、缺染料或成品槽阻塞等原因。状态按客户端当前已同步数据提示，冲印仍由服务端最终验证。说明文字显示在侧边，避免覆盖中央照片。

## 相机改装

打开手持相机改装后，相机保持固定大小，由双手从画面下方托起；关闭时放回下方。左手稳定托住机身，右手操作附件。相机模型朝向与手部路径统一到同一坐标系；每次附件动作开始时固定路径，镜头/滤镜先旋松再沿镜头轴拔出，胶卷从侧面抽出，闪光灯沿侧面接口取下。安装为对准、沿接口送入、旋紧、松手。窗口/GUI 缩放变化会安全取消旧视觉轨迹，不重复物品操作。

物品栏使用 **Cinematic 原 SceneScreen 抽屉面板**：顶部快捷栏常显，鼠标移入后展开背包，移开后收起；Tab 可以固定展开，再按取消固定。相机位于抽屉下方，展开不挪动附件目标。通过局部 Mixin 调用宿主面板与命中逻辑，原有暗房及其他工作站不受此私有抽屉实例影响。未复制依赖 JAR 或纹理。

实际物品图标与鼠标拖拽仍由 `AbstractContainerScreen` 处理，槽位和 Menu 均为 Exposure 原对象，不修改 Slot.x/y；适配器只转换屏幕坐标。支持普通点击、拖拽分配、Shift 快速转移、双击归集与数字键换槽；点击场景空白不会意外丢弃光标物品。第三人称打开时保留 Exposure 原窗口。

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

代理云环境可用 `python3 tools/cloud-build.py build --stacktrace`，保留 TLS 校验。产物为 `build/libs/cinematic-exposure-1.21.1-neoforge-0.2.2-alpha.jar`。

GitHub Actions 支持 push、PR 和手动构建。手动启用 release 后，仅在构建与打包检查成功时发布 v0.2.2-alpha Pre-release，并上传同次构建的 JAR。不会覆盖旧版 Release。

没有启动 Minecraft 客户端或服务器。需要玩家验证台面拾取范围、镜头构图、手部/物品穿模、预览与库存抽屉布局、光影及多人同步。默认皮肤/模型之外的资源包可能需要进一步调位。

源码分析见 [docs/source-analysis.md](docs/source-analysis.md)，许可证见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
