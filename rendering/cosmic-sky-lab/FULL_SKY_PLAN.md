# 全剩余天空实现（0.5.0）
用户授权完成所有尚未实现事项，之后补充二维电影式黑洞参考。保留原任务，不以新增请求替代。
冻结0.4基线 evidence/full-sky-20261006/baseline；合约 expansion-contract.json /4 160fields；原90 /3完整浏览器升级、新特效关闭。
原创 sky-core.glsl + sky-extra.glsl（方向天气/双虹/22度冰晕/异界剪影/2D黑洞）+environment-core.glsl（世界体积云/深度雾/云影/天光/3D雨丝）。
Core root插入新增uniform声明与prototype，extra文件只有实现定义，可复用core hash/noise/sun。核心tone-map前调用extra，原黑洞style2转电影类型，style1保留透镜。
World env函数skyComposeEnvironment(scene,origin,direction,sceneDistance,hitGeometry)，原已曝光scene framebuffer颜色空间合成，明确美术近似。WorldWeather uniform vec2真实雨雷，配方weatherFollowWorld=0手动/1同步；浏览器独立可模拟。
Forge AFTER_LEVEL复制当前颜色+深度，实际逆投影+无平移视图旋转重建方向及场景距离；世界origin当前camera。体积云按世界高度/厚度、有限射线步进、遮挡/穿入/平移视差。云影调制重建surface、cloud-sun透射决定天光积分。不能将天空方向效果假装世界体积。
浏览器加入程序地面盒体景深测试场景FBO（color RGBA8 + sampled depth24），同核心世界后期、相机xyz/飞行控制与场景开关，参数160自动组。
Quality volume16/24/32建议上限，light2/3/4； fog/shafts 12/20/28；避免无界世界坐标/平行分母/导数尾泄漏。
新增至少12完整预设+2D电影黑洞1个，共26起点。所有原13不改值。
验证旧off/严格4协议/全预设/独立动画与遮挡/云内外/真正GPUuniform与世界FBO/重载resize/FOV。同步library allfeaturestatus与许可证新冰晕来源，更新维护和安装skill/包/桌面launcher，保留原15模板。
不声称科学物理LUT、天气服务器逻辑或任意第三方shader共存。玩家exports和已有存档保留。

实施结果：五阶段原创代码已完成，3个核心冻结，160字段/26预设。独立shader51+环境49检查，UI/资料库/下载138，实际Forge31与协议9723均通过。0.5.0包、安装skill与桌面入口已同步，最后安装资产独立前向试作和交付审计分别记录forward/delivery报告。不扩大为物理LUT、历史帧或第三方兼容承诺。

最终完成：installed forward55/55及独立delivery291/291均passed，completion.json记录全报告通过与最终三artifact哈希。主server57641保持运行，二维金环黑洞预览已提交Codex打开。
