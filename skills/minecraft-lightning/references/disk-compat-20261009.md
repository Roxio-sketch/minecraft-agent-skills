# 光影兼容渲染

LightningShaderBridge 自动通过 Iris API 检测启用光影，保留旧命名空间与 Oculus 回退。世界阶段捕获当前FBO深度及实际投影/视图，GUI前合成；隐藏HUD时RenderTick END合成。深度在片元shader中判定，不能依赖已经清空的HUD硬件深度。核心保留凸段/圆接点、显色模式，光晕保留单路径MAX后ADD。禁止把迟绘效果当作已参与光影包Bloom或动态照明。Solas V3.7b/Oculus1.8.0/Embeddium0.3.31已在宿主ASTRA-TEST验证，其他光影包需要再验证；独立模板只有构建验证。
