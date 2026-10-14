# 素材、颜色与流动设计

## 已读取素材

`assets/glint/` 含 RenderBlender 原始 `glint_item_*.png`。九个 ID 为 red、yellow、blue、orange、green、purple、white、pink、aqua；全部 128×128。原 PNG 模式混有 RGB、RGBA 与索引 P，统一解码为 RGBA 后 alpha 均为 255。详细通道范围、尺寸和校验值见 [source-manifest.json](source-manifest.json)。

这些纹理的强度和形态存储于 RGB。没有 alpha 缺口并非错误；在原 glint 混合方式下应观察实际结果，不能强行把深色像素抠成透明后当成等价素材。white 是独立白色纹理，原始灰度范围 28–170，不是 `default` 的同义词。

按用户实际需要复制选中的纹理到：

```text
src/main/resources/assets/<目标命名空间>/textures/misc/glint_item_<颜色>.png
```

用于 `TextureStateShard` 的资源地址形如 `<目标命名空间>:textures/misc/glint_item_red.png`，包含 `.png`。不要误用模型纹理的简写路径规则。保留 `assets/NOTICE.md` 中的来源及原许可；技能内未修改这些原图。

## 生成新颜色

用户要自然语言配色、指定色标、多色渐变或随时间变色时，优先使用 [配色创作](authoring.md) 的 `author_glint.py`；下面的 `recolor_glint.py` 保留为批量单色工具。两者都允许任意合法色值，不局限于原九色。

需要 Python 与 Pillow；复用现有图片时不必运行脚本。下例中 `$SKILL_DIR` 是当前技能目录，`$OUTPUT_DIR` 是任务输出目录，两者由实际环境确定。在 PowerShell 中用以下形式：

```powershell
python "$SKILL_DIR/scripts/recolor_glint.py" `
  --source "$SKILL_DIR/assets/glint/glint_item_white.png" `
  --output-dir "$OUTPUT_DIR" `
  --color 'crimson=#FF3058' --color 'ice=#42CFFF' --strength 1.0
```

若环境的 `python` 不可用，使用已存在的有效 Python 可执行文件；不把某个人的安装路径写入技能。输出名为 `glint_item_crimson.png` 等。名称限小写资源安全字符，颜色必须为 6 位 RGB。默认拒绝覆盖已有文件；确实需要替换时使用 `--overwrite`。

脚本采用 `intensity=max(r,g,b)/255`，输出 `round(255*intensity*targetRGB*strength)` 后截取到字节范围，逐像素保留 alpha 与原尺寸。`targetRGB` 归一化到 0–1。这样能保留灰度源的纹理轮廓，也能从已有色图提取强度；它不承诺不同色相具有完全一致的感知亮度，不会自动归一化到纯白或给深色背景加遮罩。可用 strength 调整强度，最终在游戏里检查曝光。

若输入旁有同名 `.png.mcmeta`，脚本先验证其为 JSON 对象，并将其原样复制给每个输出；尺寸不改变，因此已有帧排列得以保留。没有侧车文件时不会凭空生成动画声明。它处理 PNG 像素/侧车，不负责实现物品选色或修改 Java。

**侧车保留不等于动画实现。**图集 sprite 动画与 `TextureStateShard` 加载的独立 glint 纹理不是同一条路径；仅复制 `.png.mcmeta` 不保证后者轮播帧，竖向帧图也可能整张采样。新创作工具拒绝 APNG 与带 `animation` 的载体；时间变色通过 shader 和 draw 时钟实现。若目标已有独立帧纹理更新器，应另外验证该路径。

## 流动与扩展

固定色流光沿用目标模式的 glint 纹理矩阵与时间源。单张 PNG 不必携带动画帧就能流动；修改图片颜色不应同时意外改变速度、倾斜和尺度。参考 `GLINT_TEXTURING` 与 `ENTITY_GLINT_TEXTURING` 是不同模式，应保留其原有契约。

| 需求 | 合适的起点 | 额外工作 |
|---|---|---|
| 不同物品分别红、蓝、紫、绿 | 多 PNG + 每模式缓存层 | 物品解析和对应 foil hook |
| 同一物品随时间循环色相 | 自定义 glint shader 或受控 draw 参数 | shader 生命周期、色相公式、时间/暂停策略及批次隔离 |
| 空间彩虹/多色条带 | 渐变纹理配现有 UV，或 shader 颜色函数 | 定义坐标与滚动方式，核对纹理接缝 |
| 局部附魔光 | 模型区域过滤或明确的 mask 接口 | 说明 mask 与 glint UV 是否不同、独立 sampler 和几何覆盖 |
| 指定速度和方向 | 自定义纹理状态或 shader 参数 | draw 后复位矩阵，保留暂停与重载行为 |

新工具已提供单色、空间、时间和流动渐变的创作资源；它们不是九张原贴图自带的功能，也不会自动注册进模组。按 [渐变接入](gradient-integration.md) 完成 ID、shader 与缓冲适配。图集里的 Cosmic 星体贴图、mask 和独立 glint 纹理承担不同职责，不要互换 sampler 或假设它们共享 UV。连续色相、强度和速度应按用户选择设计，不把示例颜色方案强加给项目。
