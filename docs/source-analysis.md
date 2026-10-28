# v0.2.3 相机叠加层与胶卷交互

- Cinematic Overlay.hides 只在 Scene.active 时隐藏 HUD，独立手持相机 VisualSession 不满足该条件。ClientEvents 现为 CameraSceneScreen 和无其他窗口的退出过渡应用同样的 HUD 保留名单，不改游戏选项、不影响普通库存界面。
- CameraSceneScreen 仍继承 AbstractContainerScreen 以保留原版输入，EMI 因而自动识别它。EMI 公开 exclusion API 只排除物品位置，不能完整隐藏搜索框/按钮或阻止输入；屏幕 bounds provider 也没有明确的排除返回值。
- 阅读 EMI 1.21 分支（81f6453c0d0b6df2e503d0bc5f19f1c73fd12173）及其 EmiScreenBase、EmiScreenManager、NeoForge 事件和 ScreenMixin。选择单个可选 @Pseudo Mixin，在 EmiScreenBase.of(Screen) 入口只将 CameraSceneScreen 参数映射为 null，走 EMI 自己的空界面分支。getCurrent、widgets、render、mouse/key 路径因此使用 empty base。没有改 EMI 配置或其他 Screen。
- 下载并校验官方 Modrinth EMI 1.1.22（ouSj7NfF）/1.1.24（5sIPA1To）的 NeoForge JAR SHA-512，javap 验证实际 Mojmap 方法描述符。1.1.22 的 null 分支创建空 base，1.1.24 返回 EMPTY 单例，两者均兼容；未假设它们内部实现完全相同。仅静态检查，不启动或加载游戏。
- 胶卷的局部接口从左侧移到右侧 (0.19,-0.065,0.10)，CameraAction outward 改为 +right。所有热点/标记/右手目标读取同一 socket，安装、取出路径均保持在机身右侧。未改菜单槽位编号、附件数据或物品操作。

以下为历史版本记录。

# v0.2.2 动作、抽屉与暗房状态

- 朝向修复：VisualSession.forward=(-sin(yaw),0,cos(yaw))，模型应绕 Y 轴旋转 -yaw 才能让模型负 Z 的镜头对齐 -forward。旧 SceneDrawing 使用 +yaw，转向时模型与接口路径偏离。新 CameraAction 为每个动作固定 socket/outside/carry/rest，镜头先旋松、沿轴拔出，再转移至右手；胶卷和闪光灯分别使用各自侧向轴。
- 相机进出：移除 `0.72*weight` 缩放，始终按 0.72 渲染，使用同一 entranceOffset 将机身和左手从下方抬起。此动画只用于打开/关闭改装界面，不接入拍照举机。
- 抽屉实际代码位于 final SceneScreen 的私有方法，没有公开独立组件。CinematicDrawer 组合一个不显示为当前 Screen 的 SceneScreen 实例，通过 SceneScreenAccess 调用原 drawPanel/overPanel/slotAt；布局采用原尺寸和展开公式。CameraDrawerMixin 仅对该实例跳过 Scene.consulted 初始化和库存物品绘制，原工作站不变。图标/拖拽交给原版 AbstractContainerScreen，保留原 Exposure Menu。使用 javap 核对已下载 0.18.12 JAR 中方法及调用描述符。
- 光照判定调用 LightroomBlockEntity.hasSufficientLightLevel；数字使用 LevelUtil.getLightLevelAt，普通时采样正上方，折射时采样上方再上一格及上方的四个水平邻格，阈值取 Exposure Config.Server。每 tick 只在活动场景计算显示状态，无额外数据同步。
- 工艺名称读取 getActualPrintingMode（REGULAR/CHROMATIC），切换可用性读取 LightroomMenu.canChangeProcess（isRefracted）。未折射时只提示原因，不发送无意义切换请求。缺料说明通过客户端 LightroomChecksInvoker 读取原 process/paper/dyes 检查，成品容量使用原公开方法；最终印制与消耗仍由服务端执行。

以下保留之前版本的实现依据；相机抽屉及动作以上述 v0.2.2 为准。

# v0.2.1 显示与物品栏修复依据

## 合成台持久渲染的实际机制

Cinematic 0.18.12 的原版合成台没有 BlockEntity：`TableStore extends SavedData` 在服务端按维度/方块坐标保存物品和朝向；`TableActions.sync` 向跟踪区块的客户端发送 `TableContentsPayload`；`ClientTables` 接收内容、在区块卸载或换世界时清理；`SceneRenderer` 无条件调用 `TableRenderer.renderAll`，不依赖 `Scene.active()`。活动场景只额外影响动画和遮挡。关闭场景不会删除 TableStore；方块破坏由 `TableActions.spill` 清理、掉落并同步。

Exposure `LightroomBlockEntity` 已提供库存持久化、`setChanged -> sendBlockUpdated`、`getUpdatePacket -> ClientboundBlockEntityDataPacket.create` 和 `getUpdateTag -> saveWithoutMetadata`。因此不复制合成台的自定义存储或协议：使用 NeoForge `EntityRenderersEvent.RegisterRenderers` 注册 `LightroomRenderer`，始终读取当前 BE 物品。区块加载获得原 update tag，物品变化收到原 update packet；BE 移除后由正常渲染生命周期移除显示。

