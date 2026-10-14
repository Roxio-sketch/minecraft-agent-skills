# GitHub 检索与筛选记录

日期：2026-10-11。用户要求先整理 GitHub 素材源码，为水龙卷继续迭代提供依据。研究只采用仓库本身、固定提交源码、原始 LICENSE 与源文件声明作为技术/授权依据。

## 检索方向

- `site:github.com waterspout water tornado shader source code`
- `site:github.com water tornado fluid simulation shader`
- tornado particle dynamics / volumetric cloud density erosion
- PIC/FLIP / Position Based Fluids / screen-space fluid rendering / particle surface reconstruction
- flow map water / dual-phase normals / tiled directional flow / foam

宽泛 water tornado 检索主要返回普通海面、二维流体和通用粒子项目，不能据名称声称有完整龙吸水。最终选择两份云/龙卷、三份液体、三份表面流动源码，把不足的环节写在对应 findings 中。

## 只检索，未纳入源码包的候选

| 仓库 | 检索页面体现的方向 | 本次未纳入原因 |
| --- | --- | --- |
| [GarrettGunnell/Water](https://github.com/GarrettGunnell/Water) | Gerstner/FBM 海面着色 | 主要是海面高度场；已选流动素材可覆盖当前表面研究，没有继续固定许可与文件版本 |
| [Brian-Jiang/GPUFluidSimulation](https://github.com/Brian-Jiang/GPUFluidSimulation) | DX11 二维网格水面 | 不能用于竖直水体多层片；没有进行源码和许可审计 |
| [RikkaBunny/UE4-Compute-SPH-Water-Demo](https://github.com/RikkaBunny/UE4-Compute-SPH-Water-Demo) | UE4 SPH + 屏幕空间液体 | 已选液体方案覆盖研究目标；没有取得或审计该移植工程 |
| [mishurov/fluid](https://github.com/mishurov/fluid) | OpenGL 二维不可压缩流 | 方向偏二维；本次不判断许可或可用性 |
| [piellardj/water-simulation-gpu](https://github.com/piellardj/water-simulation-gpu) | 高度图水面 | 无法表示悬空水片与多层界面；没有审计 |

这些仅为检索记录，不是“已验证可复用”的推荐。没有下载其源文件、媒体或纹理。

## 获取方式

GitHub API 出现 403 限流后，改用 `git ls-remote` / 精简 Git 元数据固定提交，再经 SHA 固定的 raw URL 或 codeload ZIP 取得精选文件。各组 manifest 记录实际方式、原始路径和 SHA256；flow 的 [44 文件远端字节核对](flow/acquisition-verification.json) 另行保存。固定版本内容未在未来更新中自动替换。

下载的 Unity、Rust、WebGPU 和 WebGL 工程均没有执行。本次执行的是自行编写的索引与校验脚本。
