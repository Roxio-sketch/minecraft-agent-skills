# Forge 1.20.1 已实测运行基线

## 工程与证据

在本技能来源项目中定位 `colored-glint-lab/`；若目录移动，搜索 `GlintLayers.java`、`GlintAudit.java` 和 `COLORED_GLINT_RUNTIME_VALIDATION.md`。不把个人绝对路径当成其他机器的默认值。

- 独立模组：Colored Glint Lab 0.1.0；Minecraft 1.20.1 / Forge 47.4.10 / Java 17 / official mappings。
- 本地实测：AXO ASTRA-TEST，ASTRA_VFX_TEST；跑次 `20260929-011858`，时间按原机 Asia/Shanghai 记录。
- 已构建生产包：`colored-glint-lab/build/libs/colored-glint-lab-0.1.0.jar`，SHA-256 `147254fd15c19c49e47544204e393a662cd5044fe5eff6adbc48c8cc470cb890`。
- 游戏原图、恢复记录和 shader 实例 ID 在 `colored-glint-lab/evidence/20260929-011858/`。复制项目或 JAR 后重新校验，不以旧证据证明新代码。

原 RenderBlender 的 Java 未改动，也未被认定构建成功。本实验复用了九张原图，另写了调用点接入和有限模式的缓冲注册。

## 核心接入

`GlintStyle.resolve(stack)` 只读解析 `ColoredGlint:{Color:"red",Force:1b}`。只有九个有效 ID 或 `default` 可强制流光，未知 ID 完整回退。原生附魔与 Force 独立。

`ItemRendererMixin` 在 `ItemRenderer.render` 内重定向 `ItemStack.hasFoil()` 为“原返回值或有效 Force”；四个 foil helper 调用点的 handler 接收原 render 的 `ItemStack` 和显示上下文，把 `MultiBufferSource` 包装成捕获不可变颜色的对象。其 `getBuffer` 只替换已知 glint RenderType，基础层及未知类型原样返回。

这种接入不需要全局当前 stack，也没有 HEAD/TAIL 清理问题。嵌套安全来自每次调用独立捕获数据，仍不代表已实测所有第三方递归 renderer。

`GlintLayers` 缓存九色 × 五个模式，保持对应 shader、GLINT/ENTITY_GLINT 纹理状态与 output target。`RenderBuffersMixin` 在默认实例构造完成后将 45 层注册进固定缓冲，且位于原固定层之后。实测 draw 计数只观察到 direct 分支；不要把五种类型的存在说成全部分支已经验收。

## 构建要点

采用原项目 Gradle 8.8 / ForgeGradle 6.0.54 的缓存环境。标准 Mixin 0.8.5 AP 需要 Gson、Guava 和 ASM 的 annotationProcessor 依赖；普通 compile classpath 的库不会自动进入 AP 隔离路径。使用 ForgeGradle `createMcpToSrg/output.tsrg` 生成 refmap，内置 `MixinConfigs` 清单项，最终执行 reobfJar。

访问 RenderBuffers 私有字段采用 `@Accessor`，refmap 实际映射 `fixedBuffers`；没有仅声明 @Shadow 却遗漏额外重混淆映射的路径。创建 RenderType 使用公开的七参数 overload，五参数 overload 在该目标版本不是 public。

## 已测范围

GUI 四色附魔剑的 Force=false 仍能选色；无附魔的青色剑 Force=true 正常发光；原生附魔和未知 ID 回退对照近乎相同，7 个像素的最大通道差为 1/255，未要求不同屏幕位置逐字节相同。两个不应发光的普通剑对照在前后帧中完全不变。

两个时刻的红、蓝、紫、绿剑 ROI 分别改变 10,020、10,264、11,132、11,132 像素（通道差 >2/255）。这是实际 glint 动态证据；Cosmic 自身动画不计入该组统计。

另外完成第一人称红/蓝右手与绿色左手、紫色第三人称、61 tick 满弓及 `bow_pulling_2`、两只掉落物与绿色展示框、两次重载后 shader 实例变更、Cosmic 剑/方块叠加截图。12 个截图点的 GL error 采样为 0。驱动 RTX 4060 Ti；没有性能基准或全驱动承诺。

验收时 glintStrength=1.0、glintSpeed=0.5；完成后恢复进入验收前的选项。纹理自身较明亮，若用户要求更柔和效果可在目标产品中另行设计强度，不能把本测试设置当成强制默认。

## 安全复跑与边界

`/coloredglint showcase` 只显示客户端样例，不改背包。`/coloredglint verify` 修改玩家状态，只接受上述命名的本地测试存档；新环境应按用户选择改验收目标并先备份。原实测背包、选中槽、模式、位置和三个测试实体清理均校验为 true；不承诺世界时间、进度和配方逐字节回滚。

测试已安装项目原有 Cosmic Material Lab，但流光模组不依赖它；没有单独跑过移除 Cosmic 后的整合环境。穿戴护甲、BEWLR、第三方骨骼动画、自建即时 BufferSource、Oculus/Iris/具体光影包和所有透明分支尚未验收。后续修改需使用 [validation.md](validation.md) 的相关场景重新验证。

## 0.2.0 自定义与渐变实测

后续独立跑次 `20260929-023739`，同一 ASTRA-TEST / ASTRA_VFX_TEST 环境。对应 JAR 为 `colored-glint-lab-0.2.0.jar`，SHA-256 `17ebe3a68b0a3f9379dc6e34c0003c1efc906a6ecf2af6bc8f6c958851210679`。证据在 `colored-glint-lab/evidence/20260929-023739/`；根目录报告 `COLORED_GLINT_GRADIENT_RUNTIME_VALIDATION.md`；源配方在 `colored-glint-lab/recipes/`。

- `rose_gold`：自定义单色 `#F5A7B8`；`aurora`：青→蓝→紫空间渐变，静态染色 PNG。
- `rainbow_cycle`：6 秒整体彩虹循环；`ember_wave`：4 秒往返、两条色带的余烬流动，均为中性载体 + 生成 shader。
- 15 张原始截图；73 项证据检查通过，包括资源/安装 JAR 哈希、同屏不同配方、强制流光与无流光对照、左右手、第三人称、61 tick 满弓 override、掉落物/展示框、两次重载和暂停/续播。GUI 截图另含 Cosmic 剑/方块组合。
- 原版 direct shader 和两个自定义 shader 在两次重载后均更换实例。动画 draw 时间跨过完整周期并回绕；暂停两次采样一致，恢复后推进。这里只冻结新增配色时间，原版纹理滚动仍继续。
- 15 个截图点的 GL error 为 0；玩家背包、选中槽位、模式、位置和测试实体清理五项恢复检查均通过。测试前另有完整存档备份。

0.2.0 保留九色静态逻辑并增加四个注册 ID，共 57 个固定层；新动态配方只替换 direct，其他模式使用原版 glint。实际 draw 记录也仅出现 direct。时钟长时间精度策略有实现，未进行超长世界时间专项运行；周期边界没有逐帧录像验收；不推广到穿戴护甲、光影包或所有渲染路径。

使用 `/coloredglint gradients` 查看不改背包的展示，`/coloredglint verify_gradients` 重新执行有恢复机制的测试。文件桥对应 `gradients` 和 `run_gradients`。新运行与旧基线分开保存；仅重读旧截图不能称为重新运行游戏。
