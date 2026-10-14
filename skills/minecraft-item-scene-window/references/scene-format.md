# 场景文件

资源 `assets/<namespace>/scenes/<path>.json`；选择ID为 `<namespace>:<path>`。可用资源包覆盖，重载后生效。参考物品为 `scenewindow:window`。

```mcfunction
/give @s scenewindow:window{Scene:"scenewindow:observatory"} 1
```

只有示例注册物品读取此1.20.1 NBT。给原版物品加同样NBT不会自动更换renderer。

```json
{
  "version": 1,
  "background": [0.02, 0.03, 0.06],
  "frame": [0.7, 0.5, 0.2],
  "camera": "offaxis",
  "eye": [0, 0, 2.5],
  "resolution": 384,
  "aperture": "rectangle",
  "nodes": [
    {"type":"box","position":[0,0,-2],"scale":[0.7,0.7,0.7],"color":[0.2,0.7,1],"spin":[0,15,0]},
    {"type":"box","position":[-0.3,0,-0.5],"scale":[0.1,1,0.1],"color":[1,0.6,0.2]}
  ]
}
```

根字段：version=1；background/frame为RGB；camera为offaxis/fixed/orbit；eye是GUI/固定模式眼位，z≥0.1；resolution为64..1024整数；aperture为rectangle/circle/diamond/mask。mask另需 `mask:"namespace:textures/mask/window.png"`，红通道≥0.5保留，按正常PNG方向供给。mask模式不自动描边，边框可由原物品提供。

节点：type为box/mesh/group；position默认0，scale默认1，rotation默认0（度，XYZ），spin默认0（度/秒），color默认白，children默认空。父矩阵×平移×旋转XYZ×缩放。时间来源世界tick与partial tick，菜单无世界时为0。orbit的轨道半径/速度目前在Java，不支持虚构配置字段。

- box：中心在原点的单位立方体，scale决定尺寸。
- mesh：vertices为扁平数值数组，每9个值组成三角形，无索引/UV/法线。
- group：只组织变换，不绘制自身。

`color` 仅影响当前绘制节点，不向 children 继承；group 的颜色不产生效果。RGB 按 0..1 设计，当前没有 HDR 材质输出。内部内容按窗口局部空间放在 z<0；模板 near=0.04、far=128，并未增加 z=0 的几何裁剪面。场景穿过窗面、跨界显示或完整世界后端需补显式裁剪。

没有主题名枚举，任意namespace新增场景均可使用。复杂资源导入需另写转换器。代码扩展点为 `Scenes.Backend.draw(scene, seconds)`；添加新节点类型时同步解析器、验证器和预算。Java变化需构建/重启，资源重载只更新资源。

验证器拒绝拼错字段、非有限值、错误向量和超预算输入；预算是模板起点，不是美术主题限制。错误场景当前记录错误并不绘制，生产适配可增加原物品/诊断占位回退。
