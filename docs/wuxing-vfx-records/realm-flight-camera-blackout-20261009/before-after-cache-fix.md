修复前后实际截图检查：Oculus + Embeddium 已安装，Solas 未启用。

两轮各 36 张截图、114 项旧版检查。修复前 FAIL，缓存修复后 PASS；两轮 GL 错误均为空，视角名称均与原版 F5 切换预期一致。

独立 worldCore 区域（避开左上 HUD）黑屏候选：修复前 32 / 36，修复后 0 / 36。

以下为 worldCore 亮度均值（0–255）及 RGB≤3 的近黑像素比例。两次运行时刻/相机插值会略有差异，因此没有宣称逐像素精确差分。

| 截图 | 视角 | 修复前亮度 / 近黑 | 修复后亮度 / 近黑 |
|---|---|---:|---:|
| 01-day-moving-first.png | FIRST_PERSON | 199.816 / 0.00% | 199.740 / 0.00% |
| 02-day-moving-back.png | THIRD_PERSON_BACK | 0.702 / 97.10% | 198.999 / 0.00% |
| 03-day-moving-front.png | THIRD_PERSON_FRONT | 0.508 / 99.57% | 196.743 / 0.00% |
| 04-day-moving-first-return.png | FIRST_PERSON | 0.478 / 99.88% | 201.725 / 0.00% |
| 15-day-back-cosmetic-off-24ticks.png | THIRD_PERSON_BACK | 0.037 / 99.92% | 186.702 / 0.00% |
| 17-day-back-bloom-off.png | THIRD_PERSON_BACK | 0.037 / 99.92% | 187.659 / 0.00% |
| 18-night-moving-front.png | THIRD_PERSON_FRONT | 0.037 / 99.92% | 146.615 / 0.00% |
| 19-night-moving-first.png | FIRST_PERSON | 0.144 / 99.88% | 148.534 / 0.00% |
| 20-night-moving-back.png | THIRD_PERSON_BACK | 0.037 / 99.92% | 156.262 / 0.00% |
| 21-night-hover-front.png | THIRD_PERSON_FRONT | 0.037 / 99.92% | 139.990 / 0.00% |
| 23-night-hover-back.png | THIRD_PERSON_BACK | 0.037 / 99.92% | 139.705 / 0.00% |
| 31-night-back-cosmetic-off-24ticks.png | THIRD_PERSON_BACK | 0.037 / 99.92% | 137.385 / 0.00% |
| 33-night-back-bloom-off.png | THIRD_PERSON_BACK | 0.037 / 99.92% | 139.140 / 0.00% |
| 34-night-no-gui-back.png | THIRD_PERSON_BACK | 141.190 / 0.00% | 141.135 / 0.00% |
| 35-night-no-gui-front.png | THIRD_PERSON_FRONT | 140.291 / 0.00% | 140.313 / 0.00% |
| 36-night-no-gui-first.png | FIRST_PERSON | 148.604 / 0.00% | 148.659 / 0.00% |

第一张修复前第一人称画面正常；首次第三人称背面与正面即黑屏，返回第一人称后继续黑。修复前关闭外观 24 tick、关闭 Bloom 均未解除已污染状态；hideGui 后各视角可见。缓存修复后这些同名对照均可见。

GL 无错误与 FBO 完整只能排除部分故障。缓存修复这轮尚未记录新增的 BlendMode 和 blend factors 同作用域断言；最终三条渲染路径将用加强后的 fixture 重新验证。

[未修图的原始截图对照页](before-after-cache-fix.html)。HTML 仅并排引用原 PNG，CSS 缩放显示，未创建、合成或修改游戏画面。
