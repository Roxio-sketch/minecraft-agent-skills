# 世界体积环境 0.2.0

日期 2026-10-06，配方 `world-volume/2`，62字段/8起点。最终共享core SHA256 `55fb1baf79f11d7d87211cc2a2786b53a98b0ff8854023c51c3ce0e09fcca275`。所有结论只认证绑定源字节、设备与用例，未借用参考包运行或旧天空工程。

| 层级 | 实际通过范围 | 随skill的报告 |
|---|---|---|
| Python配方 | 29项，八预设、严格非法输入、完整JSON/ZIP、显式/1升级保持旧50值且新12默认 | [protocol.json](validation/protocol.json) |
| 实际浮点GPU | 63项：原35合同、新分布19合同、8冻结旧core比较、末次GL检查 | [gpu.json](validation/gpu.json)、[新分布](validation/end-distributions-gpu.json) |
| 浏览器 | 27项，八预设固定时间逐字节重绘、真实JSON/PNG/ZIP下载/文件导回、390px布局 | [workbench.json](validation/workbench.json) |
| 独立0.2试作 | 两个新增起点各30项：自动载入、固定时间重放与真实PNG/JSON/ZIP导出；25个活动uniform准确 | [forward-v2.json](validation/forward-v2.json) |
| 独立Forge | 21隔离场景、24类真实Java严格拒绝，19组/62字段GPU映射与显式迁移核对 | [acceptance](validation/forge-acceptance.json)、[runtime](validation/forge-runtime.json)、[build](validation/forge-build.json) |
| Bliss密度源码 | 指定ZIP全CRC、6文件/16锚点、噪声纹理指纹；只保存元数据和说明 | [审计](validation/bliss-density-study.json) |
| AstraLex紫雾源码 | V93.0 ZIP全CRC、26文件逐字节匹配和真实范围锚点 | [审计](validation/astralex-study.json) |

本文件作为skill reference时上述链接相对references。制作工程原始报告在evidence；实际游戏截图见evidence/forge/runtime。

新密度GPU检查包括：纵向拉伸、按高度/秒数扭转与冻结、中心椭球和三维环槽分别减密度、下层雾在疏空后仍存在并随高度衰减、近清理只改变视线积分而不改raw density/光路透射、紫雾源亮度动态不改密度或透射。原模式在八个固定输入下与冻结0.1 core浮点逐项完全相同；此结果不外推全部视角/GPU。

Forge目标 Minecraft1.20.1 / Forge47.4.22 / Java17.0.20.1 / FG6.0.54 / Gradle8.8，RTX4060Ti / OpenGL4.6。每场景GLerror0、原深度逐像素差0、完整已触及GL状态恢复。实际场景证明前景墙截断、云/自发光/表面光独立、椭球/环槽/下层雾、资源重载和目标尺寸变化。单ray浮点探针还核对紫雾Time/Glow改变RGB时T相同、近清理改变T而raw密度相同。游戏场景验证隔离合同，没有把全部八艺术预设的游戏观感标为通过。

工作台用ANGLE D3D11和解析浮岛/盒子命中深度。蓝灰末地云最初高消光遮住结构，经过实际四配方对照选用较低消光；只调整艺术参数，core与Forge第一默认配方未变。原包截图用于识别构图，未证明截图来自此处索引的具体快照。原创模型学习生成机制，不是原包代码/纹理或全画面逐像素复制。

0.2独立试作分别从skill生成蓝灰扭带与紫光浮岛。完整62字段配方自动载入；同相机、时间2秒的浏览器内重放逐像素差0，实际PNG解码与渲染帧一致，导出ZIP含精确配方及共享core/wrapper。最初测试误认为shader描述中的fallback默认uniform必须等于作者配方，已保留 [错误测试假设](validation/forward-v2-fixture-assumption.json)；配方实际由运行时上传，后续分别核对默认uniform和完整作者JSON。试作报告保留修正前pack描述仍为/1的观察；最终导出元数据已修为/2，另见 [导出复核](validation/metadata-export.json)。这些结论仅覆盖记录的浏览器和解析场景。

0.1独立试作的原跨CDP严格hash失败保留在 [forward.json](validation/forward.json)，其strictByteExact仍为false；随后浏览器内恢复同相机/时间的22uniform比较为0差、0像素差。JSON不保存相机/时间，文件导入会重置相机。旧源码/JAR/失败证据已单独保存，不用来认证0.2新功能。

## 复现

复制资产到独立作品、启动 `workbench/serve.py`，另一个终端运行：

```text
python scripts/check_recipe.py
node scripts/verify_gpu.cjs msedge
node scripts/verify_workbench.cjs msedge
```

Node检查器需要Playwright和已安装浏览器；普通工作台与Python工具只需标准库。使用宿主依赖路径，不把某台机器路径写入作品。自定义端口用 `WORLD_VOLUME_URL`。八项旧core比较使用随附 `scripts/fixtures` 冻结原创内核与原始hash元数据，不依赖本机旧ZIP路径；新建便携作品的 [GPU复核](validation/portable-gpu.json) 同样通过63项。Forge复现见 [接入](forge-1.20.1.md)，测试仅使用新建独立flat存档。

## 限制

单有限区域、每周期单事件，没有服务器时钟同步。光线仅受介质遮挡，无地形光源阴影/方块光传播；单深度法线不等于材质G-buffer。当前帧全分辨率积分没有历史重投影和任意透明层适配；Oculus/Embeddium/Rubidium避让。AstraLex天空极光、战斗背景与点星另行分类，此世界介质预设不实现它们。浮点超长时间与浏览器绝对大世界坐标仍有限制，Forge位置使用double重基。

GPU draw计时不含FBO拷贝/读回，不能当作Minecraft整帧FPS或跨硬件预算。固定版本的详细计时与p95见Forge报告；原包源码存在不等于原包画面/性能已运行。
