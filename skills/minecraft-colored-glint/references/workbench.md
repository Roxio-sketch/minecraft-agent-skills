# 三维附魔流光工作台

入口：[assets/workbench/index.html](../assets/workbench/index.html)。先把工作台复制到用户的输出目录，再用标准库 HTTP 服务打开；ES modules 不支持直接以 `file://` 运行：

```powershell
python -m http.server 8798 --bind 127.0.0.1 --directory "<工作台目录>"
```

浏览器访问本机 8798 端口。脚本和 GLSL 随包提供，无外部脚本依赖；WebGL2 不可用或 GLSL 编译失败时显示具体错误。Minecraft 原版护甲贴图不随 Skill 分发，缺少贴图时显示采样底材，界面说明当前模型依据。

## 新版方向：灵力御体的层次设计

默认 `guard-wandering-dragon`（style 10，游龙护纹）使用六缕全周护纹：两组各三条、反向缠绕交叉，但两组都向上移动；错峰亮段后接下方柔尾。光纹在装备共用的圆柱角度与归一化高度上生成，正面、两侧和背面均有路径，交叉处取最大亮度以避免叠加过曝。`guard-woven-tide`（style 9，灵潮织甲）在主水光外增加错峰曲线细流与肩侧短芒。`guard-gathered-light`（style 11，凝光护甲）使用三段偏心短弧和两枚接力节点，受击时局部短弧向命中点汇聚。水光御体（style 8）保留为清淡对照；四种方案同屏选卡，切换后自动开始播放。

`guard-variants.js` 导出 `RICH_PRESETS` 和 `GUARD_VARIANTS_GLSL`，对应的 `guard-variants.glsl` 是内核导出文本，须在 `guard-look.glsl` 后使用。`guardRichSheen(style, normalizedEquipmentPosition, seconds)` 返回持续光流；`guardRichResponse(style, originalPosition, normal, hitPosition, hitAge)` **已经包含**基础 `guardImpact` 波纹，不要再叠加一次。持续光流统一由 `seconds × uSpeed` 驱动，受击生命周期独立使用 `hitAge`。

`guard-water-sheen`（style 8）在原护甲表面叠加一小片淡蓝光流，亮头短、尾迹柔软，只有极少移动微芒。主要颜色 `#91CBF5`，高光 `#D5F3FF`。所有新版方向均保持多数区域的原装备外观，不改人物皮肤材质，不生成包裹人物的外壳。

`guard-look.js` 导出 `APPROVED_PRESET` 与 `GUARD_LOOK_GLSL`；`guard-look.glsl` 是该内核的导出文本。其中 `guardWaterSheen(localPosition, seconds)` 接收**所有装备部件共用的局部坐标**，不使用每面 UV、屏幕位置或相机位置来生成光流。移动坐标来自时间乘速度，速度零冻结完整光纹。工作台按每个 Minecraft 模型像素 0.08 单位构造样件，并按整件装备边界将 Y 轴中心置零、高度缩放至 1.12；所有甲片使用同一个 `uSheenFrame`，受击波纹仍使用未缩放坐标。游戏适配时固定静态装备尺度，避免每帧随动作改变范围。

`guardSurfaceLight(baseRadiance, sheen)` 共用预览的亮度合成，保持稀疏光流的蓝色核心；波纹随后局部叠加。不能仅复制颜色 uniform 就认为已复刻样稿。

`guardImpact(localPosition, localNormal, hitPosition, hitAge)` 在受击点附近产生短暂扩散环，生命周期为 0.8 秒。工作台用各样件的固定受击点，依据点的 Z 正负选择正面或背面法线，再用表面法线与距离限制波纹。当前没有任意表面点击拾取或任意命中法线输入；侧面、曲面和复杂装甲的命中不能直接套用这个简化假设。

旧八个预设保留在折叠的对照区：御体巡流、柔光护衣、护甲细丝、交错护纹、守护脉冲、灵纹流转、冰蓝双流、灵光微芒。它们是可编辑起点，与新版水光分开。旧光纹共享 `patterns.js` 的 `glintColor(uv,timeSeconds)`；`patterns.glsl` 为该内核的导出文本，修改内核后同步导出，避免分叉。

## 本机护甲贴图

支持铁甲、钻石甲、下界合金甲和金甲的 layer 1/2。使用 [extract_armor_textures.py](../scripts/extract_armor_textures.py) 从用户指定的 Minecraft Java **1.20.1 client.jar** 本机提取，不从网络下载，也不将这些贴图打包进 Skill：

```powershell
python "<skill>/scripts/extract_armor_textures.py" --client-jar "<用户的 1.20.1 client.jar>" --output "<工作台目录>"
```

脚本只读取八个固定 ZIP 成员，先验证全部文件存在、版本标识与 PNG 完整性，再写入 `<工作台目录>/textures/`，文件名为 `iron_1.png` 至 `gold_2.png`。`source.json` 记录本机源归档、成员名称、尺寸和 SHA-256，便于追溯。

默认拒绝所有同名目标；`--overwrite` 只允许替换有本脚本来源清单且哈希仍匹配的旧提取文件。修改过的贴图、无来源清单的同名文件或无关 `source.json` 均拒绝覆盖；改用新输出目录即可。不会使用 ZIP 成员路径直接解压，也不会把写入目标重定向到输出目录外。

