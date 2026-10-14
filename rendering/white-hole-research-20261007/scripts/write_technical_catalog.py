"""Build the researched resource cards from verified bounded-file evidence."""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[1]
verification = json.loads((OUT / "technical-verification.json").read_text("utf-8"))
evidence = verification["files"]
commits = verification["fixedCommits"]

def files(keys):
    return [{"id": key, **evidence[key]} for key in keys]

cards = [
    {
        "id": "tech-psrdnoise3", "title": "PSRDNoise 3D：旋转梯度、解析导数与周期噪声",
        "url": "https://github.com/stegu/psrdnoise", "category": "算法与源码", "tags": ["流动", "三维噪声", "解析导数", "粒子", "MIT"],
        "summary": "优先于只会整体平移的普通 fBm：3D 梯度会随 alpha 旋转，函数同时返回一阶解析导数，可用于扭卷纹理、速度场及密度细节。",
        "useFor": ["白洞喷出粒子的卷曲扰动", "排出盘、放射细丝与体积羽流密度", "避免 atan 环形接缝的周期域"],
        "authors": ["Stefan Gustavson", "Ian McEwan"], "license": "MIT（GLSL；README 与文件头明确；docs 目录另为 public domain，不混用范围）",
        "licenseUrl": "https://github.com/stegu/psrdnoise/blob/419175a270862ce7ae692038fafafb42ec0427e9/src/psrdnoise3.glsl#L8",
        "priority": "P0", "integrationCost": "低：独立 GLSL150 函数；CPU 粒子需实现等价导数或预采样速度场。",
        "fixedCommit": commits["stegu/psrdnoise"], "files": files(["psrd-readme", "psrd3"]),
        "keySymbols": ["psrdnoise(vec3 x, vec3 period, float alpha, out vec3 gradient)", "gradient", "FASTROTATION", "PERLINGRID"],
        "migrationNotes": ["直接采用 src/psrdnoise3.glsl 的 desktop GLSL 子集；命名加前缀，避免与黑洞现有 permute/snoise 冲突。", "梯度在缩放或域变换后要乘链式法则；旋转梯度 alpha 是纹理演化参数，不等于完整流体平流求解。", "默认坐标是有限效果相对中心的世界位置；不能按相机位置采噪而让纹理跟玩家移动。", "周期使用源注释规定的整数范围，普通轴对齐网格最大 289；PERLINGRID 则要求 3 的倍数且不超过 288。"],
        "verificationStatus": "固定 commit 的源码和许可已读取；未编译/游戏测试",
        "readScope": "完整 README/GLSL 在内存核查，保存带行号的受限源码摘录；未下载示例素材。",
        "supplementary": [{"title": "普通 GLSL simplex：webgl-noise", "url": "https://github.com/stegu/webgl-noise", "fixedCommit": commits["stegu/webgl-noise"], "license": "MIT", "files": files(["noise-license", "noise3"]), "reason": "轻量底噪备选；不提供 PSRDNoise3 的旋转梯度与解析导数，不能混称。"}],
    },
    {
        "id": "tech-curlnoise", "title": "Bridson 等：Curl-Noise for Procedural Fluid Flow（SIGGRAPH 2007）",
        "url": "https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf", "category": "论文与算法", "tags": ["速度场", "涡流", "粒子", "作者主页"],
        "summary": "用向量势的旋度建立涡卷速度场，减少一般噪声速度场的粒子聚集；论文同时讨论边界与幅度调制。适合给向外排出的流增加切向卷曲。",
        "useFor": ["球面外喷尘粒", "喷流扰动与湍动", "受控排出盘粒子"],
        "authors": ["Robert Bridson", "Jim Hourihan", "Marcus Nordenstam"],
        "license": "作者主页明确把 curl-noise example code 标为 public domain；论文 PDF 本身不因此成为 public-domain 源码/素材。",
        "licenseUrl": "https://www.cs.ubc.ca/~rbridson/", "priority": "P0", "integrationCost": "中：需要固定 tick 粒子积分、curl 的空间导数与速度限制。",
        "fixedCommit": None, "files": files(["bridson-author", "bridson-paper"]),
        "keySymbols": ["v = curl(A)", "divergence-free velocity", "spatial modulation", "solid boundaries"],
        "migrationNotes": ["基于三个 PSRDNoise 标量及其解析导数组装 curl；粒子用 Java 固定 tick/Midpoint-RK2，画面用 partialTick 插值。", "向外径向速度单独设计，再叠加幅度受限 curl；这样是美术化排放，不声称总场严格不可压。", "把 curl 结果任意乘空间 envelope 通常会破坏 divergence-free；要严格保留应在向量势层处理 envelope 与边界。", "源头按固定 seed 从壳面出生，近核限速、远端淡出；不得反转全部历史粒子导致重生闪跳。"],
        "verificationStatus": "作者页 HTTP200，PDF 3 页文字已解析；未下载 example archive/进行数值实验",
        "readScope": "读取作者许可声明及论文文字，仅保存少量核查摘录；没有抓取视频或图片。",
    },
    {
        "id": "tech-domain-warp", "title": "Inigo Quilez：Domain Warping / fBm",
        "url": "https://iquilezles.org/articles/warp/", "category": "论文与算法", "tags": ["域扭曲", "云丝", "排出盘", "学习资料"],
        "summary": "将密度域 p 先做可控扭卷再采 fBm，形成连续云丝与流线。适合排出盘和羽流外壳，不能把二维纹理滚动当作三维粒子运动。",
        "useFor": ["排出盘细丝", "白色核心外的彩色喷涌云", "径向平流纹理"],
        "authors": ["Inigo Quilez"], "license": "该文章未核实可直接复用代码的开放许可证；仅学习算法，原创实现，不复制文章/ShaderToy 样例。",
        "licenseUrl": None, "priority": "P1", "integrationCost": "低到中：原有黑洞盘密度改采样坐标即可，但要控制多层噪声成本。",
        "fixedCommit": None, "files": files(["iq-warp"]),
        "keySymbols": ["f(p + h(p))", "fBm", "nested domain warping"],
        "migrationNotes": ["噪声实现优先用有明确 MIT 许可的 PSRDNoise，域变换、配色和支撑包络自行写。", "先在三维效果局部坐标建立球/盘/圆锥支撑，再扭曲内部密度；不要把整个边界无限扭歪。", "径向排出使用出生半径/年龄或明确平流坐标；角向旋转独立，不以把 time 改负值覆盖所有层。", "建议首版 2–3 octaves 与单层 warp，后续通过 GPU 数据再增加。"],
        "verificationStatus": "作者文章 HTTP200，算法与示例文字已读；代码许可证未确定",
        "readScope": "文章文字在内存审读，保存不到 100 words 的短摘要摘录；不下载图像或视频。",
    },
    {
        "id": "tech-pbrt-volume", "title": "PBRT 第 4 版：体积发射、吸收与透射",
        "url": "https://pbr-book.org/4ed/Volume_Scattering/Volume_Scattering_Processes", "category": "论文与算法", "tags": ["体积", "emission", "absorption", "Beer", "Apache-2.0"],
        "summary": "以发射和消光分开的辐射传输为底座，避免体积亮度随 raymarch 步数变化。PBRT 是离线 C++ 渲染器，应该迁移关系与验证方法，而不是接入其整套积分器。",
        "useFor": ["白洞有限厚度发光壳", "排出盘/双锥羽流", "镜头进入体积时仍成立的合成"],
        "authors": ["Matt Pharr", "Wenzel Jakob", "Greg Humphreys", "pbrt-v4 contributors"],
        "license": "pbrt-v4 源码 Apache-2.0；在线书章和插图的复制权不由源码许可自动覆盖。",
        "licenseUrl": "https://github.com/mmp/pbrt-v4/blob/b4ce9687e6c695f5582997c61b0c66cf064bdb4a/LICENSE.txt",
        "priority": "P0", "integrationCost": "中到高：原创 GLSL150 有限域射线积分+深度截断；先 emission/absorption，再选择加散射。",
        "fixedCommit": commits["mmp/pbrt-v4"], "files": files(["pbrt-license", "pbrt-media", "pbrt-transmittance", "pbrt-scattering"]),
        "keySymbols": ["HomogeneousMedium", "GridMedium", "MediumProperties", "IsEmissive", "SamplePoint", "T_maj *= FastExp(...)"],
        "migrationNotes": ["使用前向 premultiplied L/T：scene*T+L；每段 Tseg=exp(-sigmaT*ds)，发射用该段解析权重而非固定每步加色。", "有限球体/双锥先相交，再用真实场景最近不透明深度缩短积分，避免穿墙；相机在域内时起点 clamp 为 0。", "第一版不迁移 PBRT 的 spectral sampling、NanoVDB、null-collision Monte Carlo；这些不适合直接进当前实时 GLSL150 管线。", "复用本机 minecraft-volumetric-environment 的有限区域、L/T 和深度合同；不要因此声称其模板已有白洞或多区域管理。"],
        "verificationStatus": "书章和固定 commit 源码/Apache 许可 HTTP200；未移植或进行性能测试",
        "readScope": "介质相关符号与发射/消光路径核查，保存受限代码/书章摘录；非通读整个 PBRT，不抓取书中图像。",
    },
    {
        "id": "tech-three-ribbons-hdr", "title": "three.js：世界宽线、HDR Bloom 与显示映射对照",
        "url": "https://github.com/mrdoob/three.js", "category": "算法与源码", "tags": ["喷流", "ribbon", "billboard", "HDR", "Bloom", "MIT"],
        "summary": "LineMaterial 展示按世界宽度/相机方向展开线段的做法；UnrealBloomPass 展示 half-float 多分辨率分离模糊；tone-mapping 文件可用于对照高亮是否去饱和。三者是算法参考，不是 Forge 插件。",
        "useFor": ["廉价双向喷流核心与边缘 ribbon", "发光对象独立 HDR 附件", "保色与高亮显示策略比较"],
        "authors": ["three.js authors"], "license": "MIT（仓库 LICENSE 已核；tone mapping 含原始算法来源链接，若直接搬特定拟合还需保留对应来源/许可说明）",
        "licenseUrl": "https://github.com/mrdoob/three.js/blob/f27bb5ae1ede1309841e6fc9586cce768a99f9ad/LICENSE",
        "priority": "P1", "integrationCost": "中：需 Java 世界几何、GLSL150 材质与自有 FBO 生命周期；多 mip bloom 为中到高。",
        "fixedCommit": commits["mrdoob/three.js"], "files": files(["three-license", "three-lines", "three-bloom", "three-tonemap"]),
        "keySymbols": ["LineMaterial.worldUnits", "worldPos", "clip.xy += offset", "UnrealBloomPass", "HalfFloatType", "_getSeparableBlurMaterial", "NeutralToneMapping", "AgXToneMapping"],
        "migrationNotes": ["剥离 JS、three.js ShaderChunk/include/defines，写 Forge BufferBuilder/VertexConsumer 的世界几何与自有 shader 接口。", "喷流双锥以固定轴构造，核心用相机朝向 ribbon/交叉带，外壳体积另画；近轴视角要用替代基底避免宽度塌缩。", "羽流不是两条无限发光线；沿程用宽度、强度、噪声与端部淡出，考虑接头和路径自重叠。", "HDR 在发光支路积累，而不是把整个原版世界二次 tonemap；GL_RGBA16F 可作候选，必须验证目标驱动过滤/附件状态。", "Bloom 只扩散亮度，不保证保留色相；曝光、逐通道 clamp 及高亮去饱和应分别比较。"],
        "verificationStatus": "固定 commit 的 MIT 许可与 3 个关键文件 HTTP200，符号核查；未执行 three.js 示例",
        "readScope": "仅所列文件在内存审读，保存受限代码摘录；没有 npm 安装、示例素材或纹理下载。",
    },
    {
        "id": "tech-shockwave", "title": "pmndrs postprocessing：ShockWaveEffect",
        "url": "https://github.com/pmndrs/postprocessing/blob/9cf03cff26615636a564d8bfddf765edb8917441/src/effects/ShockWaveEffect.js", "category": "算法与源码", "tags": ["冲击波", "瞬态", "屏幕畸变", "zlib"],
        "summary": "参数化 radius、waveSize、amplitude 与衰减的环形 UV 偏移，可作为白洞喷发瞬间的廉价附加层；它是屏幕效果，不是自动 360° 的世界球壳。",
        "useFor": ["白洞出生/爆发时的短暂脉冲", "屏幕折射波备选", "为原创世界球壳借鉴包络节奏"],
        "authors": ["Raoul van Rüschen", "postprocessing contributors"],
        "license": "zlib（实际 LICENSE.md 不是 MIT；改版要明确标注改动并保留许可，不误称原作者作品）",
        "licenseUrl": "https://github.com/pmndrs/postprocessing/blob/9cf03cff26615636a564d8bfddf765edb8917441/LICENSE.md",
        "priority": "P2", "integrationCost": "低：屏幕环；中：原创世界球壳与场景深度约束。",
        "fixedCommit": commits["pmndrs/postprocessing"], "files": files(["post-license", "shock-wave", "shock-effect"]),
        "keySymbols": ["ShockWaveEffect", "mainUv", "radius", "waveSize", "maxRadius", "amplitude"],
        "migrationNotes": ["现成版本将世界点投影为屏幕中心后修改 UV，不保证地面连续性、屏幕外信息或遮挡；别把它替代已修好的黑洞世界透镜。", "屏幕波必须限定有效 UV、软衰减、深度与失效策略；相机接近/穿越中心时检查除零和过大像素位移。", "若用户要 360° 世界爆发，原创球壳密度/几何优先，屏幕折射只按需额外开关。"],
        "verificationStatus": "固定 commit 的 shader、生命周期 JS 和 zlib 许可 HTTP200；未实机接入",
        "readScope": "两个指定文件核查，仅保存受限代码摘录；没有获取整个库。",
    },
    {
        "id": "tech-bruneton", "title": "Eric Bruneton：Black Hole Shader（透镜预计算参考）",
        "url": "https://ebruneton.github.io/black_hole_shader/index.html", "category": "算法与源码", "tags": ["透镜", "预计算", "薄盘交点", "BSD-3-Clause", "研究可选"],
        "summary": "非旋转黑洞的偏折与盘交点查表，主要用于研究真实吸引透镜和星点足迹过滤；不是现成白洞渲染器，也不应作为首版新增依赖。",
        "useFor": ["白洞复用黑洞正质量透镜的算法边界", "未来高质量查表路线", "盘交点与像素足迹研究"],
        "authors": ["Eric Bruneton"], "license": "BSD-3-Clause（固定 commit LICENSE 与 README 实查，纠正 GPLv3 说法）",
        "licenseUrl": "https://github.com/ebruneton/black_hole_shader/blob/e72b3f293409893a6fa25528b29572c96fc57f57/LICENSE",
        "priority": "P2", "integrationCost": "高：离线预计算+运行时查表+抽象背景接口；首版继续用已验证的本机透镜。",
        "fixedCommit": commits["ebruneton/black_hole_shader"], "files": files(["black-license", "black-readme", "black-functions", "black-model"]),
        "keySymbols": ["GetRayDeflectionTextureUFromEsquare", "RayTrace", "SceneColor", "DiscColor", "DefaultDoppler"],
        "migrationNotes": ["functions.glsl 是查预计算纹理，不是单文件复制后自然可跑的白洞 kernel。", "model.glsl 的 GalaxyColor/StarTextureColor/DiscColor 是宿主提供的抽象接口，真实 Minecraft 场景仍需场景采样与缺失信息退化。", "白洞是时间反演边界条件，不等于负质量；不能把 lens strength 改成负数声称科学白洞。", "采样 footprint/LOD、查表精度、源纹理许可与 GL 状态需独立处理；本轮不下载作者天空贴图或预计算二进制。"],
        "verificationStatus": "固定 commit 的 README、BSD 许可、核心函数与模型接口 HTTP200；未运行 demo/生成表",
        "readScope": "仅三个文本文件与许可核查；在线 demo 可供用户自行观看，没有采集其素材。",
    },
    {
        "id": "tech-kinobloom", "title": "KinoBloom：Soft Knee、抗闪烁与 HDR Bloom（可选）",
        "url": "https://github.com/keijiro/KinoBloom", "category": "算法与源码", "tags": ["Bloom", "保色", "高动态范围", "抗闪烁", "MIT", "可选"],
        "summary": "Unity 的 soft-knee 提亮筛选、多级 downsample/upsample 与抗闪烁过滤可借鉴。预滤使用一个标量缩放 RGB，减少筛选阶段破坏颜色；仍需单独设计显示映射，Bloom 不能治愈前一步截白。",
        "useFor": ["白洞暖白核外的彩色辉光", "喷流/粒子高亮抗闪烁", "与已有 selective-bloom 的调参对照"],
        "authors": ["Keijiro Takahashi"], "license": "MIT（LICENSE.md 核查；仅 Kino/Bloom 文本源码，不扩展到示例工程附带素材）",
        "licenseUrl": "https://github.com/keijiro/KinoBloom/blob/0a04c43fa47de12bb52edb325c8d3c0125d0b4ca/LICENSE.md",
        "priority": "P2", "optional": True, "integrationCost": "中到高：HLSL/Cg→GLSL150 + Java FBO 链；优先扩已有 bloom，不新增 Unity 依赖。",
        "fixedCommit": commits["keijiro/KinoBloom"], "files": files(["kino-license", "kino-readme", "kino-cs", "kino-shader", "kino-filter"]),
        "keySymbols": ["frag_prefilter", "Brightness", "_Curve", "DownsampleAntiFlickerFilter", "UpsampleFilter", "frag_upsample_final", "RenderTextureFormat.DefaultHDR"],
        "migrationNotes": ["把 half/float/vector、tex2D 与 Unity宏改成 GLSL150，并按 Minecraft 宿主色域决定输入/输出转换；不能机械搬 GammaToLinearSpace/LinearToGammaSpace 两遍。", "当前源码是 Unity 5.5+ 时代 image effect，不是 Forge 1.20.1 mod，可复用的是过滤关系与调参合同。", "soft knee/prefilter 在浮点 emission 上完成，再模糊，最后有控合成到原世界；相机 HDR 资源重建与销毁需同窗口尺寸生命周期。", "抗闪烁过滤可能削弱稀疏亮粒子，需固定 seed 的近/远视角 A/B，而非默认全部开启。", "正文 SafeHDR 为 half-float 安全值 65000，不是 RGB clamp(0,1)；最终写 LDR 仍需要显示设计。"],
        "verificationStatus": "固定 commit MIT、Cg/HLSL 过滤器与 C# 资源链 HTTP200；未迁移/运行 Unity",
        "readScope": "只核 Kino/Bloom 指定文本源码与许可；未下载 README 远程图片、Unity package 或测试纹理。",
    },
]

