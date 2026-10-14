# 自定义和渐变流光接入

先读 [配色创作](authoring.md) 和 [基础渲染契约](integration.md)。以下 Java/API 以 Forge 1.20.1 official mappings 为依据，shader 三件套按本地 `rendertype_glint_direct` 核对；其他模式、版本及光影管线须重新适配。

## 静态自定义色与烘焙渐变

对已有 Colored Glint Lab：

1. 用 `--namespace coloredglint` 生成 `solid` 或 `spatial`；将 `integration.json` 的 `texture` 对应 PNG 复制到 `src/main/resources/assets/coloredglint/textures/misc/`。
2. 在 `src/main/java/dev/coloredglint/GlintStyle.java` 的 `COLORS` 集合加入配方 ID。`GlintLayers` 为静态配方构造五种模式，进入 `registerBuffers`；日志应使用实际层数量。Lab 0.2.0 已包含 `rose_gold` 和 `aurora`，用户新 ID 仍需注册。
3. 保留现有 `ItemRendererMixin` 的按 stack 包装和原版回退。构建/reobf 后部署新 JAR，并重启客户端；新增 Java ID 不会因 F3+T 生效。
4. 测试存档中可用 1.20.1 命令，例如注册 `aurora` 后：

```mcfunction
/give @s minecraft:diamond_sword{ColoredGlint:{Color:"aurora",Force:1b}} 1
```

`Color` 在该基线是注册 ID，直接填 `#42CFFF` 或 JSON 色标不会被解析。未注册 ID、仅复制 PNG、仅生成 `integration.json` 均不会自动扩展模组。若项目要求玩家任意输入颜色，可设计经过校验的配置注册表或有限自定义参数通道，明确重载/缓存淘汰；不要建立每个连续色值一个永久 RenderType 的无界缓存。

仅更新已注册同路径 PNG 可通过资源重载检查；新增 Java ID、shader 注册或 Mixin 改动需要重新构建/启动。普通资源包可覆盖已有资源，不能单独为任意物品创建上述 Java 分派。

## 动态 shader 路线

适用于 `temporal`、`traveling`，也可供静态模式使用。必须同时完成：shader 注册、中性载体、目标模式 RenderType、固定缓冲/提交顺序、draw 时参数绑定、重载验证。

1. 复制 `assets/<namespace>/shaders/core/glint_<id>.json/.vsh/.fsh` 和 `integration.json` 中的 **`shader_texture`**。静态 `texture` 已经染色，不能再给 shader 染一遍；动态 `texture` 本身就是中性载体。
2. 在客户端 MOD event bus 的 `RegisterShadersEvent` 中用 `event.getResourceProvider()`、`new ResourceLocation(namespace,"glint_<id>")`、`DefaultVertexFormat.POSITION_TEX` 创建 `ShaderInstance`，并把加载回调收到的新实例保存到对应 ID。不要保存构造时的临时实例，也不要跨重载复用旧 `Uniform`。
3. 按 `(配方ID, 原模式)` 构造稳定 RenderType；保留该模式的混合、EQUAL 深度、COLOR_WRITE、纹理矩阵、layering、output target。原版物品层与 glint 层几何应匹配。默认生成模板只核对 direct，不应把它直接替换五个模式并宣称全支持。
4. `ShaderStateShard` 的 supplier 在实际 setup/draw 路径获取当前 shader，并设置该配方时间；绑定 `shader_texture`。不要在 `forStack`/提交顶点时改共享 uniform 后延迟 flush。

下面是针对生成的 `rainbow_cycle` 六秒周期的注册与时间示例，放入目标工程客户端类后按该工程包名组织。它说明 shader 生命周期和 draw 时参数，不是完整 RenderType/Mixin 安装器：

```java
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;

@Mod.EventBusSubscriber(modid="coloredglint", value=Dist.CLIENT,
    bus=Mod.EventBusSubscriber.Bus.MOD)
public final class RainbowGlintShader {
    private static ShaderInstance shader;
    private static ClientLevel clockLevel;
    private static double sampledTicks;

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            new ResourceLocation("coloredglint", "glint_rainbow_cycle"),
            DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
    }

    public static boolean ready() { return shader != null; }

    // Bind this supplier to the matching layer's ShaderStateShard.
    public static ShaderInstance atDraw() {
        if (shader == null) throw new IllegalStateException("Glint shader not loaded");
        Minecraft mc = Minecraft.getInstance();
        if (clockLevel != mc.level) {
            clockLevel = mc.level;
            sampledTicks = 0;
        }
        if (mc.level != null && !mc.isPaused()) {
            sampledTicks = (double) mc.level.getGameTime() + mc.getFrameTime();
        }
        var time = shader.getUniform("EffectTimeSeconds");
        if (time == null) throw new IllegalStateException("Missing glint time uniform");
        time.set((float) ((sampledTicks / 20.0) % 6.0));
        return shader;
    }
}
```

