# 0.2.0 原透明几何后端：实机验收

日期 2026-09-29；Minecraft 1.20.1 / Forge 47.4.10 / Java 17；ASTRA-TEST / ASTRA_VFX_TEST；NVIDIA RTX 4060 Ti；Fancy；主要分辨率 1280×720，缩放案例 1000×650。安装的新 JAR 与构建产物 SHA 相同，精确指纹见 [surface-runtime-evidence.json](surface-runtime-evidence.json)。

## 已验证什么

本次实际实现、安装并测试的是 **C：原透明区块 draw 的材质注入**。原地形 A 可同时开启或单独关闭。不是在最终屏幕贴图，也未渲染第二个世界。

15 份接受的同帧原生记录全部满足：真实 chunk draw 非零、alpha 附件差异 0、depth 附件差异 0、最大深度差 0、GL error 0。

| 案例 | 实际 RGB 改动像素 | 说明 |
|---|---:|---|
| 河流：地形 + Cosmic 水面 | 55,517 | 水面与环境共享区域/材质场 |
| 河流：只改水面 | 55,510 | 地形后端关闭，水底/岸边原样；证明星点来自水面自身 |
| 河流：水墨水面 | 55,508 | 同一接入路径可换材质函数 |
| 相机旋转 | 67,740 | 区域仍取固定世界释放点 |
| 相机平移 | 189,577 | 相机与材质中心分离 |
| 受控水槽：只水 | 76,620 | 包括流动面；染色玻璃保留原表面 |
| 水槽：全部透明区块 | 189,684 | 水与染色玻璃都处理 |
| 水槽：地形 + 全部透明 | 189,684 | 透明层同帧对照独立于地形结果 |
| 纯玻璃视图，仅水 sprite 模式 | **0** | 原版与新 shader 整帧 RGB/alpha/depth 完全一致 |
| 同视图，透明区块层模式 | 902,982 | 显式选中玻璃后有变化 |
| 流水侧面/瀑布 | 495,055 | 保留原侧面网格、纹理取样与透明合成 |
| 水下视角 | 481,973 | 原水下雾继续生效，不承诺新介质光学 |
| 区域远离所有可见表面 | **0** | 整帧与原版完全一致 |
| 资源重载 | 76,620 | shader generation 1→2，sprite bounds 重取 |
| 窗口缩放 | 62,453 | 1000×650，原 alpha/depth 继续一致 |

这里的像素差统计只比较 **透明区块层**：在同一帧、同一目标初始颜色/深度、同一组 VBO 和 ChunkOffset 下分别用实际原版 shader 与新 shader 绘制，读取附件后恢复新材质结果。没有用两张不同时间的截图计算这些零误差。alpha 结果是该目标的合成附件，结合 fragment 始终保留原 alpha 的实现检查支持结论；不意味着任意第三方混合状态都验证过。

两个 0 改动案例分别验证对象排除和全区外恒等。边界画面与源码世界坐标计算支持局部影响，但没有为每个 Minecraft 像素提供独立世界位置真值或任意透明层 ID。

## 被拒绝的记录

- 首次 probe 的 VBO hook 未正确重映射，捕获 draw 为 0；不能进行有效 A/B。已修复 SRG refmap，并强制空 draw probe 报错。原失败 JSON 存在 `rejected-probe-hook`，不计入 15 份。
- 最初“纯玻璃”视角底部还能看到背后的水，RGB 非零是正确现象；移到全部射线朝上的玻璃/天空视图后重新测试，RGB 为 0。前一记录标记在 `rejected-camera-view`，不计为排除验证或实现故障。
- 初次启用时，未知 Moonlight light.glsl 指纹让系统主动回退。核对动态资源生成源码后才加入该确切版本的允许指纹，没有取消未知资源检查。

## 复现命令

默认 surface 为 basic；material 请求只在能力检查通过时生效，查看 status 的 reason 和实际 draw，而非只看 requested=true。

```text
/localmaterial hold 0
/localmaterial surface basic
/localmaterial surface material water
/localmaterial surface material layer
/localmaterial surface probe
/localmaterial surface status
/localmaterial terrain false
/localmaterial terrain true
/localmaterial off
```

`hold` 固定时刻供对照，`sphere` 使用原 20 秒扩散/收缩生命周期；新透明路径与地形读取同一中心/年龄/半径。新路径本轮没有单独做完整 20 秒生命周期定量读回，旧地形的增长记录不能直接当新路径证据。`surface alien` 已测试明确拒绝且保持原模式。

## 未认证与保留限制

- 原版水、流水侧面、水下视图、染色玻璃及它们的叠层有实际画面与读回。普通透明玻璃可能走 cutout，不属于 translucent layer；活塞移动块、BE 罐体、自定义流体 renderer、粒子和实体材质没有接入。
- 没有实际安装并测试第三方流体、Embeddium、Rubidium、Oculus 或 Shader Pack。标准路径 Mod 流体属于条件适用；自绘流体须 adapter。Fast 未单独测试，Fabulous 主动回退；不是声称所有图形模式都通过。
- 原版既有透明排序规则不改变；多层半透明排序也没有升级为 OIT。玻璃表面被排除不意味着隔着玻璃看到的水/背景不能变化。
- `water` 是 sprite 区域选择，不是严格 fluid ID；复用相同 sprite 的自定义几何存在资格歧义。未测试大坐标、抖动投影、阴影视图或动态 atlas 实现。
- 没有新增物理折射、反射或真实波浪位移；原版自身没有对应 Shader Pack 数据。水下雾仍保留原规则。完全异化只完成架构与能力拒绝路径。
- 没有毫秒级 GPU 性能认证，probe 本身产生额外绘制和同步成本；不能用 probe 帧推导正常 FPS。

## 测试现场与恢复

安装前正常保存并备份存档和 options。本轮为了检验流水/玻璃，先扫描 `(-219,170,-822)` 至 `(-193,183,-801)` 为全空气，建立有围挡的临时水槽和高处玻璃板，最后移除并再次扫描确认 **0 个非空气方块**。没有生成测试实体。恢复保存前玩家位置、视角、creative 模式、DayTime、晴天和 1280×720 窗口；所有实验材质关闭，退出 Helper 控制模式。

完整证据与脚本保留在研究工程 `research/local-environment/surface/`；原存档、第三方反编译源和完整日志不进入可移植 Skill。历史 0.1.0 的 14 份地形证据保持独立。
