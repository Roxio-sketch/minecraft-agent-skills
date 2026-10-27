# 本机可复用工程（2026-10-12）

使用前确认存在；这些是实例位置，不能作为新项目的固定路径。

- 剪辑：`E:\视频\shader-showcase`，Remotion 4.0.534。
- 成片：`E:\视频\exports`；黑洞白洞 246 秒，闪电雷劫 195 秒，1920×1080 / 30fps。
- `src/BlackHoleVideo.tsx`、`LightningVideo.tsx` 是时间轴，`BackgroundMusic.tsx` 选择最终 `public/audio/*-mambo-flow-v2.m4a`。
- `scripts/prepare-narration.mjs`、`mambo-narration.py`、`finalize-narration.py` 负责该实例的配音安排与混合；先阅读再复用，含固定项目 / 引擎路径。
- `scripts/render-videos.ps1` 支持 `-NoNarration`，需有 logs 目录；录制与视频已完成时勿重复整片渲染。
- `scripts/check-final-exports.mjs`、`check-voiced-exports.py` 是该实例的详细验收；通用技术检查使用本 Skill 的 `inspect_video.py`。
- 本机 Node：`E:\nodejs\node.exe`；FFmpeg / FFprobe 在 `E:\JianyingAI\tools`。
- `CONTENT.md` 是早期制作稿，部分时长和配音状态已过时；以实际 Composition、成片、`exports/视频说明.md` 与当前检查报告为准。

## 抖音竖版复用

`src/PortraitVideo.tsx` 和 `src/portrait-plan.json` 注册 `DouyinBlackHole` / `DouyinLightning`：1080×1920，分别 72 / 65 秒；对应 `DouyinBlackHoleCover` / `DouyinLightningCover` 是竖版封面。产物在 `exports/抖音竖版`。

`scripts/portrait-narration.py` 在已选 Mambo 服务上生成独立的原速短版旁白、时间轴与音乐混合，不覆盖横版音轨。`scripts/check-portrait-speech.py` 使用独立 ASR；`portrait-review.py` 按章节、形态变化和 UI 段抽帧检查。

竖版将真实 UI 与效果上下重排，对照使用上下两个画面；不要仅用 object-fit 把横版主体截掉。字幕按短句和原始旁白时长切换，实际转写 / 听取结果更精确。这里的黑洞片是宽盘面，闪电片是细长分叉，裁切方案按素材区别处理。

## 独立 Minecraft 录制副本

`E:\视频\capture-mod` 是 Forge 1.20.1 / Java17 源码。`src/main/java/dev/vfxworkbench/client/VideoCaptureDirector.java` 是 opt-in 调度器：`vfxworkbench.videoCapture` 启用，`vfxworkbench.videoShots` 选择已有 shot ID；Gradle 以 `-PvideoCapture=true` 和 `-PvideoShots=...` 传入。查阅源文件中的选择逻辑后运行，不能猜测 ID 或分隔规则。

录制器写入 `E:/视频/shader-showcase/public/clips`，专用存档 `Video-BlackHole-Lightning-20261011`；输出路径和存档名目前仍写在源码中，新项目应改成配置后再执行。参数快照在 `capture-manifest.json`。它捕获实际 framebuffer，并有真实 UI 控件适配。缓存 / build 已清理，下一次运行会重新构建；专用存档、mods、配置与源码保留。

其他实例可先使用已安装的 `axolotl-launcher` 查找启动；该录制桥只服务这份演示 Mod，不假设任意 Mod 都具备这些接口。

## 配音

`E:\MamboTTS` 的 GPT-SoVITS 服务地址 `http://127.0.0.1:9880`，参考音频 `models/refer.wav`。服务完成任务后已停止，调用前检查可用性。使用服务的请求 / 响应约定以项目脚本和实际引擎为准。

用户最终选择保留曼波社区预设；普通男声 / 女声仅试听。该社区预设未确认与剪映“曼波讲故事”完全相同。`exports/配音准备` 有独立 WAV、SRT、成稿和来源记录。用户新选择优先，不因这个实例而为其他用户强制曼波。

## 当前发布信息

真实项目：`https://github.com/Roxio-sketch/minecraft-agent-skills`。下载 / Release 链接沿用每条 `*-标题与简介.txt`，对外更新时核验。

黑洞与闪电配乐分别为 Scott Buckley 的 The Spaces Between / Helios，原发布文案含 CC BY 4.0 署名。音乐不随技能打包，后续视频使用前复核来源与许可。
