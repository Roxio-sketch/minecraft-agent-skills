# 参考复原、尺度标定与不确定性

已有工具能读取Minecraft几何、量柱网/剖面、拟合**统一比例 + 绕Y轴90度整数旋转 + 平移**，再生成或组合新模型。它不从一张透视照片恢复准确尺寸，不自动恢复隐藏结构，也不判断年代、榫卯与历史真实性。

## 建立可追溯参照

1. 原Minecraft存档：先读 [格式](formats.md) 和 [样本方法](method.md)，记录作者声明、版本、SHA-256和框选坐标。真实建筑：记录资料作者、机构、页码/图号、测绘日期、单位与尺度，区分原作、后期改建和修复图。
2. 在平面或三维数据上选至少三个**不共线**控制点，最好包含建筑的不同边界；要核对高度再增加不同Y的点。以像素读平面图时只对已正投影、有可靠标尺的图使用，单位写清为像素或毫米，不能假称米。
3. 按用途决定格尺度，如1格对应1米或近景构件1格对应更小尺寸。这是目标精度选择；小于1格的细节必须舍入、放大或使用细块，不能默默改变主体比例。

## 标定文件

`calibrate_reference.py`读取`architecture-reference-calibration/1`。每个点为`id/source_xyz/target_xyz`，`max_rms_blocks`默认为0.5；可用`quarter_turns`固定0～3，否则在四个方向中选择最佳。

```json
{
  "schema":"architecture-reference-calibration/1",
  "source_units":"metres (synthetic teaching example)",
  "max_rms_blocks":0.25,
  "pairs":[
    {"id":"origin","source_xyz":[0,0,0],"target_xyz":[10,2,-8]},
    {"id":"east","source_xyz":[2,0,0],"target_xyz":[10,2,-4]},
    {"id":"north","source_xyz":[0,0,4],"target_xyz":[2,2,-8]},
    {"id":"high","source_xyz":[1,3,2],"target_xyz":[6,8,-6]}
  ],
  "source_evidence":["Synthetic coordinates for demonstrating the method; not a surveyed historic building."]
}
```

```text
python scripts/calibrate_reference.py --pairs pairs.json --out calibration.json
```

这个教学例应得到比例2格/单位、顺时针一转、平移`[10,2,-8]`和接近0的误差。返回每点投影、最近整数格、均方根与最大误差。CLI在超出请求误差时返回失败并保留报告；不要只读比例数值而忽略`within_requested_tolerance`。

## 从标定到建筑

标定不会直接缩放NPZ中的状态。非整数缩放会破坏床、门、楼梯、窗格及通路，因此用标定结果**重新参数化**柱距、墙线、楼层与屋面：

- 先映射柱心、墙角、地坪和檐线；每个舍入误差写入表。`chinese-building/1`逐轴总跨度必须为偶数，冲突时明确调整一格或改用显式组件，不隐藏约束。
- 再对平面和立面各选未参与拟合的检查点，防止三个控制点恰好贴合却掩盖其他变形。不同视图比例不一致时分开校准并查图纸，不能强行平均成一个比例。
- 用`measure_components.py`复核柱高、柱距和限定屋面材料截线；用`measure_layout.py`复核区域、材料和门半。标注绑定哈希，换版本后重新标定。
- 用`chinese-scene/1`放置独立构架、墙面与屋面，保留原点和旋转；每处接口都看剖面，不能仅凭单张等轴测图。
- 测绘没有涵盖的家具、颜色、构件背面、排水、年代关系单列为未知或原创补全。游戏体素通路、状态注册表与文件回读分别验证。

## 交付用语和记录

记录至少包括`source / date_or_phase / source_units / scale / control_points / rounding_changes / measured_features / inferred_features / original_additions / verification`。报告称“按某资料复原的某部分”时，应能逐项追到尺度和检查点。机构概述、作者自称1:1、AI生成图片和风格标签都不能代替测绘。

本版本完成了标定工具与可重复教学验证，已有Minecraft来源的局部实测；尚未交付一座经过历史测绘全面核对的真实遗址复原。需要实机施工时再进入目标实例的放置、备份与检查流程。
