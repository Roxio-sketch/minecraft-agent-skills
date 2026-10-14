# 世界体积环境 Forge 示例

示例版本 0.2.0：Minecraft 1.20.1 / Forge 47.4.22 / Java 17 / ForgeGradle 6.0.54。独立客户端示例，不替换天空、不修改玩家方块、方块光照或天气。许可证 MIT；Gradle Wrapper 与运行时依赖保留各自上游许可证。

## 构建与配方

`./run-lab.ps1 -JavaHome <Java17目录> -BuildOnly`。Gradle/游戏 JVM 上限均为 768 MiB；复用既有官方依赖缓存。运行目录仅此工程 `run/`。`processResources` 拼接 `src/main/shader/wrapper-head.glsl`、`../workbench/volume-core.glsl`、`wrapper-tail.glsl`，根据共享 schema/contract/presets 生成 shader JSON 和默认配方，避免双份 GLSL 漂移。

工作台导出的 `{schema:"world-volume/2",name?:string,parameters:{全部62字段}}` 放到该客户端 `config/world-volume.json`。JAR只嵌入第一预设作为默认配方。旧完整 `/1` 默认被严格入口拒绝；显式 `/worldvolume upgrade-v1` 先对原50字段完整校验，再保留50值并从新schema添加12默认、强制densityModel=0，只更新当前会话，原文件需使用作者工具导出 `/2` 后才可正常重载。重复key、JSON注释、尾随数据、非有限值和超过120个Unicode码点的name均拒绝；配置读取失败保持上一个有效配方。未知key、缺字段、非有限/超范围、分数整数、环内径≥外径、持续时间>周期及零太阳向量均拒绝。`/worldvolume reload` 只重读配方，不重置时间；`time <秒>` / `freeze` / `play` 控制秒级时钟；`on` / `off` / `status` 控制展示。负时间由入口拒绝或 core 钳为零。

`densityModel=0` 保留原有限云/雾分布；`1` 使用纵向相干噪声、高度与时间扭转、减法椭球空洞/环槽，再添加下层雾；`2` 使用均匀薄雾密度，噪声动画只调制源亮度。`nearClearRadius` 只在模式1的视线积分中钳制密度，不改原始密度或光线透射采样。中心、bottom和voidFloor均按世界坐标解释。

## 渲染合同

AFTER_SKY 保存实际投影与视图逆矩阵，包含相机效果，AFTER_LEVEL 使用同帧缓存。inverseProjection 的光学眼点参与 ray origin；CPU 用 double 把 Bounds中心和 Layer.bottom / EndDepth.voidFloor 重基到相机，GLSL 以camera-relative坐标计算，避免世界大坐标先转换float造成精度丢失。对原相机/区域统一平移不改变局部噪声。

主 FBO 的颜色和深度先 `glBlitFramebuffer` 到独立snapshot。最终合成只采snapshot，写回主FBO，无读写同一附件。深度<0.999999 判为不透明表面，逆投影重建真实世界距离截断有限密度积分。RGB先近似sRGB解码，先叠加表面闪光，再 `scene*transmittance+premultipliedRadiance`，最后sRGB编码。当前主FBO是LDR，HDR高亮会裁切。

表面项使用单深度导数构造朝向相机的几何法线，含云介质透射；它没有材质G-buffer和闪光视角地形shadow map，不能声称真实地形阴影或真实材质BRDF。云自发光、闪光入介质、闪光入表面是独立项，可分别关掉与验收。

保存/恢复 framebuffer、viewport、texture units和Minecraft缓存、program、depth/write mask、blend及factor、cull、scissor、color mask、VAO/VBO/EBO。退出、资源重载、尺寸改变释放FBO。自定义资源/shader加载或draw失败暂停本pass，记录失败，保留正常游戏渲染；不修改世界内容。

## 限制与所有权

发现 Oculus、Embeddium、Rubidium 时自动禁用，此示例没有经过确认的替代渲染所有权适配器。主draw FBO不符或缺深度同样跳过；水/岩浆相机跳过。普通 AFTER_LEVEL 的场景色可能包含透明对象，而本合同只有一层不透明深度，任意玻璃/水分层排序不保证；需要单独承载器才能扩展。相机帧缓存无法取得时不绘制。

太阳光和flash只在本体积介质中衰减，无真实地形太阳阴影。单有限区域、每周期单事件，96视线步/12光步上限；全分辨率参考集成尚未提供半分辨率upscaling/时域历史。

## 独立验收

`./run-lab.ps1 -JavaHome <Java17目录> -AutoValidate -ExitOnComplete`，仅创建 `WORLD_VOLUME_LAB_<timestamp>` 新flat存档，测试工具墙也只建在这个存档，不打开旧存档。21场景：严格off、云、纯自发光、介质闪光、独立表面闪光、体积在墙后、球体在墙前控制、环状介质、资源重载、改尺寸，及末地基础密度/空洞/环槽/扭转/时间、下层雾/近清理、紫光薄雾。它们是隔离渲染合同的测试配方，不能外推为全部艺术预设的游戏观感验收。诊断通过同shader的私有ValidationProbe在1x1 RGBA32F目标读回中心射线透射和原始密度；该开关默认0且不在配方或公共contract中。证据目录含实际shader program、GPU回读uniform、源/目标FBO/texture、scene/post PNG、不透明与天空像素差、深度不变、状态恢复、GPU计时、多类非法配方拒绝记录。详细实际结果见项目 evidence/forge，不能用旧天空项目证据代替新测试。