`LightroomLayout` 同时提供拾取、静置物品和动作目标坐标，位置随 BlockPos 和 FACING 旋转；台面模型实际为完整 orientable 方块，表面高为 1 格。静置物品略高于表面，渲染包围盒向上扩展 0.2 格。中心照片以 Exposure `imageRenderer` 的世界姿态矩阵渲染，保留图片尺寸比例，白边与打印指示不覆盖内容；没有屏幕角落预览。

宿主该版 `MenuRenderer.renderAll` 与 `SceneRenderer` 都会调用 AddonMenuStation.renderScene。故附属 renderScene 留空，静置物品仅 BER 绘制；临时手持物在单次 renderWorld 绘制，插入中的胶卷暂不在台面重复绘制，退出立即清除视觉动作。

## 相机物品栏原因与修改

旧适配器继承 Screen，固定绘制 20 像素网格，没有展开状态；它自行发送 PICKUP/QUICK_MOVE，因此没有原版拖拽状态机。现在继承 AbstractContainerScreen，保持 Exposure `AbstractCameraAttachmentsMenu.addPlayerSlots` 的原坐标（18 像素间距、103/121/139 与 161 行），只绘制 CameraAttachmentsScreen.TEXTURE 的库存区域。

按钮/Tab 控制库存可见性。渲染与命中使用同一投影函数；原版 findSlot 经 isHovering 覆盖映射附件目标，mouseClicked/mouseDragged/mouseReleased 均继承原版。不改 Slot.x/y，也不复制 Menu 或网络输入逻辑。库存隐藏时屏蔽对应槽位的绘制/命中；拖拽途中禁止折叠。窗口重设后清理拖拽显示状态并重算布局。相机区域与库存区域分离，附件目标过密时分开命中框，原始部位保留标记。

以下为 v0.2 基础兼容适配记录，输入路径以上述 v0.2.1 修改为准。

# v0.2 场景重做依据

依赖源码基线不变：Cinematic `c79e1b9e05347165f6766700007cef45c8ce4d57`（0.18.12），Exposure `08a8227ce7e824735970d8976c5040cd389b31fb`（1.21.1 / 1.9.19）。

用户修正了初始的保留 GUI 要求。v0.2 使用手部场景代替原窗口，不再将原窗口移到右侧。

## 对照的宿主实现

- `SmithingStation.approach/lean/rest`：工作面前的站立目标、0.70 格后退和0.62格俯视高度，以及双手台面休息位置。
- `examples/addon-template/ExampleAddonScene`：注册为 AddonMenuStation，使用 Picking.cursorOnPlane、真实 slotClick 和 ItemPlacer 渲染。
- `Scene`、`Reach`、`CameraRig`：由原生场景执行接近路径、抵达后镜头过渡、碰撞/可达性与退出。旧 VisualSession 只用于手持相机，暗房不再走它的 0.35 格镜头限制。
- `CinematicClient.onScreenOpening`：取消已注册工作站的原 MenuAccess 窗口，保留真实 menu。
- `MenuActions.provider/open` 与 `Sessions.openWindow`：标准路径要求方块提供 MenuProvider。Exposure Lightroom 继承 Block，没有提供此入口；原 useWithoutItem 直接传入方块实体，并写入 BlockPos。

## 兼容适配

`LightroomMenuProviderMixin` 只为 ADDON 类型的 Lightroom 返回一个委托 Provider。getDisplayName/createMenu 使用原方块实体；保留原交互统计、setLastPlayer 与 setChanged；writeClientSideData 写入 Exposure 原 fromBuffer 所需的 BlockPos。场景与宿主回退窗口均走同一适配，不重写冲印系统。

AddonStations 在 common setup 注册，AddonScenes 在 client setup 注册。关闭配置时 AddonAvailabilityMixin 令宿主回退原窗口。障碍/距离导致无法靠近时也使用宿主的原有回退。

`LightroomStation` 使用实际槽位 0–6（胶卷、相纸、四种染料、成品）。场景控制发送原按钮 ID 0/1/2/3/4，不以客户端条件代替服务端检查。真实进度开始、结果增加或槽位减少触发视觉动作；相纸/染料操作复用宿主光标手部跟随和 slotClick。

`CameraSceneScreen` 仅提供目标拾取和库存选择，将点击交给原 MultiPlayerGameMode.handleInventoryMouseClick。VisualSession 读取原菜单和同步包，仅操作渲染副本。没有创建替代容器、配方或自定义网络协议。

相机使用原模型、Cinematic Hands Invoker 和独立过渡；Lightroom 使用宿主自己的手臂/镜头生命周期。原 ContainerScreenAccessor 和 ScreenBackdropMixin 已删除。

编译检查不会验证游戏中的 Mixin 应用、路径可达性、实际渲染/鼠标命中及多人表现。这些仍需玩家实测。
