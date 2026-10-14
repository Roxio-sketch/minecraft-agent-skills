# Trail & Beam Lab — Forge 1.20.1

原创客户端拖尾/束参考工程，Java 17、Forge 47.4.10。由 `../skill/minecraft-trails-beams/scripts/author_effect.py` 生成；设计入口见该技能的 SKILL.md。`workbench/index.html` 可离线设计并导出实际读取的 `recipe.json` 与 `path.json`。参考工程包含完整源码和纹理，无 Lodestone / Veil / Photon 运行依赖。

## 构建

```powershell
$env:JAVA_HOME='D:/MC/meta/java_versions/mojang-java-runtime-gamma-windows-x64-89ce85ccb518'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/Users/MAINTAINER/.codex/tmp/cosmiclab -Dfile.encoding=UTF-8'
.\gradlew.bat --offline --console plain build
```

这些路径仅适用本机。其他电脑使用自己的 JDK 17；依赖未缓存时去掉 `--offline`。输出 `build/libs/trail-beam-lab-0.1.0.jar`，build 会运行 coreCheck 和生产重混淆。替换实例 JAR 前确认客户端已关闭；同实例并行任务应顺序部署/测试。

## 使用

- `/trailbeam showcase`：刀刃双挂点、双层束、自定义槽的材质画廊。
- `/trailbeam world`：在视角前方放置解析刀刃轨迹、固定对照束与自定义路径束；不创建实体/方块。
- `/trailbeam off`：清除演示。
- `/trailbeam reload`：重载 `<gameDir>/config/trailbeam/recipe.json` 和 `path.json`；第三槽使用它们，无文件时使用打包的默认设计。
- `/trailbeam verify`：仅在已授权的本地 ASTRA_VFX_TEST 存档中执行五场景截图和资源重载；结束恢复原 Screen 与失焦暂停设置。

真实武器通过 Effects.sample 接刃根/刃尖世界坐标，任意路径束通过 Effects.beam 接路径点。示例数学运动不等于任意武器已适配。第一人称、骨骼、服务端命中与光影包都需针对实际目标接入并验收。

## 结果

核心几何和配方检查输出在 `evidence/build-expanded-final.log`；实机记录见工作区 `TRAIL_BEAM_SKILL_DELIVERY.md`。MIT 原创实现；Gradle wrapper 采用自身 Apache-2.0 许可。
