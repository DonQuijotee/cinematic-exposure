# 源码分析与设计依据

分析先核对 Exposure，再核对 Cinematic；没有依据名称猜测容器类。第三方源码仅放在工作区参考目录，不并入本工程。

## Exposure 1.21.1 / 1.9.19

基线提交 `08a8227ce7e824735970d8976c5040cd389b31fb`，分支 [1.21.1](https://github.com/mortuusars/Exposure/tree/1.21.1)。官方 Modrinth NeoForge 发布版本 ID `iBvaR6AK`。上游 `gradle.properties` 使用 Java 21 / NeoForge 21.1.200；本附属采用 Cinematic 共同基线 21.1.255。

包名前缀为 `io.github.mortuusars.exposure`：

| 实际源码 | 结论与适配 |
| --- | --- |
| `world.inventory.LightroomMenu` | 0 胶卷，1 相纸，2–5 染料，6 成品；7 起为玩家物品栏。使用这些实际常量，不创建替代容器。 |
| `client.gui.screen.LightroomScreen` | 原按钮、照片预览、帧选择、打印模式保留。预览进入 `LightroomFrameInspectScreen`，此时附属退出。 |
| `world.block.entity.LightroomBlockEntity` | 服务端维护冲印、照片生成、材料与经验。普通冲印通过 ContainerData 同步；附属只读 `isPrinting()`。 |
| `world.block.LightroomBlock` | `FACING` 指示工作面，原 `useWithoutItem` 打开 MenuProvider；附属不拦截 use。 |
| `world.inventory.AbstractCameraAttachmentsMenu` | `CameraItem.getAttachments()` 决定前置附件槽；原 `onContainerChanged` 是附件写回唯一执行方。 |
| `world.inventory.CameraInHandAttachmentsMenu` | 手持/背包内相机的实际改装容器；相机槽由原模组禁止移走。仅接入此类型。 |
| `world.inventory.CameraOnStandAttachmentsMenu` | 相机支架不在任务范围内，不适配。 |
| `client.gui.screen.camera.CameraAttachmentsScreen` | 原 GUI 的附件槽、提示、皮肤按钮和帮助列表保持可用。 |
| `world.item.camera.Attachment` | 实际定义 `FILM`、`FLASH`、`LENS`、`FILTER`，没有虚构附件。 |
| `world.item.StackedPhotographsItem` | 照片堆通过 `getPhotographs(stack).size()` 表示数量；取出动画同时处理此数据。 |
| `client.render.model.CameraModel`、`mixin.client.ItemRendererMixin` | 原物品渲染器按组件组合机身、闪光灯和镜头。附属通过原 ItemRenderer 渲染，复用原资源。 |

冲印按钮：0 普通、1 创造、2 上一帧、3 下一帧、4 切换流程。附属没有发送这些按钮请求，也没有复制 `clickMenuButton` 或任何冲印代码。普通成功动作跟随同步进度的开始；瞬时完成用成品增加确认。附件操作观察已由原点击和网络处理完成的槽位状态。

## Cinematic Interactions 0.18.12

编译/运行基线为官方 [v0.18.12-neoforge-1.21.1](https://github.com/dcvke/CinematicInteractions/releases/tag/v0.18.12-neoforge-1.21.1)，提交 `c79e1b9e05347165f6766700007cef45c8ce4d57`。实际 Mod ID 是 `cinematic`。此版本使用 NeoForge 21.1.255。

已读取 [addon-api.md](https://github.com/dcvke/CinematicInteractions/blob/v0.18.12-neoforge-1.21.1/docs/addon-api.md) 及以下真实实现：

- `dev.dyslexic.cinematic.api.AddonStations`：注册方块 ID、真实 Menu 类型和可用工作面，仅支持方块。
- `api.client.AddonScenes`：注册 `ClientStation` 场景工厂。
- `api.client.AddonMenuStation`、`client.MenuStation`：标准场景会接管菜单显示与交互，包含真实 slotClick、退出和网络生命周期。
- `client.ClientStation`：镜头、双手、工作面及渲染契约。
- `client.Scene`、`client.CameraRig`：全局场景会话、INTRO/ACTIVE/OUTRO、相机覆盖与视角恢复。
- `client.CinematicClient`：`ScreenEvent.Opening` 在标准场景中取消原 MenuAccess 窗口。将 Lightroom 直接注册为站点会替换用户要求保留的 GUI。
- `client.SceneRenderer`、`client.Hands`：世界渲染在 AFTER_ENTITIES 阶段；`Hands.drawArm` 绘制真实玩家皮肤与袖层，方法为包内静态方法。

因此没有注册 AddonStations/AddonScenes：标准公开 API 无法同时满足手持物品菜单和保留 Exposure 全 GUI/照片预览的要求。采用任务允许的独立客户端适配器，复用公开 `CameraRig.Pose`、只读 `Scene.active()` 与精准 `Hands.drawArm` Invoker，不写入宿主 Scene 字段，不伪造方块或网络会话。

## 精准 Mixin 边界

1. `CameraMixin`：vanilla Camera.setup 末尾应用纯视觉姿态；使用 NeoForge Camera 的实际三参数 setRotation。
2. `HandsInvoker`：仅调用已安装 Cinematic 的包内绘制方法，不复制宿主手臂资源。
3. `ContainerScreenAccessor`：读取窗口宽度、调整原 leftPos；所有原按钮在 Init.Post 统一平移。
4. `InventoryClickMixin`：vanilla handleInventoryMouseClick 返回后只读观察，不取消/修改点击。
5. `MenuSyncMixin`：vanilla 内容/单槽包处理后更新视觉基线，整包纠正清理旧动作。
6. `ScreenBackdropMixin`：仅当目标 Exposure 主界面的动画会话存在时去掉全屏暗色渐变，原纹理、槽位、按钮、提示继续绘制。预览子界面不受影响。

上述 Mixin 全部列在配置的 `client` 数组，服务端不加载客户端类。

## 动画与真实逻辑分离

VisualSession 保存副本，Motion 没有任何游戏逻辑 Runnable 或延时点击。速度只推进视觉秒钟，不影响冲印时间。镜头移动不更改玩家位置/朝向/视角选项，退出目标使用本帧原始相机姿态。渲染副本的组件掩码不会回写原相机。

所有动画都可因新状态或窗口生命周期变化而中断。服务器回滚优先于播放完整动作；动画不阻塞快速操作，也不试图重现服务端已经否决的结果。
