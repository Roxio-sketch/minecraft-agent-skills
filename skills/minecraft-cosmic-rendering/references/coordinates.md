# 坐标、相机与深度契约

## 无限远方向

先说明顶点 Position 是模型、世界、相机相对、眼空间还是裁剪空间；变量名不足以证明。避免重复减相机位置、重复乘视图矩阵。

已测实验采用全屏三角形和当前投影 P：

```glsl
vec4 p = inverseP * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
vec4 eyeH = inverseP * vec4(0.0, 0.0, 1.0, 0.0);
vec3 eyeView = eyeH.xyz / eyeH.w;
vec3 d = normalize(inverseViewRotation * (p.xyz / p.w - eyeView));
```

这里的 `-1` 是本实验 OpenGL 常规透视的近裁剪面 NDC z，不是跨 API 的约定。反向深度、正交投影、非标准投影须重新推导。1.20.1走路bob会把平移写进projection，eyeView消除它对方向的污染；原15用例模板关闭bob且eyeView=0，历史模板原源保持独立。`inverseViewRotation` 取本次天空回调 PoseStack 3×3 部分的逆，去掉平移；不要拿任意时刻保存的矩阵替代当前 pass 矩阵。若含缩放/受伤/摇晃变换，它未必是纯旋转，需验证目标效果应继承哪些变换。

无限远天空只依赖世界方向，平移不应改变固定时间图案。局部体积必须使用相机位置，相机平移产生视差通常才是正确行为。物品星空则先决定随模型、随世界还是随屏幕固定，不将天空的平移不变性强加给所有材质。

## 独立方向检查

先输出 `d*0.5+0.5`。已测 Forge 1.20.1 第一人称零 yaw/pitch 时 Camera.look=(0,0,1)，left=(1,0,0)，up=(0,1,0)；OpenGL eye-space 看向 -Z。直接把 `(x,y,-1)` 乘 Camera quaternion 会把参考方向弄反。

对无摇晃、常规对称透视的实验，CPU 参考为：

```text
x = 2*(pixelX+0.5)/framebufferWidth-1
y = 1-2*(pixelY+0.5)/framebufferHeight
ray = normalize(Camera.look - Camera.left*x/P00 + Camera.up*y/P11)
expected = 0.5*ray+0.5
```

该计算不复用 GLSL 的逆矩阵链，适合交叉验证。像素坐标在读回图像中从顶部开始。确认实际像素格式再解码 RGB；实验 NativeImage 整数是 ABGR。颜色管理、tone mapping、曝光和后处理会改变方向 RGB；先隔离它们，不简单扩大误差容忍度。

中心朝 +Z 时 RGB≈(0.5,0.5,1)；yaw=90° 朝 -X 时≈(0,0.5,0.5)。测试边缘像素才能识别 FOV/宽高比错误；只看中心不够。使用 framebuffer 尺寸，非 GUI 缩放尺寸。

## 有限体积与场景深度

先写清 `p(t)=origin+t*direction` 的空间和 direction 是否单位化。若单位化，t 是沿射线的距离；`-viewPos.z` 是轴向距离，离轴像素不能直接与 t 比较。

从深度纹理反投影得到 viewPos 后，可在同一眼空间用 `dot(viewPos-eyeView,direction)` 得到沿射线参数；单位方向下 `length(viewPos-eyeView)` 是前方命中距离。世界射线origin为camera加逆view变换后的eyeView及逆view translation。背景深度、反向深度、坐标范围等需要单独处理。优先与实际重建点交叉核对，而不是背固定 depth linearize 公式。

先 sphere/box 相交得到 `[tEnter,tExit]`，再裁到相机前方与场景命中：`start=max(tEnter,0)`，`end=min(tExit,tScene)`。空区间不积分。核对深度纹理来自哪个 framebuffer、何时复制、MSAA 是否解析、resize 是否同步、透明物体是否写入该深度。

体积合成要声明 straight/premultiplied alpha，RGB 是否线性，以及前向累计透射率方式。不要用清空场景深度解决遮挡问题。原15用例模板没有体积运行验证；扩展procedural-sky-lab的世界环境契约和证据独立记录在authoring与advanced-sky。
