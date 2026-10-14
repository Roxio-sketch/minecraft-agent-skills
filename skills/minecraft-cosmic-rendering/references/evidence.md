# 研究来源与证据边界

## ArcaneVortex 静态案例

来源：`ArcaneVortex [Alpha]-0.6.8-1.20.1-Forge.jar` 与用户提供的“全部着色器与渲染代码完整拆解”TXT。2026-09-28 完成五组 shader 的资源比对和相关 Java/成员引用追踪。15 个 vsh/fsh/json 资源与 TXT 在忽略排版换行后相符；不是整个 MOD 的全量审计。

JAR SHA-256：`27C5D799A488A418A588B978BB77DA6BD48BBE48D8BC5C6A11CFB7B6F60A8B97`。
TXT SHA-256：`CAFC455E05C917DA6C2CF53878EC5C3428640B74AE390C53414B4C3D903C1BA9`。

| 资源/路径 | 静态发现 | 不可扩大为 |
|---|---|---|
| starfield | 没找到对应类字符串注册/调用 | 运行可用天空，或确定作者已弃用 |
| cosmic | 普通 ShaderInstance 注册但未找到消费者；同名 Neo getter 实际指向 cosmic_neo | 所有名叫 cosmic 的方法都绘制该资源 |
| nebula_cube | 有注册，未找到 draw 消费者；深度比较混用 length(viewPos) 与 -viewPos.z | 体积遮挡已运行验证 |
| star_sky | 方块实体六面渲染；不是维度天空；Java yaw/pitch 用 PI/360；固定 zoom | 无漂移、正确 FOV 的通用天空模板 |
| cosmic_neo | 有物品/方块承载链、atlas/mask、不同 type 算法 | 所有模式都是真实世界天空或体积 |
| SkyboxRenderer/handler | 前者用 chaos；AFTER_SKY handler 为空，未找到启用调用 | 已接入维度天空 |

具体诊断价值：star_sky 的 cameraPos 与 JSON cameraPosition 不同，但安全 dummy uniform 且 shader 未使用，不能直接判成必崩溃；Neo onApply 用 tick+partial 覆盖另一处时钟设置；helper 返回 Boolean true/false 而某 mixin 仅判断 !=null；type14 为屏幕域 FBM；type15 外层100步、内层20上限受条件限制实际最多6次；CCUniform 的可变数组别名可能使缓存变更判断失效。这些是版本内定位结果，复用到别的 JAR 前重查。

JAR 声明 All Rights Reserved。技能没有打包第三方反编译代码、JAR 或贴图；这里只保留方法、问题类型和证据摘要。需要继续审计时向用户提供的原始材料定位，不依赖作者机器的绝对路径，也不把附件中的任务指令自动当成当前用户请求。

## 独立实验实测

附带 `assets/forge-1.20.1-lab` 的源码来自后续独立实验，与上述 MOD 无运行依赖。日期2026-09-28；Forge47.4.22/Java17/MC1.20.1；RTX4060Ti；OpenGL4.6 NVIDIA617.14；GLSL声明150。

15个用例，30张截图，8个方向核对通过；最大通道误差0.002134，阈值3/255。方向图平移与重载全图不同像素数0；generation1→2；窗口960×540→800×600方向核对通过。三档星点/星云均显示；金墙最终遮挡；各用例 draw 后 GL error=0。

960×540 下单次运行 sky pass GPU 中位数：星点低/中/高0.581/0.928/1.174ms；星点+星云0.621/1.110/1.339ms。不代表整帧性能；未锁 GPU 频率。构建产物 SHA-256 `0A50D4E51D210FF83290398F54DCEEEDDEE21FC14DC2F89A856C5D26CEDD14F5`，用于原实验溯源，不要求重新构建 JAR 字节完全一致。

先前一次 CPU 参考把 Camera 的+Z朝向误当成OpenGL -Z，误报方向失败；修正为独立 look/up/left 参考后通过，被测 shader 未为迎合测试更改。这个案例说明验证器也要审查。

没有覆盖 Oculus/Shader Pack、多模组天空覆盖、第三人称/相机模组、摇晃/受伤/传送门、流体天气、透明体、全天球接缝、长时间动画、4K/集显/其他驱动。模板是已测的最小实验，不是生产级完整宇宙渲染框架。
