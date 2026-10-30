# 遁光 V8 研究原型

当前新增独立起遁/到达控制：旋带聚光与扩散、轴向卷入与回卷。普通入口仅巡航；[旋带完整演示](index.html?form=needle&sequence=rings)、[旋流完整演示](index.html?form=needle&sequence=vortex) 自动循环，可取消循环并暂停。详见 [V8说明](../../references/transition-stages-v8.md)、[关键帧](transition-stages.jpg)、[连续动态](transition-motion.gif)。八项巡航保持，仍无人物或载体模型。

第二批液光流绸与碎芒汇流默认 V6，可切 V5 对照；长虹与双流仍默认 V5，烟、羽状 V3 与赤焰保留。详见 [V6说明](../../references/liquid-shards-v6.md)、[第二批静帧](liquid-shards-comparison.jpg)、[第二批动态](liquid-shards-motion.gif) 和 [运动关键帧](second-flow-motion-keys.jpg)。扇流后续见V7，起遁/到达后续见V8。第一批 [说明](../../references/flow-iteration-v5.md)、[静帧](flow-comparison.jpg) 和 [动态](flow-motion.gif) 仍可查看。

打开 [index.html](index.html)。无 CDN，所需纹理以 PNG 字节嵌入脚本，可离线运行。

当前“红黑离烟”和“赤焰撕裂”各自默认恢复 V3；各族的“效果版本”下拉可单独选择 V4 实验，互不影响。用户指出 V4 两者像退化后，保留旧轮廓与流动作为后续修改基础。用户指定的“羽状分叉 V3”另有独立按钮，保留 V3 造型；“开放流羽 V4”作为另一选项。两者共用 V4 相机与合成宿主。原 `/v3` 也获得同源硬缝修补，旧 V3 ZIP 与修前源码快照仍保留。只制作程序几何与着色器，不加入人物、武器或载体模型；不是 Forge 运行 JAR。

可拖动旋转、滚轮缩放，暂停后检查几何。停止保留时间老化。侧面相机沿当前飞行方向构造，三个中性背景用于审阅；选择烟或火后，在“效果版本”下拉切换 3/4，并以相同时间、参数和相机比较。烟与火的版本选择分别保存；展开“合成与相机”仍可查看 V3 消费者同场景基线。

- [当前默认与实验对照](regression-correction.jpg) / [历史 V3/V4 形体对照](iteration-comparison.jpg)
- [局部硬缝修前后对照](seam-close-comparison.jpg)：相同参数与合成下检查切口，不是整体重设计
- [历史 V3/V4 连续采样](motion-preview.gif)：开放流羽 V4、烟 V3、火 V3；固定步截图合成，不代表实时性能
- [学习来源](learning.html) / [纹理来源](textures/SOURCES.md)
- [回退原因与后续方向](../../references/regression-correction.md) / [V4实验与边界](../../references/lookdev-v4.md) / [工作台](../../references/workbench.md)

此前的局部修补使连续整段 ribbon 按弧长短窗渐隐，火舌不再硬挖矩形洞，并补充 V4 羽筋收尾、水膜/漩涡共享顶点透明度及水膜连续法线；未增加 Bloom 或改配方/整体形态。已查看同条件与运动截图，火焰矩形洞移除，羽尾平切弱化。V8十一入口 WebGL、八入口离线、双阶段时序/几何及八项保留性检查通过；V2 活跃入口重定向 `/v3`，休眠旧源码未改。

默认 V3 烟为纹理密度卡片；V4 烟实验为有界程序密度积分与艺术散射近似，没有流体求解、多重散射或透明物体深度。羽膜/火焰仍为分层表面，部分角度仍可能显出面片感。技术检查不证明影片湍流卷吸、镜头感或完整生命周期已还原，未获新的美术验收。
