# 独立安装后的使用方式

在本Skill目录运行 `python scripts/serve.py`，选择卡片打开效果。默认使用本机空闲端口并打开浏览器，终端Ctrl+C停止。可传`--port 8765 --no-browser`。

在Codex中可说：

> 使用 $minecraft-xiantu-vfx-library 修改焚空裂隙，保持整体轮廓与尖角，增强沿边缘传播的局部波动；先给预览，不安装游戏。

复制可编辑项目：`python scripts/create_project.py <新目录>`，再用`python -m http.server --bind 127.0.0.1 --directory <新目录>`打开。不要直接双击HTML；模块、Shader和贴图需要HTTP服务。

有导出按钮的效果可以保存对应JSON。不同效果的格式和导入能力不同；导出不会自动更新游戏。修改裂隙共享核心后运行其`scripts/build.py`再检查。`python scripts/check_library.py`是资源检查，不等同GPU或游戏验证。

参数解释和9组效果范围见[effects.md](effects.md)。游戏接入前提供版本、源码、挂载点、时长与取消规则，避免把预览当成已安装模组。完整仓库另有docs/skills-usage.md说明如何安装和调用其他Skills。
