# 方块形状预览与验证

`render_shapes.py` 将常见建筑细块按1/16格实体网格生成表面，计算相邻细块遮挡并合并共面矩形。新 `generate_building.py` 默认使用它输出等轴测、正视与侧视；旧 `render_model.py` 仍保留快速整格预览。新图仍是近似色离线图，没有游戏纹理、透明效果或光照。

```text
python scripts/render_shapes.py MODEL.npz --out shapes.png
python scripts/render_shapes.py MODEL.npz --out top.png --view top
python scripts/render_shapes.py MODEL.npz --out south-view.png --camera-turns 2
python scripts/test_shapes.py
```

每张图旁边生成同名JSON，列出立方体回退状态及数量。回退既包含本就实心的普通方块，也可能包含尚未实现的箱子、门、活板门、链条、灯笼、植物等。不能把回退总数说成“画错了多少格”，也不能忽略其中的细块。

`--camera-turns` 通过将显示模型绕Y轴旋转四分之一圈改变观察方向，方块方向状态一起旋转，不修改输入NPZ。0为默认+X/-Z侧视角，2看到相反一侧；正投影和俯视也受此参数影响。报告记录所用转角，比较方案时保持一致。

## 精确核对的范围

通过官方版本清单下载Java 1.21.4客户端JAR，只读其中JSON模型，未执行JAR。下载的大小和SHA1与官方清单一致。`verify_vanilla_shapes.py` 对比预览占用网格和模型元素的实体并集，共246种状态全部一致：

| 形状 | 核对状态数 | 说明 |
|---|---:|---|
| 橡木楼梯 | 40 | 四向×上下半×直、内左、内右、外左、外右 |
| 橡木半砖 | 3 | 下、上、双层 |
| 橡木栅栏 | 16 | 四个连接方向的所有组合 |
| 玻璃板 | 16 | 四个连接方向的所有组合，仅几何实体 |
| 圆石墙 | 162 | 顶柱有无×四向none/low/tall；包含游戏通常不会自然生成的组合 |
| 雪层 | 8 | 1～8层 |
| 红色地毯 | 1 | 1/16格高度 |

同形材料族使用相同程序，但没有逐个检验所有材质变体。铁栏杆使用零厚度面与透明贴图，当前用细杆外包实体近似，另列 `solid_envelope_approximations`，**不在246种一致结果里**。几何实体并集一致不等于贴图面、遮挡规则、碰撞箱和实机渲染一致；例如栅栏的碰撞高度高于可见模型。

复核命令（自行提供合法的官方客户端JAR，不需要运行它）：

```text
python scripts/verify_vanilla_shapes.py --client-jar CLIENT-1.21.4.jar --out vanilla-shapes.json
```

本地证据为 `E:/建筑/analysis/vanilla-reference/source.json` 与 `E:/建筑/analysis/qa/vanilla-shapes.json`，客户端素材没有放进Skill包。独立单元检查还覆盖随机面片重建无扩大/重叠、半砖之间遮挡、楼梯体积及旋转、四种投影视图和回退报告。

## 用图判断建筑

先看整栋轮廓，再放大屋檐、山花、栏杆及台阶。相同屋面在整格预览中显得更厚，梯面朝向错误可能被掩盖；新图能帮助发现这一点。仍需在目标客户端核对半砖与楼梯贴合、方块自动连接、透明材质、照明和玩家通路。

为了观察被植被遮挡的屋体，可制作单独的分析副本并记录过滤条件，不改原文件。材料过滤应匹配基础方块名，不能因`waterlogged=false`中含有`water`就误删所有含水状态的楼梯、半砖和栏杆。


模组命名空间不套用原版后缀规则：自定义 `_slab`、`_wall`、`_stairs` 在未读取其模型前按整格回退，并写入报告。原版模型核对不覆盖这些状态。合集实例见 [ancient-structures-study.md](ancient-structures-study.md)。
