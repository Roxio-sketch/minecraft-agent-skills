# 彩色附魔流光：游戏内复现结果

**已在用户选定的 AXO ASTRA-TEST / ASTRA_VFX_TEST 中复现。** Forge 1.20.1 / 47.4.10，Java 17，NVIDIA RTX 4060 Ti。本机记录跑次为 `20260929-011858`（Asia/Shanghai）。

新建独立 `colored-glint-lab` 模组，保留既有 Cosmic Material Lab。生产 JAR 已构建、重混淆并安装；项目和安装 JAR 的 SHA-256 均为 `147254fd15c19c49e47544204e393a662cd5044fe5eff6adbc48c8cc470cb890`。启动器与 MCP-HELPER 实时返回选定存档，之后才执行验收。

![同屏彩色流光实测](colored-glint-lab/evidence/20260929-011858/gui-colors-a.png)

## 实测结果

| 场景 | 结果 |
|---|---|
| 同屏红、蓝、紫、绿附魔剑 | 各自颜色可见，Force=false 仍可选色；像素通道变化符合对应颜色 |
| 未附魔青色剑 Force=true | 正常显示移动的青色流光 |
| 无附魔且 Force=false；普通剑 | 两组对照相对基线与跨帧均无像素变化 |
| 原版流光与未知 ID 回退 | 外观一致；比较 ROI 为 33,856 像素，其中仅 7 像素相差 1/255，无超出 2/255 阈值的差异 |
| 动态变化 | 红/蓝/紫/绿 ROI 分别有 10,020 / 10,264 / 11,132 / 11,132 像素随时间变化 |
| 第一人称与左右主手 | 红/蓝右手、绿色左手画面可见 |
| 第三人称 | 紫色持剑在当前视距可见，不代表全部视角矩阵 |
| 弓 override | 满弓 61 tick，解析到 `minecraft:item/bow_pulling_2`，流光仍存在 |
| 掉落物与展示框 | 两个掉落物和固定展示框显示各自颜色 |
| Cosmic 叠加 | 星空剑和三维方块保留 Cosmic 材质并叠加彩色流光 |
| 两次资源重载 | core glint shader 实例均更换，重载后五种实测颜色保持可见 |
| GL 错误 | 12 个截图点均为 0；不是全帧 GPU 调试结论 |
| 恢复 | 背包、选中槽位、模式、位置及自建实体清理均记录 true |

GUI 验收分辨率 1280×720、GUI scale 2，流光强度 1.0、速度 0.5。像素变化阈值为单通道差 >2/255。源纹理较亮；截图代表本次设置，不是所有用户选项下相同的曝光。结束时恢复原显示设置，并打开不改背包的实时展示屏。

![手持与世界场景](colored-glint-lab/evidence/20260929-011858/runtime-contact-sheet.png)

## 证据与复跑

- [原始运行记录](colored-glint-lab/evidence/20260929-011858/results.json)
- [证据检查结果](colored-glint-lab/evidence/20260929-011858/validation.json)
- [可复跑检查脚本](colored-glint-lab/scripts/validate_runtime.py)
- [部署记录](colored-glint-lab/evidence/deployment.json)
- [成功构建日志](colored-glint-lab/evidence/build-third.log)
- [脱敏客户端日志](colored-glint-lab/evidence/20260929-011858/client-sanitized.log)
- [操作说明与命令](colored-glint-lab/README.md)

首次证据检查对两个不同位置的原版回退 ROI 要求逐字节一致，检测到 7 个像素的 1/255 差异。已保留该严格比较结果，并将回退比较统一为其他像素测量使用的 2/255 容差。没有修改截图或把大幅颜色变化当作采样误差。

构建早期分别遇到 Mixin AP 缺少隔离依赖和 RenderType 五参数 overload 不公开，补齐 AP 依赖、采用公开 overload 后构建成功。`test NO-SOURCE` 表示项目未添加 JUnit，本次游戏运行与图像检查提供运行证据。

日志保留原版/Moonlight 的 Sampler2、IViewRotMat 与缺失声音等警告，未发现本模组 Mixin 应用、纹理加载或 shader 错误；不宣称整合包“日志零警告”。

## 范围

当前验证普通 baked item 路径、目标样例模型和原有 Cosmic 模组组合。穿戴护甲、BEWLR、骨骼动画、第三方即时 BufferSource 和光影包未验证。五种 RenderType 模式已经构造，但本次实际 draw 记录只出现 direct；不能据此扩大兼容结论。

测试前已完整备份存档至 `colored-glint-lab/evidence/preflight/ASTRA_VFX_TEST-before.zip`。本轮不改方块，自建三个实体按 UUID 清理。世界时间与可能解锁的进度/配方没有整体回滚。游戏保留在实时展示屏；Esc 返回世界，`/coloredglint showcase` 可再次打开。
