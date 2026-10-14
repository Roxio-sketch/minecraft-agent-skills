# Cosmic 防具与盾牌运行验收（Forge 1.20.1）

2026-10-02 在 AXO `ASTRA-TEST` 的本地单人存档 `ASTRA_VFX_TEST` 中，使用 Forge 47.4.10、Java 17、NVIDIA GeForce RTX 4060 Ti 对 `cosmic-material-lab-0.4.0.jar` 完成实际客户端验证。生产 JAR 已执行 `reobfJar`，安装包与构建包 SHA-256 均为 `207e8475e100019d803ea8257f6fc2a05702d16c3ee13fbec8ea0d0c3ca5bf7a`。

## 结果

- 装备脚本完成 9 个场景：装备 GUI、整套防具第三人称正/背面、盾牌第一人称、连续举盾 30 tick、纹章盾、保留金属边框的盾牌、边框与纹章组合、资源重载。
- 四个实际穿戴槽位 `HEAD`、`CHEST`、`LEGS`、`FEET` 在正/背面均有 Cosmic draw；普通盾牌与边框盾牌分别走自己的实际 GUI、第一人称 RenderType。全部截图点 `glError=0`。
- 人工核对第三人称正/背面截图，玩家身上的头盔呈蓝青星空、胸甲呈金色星空、护腿呈青色星空、靴子呈深蓝星空。它们出现在玩家实体表面，而非仅出现在物品栏图标。
- `shield-framed` 和 `shield-framed-patterned` 的真实游戏截图与 GUI 并排图显示：金属框留在 Cosmic 盾面之外，纹章图案仍在盾面之上。边框受原版 glint 影响会呈浅紫色。
- 资源重载后 shader generation 从 `1` 增至 `2`，材质继续绘制。测试结束时背包、选中槽位、游戏模式和位置检查均为 `true`。
- 同一 0.4.0 客户端另跑原始物品的 10 场景回归，全部 `glError=0`，满弓 41 tick 解析到 `minecraft:item/bow_pulling_2`，原脚本记录的玩家状态和测试实体均恢复。

原始装备证据在 [runtime](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/)；重点图片是 [GUI 并排对照](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/equipment-gui.png)、[穿戴正面](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/armor-front.png)、[边框盾](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/shield-framed.png) 和 [边框纹章盾](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/shield-framed-patterned.png)。[装备结果 JSON](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/results.json)、[25 项独立检查](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/runtime-validation.json) 和 [旧物品回归结果](E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/item-regression/results.json) 可复核。

复核命令：

```powershell
cd E:/着色器渲染/cosmic-material-lab
C:/Users/MAINTAINER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe scripts/validate_equipment_runtime.py
```

## 实现与使用

物品图标仍通过 baked model wrapper；穿戴防具通过 `HumanoidArmorLayer` 的原模型 base pass 接入，原姿态、纹饰和 glint 保留；原版 `minecraft:shield` 通过 BEWLR 的模型和纹章入口接入。所有装备都需 `CosmicMaterial` 复合 NBT 才启用。边框预设示例：

```mcfunction
/give @s minecraft:shield{CosmicMaterial:{Palette:"cyan",ShieldStyle:"framed"}} 1
```

[HTML 预设工作台](E:/着色器渲染/cosmic-material-lab/assets/shield-presets/index.html) 提供完整 Cosmic、边框 Cosmic、纹章 Cosmic、边框纹章四种组合，以及三套配色和对应指令。工作台是 Canvas 设计预览，游戏截图才是生产 shader 的运行证据。

## 边界

已认证的是原版 `HumanoidArmorLayer` 路径的穿戴防具和原版盾牌 BEWLR。第三方自定义盔甲层、其他盾牌 renderer、骨骼装备、透明多 pass、Oculus/Iris 光影包尚未验证。边框保留由原版盾牌纹理中中低亮度中性色识别，资源包更换盾牌贴图时应重新核对边框。没有 FPS 或 GPU 时间基准。验收脚本会临时装备/移动玩家，运行前已备份存档；脚本恢复玩家状态，但不宣称整个存档字节级回滚。
