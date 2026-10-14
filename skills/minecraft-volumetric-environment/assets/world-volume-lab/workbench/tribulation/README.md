# 渡劫黑云凝聚工作台

原创 WebGL2 作品，默认24秒形成。打开由现有工作台服务器提供的 /tribulation/ 页面。单独使用时以 Python http.server 服务此文件夹。

三维噪声云密度以形成进度调整覆盖阈值、侵蚀和高度生长；逆向采样域按 exp(contraction × progress) 扩大，使可见噪声特征向世界中心收缩，并叠加角向旋转。区域包络固定，噪声不是流体守恒模拟。日光和环境受光随进度降低。形成起点密度严格为零，成熟后的云内闪光来自确定性周期事件。

JSON 是 tribulation-cloud/1，包含基础 world-volume/2 完整62字段、formation时间线、相机及时间。普通原版Forge示例不识别此形成时间线和新增Formation uniform，需要另行适配。页面禁止误导性的游戏资源ZIP导出；此源码ZIP用于工作台复现。

没有改写安装skill的既有62字段协议或认证新Forge效果。参考 iterationT 的机制，使用既有原创噪声和独立向心映射，没有使用原光影包纹理。
