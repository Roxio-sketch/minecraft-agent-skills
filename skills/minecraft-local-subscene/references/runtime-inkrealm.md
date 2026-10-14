# 已实测的 Forge 轻量异空间起点

## 能力与选择

`assets/forge-inkrealm-example` 保存 Ink Realm **0.2.0** 的原创完整源码：Minecraft **1.20.1**、Forge **47.4.10**、Java **17**、ForgeGradle **6.0.54**、Gradle **8.8**。它是实现起点；源码研究/API 探针仍负责其他架构的论证。

已实现：世界空间静止椭圆门、独立 FBO 程序化山水、外部视差和前景遮挡、穿入后全视野显示、内部转头/移动、原路退出、资源重载、单次异常恢复和关闭。

**视觉进入不改变玩家实际维度。** 山峰、水和船是独立程序场景，没有相应的 Minecraft 碰撞、实体或交互。主世界碰撞和游戏逻辑仍存在。需求包括“踩在子场景地面上、攻击那里生物”时，需要另行设计玩法世界；不能只给这个 shader 换画面。

示例未覆盖球体、有限体积出口后景、移动入口、高速 swept crossing、递归、真正第二 ClientLevel，也没有对手/UI、Fabulous、所有相机模式进行完整认证。Oculus/Embeddium/Rubidium 存在时禁用；不要将禁用策略说成兼容。

## 生成与构建

从 Skill 根目录运行：

```powershell
python scripts/scaffold_inkrealm.py 'E:/my-projects/new-subscene'
# JAVA_HOME 指向 Java 17；脚本不会修改用户的 Java 安装。
Set-Location 'E:/my-projects/new-subscene'
.\gradlew.bat --offline --console plain build
```

目标必须为空且在 Skill 外；脚本先检查模板哈希再复制，不覆盖已有工程，不部署或控制游戏。首次缺少 Gradle/Forge 依赖时按正常流程联网构建，不能把离线缓存缺失当作渲染 API 失败。Windows 短 socket 临时目录规避见 [探针说明](probes.md)。

产物为 `build/libs/inkrealm-lab-0.2.0.jar`。模板保留 `inkrealm` mod ID；同实例只加载一份，多个设计要么整合为同一模组的场景，要么完整重命名 Java ID、资源命名空间、shader 引用、依赖段与控制目录。构建完成再在目标实例退出后部署、启动，勿一边写 JAR 一边启动。

本包曾在独立空目录从模板构建并与原实测 JAR 比较全部非目录 ZIP entry 的未压缩字节，记录见 [模板复建](evidence/inkrealm/scaffold-build.json)。该检查不要求 ZIP 时间戳相同，也不代表在新硬件重新实测。

## 替换位置

| 文件 / 方法 | 职责 | 修改时的验收 |
|---|---|---|
| `InkRealm.java` | MOD 入口和 client 隔离 | dedicated server 类加载需要另测 |
| `InkClient.open/render` | 固定门基、camera→门局部、inside 状态与阶段 | 进入/返回、换世界；不能把 near return 放到穿越之前 |
| `InkClient.renderScene` | owned target、外部 off-axis / 内部 viewport 投影 | FOV/aspect、走动、转头、近入口和 resize |
| `landscape.fsh` | 山水、雾、水、船与可变深度 | 更换任意原创场景；保留射线/深度接口或同步修改 Java uniform |
| `aperture.fsh/.vsh` | 外部形状与父入口深度；内部全屏合成 | 外形变化同步交互 mask 与 probe，边框不能替代 aperture |
| `State`、`release`、shader/reload 回调 | 状态还原与 GPU 生命周期 | 改过的状态分别验证；不把当前 scope 当全状态适配 |

不要以“统一模板”为由固定所有未来艺术设计。复用渲染接入、相机与恢复机制，依据需求替换 scene source、区域和边界。若只做观看窗口，可关闭进入能力，但须明确交付合同。球体需读 [射线区间](geometry-depth-camera.md)，真实第二世界需读 [生命周期](secondary-world-lifecycle.md)。

## 控制与取证接口

客户端命令：`/inkrealm activate` 在眼前约九格生成门；`/inkrealm off` 关闭；`/inkrealm status` 查看 draw/generation/stateErrors。进入后可原路穿回。

