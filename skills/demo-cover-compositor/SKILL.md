---
name: demo-cover-compositor
description: 为游戏 Mod、开源项目和 Skill 介绍制作真实截图拼接封面；用于录像选帧、多形态对照、调参 UI 与大字排版，不负责完整视频剪辑。
---

# 演示封面拼接

用已有录像和截图表达作品的真实外观与可自定义性。优先拼接；没有清楚的素材时补拍或指出缺失素材。生成图片只在用户明确选择该方式时使用，不用虚构效果替代实机演示。

## 开始

- 找到成片、原始录像、干净截图、当前封面工程及发布标题。保留用户已有修改。
- 先看实图再选素材。瞬时闪电等效果不能默认取第一帧；用 `scripts/extract_frames.py` 在多个时点选帧，查看接触表和原尺寸图。
- “自定义”要用可区分的实际配色、形状、材质或数量对照支撑。按画面容纳能力选素材数量，不机械要求四宫格。
- 裁掉无关界面。需要表达调参能力时单独裁出真实参数 UI；不得把后期绘制的滑块说成软件原界面。

## 个人默认风格

以下是这位用户已确认的起点，用户新要求优先：

- 1920×1080 PNG 和 JPG；主体真实截图拼接，避免 PPT 卡片、参数列表和夸张口号。
- 主题大字使用亮黄 `#FFEE00` 加黑描边，字号大于白色副标题。
- 两行均以整张封面的水平中心为准，即使右侧有 UI；文字层放在 UI 之上，保留明显行距。
- 副标题简洁表达“开源 Mod · Skill”，按实际项目调整；不添加不适用的关键词。
- 需要时右侧加入真实自定义 UI。默认不加 GPT 角标；标题中的 GPT 表述与封面角标分开处理。
- 字体、数量和位置按主题调整，避免文字完全挡住效果核心。相同色的相似截图不等于多形态展示。

## 制作与交付

1. 选择清晰且有区别的素材，记录来源和录像时间点。补拍时调用现有录制方式，保持清晰度与素材真实性。
2. 在现有 Remotion 封面组件里修改，或使用 `assets/CoverTemplate.tsx` 和 `assets/cover-entry.tsx`。模板使用 1920×1080 逻辑坐标；每个 shot 的 `box` 是裁剪窗口，`image` 是窗口内图像位置和尺寸。UI 也作为一个 shot，位于文字下层。
3. 参考 `assets/blackhole.example.json` / `assets/lightning.example.json`；它们引用本机示例工程的 public 资源，不包含截图。其他项目必须换成自己的真实素材。
4. 运行 `scripts/render_cover.py` 输出 PNG、JPG 和 320×180 缩略图。脚本需要已有 Remotion 工程、Node 与 FFmpeg，不自动安装依赖。
5. 查看原尺寸和缩略图，检查主次、整图居中、行距、裁切和效果可辨识度。程序成功只证明导出成功，不能代替视觉检查。
6. 用绝对路径展示最新封面，注明最终文件位置；封面修改不触发完整视频重渲染。

本机复用时只读 [references/local-project.md](references/local-project.md) 来定位工具、素材和最终组件。

## 示例命令

把两个 TSX 文件复制到项目 `src/skill-cover/`，再将适配好的 JSON 保存为 `cover-props.json`：

```powershell
python -X utf8 scripts/render_cover.py --project 'E:\视频\shader-showcase' --entry src/skill-cover/cover-entry.tsx --props cover-props.json --output 'E:\视频\exports\新封面' --node 'E:\nodejs\node.exe' --ffmpeg 'E:\JianyingAI\tools\ffmpeg.exe'
```

命令中的脚本路径相对于本 Skill 目录；输出已存在时仅在用户要求更新该封面的范围内加 `--overwrite`。
