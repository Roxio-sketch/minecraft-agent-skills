# Forge 1.20.1 接入

工程在 `assets/world-volume-lab/forge`，详细启动、命令与渲染入口见 [工程合同](../assets/world-volume-lab/forge/INTEGRATION.md)。Minecraft 1.20.1、Forge 47.4.22、Java 17、ForgeGradle 6.0.54 是本次实际验证版本，不从同名 API 推广到其他版本。

用 `scripts/author.py` 复制完整作品后，在作品 `forge` 目录执行：

```text
./run-lab.ps1 -JavaHome <本机Java17路径> -BuildOnly
./run-lab.ps1 -JavaHome <本机Java17路径>
```

Gradle Wrapper 会按标准方式获取依赖；已有缓存时可直接复用。Windows 启动脚本核对官方 native 包 SHA1 并在工程自身 build 目录解出 DLL，避免宿主混合平台缓存。无需安装到已有玩家实例来验证示例。

运行配置来自 Forge 实际 config 目录的 `world-volume.json`，配置失败保持上个有效配方。`/worldvolume reload` 重新读取；`time <秒>`、`freeze`、`play` 控制视觉时钟；`on`、`off`、`status` 控制此 pass。修改的是客户端效果，未写入方块光或服务器天气。

## 迁移需要保留的合同

- AFTER_SKY 捕获实际同帧投影/视图，AFTER_LEVEL 使用缓存。投影中的光学眼点参与原点与射线，不能用删 view translation 代替。
- CPU double 把区域中心、云底相对实际相机重基，GPU 使用一致参考系。密度噪声取区域相对位置。
- 独立 snapshot 拷贝原色与深度，最终采样 snapshot、写回主 FBO。深度保持原字节；资源失败时禁用本 pass。
- 工作台线性场景最终 tone map；原生 Minecraft LDR 主颜色近似 sRGB 解码/编码后合成，高亮可能裁切。两者不是完整 HDR 色彩管理。
- 表面光用深度导数法线，介质透射和范围衰减；没有材质法线贴图、地形光源阴影或多重散射收敛证明。
- 模板遇到 Oculus/Embeddium/Rubidium 自动避让，水/岩浆相机跳过。没有实际适配测试之前，不删除这些所有权保护。

## 测试与证据

`run-lab.ps1 -AutoValidate -ExitOnComplete` 只创建命名的独立 flat 测试存档，验收工具墙仅在该存档构建；不打开玩家旧存档。`check-validation.py` 用标准库解码实际 PNG，并与 GPU 读回报告核对几何/天空变化、深度保持、状态恢复与资源重载。

自动验收需要真实图形设备与游戏客户端，编译成功不等于这一层通过。修改 GLSL 或 Java 后重新绑定 source/class/resource/JAR 哈希，保存失败报告，不能把随附冻结结果当作改后实现的认证。