实验接口在当前 gameDir 下 `inkrealm-lab/request.json`。UTF-8 写入临时文件后原子替换为 request，串行发请求，使用唯一 `nonce` 并等待 `status.json` 回显。确认实际 gameDir 与存档，避免控制另一个实例。此接口只处理本例功能，不接受任意命令。

```json
{"action":"capture","nonce":"unique-id","name":"case-inside"}
```

| action | 参数 | 完成判据 |
|---|---|---|
| `open` / `off` | 无 | enabled/inside 符合请求；off 后 disabled |
| `time` | `value`: 0 等固定秒数；负数为实时 | nonce 回显；捕获记录中的 time 符合预期 |
| `resolution` | `value`: 128…1536，子目标宽度 | 下一次 capture 的 sceneWidth；高度随模式变化 |
| `capture` | `name`: `[a-z0-9_-]{1,60}`，每次新名字 | **nonce 只表示已排队**；等待 captures 下同名 JSON 和三张 PNG 全部产生 |
| `reload` | 无 | nonce 后继续等待 shaderGeneration 增加与新一帧有效捕获 |
| `inject_error` / `resume` | 无 | failed=true/stateErrors=0；resume 后新 draw 和捕获成功 |

三张 PNG 为同一次渲染的 `-before`（合成前）、`-world`（合成后）、`-scene`（独立 FBO）。world 是该阶段主 target，不是手/HUD 全部结束后的最终屏幕。另取最终屏幕时关联 frame/camera；Helper 的延迟/缓存截图不能与先前场景纹理按同一帧比较。

该样例记录 eye/time/代次等，但缺完整朝向/FOV/projection。新实现增加 frame ID、完整 view/projection、viewport、区域变换、artifact hash 和 seed；这些是后续取证改进，不能声称历史文件已包含。移动控制、原玩家状态保存/恢复由测试工具负责，本包脚本不擅自传送玩家。

## 实测证据与边界

2026-09-29，ASTRA-TEST / ASTRA_VFX_TEST，RTX 4060 Ti，1280×720。原实测/部署 JAR SHA-256：

```text
bd6f5383323d0d94a11219f0d455e57c639def3e8f21e5343d5e03bb837fe0c7
```

常规 session `20260929-100841` 为九个捕获；穿越 session `20260929-100726` 为七个捕获。清单、原始 JSON、全部 scene PNG 与五组关键 before/world 成对图位于 `references/evidence/inkrealm`。0.1.0 的旧证据未混入该基准。

- 16 案例均记录 FBO complete、GL error=0、所检查状态无差异；所有外部窗口记录 outsideChangedPixels=0、foregroundChangedPixels=0。
- 前景案例有 12,795 个被主几何遮挡的窗口内部像素，未被改写；121,864 个像素显示新内容。inside 合成改变全部 921,600 像素。
- 独立离线核对五组成对图，其变化数与 GPU 记录一致。红帆左右归一化位移 **0.130527**，静态几何预期约 **0.130769**；这不是仅凭“看起来有视差”判断。
- 固定时间窗口 reload 前后 scene PNG 一致；进入后转头/移动有图像响应；inside reload 保持 interior，**不断言其像素完全相同**，因完整相机未被锁定/记录。
- return 记录一次进入和一次退出，off 清理状态；注入一次渲染异常后被检查状态恢复，resume 又可绘制。

独立核对脚本 `scripts/verify_inkrealm_evidence.py` 需要 Pillow/numpy；[结果](evidence/inkrealm/verification.json) 说明哪些结论是 PNG 重新计算、哪些依赖 Java probe。它只检查保存证据，不启动游戏。更换场景后，红帆阈值与理论位移不再通用，应改用该场景可辨识的固定几何。

当前检查集合限于 viewport、read/draw FBO、depth enable/write/func、blend/cull/stencil enable、active texture 和 current program。代码还恢复部分其他状态，但没有全部独立认证。退出、死亡等路径存在代码；本轮没有对每条生命周期路径做故障注入全覆盖。各项仍按 [验收层级](validation.md) 判定。

关键同帧画面：[窗口及前景](evidence/inkrealm/runtime/20260929-100841-foreground-world.png)、[内部全景](evidence/inkrealm/crossing/20260929-100726-inside-world.png)。
