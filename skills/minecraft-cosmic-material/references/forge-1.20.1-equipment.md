# Forge 1.20.1 穿戴防具与盾牌 Cosmic 接入

可运行工程：`E:/着色器渲染/cosmic-material-lab/`，当前生产 JAR 为 `build/libs/cosmic-material-lab-0.4.0.jar`。2026-10-02 已在 AXO `ASTRA-TEST` / `ASTRA_VFX_TEST` 中真实启动并验收；独立报告见 `E:/着色器渲染/COSMIC_EQUIPMENT_RUNTIME_VALIDATION.md`。这是既有物品 shader 的几何适配，不是天空盒、UV 滚动贴图，也不表示任意第三方装备无条件兼容。

## 路径选择

- 普通 baked item 继续使用 `CosmicModels` 的 wrapper；物品栏图标成功不证明穿戴模型成功。
- 玩家实际穿戴的原版式防具从 `HumanoidArmorLayer.renderModel` 的 `MultiBufferSource.getBuffer` 接入，保留原模型顶点、肢体姿态、slot 可见性、贴图、trim 与 glint。读取当前 `ItemStack` 和 slot，只在有 `CosmicMaterial` 复合 NBT 时选择 Cosmic RenderType。注意 Mixin 的 Forge 运行映射和重载。
- 原版 `minecraft:shield` 是 BEWLR。重用 `ShieldModel`、其显示变换、atlas sprite wrapper 和 foil consumer；无图案盾牌替换模型 RenderType，有图案盾牌先绘制 Cosmic 底板，再保留原版 banner 图案层。图案列表索引 0 是不透明底色，应跳过，否则盖住 Cosmic。
- 物品 atlas 与装备贴图来自不同纹理源。`Sampler0` 采当前装备/盾牌贴图，独立 `CosmicAtlas` 采十张星体 sprite 和遮罩；按 RenderType texture/surface/context/palette 隔离缓存，在实际 draw 时绑定统一参数。资源重载后重建 RenderType 和 uniform 状态。

## 盾牌边框预设

缺省盾牌全覆盖。`CosmicMaterial:{Palette:"cyan",ShieldStyle:"framed"}` 使原版纹理的中低亮度中性色金属框保持原样，其他像素仍是 Cosmic；纹章白色底色由 Cosmic 替代，图案层保留。这个分类针对原版盾牌纹理做过实测，资源包改变贴图或自定义盾牌 renderer 时需要重新看图验证，不应套用这个颜色阈值。

随 skill 打包的 [HTML 工作台](../assets/shield-presets/index.html) 与实验工程 `assets/shield-presets/index.html` 内容相同，包含完整、金属框、纹章、金属框加纹章四个预设。提供三种已实现配色与 `/give` 复制。Canvas 是设计预览，实际像素和 glint 以游戏截图为准。

## 验收

在 `ASTRA_VFX_TEST` 运行 `/cosmicmaterialequipment verify`，或向 `<gameDir>/cosmic-material-lab/equipment-request.json` 写入 `{"action":"run"}`。验收驱动暂时装备四件钻石防具和盾牌、切换相机、举盾、加载纹章并重载资源，然后恢复玩家装备、模式、位置及显示设置。九场景结果位于 `<gameDir>/cosmic-material-lab/equipment-runs/<时间>/`。

现成证据位于 `E:/着色器渲染/cosmic-material-lab/evidence/equipment-20261002/runtime/`，含九张 PNG、draw counters、shader generation 和恢复状态；`scripts/validate_equipment_runtime.py` 独立检查 25 项。0.4.0 还跑过原普通物品十场景回归。原版盾牌第一人称未举起时位于画面左侧，验收图片主要看左边区域；GUI 并排图更适合检查完整边框与纹章层。

未认证：第三方自定义盔甲层、非原版盾牌 BEWLR、骨骼动画、半透明多 pass、光影包，以及性能预算。遇到这些载体，先定位其真实顶点与 buffer 入口，再建立独立适配器和场景验收。
