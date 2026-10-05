# 世界透镜、相机与前景回归

使用独立工作副本与专用存档。先验证 shader 确实加载、pass 次序/主 FBO/深度和 stencil 一致，再判外观；build 成功或 JSON center 不变不代表画面中心不漂移。

## 可直接复跑的检查

工程根目录：

```powershell
python scripts/audit_resources.py
./gradlew.bat --offline --console plain blackHoleCheck build
node scripts/validate_lens_geometry.cjs --output evidence/lens-math.json
node scripts/check_shader_interfaces.cjs --browser "C:/path/to/chrome.exe" --output evidence/interfaces.json
node scripts/validate_disk_motion.cjs --browser "C:/path/to/chrome.exe" --output evidence/disk
node scripts/validate_particle_motion.cjs --browser "C:/path/to/chrome.exe" --output evidence/particles
```

Java 检查包含180组不同FOV/远裁面/bob/hurt/portal/off-axis/仿射view的独立眼点和固定锚点 oracle。角向脚本是CPU模型，接口/运动脚本是WebGL2，均不代替桌面OpenGL/Forge。

可在工程先创建 evidence 目录，然后 `blackHoleCheck -PrecipeEvidence=evidence/forge-recipe.json` 写有类型的测试世界配方。从 skill 根运行 `node scripts/validate_mature_defaults.cjs --browser <浏览器> --world-recipe <工程/evidence/forge-recipe.json> --output <证据目录>`，检查7个水平预设、明确角度、world-only字段保留、共用编辑和非法metadata拒绝，生成 world-roundtrip.json。工程再跑 `blackHoleCheck -PrecipeEvidence=<配方路径> -PrecipeRoundtrip=<world-roundtrip.json>` 完成 Forge→工作台→Forge 验证。工作台导入从暂停开始，重导时间/播放状态以当前预览状态为准。

```powershell
./gradlew.bat --offline --console plain runClient -PblackHoleGroundRegression=true
python scripts/accept_ground_preview.py --source run/black-hole-lab/world-probes --out evidence/ground
```

flag 默认关闭。开启后仅创建/进入 `BlackHoleLab-Ground-Placement-Check`，搭独立棋盘地面；从真实向下 world 放置**一次**，之后仅移动观察者。首次 Accessibility 引导也仅在此专用模式跳过。六视点自动捕获并退出这个开发进程，不能对用户运行实例使用退出桥。Python需NumPy/Pillow，验收读取fixture的实际地形VP/raw camera，用独立球相交预测PNG边界，再测真实黑核心边界的双向距离，不拿renderer projectedCenter当oracle。允许1.5px抗锯齿容差，禁止只检查剩下半球的单向边界。

## 强度与360°地面

固定seed/time、中心/scale/core/相机，分别取 lensStrength=0/0.35/0.7/1 与 bend=0/1/2。隔离盘 density=0、halo=0、particles=false，以棋盘/建筑边缘为背景。俯视/平视/垂直正视都应围绕质量轴连续，地面不能只半圈受影响。比较 `field-no-lens`（真实场景角向映射）而非 field-before（还包含核心/盘/光晕）。范围保持独立，检查圈外回归；屏外源平滑回退允许局部弱化，但不能用它解释画幅中央整体消失。

常见错误：表面前后mass-plane筛选误用于地面；有限表面深度减弱偏折；屏幕分辨率/长宽比直接改变角度；只看水平而遗漏俯视；曝光调高伪装成透镜强度增加。源码采用角向映射而非物理GR，验收应称美术环境透镜。

## 玩家参与与碎片

在独立场景中只保留一个可见测试演员，固定camera/pose/装备、seed/time。分别取站立、潜行、游泳/滑翔、手持装备、前方/跨质量平面/后方的同帧 off/on 对照。文件桥示例：

```json
{"action":"settings","lens":0.7,"lensAffectsPlayer":false,"time":1.7,"particles":false,"halo":0}
```

读回执后用 `{"action":"probe","name":"player-off"}` 取证；切 true 再取 player-on。before原场景不能因移动/姿态改变而与另一组失配。off 必须保护**完整模型**与手臂/装备；on 的 playerLensActive=true 且实际演员像素必须变化，前方/跨平面也不能被whole-behind规则否决。非玩家前景规则仍按整实体分类。

probe包含 actualEntityDepthPixels / actualEntityDepthChangedPixels / actualEntityDepthLensChangedPixels，由原场景深度减实体前环境深度识别真实覆盖；独立单演员场景能检测漏保护的 limb。再查 source-kind/source-coverage，分辨真正参与与背景空白的变化。开启后的多重像、缩放、扭曲是选定美术映射的预期行为；关闭后的碎肢/裂片、身体跨plane被一刀切、采样源假重影是bug。

## 定位与状态

地面放置后左右/前后移动、bob on/off、sprint、hurt、第三人称碰撞/旋转都需实际PNG固定球oracle。相机点和方向分别转换；盘/轨道沿世界轴，不能跟着view旋转。inverse(view)眼点遗漏projection的bob平移，raw camera也不能替代光学眼点。

另检查资源重载、resize/world切换/off、首次启动、主目标stencil开/关、云层、有实体前景/晚透明层。同帧 before/field/after 深度差0、GL error0；失去主深度/替代FBO时按adapter规则退化。状态恢复需验证后续原版物品/粒子/GUI，不把无GL error当完整状态认证。

2026-10-05 此模板已通过fresh Forge六视点核心定位，最大轮廓误差0px。上述完整active lens/player/particle/state matrix 是复用验收要求；尚未全部在新独立模板实机跑完，不能引用生产mod或历史旧JAR证据替代。
