# HTML 工作台

双击 `assets/workbench/index.html` 即可离线打开；无需网页服务器或外部 CDN。它提供七个可修改的起点：霜蓝双刃、棱彩双芯束、金色螺旋、熔火鞭痕、紫电鞭束、符文牵引、分叉雷束。选中预设后所有参数仍可编辑，没有风格白名单。

左侧画布可拖动旋转、滚轮缩放、暂停、拖动时间轴和换路径种子。右侧依次编辑截面与路径、分层材质、采样预算、文件。层可增删；每层独立设置混合、图案、纹理 ID、宽度、色带节点、宽度节点、纹理重复和正反向流动。路径可选直线、弧线、正弦、螺旋、折线、闪电，并设长度、幅度、周期、速度、随机种子、点数和分叉。下方状态显示路径点、层数与估计四边形数，超预算或格式错误会阻止导出。

使用“导出完整设计”保存一个便于回传或再导入的 `bundle.json`；使用“导出 recipe.json”及“导出 path.json”得到游戏真正读取的两个文件。导入接受完整设计对象，不能直接导入只有配方或只有路径的单文件。生成项目可用：

```powershell
python scripts/author_effect.py --recipe D:/Design/recipe.json --path D:/Design/path.json --out D:/Projects/trail-demo
```

已构建的示例则把两个文件放到 `<gameDir>/config/trailbeam/`，在游戏里执行 `/trailbeam reload`，用 `/trailbeam world` 观察。路径文件只改变演示束与第三画廊槽；`blade` 的真实扫掠仍依赖调用方提供逐帧刃根、刃尖世界坐标。自定义纹理 ID 可以导出，但 PNG 文件必须另外放入对应资源命名空间并重新构建或装入资源包。

Canvas 预览近似展示形状、色带、宽度、图案和时间节奏；它不使用游戏 GLSL、深度缓冲、纹理采样、相机投影或真正的武器骨骼。因此不能把工作台截图当作 Forge 截图。每次扩展 shader/profile/path 契约，要同步 `app.js`/`core.js`、Java parser、GLSL、说明和检查；不要求 2D 预览逐像素等同游戏。