当前加载逻辑要求八张本机贴图全部可用，否则整体回退至采样底材。长剑仍是程序样件，不采样护甲贴图。护甲样件按原版 HumanoidModel 方盒与 UV 构造，没有动画骨骼或玩家皮肤；它用于设计比较，不等同于游戏实机截图。

## 配方契约

支持两侧原装备对照、拖动旋转、正面/右侧/背面/左侧四向按钮、六字段调节、双色、受击波纹、播放暂停、回放时间轴、流光和受击两组三帧分镜、JSON 导入导出、PNG 对比图；完整 JSON 同时恢复预设、参数和视角。

游龙护纹的 `scale` 控制绕行密度（螺距），`angle` 控制环绕起角，`secondary` 控制另一组交叉护纹的相对亮度；默认始终保留六缕细光。归一化高度随 `seconds × speed × 0.20` 向上平移，因此零速会冻结整套路径和亮段；旋转模型不会改变装备上的光纹位置。

schema 为 `spirit-guard-glint/1`，字段为 `preset`、`parameters`、`scene`。`preset` 为包内有效 ID；`parameters` 包含 `style`（0..11，且与 preset 匹配）、`color/color2`（六位 HEX）、`intensity`（0..2）、`speed`（0..2）、`scale`（0.4..3）、`width`（0.03..0.4）、`angle`（-90..90）、`secondary`（0..1）。`scene` 含 `shape`、`material`、`yaw`、`pitch`、`orbit`；护甲部件含胸甲、头盔、靴子、护腿、全身样件。拒绝未知字段、非有限数字和不合法范围；失败时保留当前状态。旧 `spirit-guard-glint-preview/1` 与 `/2` 的颜色、亮度、速度、尺度可迁移到旧巡流预设。

配方与既有 `author_glint.py` 的 `version:1` 配色格式分开。工作台 JSON 目前不是游戏模组的自动加载入口；普通 solid 配色仍使用既有生成器。

## 接入与验证边界

新版水光和局部受击波纹目前是**工作台设计样稿**，没有修改游戏模组，也没有通过 Minecraft 穿戴验收。旧八种程序光纹也不能继承 Colored Glint Lab 九色／渐变的游戏运行证据。

游戏接入需要把真实 ModelPart 变换后的顶点汇入一个稳定的装备坐标，传递同一坐标系下的表面法线、命中点及时间，匹配实际顶点格式、uniform 和绘制入口；每件装备独立恢复渲染状态。工作台的独立表面绘制不能证明 Minecraft 中的深度遮挡、透明分支或与原版附魔层共存已正确。

穿戴动作、左右臂运动、蹲伏、游泳、第三方自定义护甲、装备变换和任意命中方向均未在本工作台中验收。扩展命中方向时应传真实命中法线，而不是继续依据 Z 正负推断。游戏实装也需按真实护甲管线决定混合和深度策略，不能把工作台的采样光照视为 Minecraft 的光照结果。

2026-10-12 的旧工作台曾验证八种预设两个固定时刻有不同光纹，基础对照不变、速度零冻结、JSON 恢复、无效参数拒绝，以及当时五种简化样件无 GL 错误。这些证据针对旧渲染器；新版护甲 UV、局部坐标与水光的结果应重新记录，不能沿用为新版成功证明。

2026-10-13 新版已完成本机浏览器验证：27 帧完整周期中原装备对照零像素变化、流光位置移动；铁甲、钻石甲、金甲也保留原装备对照。速度零冻结；受击 0.15/0.35 秒有局部波纹，0.9 秒与同刻无受击画面相同；正面受击在背面观察时没有波纹；JSON 导出再导入内容一致。护甲五种样件和示意长剑无 GL 错误。证据保存在创作项目 `spirit-guard-glint-lab/evidence/water-sheen-20261013/validation.json`，不随 Skill 携带本机原版纹理截图；这些结果均是工作台级验证。

同日三种层次方案另完成 60 帧实际截图验收：各自光流移动、原装备对照零变化、零速冻结、受击 0.35 秒可见且 0.9 秒消散；铁甲和钻石甲动态通过；正面受击的波纹和附加回应在背面为零；三种新配方导入再导出内容一致；无 GL 错误。证据与真实动态对照在 `spirit-guard-glint-lab/evidence/layered-glint-20261013/`，仍不代表游戏穿戴或世界遮挡验收。

游龙护纹全周上行改版另记录四个固定方向各 12 帧、共 48 帧：每个方向均有光纹和移动，原装备对照零变化；平面甲片的光层匹配显示，0.25 秒后光纹上移约 12 屏幕像素。零速冻结、受击 0.35 秒可见/0.9 秒消散、背面无正面受击波纹、铁甲/钻石甲动态通过，无 GL 错误；全身甲、头盔、靴子、护腿和示意长剑另作编译与绘制检查。证据见创作项目 `spirit-guard-glint-lab/evidence/dragon-wrap-20261013/`。这是浏览器工作台验证，未验证游戏穿戴。
