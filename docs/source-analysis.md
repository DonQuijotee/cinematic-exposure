# v0.2.9 负片显影与移除光照条

- Exposure 提供 RenderableImage.modifyWith(ImageEffect.NEGATIVE)，底层 ModifiedImage 是只读像素包装，保留 alpha。使用负片作为不透明底图、原图作为渐显上层；NEGATIVE_FILM 会按亮度修改 alpha，不适合本次相纸上的预览，故不用。
- 每张图片仅使用原图标识和稳定 negative variant，复用 Exposure ImageRenderer 的贴图缓存与清理，不生成随进度变化的贴图标识，不复制或改写照片存储。正片层比负片高 0.0005 格，避免共面深度闪烁。
- 当前菜单的选帧与冲印进度均来自 LightroomMenu 的独立 ContainerData，与原 LightroomScreen 相同。空闲状态根据输出槽 PhotographItem、StackedPhotographsItem 或 ChromaticSheetItem 的实际帧标识决定是否已经有对应成品；退出/重进无需临时完成缓存。移走对应输出后恢复负片，实际开始再次冲印时按新进度显影。混色仅预览当前帧，不模拟 Exposure 的多层照片合成。
- 移除 LightroomStation 前缘光照条、常驻标签、编号 11 拾取区域；保留冲印悬停中的光照/材料说明。移除 BER 中图片下方闪烁条。原四个按钮、手部动画、容器逻辑和服务端校验不变。
- 旁观者没有菜单的持续进度，按实际成品/胶卷显示静态负片或成片；不新增同步。实际透明混合和光影兼容尚未经游戏内验证。

以下为历史版本记录。

# v0.2.8 修复显影进度来源

- v0.2.7 错误地从客户端 LightroomBlockEntity.getProgressPercentage 读取进度。实际 LightroomMenu.fromBuffer 创建独立 SimpleContainerData，菜单数据包更新它而非客户端 BE 的 progress/printTime。因而图片透明度停留在零，只有不透明相纸可见。以下 v0.2.7 记录中的进度来源结论已由本节纠正。
- 对照 Exposure LightroomScreen.renderBg 的实现，改读当前匹配方块菜单 getData() 的 CONTAINER_DATA_PROGRESS_ID / CONTAINER_DATA_PRINT_TIME_ID。总时长 > 0 时按真实进度淡入；总时长归零即恢复完整图片，不再混用方块 PRINTING 状态和菜单同步数据。
- 保留原 ImageRenderer、图像尺寸/内容、世界坐标与相纸底层。无新计时器、网络包、容器写入或游戏逻辑改动。旁观和退出菜单仍显示完整预览，创造模式即时冲印不伪造渐入过程。

以下为历史版本记录。

# v0.2.7 暗房预览淡入

- 实查 Exposure LightroomBlockEntity：getProgressPercentage 返回 progress/printTime，isPrinting 读取方块 PRINTING 状态。LightroomMenu.addDataSlots 使用原 ContainerData 持续同步进度/总时长，普通旁观客户端不持续获得这些数据。
- LightroomRenderer 在对应真实 LightroomMenu 打开且该 BE 正在冲印时，仅将中央图像的 alpha 设置为原百分比的平滑曲线；纸底和边框不透明。空闲、完成、取消、退出后按原方式完整显示预览，避免进度清零造成照片消失。无本地计时猜测、无网络包、无容器写入。
- Exposure ImageRenderer 的 Color 参数传到 RenderedImageInstance 顶点色；mipmap 渲染类型明确使用 TRANSLUCENT_TRANSPARENCY，非 mipmap 使用原 RenderType.text。复用原透明渲染，不修改图像像素或全局 shaderColor。真实时长由 Exposure 决定，附属动画速度不影响淡入进度。创造模式瞬时冲印不伪造长过程。
- 本次只修改暗房图像绘制与版本/文档，不改变相机姿态或暗房手部动作。未进行游戏内验证。

以下为历史版本记录。

# v0.2.6 侧向装配重做

- 用户反馈 v0.2.5 的动画观感混乱。原版模型尺寸的纯数学检查不能证明动画自然；本次不继续增加绕行，而是将装配姿态改为机身侧对玩家：模型额外 Y 旋转 -80°、X 旋转 8°，镜头朝左、后部胶卷朝右。
- CameraAssembly.orient/unorient 与 SceneDrawing 使用一致的 Y×X 旋转，部件原本面朝向的 180° 翻转放在这两个变换之后。镜头和闪光灯的进出轴也使用同一变换，避免只转模型或手而遗留旧轴。
- 删除 FILM_CLEARANCE、下方胶卷偏移、旧多段绕行和悬停手势。FilmPath 仅保留掏取（未持物时）、后部对准、直线装卸、回位；胶卷 1.15 秒。原高亮/图标/点击槽位保留，鼠标移动不触发伸手。
- 右手处理胶卷/闪光灯，左手处理镜头/滤镜；另一只手保持支撑。根据实际 Attachment 的匹配谓词选择掏取手；运动起点、平滑缓存、口袋位置、持物显示使用同一操作手。窗口重设同时清除左右手缓存。
- 区分支撑握点与持物休息点：安装完成回握点，取出附件后保持在对应侧机身外，避免持物手又钻回机身。手和模型的进出平移在手部平滑之后叠加，减少托起过程的不同步。没有改菜单、网络或 Exposure 数据。
- 独立数学回归新增镜头左向、胶卷右侧、两手支撑位置断言；检查侧向变换、简化路径与左手镜头端点。测试不加载 Minecraft，亦不能代替实际动画/模型渲染验证。

