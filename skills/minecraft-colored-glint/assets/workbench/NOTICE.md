# 工作台资源来源

`glint.png` 原样复制自用户项目中的 RenderBlender 源码归档：

```text
research/cosmic-material/sources/RenderBlender-master/RenderBlender-master/
  src/main/resources/assets/renderblender/textures/misc/glint_item_white.png
```

它是旧程序光纹使用的中性亮度载体，未重编码、未改像素，SHA-256 为 `5fccbb464fb55e47d9fc7f293d441a4fd86a5dc05e5436b475259c8f8283377d`。新版 `guard-look.js` 的水光和局部波纹由程序生成，不从这张贴图提取图案。

原项目 `gradle.properties` 声明 RenderBlender 1.1.2、作者 cu6、cnlimiter、IAFEnvoy、许可 LGPL-3.0；[LICENSE-RenderBlender.txt](LICENSE-RenderBlender.txt) 是该归档的原始 LICENSE。没有为上游素材另行改写许可，也未确认归档之外更早的独立作者。完整源素材清单见 [source-manifest.json](../../references/source-manifest.json)。取用或调色时保留来源与许可，并注明实际修改。

Minecraft Java 1.20.1 的铁甲、钻石甲、下界合金甲、金甲原版贴图**不随这个工作台或 Skill 分发**。用户可用 [extract_armor_textures.py](../../scripts/extract_armor_textures.py) 从自己的本机 client.jar 提取到输出工作台的 `textures/`，仅作为私人本机设计比较；来源记录写入当地 `textures/source.json`。复制或发布 Skill 时不要将这些本机提取的文件纳入包中。

工作台源码和 `guard-look.js` 是本项目创作的预览程序，不包含 RenderBlender Java 类、Minecraft JAR、存档或个人配置。预览不构成新版穿戴护甲效果已通过游戏验收的证明。