for card in cards:
    assert all("error" not in entry for entry in card["files"]), card["id"]

catalog = {
    "schema": "white-hole-technical-research/1",
    "verifiedOn": "2026-10-07 (Asia/Shanghai)",
    "target": "现有 minecraft-black-hole skill / Minecraft Forge 1.20.1 / Java 17 / GLSL150",
    "scope": "研究资源与迁移方案；未实现白洞、未安装新模组、未进行游戏性能/视觉验证。",
    "verificationMethod": "GitHub API rate-limit 后以 git ls-remote HEAD 确认 commit，再读取固定 commit raw URL 和 LICENSE；学术文章使用作者站点 HTTP200 与短文本核查。",
    "sources": cards,
    "knownCorrections": ["Bruneton black_hole_shader 为 BSD-3-Clause，不是 GPLv3。", "postprocessing 为 zlib，不是 MIT。", "PSRDNoise 的 MIT 写在 README 与 GLSL 文件头，没有独立根 LICENSE 文件。", "KinoBloom 的许可证名为 LICENSE.md，shader 位于 Assets/Kino/Bloom/Shader/。"],
    "excluded": [{"names": ["Shimmer", "Veil", "Lodestone", "Photon"], "reason": "本轮算法资源足够，无必要引入额外运行时。没有核其具体 Forge 1.20.1 artifact/build/license，故不列成推荐依赖，也不声称兼容。"}],
    "evidence": "technical-verification.json",
}
(OUT / "technical-sources.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Wrote {len(cards)} source cards.")
