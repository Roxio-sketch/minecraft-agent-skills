# 来源、许可与发布范围

原创 Skills、编排脚本、工作台及明确原创实现遵循仓库根目录 MIT。子目录保留的许可证、版权与来源声明优先约束对应第三方内容，不能将整个集合概括为所有资产均 MIT。

| 来源 | 发布处理 |
|---|---|
| Stefan Gustavson / Ian McEwan psrdnoise 等噪声 | 原文件 MIT 版权及许可保留；见 energy 内核与参考说明 |
| Three.js | `minecraft-glass-rim/assets/vendor/` 保留代码头许可证与同目录说明 |
| Lucide | 描边工作台的 `LICENSE-lucide.txt` 保留 |
| RenderBlender 贴图 | 按原 LGPL-3.0 许可分发，保留 `LICENSE-RenderBlender.txt`、NOTICE 与来源哈希；不改称 MIT |
| Gradle wrapper | 仅必要 wrapper JAR 与相应 Apache-2.0 来源声明；不含 Minecraft/Forge 依赖缓存 |
| 水龙卷精选研究源码 | 原目录中每项目许可证、固定提交及逐文件哈希保留，分别遵循原许可/署名要求 |
| 五行仙途炼丹炉模型 | 用户项目的模型快照与审计供项目专用预览使用；来源见 MODEL_RANGE.md 和 model-audit.json，发布者应维护原模型权属及署名 |
| EnergyVFX 0.2.5 运行库 | 明确随 Skill 分发的原创生产 JAR 保留；ZIP CRC 与七份共用 GLSL 已核对，见 energy-runtime-package.json |
| Minecraft 原版护甲纹理 | 不分发。用户可按脚本从自己合法安装的 client.jar 提取 |
| 商业/影视参考视频 | 不分发原视频和抽帧参考；研究工作台允许显式选择本机有权使用的视频 |
| 其他下载的第三方整库/光影包 | 不分发。保留上游链接、固定提交、研究说明和核查元数据 |

## 此次收录

23 个渲染相关 Skill，32 个工作台/预览/研究入口，18 组开发源码目录，51 组五行仙途特效与性能报告。原有仓库内容保留。

排除 `.git`、`.gradle`、node_modules、build/run、日志、依赖缓存、个人游戏配置、QA 宿主 JAR、旧 ZIP 和重复历史工程快照。原始本地文件未改动；发布副本移除的原版纹理与本机桌面维护脚本按用户规则移入 `E:/垃圾/2026-10-14/着色器开源发布`。

历史记录的本机路径仅作为开发来源，不能当作其他用户需要存在的路径。完整公开文件校验清单见 [release-manifest.json](release-manifest.json)。

本次发布对少量既有文件规范化行尾空白，并移除本机 Windows 账户标识；历史报告描述原实验，不声称重新取得原始全部源字节认证。

## 2026-10-19 遁光补充

新增 `minecraft-dunguang-flight` V8研究Skill及浏览器原型。原创代码遵循仓库MIT，Kenney纹理保留CC0原许可与来源。商业参考视频、抽帧和五行仙途宿主源码不随包发布；历史本机证据路径仅供追溯，不是便携依赖。本次发布副本隐去Windows账户名，未改本机已安装Skill。Shadertoy访问受限的记录保留，未读取或复制作品源码，未确认作品许可。
