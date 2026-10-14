# 可调水波技能交付

完成日期：2026-09-30。

## 交付内容

- 技能：`skill/minecraft-water-ripples/SKILL.md`，将同步安装到本机 `.codex/skills/minecraft-water-ripples`。
- 源码：`water-ripple-lab`。Forge 1.20.1、47.4.10、Java 17；不依赖 Photon 或额外渲染模组。
- 参数工作台：`water-ripple-lab/workbench/index.html`。可直接打开，或 `node workbench/serve.cjs 8766` 后访问 http://127.0.0.1:8766。
- 研究：`skill/minecraft-water-ripples/references/research.md`，实际读了三份 ZIP 的更新、法线、表面和焦散实现。
- 参数定义：`references/parameters.md`。水面宽度、分辨率、速度、衰减、扰动半径/强度、限幅、法线、高光、水色、反射、透明度与雨滴频率均可调。

## 使用

在 Codex 里调用 `$minecraft-water-ripples`，例如：“做一个半径较小、衰减很慢、深蓝色的落水涟漪，并打开参数工作台”。技能生成器支持 default、rain、impact、shallow、magic 五种预设。

```powershell
node skill/minecraft-water-ripples/scripts/author.cjs --out my-water-effect --preset shallow
```

将 JAR 放到 Forge 1.20.1 实例 mods。客户端使用：

- `/waterripple demo`：在玩家脚下创建 8×8 格水面补片并注入波纹；宽度来自配置。
- `/waterripple drop`：当前中心再注入一次。
- `/waterripple rain`：切换持续雨滴。
- `/waterripple reload`：重读参数并重建水面，旧波纹会清空。
- `/waterripple off`：关闭水面。

网页导出完整 recipe.json。通过附带 localhost 服务打开时，文件直接保存到工作台 `exports` 目录，并显示实际路径，兼容内置浏览器不支持 Blob 下载的情况。直接打开 HTML 时会尝试普通下载，也可从“查看 JSON”复制文本保存。

将参数文件重命名为 `recipe.json`，放在游戏目录 `config/waterripple/`，再执行 reload。生成器会同时输出根目录 recipe.json、run/config 配置和网页初始配方，三者一致。

自己的客户端代码可以调用 `RippleClient.create(center, recipe)` 与 `RippleClient.impulse(worldX, worldZ, radius, signedHeight)`。当前模板仅维护一个活动水面。多水面、技能网络同步和原版水面替换是进一步扩展项。

## 验证证据

- 本体 Gradle build 通过，产出经过 reobfJar 的分发 JAR。
- 从技能模板生成独立工程 `skill-validation/water-ripples-fresh`，其 Gradle build 通过，证明不是仅原目录能编译。
- Java 核心测试：传播、对称性、衰减、正负干涉，以及 velocity/history × absorb/reflect 四种组合的长时间限幅稳定性通过。
- JavaScript 同样四种组合的稳定性、非法参数拒绝与 JSON 往返通过。
- 跨语言数值对照：history/reflect、25 ticks 后三个位置的 Java 值为 `0.0011670509, -0.0007353192, 0.00019674095`；JS 为 `0.001167050446, -0.000735316775, 0.000196741021`，差异小于 3e-9。
- 网页实际检查：桌面/窄窗口布局，雨滴、双源干涉、暂停、清空、浅水预设、129² 网格、JSON 显示；本地参数导出保存及文件选择器导入往返成功，控制台无错误。
- Forge 开发客户端成功启动并完成资源/着色器加载，没有此模组的 shader 加载错误。只做到客户端资源加载检查，没有在 Minecraft 世界中完成视觉截图实测；网页预览图不能替代游戏截图。
- skill-creator 的 quick_validate.py 通过。Windows 执行此校验器需要 Python `-X utf8`。

## 实现范围

这是可叠加和干涉的真实高度场波动、网格起伏与法线材质，不是几条圆环的缩放动画。velocity 模式来源于前两份参考的离散波动思想；history 模式借鉴浅水项目的两时刻递推。

材质使用 Schlick Fresnel、程序天空反射和镜面高光。背景透过来自透明混合；没有场景颜色折射、真实地形反射或世界焦散，不自动改变全部 Minecraft 水方块。第三份参考也没有完整浅水流体的水平动量/水深求解。

目标实例的第三方着色器兼容、半透明排序、水面负振幅遮挡与世界内效果仍需实测。原版水上叠加时请控制水面基准高度与负振幅；目前 demo 在玩家脚下创建，适合先在陆地空旷处观察独立补片。
