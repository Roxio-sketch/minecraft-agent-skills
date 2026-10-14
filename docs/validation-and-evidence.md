# 验证证据与范围

快照日期：2026-10-14。下面是已存在、随本次发布公开的历史记录，不冒充本次重新执行的游戏测试。此次发布的检查结果另见 [发布检查](release-validation.json)。

| 内容 | 证据入口 | 可支持的结论与限制 |
|---|---|---|
| Cosmic 物品/装备 | [物品运行记录](rendering-records/COSMIC_MATERIAL_RUNTIME_VALIDATION.md)、[装备](rendering-records/COSMIC_EQUIPMENT_RUNTIME_VALIDATION.md) | 独立 Forge 代表性画面；不外推所有模型、骨骼或光影 |
| 彩色 glint | [单色](rendering-records/COLORED_GLINT_RUNTIME_VALIDATION.md)、[渐变](rendering-records/COLORED_GLINT_GRADIENT_RUNTIME_VALIDATION.md) | 对应实验运行记录；御体浏览器设计稿不继承游戏认证 |
| 体积云雾 | [验证合同](../skills/minecraft-volumetric-environment/references/validation.md) | 配方、GPU、浏览器和独立 Forge 分层报告；水龙卷页明确尚无 Forge 接入 |
| 能量烟纱 | [Skill](../skills/minecraft-energy-vfx/SKILL.md)、[参考资料目录](../skills/minecraft-energy-vfx/references/) | 发散、装备来源、近镜头与性能取舍有版本说明；没有完整流体求解和多人同步 |
| 物品内场景 | [运行记录](rendering-records/ITEM_SCENE_WINDOW_RUNTIME_VALIDATION.md) | 真实离屏三维窗口示例；不等于任意维度可进入系统 |
| 局部异空间 | [Ink Realm 实测](../skills/minecraft-local-subscene/references/runtime-inkrealm.md) | 固定平面入口及轻量领域的明确范围；不自动具备第二 ClientLevel |
| Halo | [Skill](../skills/minecraft-halo-decoration/SKILL.md) | 离线素材与行为检查；无通用可安装 JAR 和游戏运行认证 |
| 御体光纹 | [设计和验证说明](../skills/minecraft-colored-glint/references/workbench.md) | 浏览器动态、零速冻结、受击消散和配方回读；未验证游戏穿戴 |
| 五行仙途特效 | [全部 51 组记录](rendering-catalog.md#五行仙途中的特效开发和优化记录) | 原生/指定光影、拖尾、御空、护盾、黑云、火焰的项目级证据；历史失败与后续修改保留 |

## 炉底 GPU 优化实例

[性能 Skill](../skills/minecraft-vfx-performance/SKILL.md) 和 [深度重建方法](../skills/minecraft-vfx-performance/references/volume-depth-composite.md) 从项目实际问题中提炼。

1. [短区间采样试验](wuxing-vfx-records/furnace-near-20261014/检查与优化.md)：保持全分辨率，固定测试约 8% 改善，仍然过重；不是最终解决。
2. [低分辨率积分与重建](wuxing-vfx-records/furnace-near-performance-20261014/修改与验证.md)：RTX 4060 Ti / 1280×900，三分之一宽高 RGBA16F、原屏幕深度和预乘颜色重建。原生底火积分约 83–86→11–11.5 ms；Solas V3.7b 含合成约 83–86→11.1–11.5 ms。每版本 24 次交替 GPU draw 取中位数；仅测底火绘制，非整游戏 FPS。
3. 同射线场计算不变与屏幕重建画面一致是两件事：低分辨率细节略柔和。用户后续指出暗紫火过淡和地面透出，Skill 明确保留这一视觉限制；[后续炉底方案](wuxing-vfx-records/cold-sun-colors-20261014/炉底新方案.md) 不冒充已经完成的管线。

结果是单硬件、固定场景和指定版本的记录，不能外推任意视角或硬件。生产路径不默认开启 GPU timer；初始化缓存与稳定绘制分开衡量。

## 当前重要边界

- 浏览器代理模型、解析深度和独立 shader 编译不能证明 Minecraft 世界遮挡、装备动作和任意光影兼容。
- 研究目录收录思路、代码入口和许可证核查，不能表述为全部实现已交付。
- 所有近距裁剪、降分辨率、可见包预算和采样调整都需要独立视觉检查。已有记录中的开放问题仍保留。
- 未在本次发布中重新构建全部 Forge 工程、启动游戏或安装任何新模组。

## 此次发布复核

25 个总 Skill 的元数据、渲染工程 Python 语法、32 个入口及其 HTML 脚本/样式资源检查全部通过；全部 Skill.md 的相对文件链接存在。Edge 无头加载复核的 32 个入口最终均未报告页面脚本异常或必需资源失败。[浏览器记录](browser-release-smoke.json)。

为限制自动化绘制负载，本次浏览器复核限制 requestAnimationFrame 回调次数，不能证明完整动画、视觉质量或导出 API。首次混合批次中的两个 GPU 页面加载超时，换用新浏览器会话后通过；首次观察保留。工作台截图仅展示已人工查看的代表性界面。EnergyVFX 0.2.5 的 ZIP CRC 和七份共用 GLSL 字节一致检查通过，[运行库检查](energy-runtime-package.json)。

本次未重新运行全部 Forge 构建、游戏测试或各 Skill 的所有历史验证脚本。
