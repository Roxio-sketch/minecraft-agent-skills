# 算法与自由设计

内核的 art rim 为 `pow(1 - clamp(N·V,0,1), power)`，双面时先取点积绝对值。power 越大，亮带越窄。gain 控制亮度，不改变带宽；baseAlpha 和 rimAlpha 控制覆盖。脉动只改变发光增益，不移动法线或重新随机化几何。

另提供 Schlick 型权重：`F0 + (1-F0)*(1-N·V)^5`，其中 `F0=((ior-1)/(ior+1))^2`，假设外侧介质折射率为 1。这只借用介电界面的角度权重；拿它混合色彩与 alpha 不是完整玻璃 BRDF，也不执行折射。两种模式明确分开：art power 不用于伪装物理参数。

GLSL 输出颜色为 `baseColor + rimColor*weight*gain*pulse`，alpha 为 `clamp(baseAlpha+rimAlpha*weight,0,1)`。这是直通 alpha：baseAlpha=0 且 rimAlpha=0 时，即使 RGB 非零也完全不可见。glow 强度还受合成 alpha 影响，不能把 gain 当成最终屏幕亮度的线性保证。

颜色输入在计算空间中使用线性 RGB。HTML HEX 由 Three.js Color 转到线性工作空间；预览最后执行显示转换。Forge 中先查目标 framebuffer/既有 shader 的颜色约定，避免重复 gamma。HDR gain 可能超过 1，LDR 截断与 tone mapping 是单独决定。

设计变化包括几何的光滑/硬边法线、局部 mask、边缘色带、随视角的颜色过渡、空间纹理调制、脉动包络。附带内核仅实现一种边缘色与亮度脉动；需要色带/纹理时扩展 GLSL 和参数绑定，不能给 JSON 加未读取字段冒充实现。

实际模型建议：光滑球体适合护罩；分面晶体显示各面的视角变化；generated 剑正面法线大体一致，转动时可能整面变亮，不会自动沿刃轮廓形成细线。单独外壳须定义厚度、是否遮住主体和 mask 范围，不用巨大 polygon offset 掩盖几何冲突。
