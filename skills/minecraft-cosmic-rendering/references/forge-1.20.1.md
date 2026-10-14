# Forge 1.20.1 实测接入与模板

基线：MC 1.20.1、Forge 47.4.22、官方 mappings 1.20.1、Java 17、Gradle 8.8、ForgeGradle 6.0.54；GLSL 150。模板是独立实验工程，不能直接覆盖已有模组工程。

## 注册到 draw

- MOD 客户端总线 `RegisterShadersEvent`：创建 `ShaderInstance(ResourceProvider, ResourceLocation, DefaultVertexFormat.POSITION)`，在注册回调保存加载成功的实例。每次重载更新引用，generation 用于验证。
- MOD 客户端总线 `RegisterDimensionSpecialEffectsEvent`：注册 DimensionSpecialEffects。实验使用 `minecraft:overworld`，会替换所有采用该 effects 的维度，其他模组同键注册存在冲突。实际产品按用户目标限定维度/配置。
- 重写 `renderSky(ClientLevel,int,float,PoseStack,Camera,Matrix4f,boolean,Runnable)`；绘制后返回 true 才替代原版天空。false 让原版路径继续。
- 眼空间/方向矩阵取本次回调参数。模板不需要 cameraPosition、depth sampler 或独立 framebuffer。
- 用 POSITION 格式提交覆盖屏幕的三角形 (-1,-1)、(3,-1)、(-1,3)，VSH 直接写裁剪位置。普通 BufferUploader.drawWithShader 路径，不从其他 MOD 拷贝扩展 ShaderInstance。

## 三方接口

| Java/GLSL 名 | JSON 类型与 count | GLSL |
|---|---|---|
| InverseProjection | matrix4x4 /16 | mat4 |
| InverseViewRotation | matrix3x3 /9 | mat3 |
| Mode | int /1 | int |
| Quality | int /1 | int |
| Time | float /1 | float |

shader 在 `assets/cosmiclab/shaders/core/sky.{json,vsh,fsh}`。模板没有 samplers，资源命名空间和 JSON vertex/fragment 引用必须一并改名。新增顶点属性时同步 BufferBuilder 格式、JSON attributes、VSH 输入和 RenderType。

模板在 draw 前关闭 depth test/write、blend、cull，并 finally 恢复相应状态与 RenderSystem shader 引用。这里是一个已测 pass 的约定，不是所有材质的通用设置。每帧 GL 查询/矩阵分配尚未优化；没有证明所有驱动状态和光影管线都恢复正确。避免用“总是关闭深度”修复物品或体积材质。

## 使用资产

复制整个 [forge-1.20.1-lab](../assets/forge-1.20.1-lab) 到新的目标工作目录，再设置目标机器的 JAVA_HOME 为 Java 17。不要在安装的技能资产目录内构建或创建存档。

```text
gradlew.bat --console plain build
gradlew.bat --console plain runClient -PlabAuto=true
```

其他系统使用 `sh gradlew ...`。默认不带 labAuto 会进入普通菜单。自动流程从主菜单开始，首次启动若显示无障碍设置引导，完成该引导后才会自动继续。自动模式在复制项目 `run/saves` 新建时间戳世界，不读取旧存档；创建金墙并改变该开发客户端的相机、窗口和画质。运行时避免人工改变视角。完成后保留客户端，输出 `validation/results.json`、`summary.json` 和 30 张 PNG；下一次覆盖同名结果，需留证时先归档整个 validation 目录。不得把旧 summary 或旧截图当作新一次运行完成，核对当前世界标识和日志。

命令：`/cosmiclab direction`、`stars`、`nebula`、`quality 1|2|3`、`freeze`、`off`。freeze 在时间 0 与实时时间之间切换，不是连续暂停。自动验证后恢复显示 HUD、实时闪烁与中档星云，其余实验选项仍保留。

输出 JAR 在 `build/libs`。原实验已通过开发客户端运行与 JAR 构建/重混淆，尚未另验启动器安装包。复制模板的自有源码适用其 LICENSE；LICENSE.txt 是随官方 MDK 保留的 Forge 许可。Gradle Wrapper 和依赖遵守各自许可。

## 模板的运行验证

15 个固定用例：8 个 direction（基准、平移、yaw90、抬头45°、FOV40/100、800×600 resize、资源重载），3 个 stars，3 个 nebula，1 个金墙遮挡。其余 960×540；Time=0，关闭摇晃/FOV 动效，render distance=4、simulation distance=5。

每用例等待≥50 tick 与≥90 次天空绘制；九点方向参考容差 3/255，平移与重载记录全图像素差；前约30次天空绘制预热后异步 GPU timer。`*-sky.png` 是 sky pass 后，`*-world.png` 是整帧后。检查结果再查看画面：[check_validation.py](../scripts/check_validation.py)。

## 仅遇到对应症状时处理环境

原机器 Java 17 的临时 Unix-domain socket 曾导致 Gradle loopback 错误；为当前进程设置 `-Djdk.net.unixdomain.tmpdir=<短的可写目录>` 有效。不要默认改系统网络或固定他人目录。

原机器共享 ForgeGradle client POM 曾含 Linux natives，Windows 启动缺 lwjgl.dll。先核对真实 OS/架构和依赖；必要时从 Mojang 版本元数据下载正确平台的 LWJGL 原生库，核对哈希，再用模板可选 `-PlabNativeDir=<目录>` 传给 org.lwjgl.librarypath。不要把另一平台 DLL 作为跨平台默认依赖，也不要批量修改共享缓存。模板不包含那台机器的专用启动脚本。