在目标 RenderType 子类中将对应状态设为 `new ShaderStateShard(RainbowGlintShader::atDraw)`，使用对应中性 PNG，并把该层加入正确固定缓冲。调用方在选用替代层前检查注册就绪；无法加载时明确报错或回退原层，不能静默返回不匹配的 shader。此示例只为一个配方保存实例；多个配方用有限 ID 注册表，每个层绑定自己的配方，周期必须与生成 JSON 相同。

这里按世界 tick + partial tick 计时，在单机暂停时保留配色时间样本；无世界时从零开始。原版 `GLINT_TEXTURING` 仍按自身时钟滚动，所以暂停并不冻结整个图像，traveling 的空间采样也可能随原 UV 移动。若用户要完全冻结，需另行适配纹理矩阵时钟。多人菜单未必暂停服务器时钟。先以 double 取周期余数，最后转 float，避免长世界时间的 float 精度丢失。用户要现实时间持续播放、独立起始时刻、反向运动或暂停后追赶时，需要有意识地改变时钟/相位策略并验收。

来源工程的 `GlintShaders.java` 已将该模式推广为有限 `PERIODS` 与 shader 实例表；新增动态配方时，周期必须与生成配方一致，同时登记 `GlintStyle.COLORS`。`GlintLayers` 为动态 ID 仅创建 direct 层，其他原类型不替换。0.2.0 的 11 套静态配方 × 5 模式 + 2 套动态配方 × direct = 57 层；不能把这个数当作所有后续项目的固定常量。

## 坐标、接缝与批次

- 生成模板的 `texCoord0 = (TextureMat * vec4(UV0,0,1)).xy` 同时采载体与计算色场，沿用 glint 滚动。要固定剑尖/剑柄配色，需单独提供稳定且标准化的模型坐标，计算如 `t=clamp((localY-minY)/(maxY-minY),0,1)`，不能擅自把滚动 UV 当 localY。
- 独立坐标可能需要新增顶点字段/varying、保留物体位置或绘制时变换；同步更改真实 consumer、VertexFormat、shader 声明和 RenderType。GUI、左右手、第三人称、弓形态要各自检查。不要只改一个 GLSL 表达式就承诺对象锚定。
- `repeat` 的首尾色相必须闭合；`pingpong` 在中点折返，颜色连续但速度方向会改变。若需导数也平滑的循环，另加平滑相位映射，并同步 CPU 预览/GLSL/测试。
- `tileable:true` 只保证色场边界，原载体可能已有纹理接缝。静态 PNG 重复平铺与游戏滤波都要看，避免把连续色板当成载体也无缝的证明。
- 不把 shader JSON 的 blend 小节孤立地当成普通透明混合方案；保留目标版本实际 glint RenderType 状态与 shader apply 顺序。保持基础物品先绘制、深度匹配以及原生 `GlintAlpha` 强度控制。
- 同屏两把不同配方、相同配方多个物品、无配置物品都应独立。不要按秒数、每帧颜色或未经限制的用户输入增长层缓存；重载需替换 shader 实例并清理自己的过期 GPU 资源。

## 验证层级

配方/PNG 通过 → GLSL 编译链接及颜色数学通过 → Java/Mixin 构建通过 → 目标客户端画面与循环通过，各层不能替代下一层。四套预设 shader 在本机 WGL 编译链接通过，250 个浮点 GPU 采样与 Python 计算一致（容差 0.0001）。Lab 0.2.0 另完成新四套配方的 Minecraft 实测，跑次 `20260929-023739`；详情见 [运行记录](forge-1.20.1-runtime.md) 的 0.2.0 小节。动态时间采样覆盖完整周期与回绕，不等同于逐帧录制证明循环接缝不可见。原九色证据不替代新增渐变证据。

至少检查完整周期边界前后、同屏不同配方串色、暂停/继续、两次 F3+T、长世界时间、GUI/左右手，以及用户要求的 Cosmic/动态模型路径；记录所用配方与资源哈希。参见 [验收与排错](validation.md)。
