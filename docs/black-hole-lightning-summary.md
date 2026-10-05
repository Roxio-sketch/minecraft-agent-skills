# 黑洞与闪电：发布说明草稿

根据“skill转mod”“黑洞与吸积盘”“闪电特效”三个聊天中的研究、实现和后续修正整理，最新修正优先。发布内容仅引用明确开源许可来源。

## 黑洞

五种盘风格、核心/盘/粒子/三层光晕/喷流独立调色；角向旋转与径向向内纹理输运分别控制。默认盘倾角为水平 0°，保留导入配方中的显式角度。沿表面命中点放置并固定世界中心，核心离地留 0.05 方块间隙。世界透镜可调范围与强度，玩家参与默认关闭。使用实际地形 VP 重建视点，覆盖相机摇晃与受伤投影平移；保护前景，处理云层阶段及 framebuffer/stencil 状态。属于实时美术近似，不宣称科学相对论模拟；屏幕透镜不能恢复画面外场景。

## 闪电

grow 固定根部生长、spektr 滑动脉冲窗口、ring 环绕，以及带种子的分形落雷、分支预算、集束与击地放电。几何固定 tick 更新并缓存，不逐帧随机重建。单路径光晕取 MAX 覆盖，独立路径 ADD 合成，凸分段与圆接头减少三角尖刺和接头亮点。配方严格 61 字段；旧 60 字段导入补 gradientRatio=50。比例 0 使用末端色，100 使用起点色；击地弧/环/火花/闪光继承实际落点颜色。

## 已有证据与限制

2026-10-05 项目完成记录报告：黑洞六视点地面 GPU 帧、轮廓最大误差 0；闪电普通深度五帧和 stencil 五帧；配方、链接、frontmatter、可移植模板和独立构建通过。这是既有开发记录，本次整理未重新运行游戏或构建。完整玩家开关、所有 shader pack 与操作系统热键组合并未在同一轮全覆盖。浏览器预览与 Forge 不是所有像素效果完全相等。

调试 mod 为 Forge 1.20.1 / Java 17 / 0.1.0，包含水波实验；F6/F7/F8 可重绑。和 MCP-HELPER 共用时注意 F8 设置。MCP-HELPER 提供辅助控制接口；这两个材质 skill 和调试 mod 不要求其 Photon 扩展。

## 参考来源与再分发

- [SpektrLightning](https://github.com/keijiro/SpektrLightning)：脉冲窗口研究；[Ashima webgl-noise](https://github.com/ashima/webgl-noise) / [NoiseShader](https://github.com/keijiro/NoiseShader)：Simplex 实现，完整 MIT 通知随 skill 与 mod 保留。
- [UnityLightning](https://github.com/nullsoftware/UnityLightning)：闪电形态和参数研究。
- 初始附件 thunderhead-main 与 unity-lightning-system-main：拓扑、分支和击地表现研究，来源以随附快照原 README/许可为准，不从文件名推测作者 URL。
- [WittyScrap unity-black-hole-shader](https://github.com/WittyScrap/unity-black-hole-shader)、[urp-black-hole](https://github.com/tantaneity/urp-black-hole)、[Bruneton black_hole_shader](https://github.com/ebruneton/black_hole_shader)：补充研究来源；详见 skill 内来源比较与算法说明。


## 初始开源参考附件

四份闪电参考包和三份黑洞参考包均保留根许可证；只分发源码、配置和文本，不附图片、视频、二进制、Unity 场景或第三方资源。附件名包含 source-snapshot：由现有解压目录重建，不等同原 ZIP；SHA256 与许可文件见 release 附件清单。未明确许可的来源不引用、不附。

黑洞三份来源：blackhole-main（MIT，2026 Furkan），unity-blackhole-shader-master（MIT，Mario Gutierrez），unity-black-hole-shader-main（MIT，2025 Francesco Litrico）。源码内的路径索引见 skill 来源对照。无法从原包确认仓库地址时以包名、许可与原始哈希识别，不猜测 URL。
