# Forge 1.20.1 接入

模板固定 Forge 47.4.10、ForgeGradle 6.0.54、官方 1.20.1 mappings、Java 17，使用现有本机缓存验证。升级依赖时查看官方 Forge 发布页，并重新构建。

源码入口：`WaterRipple` 仅作为 Mod 入口；`RippleClient` 处理命令、tick、世界卸载；`WaveField` 与 `RippleRecipe` 是参数和求解；`RippleRender` 注册 shader 并生成网格。

在客户端线程调用：
```java
RippleClient.create(new Vec3(x, surfaceY + 0.02, z));
RippleClient.impulse(hitX, hitZ, 0.35, -0.12);
```
这是一个活动补片。改为多补片时给每片独立 WaveField、原点与生命周期，限制数量及总网格预算；按相机距离排序半透明补片。连接落水、脚步或技能事件时使用实际世界坐标。服务器事件应通过明确客户端包转发；模板没有网络同步。

网格默认 4096 四边形，每帧 16384 顶点；每 tick 三次 65² 标量求解，不需要 compute shader 或额外渲染依赖。RGBA 顶点颜色编码法线，UV 编码局部水面位置；不要用该 vertex format 传 tint，tint 是独立 uniform。

波面顶点在世界空间，然后减相机一次，事件 PoseStack 转入观察空间。RegisterShadersEvent 能在资源重载后替换 shader。RenderType 开启 LEQUAL 深度、NO_CULL、透明混合和 COLOR_WRITE，在 AFTER_TRANSLUCENT_BLOCKS 绘制；这不是原版水的网格替换。底层透明排序、雨天 fog、光照及第三方着色器兼容没有通用保证。

扩展真实折射必须捕获场景颜色与深度、处理水面遮挡、坐标重建、FBO 恢复和世界卸载清理。原项目的泳池 ray intersection 仅能复用到自行构造的水槽；不得把它直接当作 Minecraft 任意世界射线求交。


2026-10-09 性能约束：复用 CPU 数值与上传缓冲，不通过降低采样精度、网格分辨率或粒子数量替代优化。黑洞／白洞前景遮挡缓冲每次使用前 clear 并完整覆盖，保证 GL/UNPACK 状态恢复；水面高度每帧每节点只采样一次，再复用邻点生成法线，保留原网格边界与插值语义。宿主参数缓存必须在单值修改、原子导入及重置默认时失效。
