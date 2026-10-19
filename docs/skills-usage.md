# Skills 使用说明

## 安装与直接打开预览的区别

- 只想看效果、调整参数：不需要安装Skill，也不需要API key。用Python打开工作台即可。
- 希望Codex按这些流程修改效果、生成代码或接入项目：将所需的技能文件夹复制到Codex技能目录，再在对话中调用。
- 安装Skill不会自动给Minecraft安装模组，也不会替换存档或资源包。游戏接入需明确目标工程与版本，并单独构建、安装和实测。

## 1. 获取仓库

```powershell
git clone https://github.com/Roxio-sketch/minecraft-agent-skills.git
cd minecraft-agent-skills
```

已有仓库时可先检查`git status`，保留自己的改动后再更新。尚未合入主分支的新内容请从对应PR分支获取，不要把未合并内容当成main已有。

## 2. 按需安装一个Skill

下面以仙途特效库为例，在仓库根目录运行。目标目录已存在时脚本会停止，先备份旧版本再决定是否更新。

```powershell
$skillsDir = if ($env:CODEX_HOME) { Join-Path $env:CODEX_HOME 'skills' } else { Join-Path $env:USERPROFILE '.codex\skills' }
$skillName = 'minecraft-xiantu-vfx-library'
$destination = Join-Path $skillsDir $skillName
if (Test-Path -LiteralPath $destination) { throw '该Skill已存在，请先备份再更新。' }
New-Item -ItemType Directory -Path $skillsDir -Force | Out-Null
Copy-Item -LiteralPath (Join-Path '.\skills' $skillName) -Destination $destination -Recurse
```

安装后应为`skills/minecraft-xiantu-vfx-library/SKILL.md`这一层级，不要额外套一层同名目录。换装其他技能只需更改`$skillName`，具体可选名称见[完整目录](rendering-catalog.md)。如果当前会话没有发现新Skill，可新建会话或重新启动Codex后再检查。

## 3. 在Codex中调用

可以直接写技能名称，也可以使用`$技能名称`明确调用。最好同时提供目标效果、工程路径、版本和预期输出。例如：

```text
使用 $minecraft-xiantu-vfx-library 修改焚空裂隙：整体轮廓与尖角保持稳定，金色边缘沿裂口传播局部波动；先给我可调预览，不安装游戏。
```

```text
使用 $minecraft-xiantu-vfx-library 优化九霄剑决的四角剑流，使用现有实际剑模型，检查剑尖、拖尾挂点和人物穿插，先制作预览。
```

```text
使用 $minecraft-energy-vfx 制作绕手的白蓝灵气，目标为Forge 1.20.1 / Java17；保留现有动作，生成配方并说明需要接入的接口。
```

```text
使用 $minecraft-trails-beams 为现有剑模型增加金白拖尾，提供实际剑尖和剑柄挂点，检查暂停、取消及销毁后的清理。
```

能自动发现技能的环境也可用自然语言提出任务；名称相近时明确写`$skill-name`可减少误选。Skill是工作流程和可复用资源，不是固定的“一键完成全部游戏接入”命令。

## 4. 打开全部工作台或只打开仙途特效库

统一入口（Python3.10+）：

```powershell
python .\rendering\启动全部工作台.py
```

输入编号打开相应工作台，输入`0`关闭该启动器启动的服务。新增入口编号可能随仓库更新变化，以当前菜单为准。

只打开九组特效库：

```powershell
python .\skills\minecraft-xiantu-vfx-library\scripts\serve.py
```

浏览器打开后选择卡片即可。仅使用Python标准库；默认只监听`127.0.0.1`并选择空闲端口。保持终端运行，关闭页面不会自动关闭服务；在对应终端按Ctrl+C停止。需要自选端口或不自动打开浏览器时：

```powershell
python .\skills\minecraft-xiantu-vfx-library\scripts\serve.py --port 8765 --no-browser
```

请通过HTTP地址打开，直接双击HTML可能使浏览器拒绝本地模块、GLSL或贴图读取。实时预览主要需要WebGL2和浏览器硬件加速。

## 5. 修改、导出和保存

仙途库包括剑阵、金白剑气、五行气海、两类裂隙、灵气、闪电、护盾和拖尾。先调整该预览提供的参数；有“导出参数/配方”按钮的效果可导出JSON，再按其支持的方式导入。不是所有预览都使用同一个参数格式或都支持配方导入。

- 焚空裂隙：边缘呼吸强度控制局部边界波包，传播周期控制沿边缘运动；上下高度独立于动态波动。整体不做鼓起回落。
- 气海：修为控制旋转和亮度，灵气储量控制充盈；五行参数可分别调整，不读写玩家存档。
- 剑阵：密度、转速、时间、拖尾、辉光与挂点检查属于预览参数；10秒形态原型不等同后续15秒游戏战斗版本。

需要独立修改副本时：

```powershell
python .\skills\minecraft-xiantu-vfx-library\scripts\create_project.py .\my-vfx-library
python -m http.server 8765 --bind 127.0.0.1 --directory .\my-vfx-library
```

复制目标必须是新目录，以免覆盖已有设计。焚空裂隙共享算法为`cataclysm-rift/shader/rift-core.glsl`，修改后在副本内运行`python cataclysm-rift/scripts/build.py`，重新生成WebGL2和GLSL150，不只修改生成文件。

## 6. 验证和Minecraft接入

```powershell
python .\skills\minecraft-xiantu-vfx-library\scripts\check_library.py
python .\scripts\check_rendering_release.py
```

第一个检查九组便携资源；如果PATH中有Node.js，还会检查JS语法。第二个检查全仓库技能元数据和工作台HTTP资源。两者都不代表游戏或外部光影测试通过。

焚空裂隙还附桌面GPU检查脚本，需要自行准备moderngl、glfw、numpy、Pillow；可选`--video`另需FFmpeg。详见[技能入口](../skills/minecraft-xiantu-vfx-library/SKILL.md)。

接入游戏时应告诉Codex：Minecraft/加载器/Java版本、源码位置、效果挂载对象、持续时间、取消条件、是否需要敌我和伤害。Forge1.20.1示例通常面向Java17；其他版本需要适配。真实场景折射要接世界颜色和深度，游戏配置、网络同步、伤害与清理也需单独实现。

## 常见问题

| 问题 | 检查方式 |
|---|---|
| Codex找不到Skill | 检查技能目录下是否直接存在SKILL.md；检查CODEX_HOME；新会话重新加载 |
| 页面空白或资源报错 | 使用serve.py的HTTP地址，保留终端，检查WebGL2/硬件加速及页面错误提示 |
| 参数导出后游戏没变化 | JSON导出只保存设计，不自动安装；还需要宿主读取和游戏接入 |
| 显示很慢 | 降低分辨率、密度、体积采样或辉光质量，再对实际GPU耗时做检查 |
| 想改原有Skill | 先备份安装目录，修改项目副本，并保留第三方许可证及来源声明 |
