# 来源与许可边界

本轮输入快照的 SHA-256：

| 文件 | SHA-256 |
|---|---|
| `SpektrLightning-master.zip` | `97F0C473467C0E0D6A10377F3E30C17310D8AAE21BB04AA45E614296B3D5A2FC` |
| `UnityLightning-master.zip` | `204F75754F822D615C409A4D9879794FDE7068C99DE18DC71C63C7BC0BFC667F` |
| `thunderhead-main.zip` | `D08133C58E98ABD506CA525EF7ED0E9BE5793E75767FF656C5CCEB935641D023` |
| `unity-lightning-system-main.zip` | `7F0FFAB263C669B8694129D245EDE4008EE376F6EB498DDEB1FDCA4E46A8F7A7` |
| `research/lightning_refs/downloaded/three-windline` | Git clone of `pajama-studio/three-windline`, ring/arc centerline reference; MIT, retained as research material |
| `research/lightning_refs/downloaded/lightning-arc` | Git clone of `eXoddiS/lightning-arc`, seeded LightningStrike/SimplexNoise and multi-hop arc reference; MIT, retained as research material |

四个输入包都包含 MIT 许可声明。当前交付移植了 Spektr 所用的 Ashima 2D Simplex 噪声数学实现到 Java 与 JS，并改写了脉冲窗口。相应版权和完整 MIT 文本见 [第三方声明](../THIRD_PARTY_NOTICES.md)，也随工作台和 JAR 分发。Unity 场景、图片、C# 组件和其他仓库运行时没有直接复制进交付工程。

另两个下载仓库只作为研究素材读取，未运行其中的安装脚本。three-windline 的曲线只是路径参考，不能证明电流外观已还原。

| 仓库 | 固定研究版本 |
|---|---|
| pajama-studio/three-windline | `83b1a4139ac9ed5da785768db3fc7dac6110d0a3` |
| eXoddiS/lightning-arc | `ca694abf93d14f3d5101eb14e9709878186b4ff6` |
