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
