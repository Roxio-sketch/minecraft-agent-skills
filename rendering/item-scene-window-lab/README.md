# Item Scene Window — Forge 1.20.1

原创、可编辑的物品内部三维场景参考工程。它将真实三角形和深度缓冲渲染到离屏目标，再通过物品平面的开口显示。支持跟随观察者的 off-axis 视差，不以滚动贴图替代内部空间。

## 构建

- Minecraft 1.20.1，Forge 47.4.10，Java 17。
- Gradle wrapper 8.8，ForgeGradle 6.0.54，official 1.20.1 mappings。
- 设置 `JAVA_HOME` 为本机 JDK 17，然后运行 `gradlew.bat build`（Windows）或 `./gradlew build`。
- 首次构建需要下载依赖；缓存完整后可 `--offline`。生产 JAR 在 `build/libs/`，构建会执行 `reobfJar`。
- 游戏完全退出后安装 JAR；不要在运行或启动期间覆盖已加载的 JAR。

## 使用

```mcfunction
/give @s scenewindow:window{Scene:"scenewindow:observatory"} 1
/scenewindow preview
```

场景资源在 `src/main/resources/assets/scenewindow/scenes/`，示例为 observatory、garden、gallery、keyhole。资源包可增加任意 namespace 下的新场景，也可覆盖这些资源；F3+T 后更新。Java 变更需要重新构建和重启。

`scripts/make_examples.py <目标场景目录>` 重新生成四个原创示例；它会覆盖同名 JSON，仅在需要重新生成时运行。自由创建 box、triangle-list mesh、group，通过层级变换、颜色、旋转速度、相机和开口组合新场景。示例名称不限制主题。

## 文件与扩展点

- `SceneWindow.java`：注册专用物品及客户端 renderer。
- `Scenes.java`：资源解析、层级几何、`Backend.draw(scene, seconds)` 扩展点。
- `WindowRenderer.java`：观察点、off-axis 投影、FBO、窗口合成、状态恢复。
- `WindowAudit.java`：可选、主动触发的本地验收。生产项目可删除此类。
- `aperture.*`：矩形、圆、菱形与 PNG 红通道遮罩。

普通 mesh 为无索引、无 UV 的三角列表，单节点单色；多颜色拆节点。box 使用固定面明暗。这不是完整 Minecraft 世界模拟，未包含真实世界/跨维度远程相机、纹理网格、光照阴影、骨骼、OBJ/glTF 导入、递归镜面或 bloom。

本参考物品是单一平面；只给任意原版或模组物品添加 Scene NBT 不会启用渲染。为剑、方块模型、骨骼部件等添加 adapter 时，需要保留原模型/动作并定义窗口面、坐标基和缓冲提交时机。曲面、多面连续空间和多窗口需扩展投影与调度，不能仅拉伸输出图片。

## 实测

`/scenewindow verify` 只允许在名为 ASTRA_VFX_TEST 的本地单人存档运行。测试会暂时修改背包选中槽、窗口尺寸和视角，保存背包恢复数据并在正常完成后恢复。存档需预先备份，运行失败时检查 `restore-inventory.nbt`；这不是整个世界的事务回滚。

同一操作可由游戏目录下 `item-scene-window-lab/request.json` 的 `{"action":"run"}` 触发；`{"action":"preview"}` 只打开预览。输出 `item-scene-window-lab/runs/<时间>/` 含截图与 results.json。不要让测试桥成为生产服远程控制接口。

`scripts/validate_runtime.py <run目录>` 独立检查截图、上下文、视差、深度、重载与恢复。依赖 Python 3.10+、Pillow、NumPy；构建本身不依赖 Python。数字通过后仍需看实际截图，特别是遮罩、左右手与第三人称。

具体通过范围见配套 skill 的 runtime-evidence.md。未测试的上下文、显卡、渲染加速模组和新 backend 不继承测试结论。

## 来源

Java、GLSL、场景 JSON 与遮罩为此工程原创。Immersive Portals、Veil、Lodestone 的用户提供快照只用于架构研究，未作为运行依赖，也未复制其源码。Gradle wrapper 来自标准 Gradle 发行工具，保留其原许可声明；Minecraft、Forge 及其依赖适用各自许可。
