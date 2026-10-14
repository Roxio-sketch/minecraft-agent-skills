# Cosmic 制作台 P2 / P3 处理记录

## 状态

| 问题 | 结果 |
|---|---|
| P2：重复端口、可能访问到其他服务 | 已修复并通过回归检查 |
| P3：验证脚本只在研究目录 | 已迁入 skill，研究入口只转发，不再维护两套实现 |
| P3：冻结时偶发 1/255 差异、根因不明 | 已复现并增强留证；根因仍未确认，保持开放 |

本次没有操作 ASTRA-TEST、替换模组 JAR 或运行 Minecraft 验收。处理对象为独立 WebGL 制作台及其随包验证。

## P2 的具体修复

- 默认端口从固定 8768 改为 0，由系统分配空闲端口，输出真实绑定的地址。
- Windows 显式 `SO_EXCLUSIVEADDRUSE`，关闭 `allow_reuse_address` 和 `allow_reuse_port`。
- 指定端口已占用时退出码 2，既不接管也不关闭已有服务。
- 健康端点返回应用标识、协议版本和四个资源文件的 SHA-256。验证脚本拒绝错误制作台、过期资源或不兼容协议。
- 验证默认自行启动动态端口临时服务，结束仅清理自己创建的服务和浏览器。显式指定 `--url` 时不关闭该外部服务。

服务回归 5 项通过：并行动态端口、第二监听者被拒绝、Windows 地址复用抢占被拒绝、占用端口返回失败、健康端点与资源响应。

服务身份反例 3 项通过：错误应用名、错误资源哈希、错误协议都在渲染前被拒绝。

## P3 的交付修复

维护入口在 [随包 validate_preview.cjs](skill/minecraft-cosmic-material/scripts/validate_preview.cjs)，随包提供冻结检查、服务支持模块及端口测试。它只读取 skill 内的资源，不读取旧研究截图或工程源文件；不硬编码本人的浏览器或 Node/Python 安装目录。

```powershell
python -B scripts/test_preview_server.py
node scripts/test_preview_validation.cjs
node scripts/validate_preview.cjs --python python --out ./validation-output
```

需使用已安装的 Python 3、Node.js 18+、Playwright 和 Chromium 浏览器；解释器不在 PATH 时传完整路径，Playwright 位于其他 Node 环境时设置 `NODE_PATH`。具体选项见 [操作说明](skill/minecraft-cosmic-material/references/authoring-workbench.md)。

每轮记录像素变化数、最大通道差、实际 shader 时间、DITHER、浏览器/GPU、资源哈希与采样目标。首次冻结失败保留前后 PNG 和完整输入。默认跨三配方、三画布进行 30 轮两类比较，共 180 对，仍以零差异为标准。

已从 `C:/Users/MAINTAINER` 工作目录直接运行安装后的脚本，使用安装包自身资源，不依赖研究目录输入；该独立跑次使用 3 轮冻结检查（18 对），[22 项全部通过](research/cosmic-material/authoring/review-fixes/installed-only/validation.json)。项目与安装副本的 24 个文件哈希一致，静态检查 12 项通过。这验证了脚本可随技能独立使用，**不抵消下面较长跑次中已记录的偶发失败**。

## 冻结偏差的排查证据

初始小型探针在原版、关闭 DITHER、规范化静态时间三类条件下未复现。完整套件随后在静态时间已经为 0、DITHER 已关闭时，捕获 87 个像素、最大通道差 1/255；因此不能把问题简单归为时间仍在推进或未关闭抖动。

后续对照：

| 试验 | 观察 |
|---|---|
| 自有 RGBA8 framebuffer | 完整套件仍出现首轮 87 像素偏差，不能据此归因于默认显示缓冲 |
| 修正负底数 pow(x,2) 为 x*x | 修复一处确定的 GLSL 未定义行为，但冻结偏差仍存在 |
| 解析式像素足迹替代导数 | 完整套件仍记录 68/72 像素的 1/255 偏差，没有消除问题 |
| 同帧连续读回、独立 RGBA32F 等小型探针 | 未稳定复现，未建立足够因果关系 |

未能解决问题的 framebuffer 与足迹替代已撤回正式 host；保留静态材质上传时间 0、DITHER 明确关闭、必要诊断，以及独立正确性缺陷的 pow 修正。没有把“消失一次”或“肉眼看不出”当作修复证据。

当前最终项目跑次 [final-strict/validation.json](research/cosmic-material/authoring/review-fixes/final-strict/validation.json) 为 **22 项中 21 项通过，zero_motion_is_static 失败，退出码 1**。这不是全部验收成功。失败详情及像素对照均位于该目录。

- [首次强化检查的失败记录](research/cosmic-material/authoring/review-fixes/run-1/freeze-failure.json)
- [最终冻结失败的输入与诊断](research/cosmic-material/authoring/review-fixes/final-strict/freeze-failure.json)
- [冻结前](research/cosmic-material/authoring/review-fixes/final-strict/freeze-before.png) / [冻结后](research/cosmic-material/authoring/review-fixes/final-strict/freeze-after.png)

底层数值偏差的具体原因仍待定位；本轮没有修改浏览器全局设置、显卡驱动或降低验收阈值。旧 Forge baked-item 实测结果与这个 WebGL 问题分开记录。
