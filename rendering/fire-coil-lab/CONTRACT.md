# 异火缠绕 v0.2 实现契约

所有 JSON 字段精确对应 current-recipe.json，schema=fire-coil/2。36 字段（含 schema 与三个 HEX 色）；有限数值，未知/缺失/越界拒绝，保留上次有效配方。以实际统计为准。此效果独立，不覆盖原 energy-vfx 配方或工作台。

默认绕本地玩家身体，身体朝向和插值位置真实来自玩家；离体 wisp/ember 用固定 20 Hz 世界空间模拟，绑定者移动时旧粒子不重新挂回。第一人称仅降低自身主体与出生强度，不改第三人称数量。暂不声称任意装备表面或骨骼贴合，不提供伤害和服务器同步。

## 范围（min,max）

radius(.15,1.8), height(.2,3.5), turns(.25,5), strands(1,8 整数), width(.04,.65), rotationSpeed(-2,2 圈/秒), riseSpeed(0,3), waveAmplitude(0,.3), waveSpeed(0,3), turbulence(0,2), tongueLength(.1,2), erosion(0,1), intensity(.2,4), opacity(0,1), emberRate(0,160), emberLife(.15,3), emberSize(.005,.1), wispRate(0,80), wispLife(.15,2), wispSize(.05,.6), outwardSpeed(0,2), lift(0,2.5), motionInheritance(0,1), seed(0,1000000 整数), segments(24,512 整数), rampTime(.05,2), firstPerson(0,1)。颜色 #RRGGBB。

容量：最多512离体粒子，生成拒绝计数明确；默认稳定约34粒子，其中约11个离体灵火。体积通道每帧最多选择24个离体灵火，统计分别报告模拟与实际绘制数量。帧率不影响出生/过期。暂停不推进，世界切换和传送>8m清理。所有模拟在 tick；渲染使用插值，不重复生成。

## 共享 Shader

root维护 `fire.volume.core.glsl`（主火流和离体灵火）与 `fire.frag.core.glsl`（小余烬），无version/main的GLSL 150/300 ES公共函数。Web和Forge使用原样共享代码，构建记录SHA。主火流和离体灵火均从三维密度积分，不绘制UV火带或面朝相机的火焰平面。每个像素读取独立场景深度，射线进入实际包围域，遇实体表面终止。

体积函数 `vec2 sampleFireVolume(vec3 p,float t)` 返回密度和热量；p为以真实脚底插值位置和身体朝向转换后的身体局部米坐标。`vec3 fireVolumeColor(float heat)` 返回三色热量映射。主要uniform：Time、Radius、Height、Turns、Width、RotationSpeed、RiseSpeed、WaveAmplitude、WaveSpeed、Turbulence、TongueLength、Erosion、VolumeStrength（float）；Strands、Seed、WispCount（int）；CoreColor、MidColor、EdgeColor（vec3）。Wisps[24]为局部中心xyz和size，WispVelocity[24]为局部速度xyz和seed，WispMeta[24]为归一年龄和strength。强度Intensity及透明度Opacity只在积分wrapper各消费一次。

解析螺旋骨架、三维噪声侵蚀、空间流场扭卷和内部细丝共同形成密度；它是程序化三维火场，不是网格流体求解器。离体中心由世界空间模拟决定，转换局部坐标仅为统一shader采样。segments实际控制24–512射线积分步数，默认512步。默认蓝白灵火：细小白亮火核、蓝色中层和透明深蓝外焰。当前用户要求以造型和运动为先，计时只记录，不用于降低质量。

Web输出预乘alpha、Forge输出straight alpha，各用对应混合公式。二者都先渲染场景深度；Forge只复制深度到独立纹理，再写主颜色，避免采样正在写入的深度附件。相机入域时从0开始积分。小余烬沿用POSITION_COLOR_TEX及 `shadeFire(...,2.0,...)`。自发光来自材质颜色，不承诺照亮场景。

WebGL2预览代理人形、可旋转/缩放、演示移动、暂停重置、分层开关、参数和多色起点，提供 `window.fireCoil={getRecipe,setRecipe,getStats,setTime,setCamera}` 供验证。与Minecraft共用shader；人体只是观察代理。

Forge项目 fire-coil-lab/forge，modid=firecoil，独立namespace。配置 config/firecoil/recipe.json；fixed command bridge config/firecoil/command.json→result.json：action=status/start/off/reload，nonce精确回应，status包含active,particles,spawnRejected,renderedVertices,recipeHash/或完整关键参数。默认主动body效果，仅本地玩家，进入世界加载。

单独生产构建与数值行为测试；独立QA可借energy-vfx/forge里的AutoQa模式但不要控制用户相机或世界。开发证据和实机边界单独记录。root负责编辑器服务与安装/启动。

新增 attachment(0,2 整数：身体/武器/两者)、weaponRadius(.01,.6m)、weaponWidth(.02,.5m)、weaponTurns(.25,5)、weaponStrength(0,3)。Python/JS/Java全部支持精确31字段旧fire-coil/1升级；缺字段、额外字段、残缺v2和非法旧值均拒绝。

武器 shader 使用 WeaponOrigins/Axes/Sides[2] 与真正 blade length/radius，并保持实体深度。DensityStep 为积分wrapper提供的局部射线段，用于细丝高斯段内平均；武器转换到自身基底。全分辨率512积分保持，未宣称性能优化。Native afterItem 用带finally的上下文栈，仅本地玩家手持剑执行，GUI/外层BEWLR排除；透明生成模型按alpha像素求范围，宿主profile优先。