以下为历史版本记录。

# v0.2.5 闪光灯与胶卷避让

- 实查 Exposure CameraModel.modifyCameraModel：安装闪光灯后组合 CAMERA_FLASH 原模型，没有独立的位移。camera_parts/flash.json 几何范围为 [1,1,6]..[5,5,8]；金色版本只继承并换纹理。附属机身中心 [8,3,8]、比例 .72，转换为右/上/前局部坐标后闪光灯范围为 x=.135..315、y=-.09...09、z=-.09..0。旧右手休息位 (.28,-.08,-.09) 与胶卷点 (.19,-.065,.10) 均贴近该范围，手掌/袖子及直线移动会穿入闪光灯。
- 新 CameraAssembly 统一机身 26° 抬头倾斜、左手托底握点、右手下方休息位和附件位置。SceneDrawing 与 VisualSession.local 使用相同旋转，不再单独移动手而遗漏模型。镜头安装方向沿倾斜后的镜头轴；闪光灯仍保留在实际模型上。
- FilmPath 对安装、拆卸、悬停与松手回位提供明确右下方绕行路径，物品在手中向上偏移 .10，让手掌托住胶卷下部。两种装卸不走穿越闪光灯的直线捷径。即使没装闪光灯也使用相同路径，避免安装状态改变时动作跳动。只有独立相机的胶卷动作延长到 1.45 秒，暗房时长不变。
- fitWork 同时约束左手和整个胶卷路径，避免 HandReach 限距把设计好的路径投影回闪光灯区域。已有视角恢复、菜单与服务端物品操作均保留。光标槽位仍是原 Exposure 槽位。
- tools/CheckCameraAssembly.java 只加载成品 JAR 中不依赖 Minecraft 的 CameraAssembly 类，检查初末点、各阶段连续性、旋转可逆、路径可达距离及原模型闪光灯间距；采样包含手掌/袖子 .11、胶卷道具 .156 的保守半径，9 组初始构图、不同初始手位、装卸/手持状态、30/60/144 FPS 和 .25/1/3 速度下的指数平滑。它不加载游戏类，不启动客户端/服务器，不进行画面验证。测试包围盒不代表任意资源包模型。

以下为历史版本记录。

# v0.2.4 手部范围、入口分流与掏取

- 重新核对 Cinematic 0.18.12 的 Hands.follow：普通目标才会应用 maxReach，handsOverride 提前返回而不限制距离；默认 maxReach 为无限。Lightroom 以前在 hover 使用宿主左右选手，动画则固定右手，且道具使用未平滑的目标坐标。本版覆盖双手目标并统一限距，胶卷固定左手，道具通过 Hands.position 的只读 Invoker 跟随实际弹簧平滑后的手。宿主仍渲染皮肤、袖子、进出场过渡；没有重写手臂模型。
- Lightroom 后排四个控制标签保留，手势移至照片边缘而非远端标签；点击仍调用原按钮/槽位路径。非 ACTIVE 阶段只同步视觉快照，不把初始菜单同步误当成装卸动作。持久 BER 和照片生成未改。
- SceneDrawing 旧旋转矩阵把手腕和整条手臂（连同肩部）一起绕接口转动；移除该矩阵，旋转仅用于附件。相机路径结束后加入短暂回位阶段，防止立刻再次伸向鼠标下的接口。左右手均限距，小窗口构图同时约束机身与左手握点。
- Exposure CameraItem.overrideOtherStackedOnMe 调用 openCameraAttachments(..., true)，手持相机 use 的 secondary-use 路径传 false；服务器原菜单附加数据同步 openedFromGUI。ClientEvents 直接读取 isOpenedFromGui，仅 false 使用沉浸适配，不猜测收到网络包时 Shift 是否仍按下，不新增打开请求。
- CameraSceneScreen 移除黄色点，并在附件框内转换绘制坐标后调用原 AbstractContainerScreen.renderSlot；原槽位不移动，保留数字、空槽背景与拖拽预览。普通 Exposure GUI 不再应用附属的 HUD/EMI 排除。
- HandPickup 观察 carried 物品身份，先空手下探，底部换物后抬回；数量变化不重复触发。已发生装卸时直接接管动作，不再额外掏一遍。Shift 安装没有 carried 时走同样的下探路径。全程不发送操作或写菜单。手臂/物品的实际视觉效果仍待游戏内验证。
- 静态打包检查增加官方 Cinematic JAR 中 Hands.position(boolean) 与 drawArm 的签名检查；不启动游戏或执行其类。

以下为历史版本记录。

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
