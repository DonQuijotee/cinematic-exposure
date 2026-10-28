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
