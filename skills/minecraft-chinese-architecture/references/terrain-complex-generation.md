# 山地组团与地形整平

`generate_complex.py` 生成原创局部高度场、建筑平台、挡土面和坡路，把厅堂、门亭及多层楼阁组装成可检查的山地宗门总平面。它创建新的结构文件，不修改现有世界，不宣称复原具体宗门或历史寺观。

```text
python scripts/generate_complex.py --config assets/mountain-sect.json --out-dir generated/sect --name sect
python scripts/generate_complex.py --config assets/flat-axis-complex.json --out-dir generated/axis --name axis
```

配置为 `chinese-complex/1`，起点见两个JSON预设。`origin_xz`、`size_xz` 明确网格位置和范围，每轴32～512格，总模型仍受800万体素预算约束。`components` 有1～24项，包含唯一 `id`、`kind`、`position_xz`、顺时针 `turns`（0～3）和对应生成器的 `config`。组件可选building、tower、regional、landmark、garden、courtyard、multi_court、inhabited、infrastructure、settlement；后续八类必须有明确入口与内部目标，纯装饰用自由场景。`elevation`可选，未给时取平台原地形中位高度。`through: true`用于平台高1的开放亭或有明确穿行契约的牌楼、城门；不可把普通房屋当成开放通道。详见[组合接口](composition-and-materials.md)。

## 地形与路径

`terrain` 可给 `base`、`grade_z`、`wave` 和至多8个高斯山包 `hills`。也可只给 `heightmap`，读取NPZ中的整型 `height_zx` 和 `origin_xz`；尺寸、原点、海拔范围不一致直接拒绝。NPZ表示表面高度，不能还原洞穴、水、树、实体或原建筑。

每栋建筑的旋转后完整包围盒加 `pad_margin` 得到平台，平台不得重叠或越界；`max_pad_cut_fill` 限制单柱填挖高度。建筑底面位于平台上方，暴露的整平侧面加石砖。报告保留每台的原始填挖量，以及整个场地最终净填挖量；两类数值含义不同，不相加当施工工程量。

若整平区低于四周形成非设计的矩形凹坑，先调整建筑和入口标高，再把地形渐变或正式台基接到周围；不以补高室内地板解决。新增道路转角的全幅底面也须承托。现场改造及剖面验证见[街道与场地收口](street-site-finish.md)。

`connections` 明确连接 `entrance` 和 `ID.entry` / `ID.exit`。3/5/7格宽路径在X、Z、高度三个维度搜索，每横向一步最多升降1格。`max_path_grading` 约束路径对当前地表的改动。整个平台及已铺路径高度锁定，完整建筑包围盒避让；宽路带需要同时满足高度、避障和两格净空条件。寻找失败、预算耗尽、过紧回头弯造成高差冲突都会报错。此时增加场地与组件间隔或调整路径、海拔，不自动取消限制。

## 核验和交付

输出 `.schem`、`.npz`、预览、报告以及 `heightfields.npz`（原始及整平后高度）。报告记录平台、部件朝向、各层目标、路径中心线、节点数、填挖和所有碰撞回执。`test_terrain.py` 检查真实障碍绕行、不同路宽、固定标高、不可达/预算拒绝、负高度地形、文件往返和默认五建筑所有楼层的可达性。

B10默认样例为161×80×205格、603405个非空气体素；9个室内/楼层/楼梯平台目标可达。最终净削去5816格、填入5425格，5539个地形柱改变。这些是离线体素统计。人工预览检查了屋檐间距、平台接地与台阶轮廓。

限制：通路是保守的方块级地板/净空近似，楼梯和半砖没有完整玩家碰撞模拟；不保证无跳跃、无障碍通行或所有悬崖有护栏。挡土面是视觉构件，不是岩土稳定性验算。尚无真实游戏导入与方块更新验收。现有建筑、地形洞穴和水体需另行保留、调查及适配。
