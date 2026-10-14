"""Build an offline community reference library; never changes the VFX editor."""
from pathlib import Path
import json
from collections import Counter

ROOT = Path(__file__).resolve().parent
DATE = '2026-10-13'

ROUTES = [
    {
        'id': 'fire', 'title': '异火缠绕', 'kicker': '轨迹 + 火舌 + 余烬',
        'goal': '火带绕着身体或武器盘旋，同时有局部火舌翻卷、离体散出和尾端侵蚀。',
        'timeline': ['引火', '绕体聚拢', '持续缠绕', '喷发 / 收火'],
        'recipe': '先用螺旋路径上的带状网格或 ribbon 做大形，噪声侵蚀做火舌；少量离体粒子补运动。整体环绕和局部翻卷分别控制。',
        'parameters': ['缠绕半径', '圈数与节距', '转向 / 转速', '路径跟随强度', '火舌长度', '侵蚀阈值', '离体率', '余烬寿命', '颜色梯度', '触发阶段'],
        'query': ['Stylized Fire Ring', 'Fire Tornado', 'Stylized Fire Spell', 'Fire character', 'Simple Fire Shader'],
        'accept': '固定镜头可看出环绕方向；暂停整体路径后局部火舌仍运动；身体移动时留在世界中的烟火不会整团黏着。',
        'risk': '绕体半透明面会叠加过绘；近距只保留有轮廓贡献的层，体积火作为局部可选层。',
        'skills': ['minecraft-trails-beams', 'minecraft-energy-vfx', 'minecraft-procedural-particles'],
    },
    {
        'id': 'swords', 'title': '万剑归宗', 'kicker': '剑阵 + 朝向 + 分批齐射',
        'goal': '大量可辨认的剑从阵列显现，经盘旋和锁定后汇成剑流，分批冲向目标。',
        'timeline': ['显剑列阵', '盘旋蓄势', '朝向锁定', '分批齐射', '命中 / 回剑'],
        'recipe': '一组共享剑网格配合逐剑轨迹和阶段状态；轨迹先确定，再加拖尾、加速光带与命中环。优先控制阵形、节奏和朝向，而非只堆剑数量。',
        'parameters': ['剑数预算', '阵列形状', '阵列半径', '高度层次', '目标点', '锁定时长', '发射间隔', '速度曲线', '路径弯曲', '拖尾长度', '命中规模'],
        'query': ['万剑归宗', 'FlyingSwords', 'Hail of Swords', 'Holy Swords', 'Sword Storm'],
        'accept': '从正面、侧面都能读出阵形；剑身方向与运动一致；齐射有层次，命中后及时回收；测试多个施法者同屏。',
        'risk': '共享网格与批量数据是实施建议，尚未完成实例化后端；不要给每把装饰剑创建完整生物实体或独立体积烟。',
        'skills': ['minecraft-procedural-particles', 'minecraft-trails-beams', 'minecraft-animated-material-adapter'],
    },
    {
        'id': 'spirit', 'title': '灵剑', 'kicker': '剑身材质 + 挂点 + 剑气',
        'goal': '剑身有灵纹和内部流动，持握、出鞘、挥斩、御剑时表现连续且可区分。',
        'timeline': ['待机灵纹', '充能点亮', '出鞘 / 挥斩', '剑气离体', '回落'],
        'recipe': '把剑身材质、贴着刃口的光、真实刀刃双挂点拖尾和离体剑气分开。先保留原武器动作与变换，再接充能、显隐和发射阶段。',
        'parameters': ['灵纹显现', '内部流速', '边缘亮度', '两端挂点', '拖尾宽度曲线', '残留时间', '充能量', '剑气形状', '离体速度', '配色'],
        'query': ['AnimTrail / Sword Trail', 'Furnace Blade', 'Sword Flame Effect', 'StickWeaponTrailEffect', 'Soul Striker', 'Cyber sword', 'Fantasy Moon sword'],
        'accept': '慢动作挥剑时拖尾紧贴刀刃；遮挡和正反面正确；关闭光晕后剑气轮廓仍清晰；持握与掉落分别检查。',
        'risk': '特殊骨骼武器需适配真实模型挂点；屏幕 Bloom 与照亮场景分别接入，不能用同一个亮度参数代替。',
        'skills': ['minecraft-trails-beams', 'minecraft-animated-material-adapter', 'minecraft-glass-rim', 'minecraft-selective-bloom'],
    },
    {
        'id': 'alchemy', 'title': '炼丹炉特效', 'kicker': '炉火 + 药气 + 炼化时间线',
        'goal': '炉底火、投料、炼化、符阵、凝丹和出丹共同表达炼丹进度与成功失败。',
        'timeline': ['点火温炉', '药材悬浮', '吸入炼化', '沸腾药气', '符阵激活', '凝丹收束', '出丹 / 失败喷发'],
        'recipe': '按状态时间线控制炉底、炉口、炉身符纹和悬浮丹丸几个挂点。烟用动画粒子和低层数网格，凝聚用螺旋轨迹；只有模型有开放液面时才做药液。',
        'parameters': ['火候', '炉口散发率', '药气升速', '涡旋强度', '投料轨迹', '符纹显现', '凝丹半径', '阶段时长', '品质配色', '失败爆发'],
        'query': ['Halloween Special', 'Bubbling Cauldron', 'Whirlpool', 'Magic Explosion', 'Vortex Cauldron', 'Smoke Cauldron'],
        'accept': '只看效果就能区分温炉、炼化、凝丹和失败；闲置时烟火容量稳定；多炉同时炼丹仍有数量上限。',
        'risk': 'Houdini 的 sparse pyro / Alembic 先用于形态学习或烘焙，不能直接当作实时 Forge 后端。',
        'skills': ['minecraft-energy-vfx', 'minecraft-procedural-particles', 'minecraft-trails-beams', 'minecraft-dynamic-lighting'],
    },
]

# These are design directions, not claims that the exact effects are implemented.
DIRECTIONS = {
    '异火': ['螺旋缠身', '盘绕武器', '手心聚火', '火龙吐息', '火龙卷', '地面火阵', '活体火灵', '火莲爆发'],
    '剑阵': ['环形剑阵', '分层悬剑', '扇形齐射', '剑流汇聚', '天降剑雨', '旋转剑风暴', '巨剑落斩', '回剑入鞘'],
    '灵剑': ['灵纹逐段点亮', '剑身流动材质', '刃口边缘光', '真实挥剑拖尾', '弧形剑气', '离体能量剑', '魂火缠剑', '御剑伴飞'],
    '炼丹': ['点火温炉', '药材悬浮', '螺旋投料', '药液沸腾', '炉口药气', '炉身符阵', '凝丹显现', '失败喷发'],
    '拓展': ['雷劫落雷', '绕体电流', '冰锥生长', '寒冰领域', '水系冲击', '吸入水涡', '藤蔓束缚', '灵蛇群攻', '莲花灵气', '治疗升光', '多层结界', '符纸飞散', '鎏金水墨', '空间裂隙', '传送显隐', '飞升突破'],
}

def group_for(filename):
    return {'fire-sources.json':'fire', 'sword-sources.json':'sword', 'alchemy-sources.json':'alchemy',
            'extension-sources.json':'extension', 'community-sources.json':'community'}[filename]

def main():
    sources, seen = [], set()
    files = ['fire-sources.json','sword-sources.json','alchemy-sources.json','extension-sources.json','community-sources.json']
    for filename in files:
        entries = json.loads((ROOT / filename).read_text(encoding='utf-8-sig'))
        for entry in entries:
            assert all(entry.get(k) for k in ['title','url','author','platform','kind','category','learn','license','evidence']), (filename,entry)
            assert entry['url'].startswith('https://'), entry['url']
            canonical = entry['url'].rstrip('/')
            if canonical in seen:
                continue
            seen.add(canonical)
            sources.append(dict(entry, id=f'S{len(sources)+1:03}', group=group_for(filename), checkedAt=DATE))
    for route in ROUTES:
        route['sources'] = list(dict.fromkeys(s['id'] for q in route['query'] for s in sources if q.casefold() in s['title'].casefold()))[:7]
        assert route['sources'], route['title']
    data = {'schema':'cultivation-vfx-community-library/1','date':DATE,'status':'社区资料与迁移建议；尚未制作或实装这些新效果',
            'sources':sources,'routes':ROUTES,'directions':DIRECTIONS,'directionCount':sum(map(len,DIRECTIONS.values()))}
    (ROOT / 'sources.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    html = (ROOT / 'library.template.html').read_text(encoding='utf-8')
    payload = json.dumps(data,ensure_ascii=False).replace('<','\\u003c')
    (ROOT / 'index.html').write_text(html.replace('__LIBRARY_DATA__',payload),encoding='utf-8')
    byid = {s['id']:s for s in sources}
    groups = Counter(s['group'] for s in sources)
    lines = [f'# 修仙特效社区学习参考库\n\n核查日期：{DATE}。共 {len(sources)} 条去重来源、{data["directionCount"]} 个可拓展方向，围绕异火缠绕、万剑归宗、灵剑、炼丹炉优先组织。',
        '本轮已检索并阅读作者原帖、教程正文、作品描述或源码入口；具体核查深度见每条资料。多数视频尚未完整观看，未制作这些新特效，也未在 ASTRA-TEST 验证。下面的组合方案是面向现有 Forge 1.20.1 项目的实施建议。',
        '参考库独立保存。现有 48 参数编辑台与桌面启动器未修改。双击本目录 index.html 可离线搜索、按类型筛选、收藏、写笔记并导出选定学习计划。导出 JSON 是学习计划，不是 MOD 配方。',
        f'来源分组：异火 {groups["fire"]}；剑阵/灵剑 {groups["sword"]}；炼丹 {groups["alchemy"]}；拓展教程 {groups["extension"]}；中文社区作品 {groups["community"]}。同一来源不按镜像页、预设数量或视频集数重复计数；作者单独发布的作品或元素变体保留独立条目，并在说明中标出关联。这是学习条目数，不是独立算法数。',
        '## 四个重点\n']
    for r in ROUTES:
        lines += [f'### {r["title"]}\n',r['goal'], '**建议的制作路径：** '+r['recipe'],
            '**阶段：** '+' → '.join(r['timeline']), '**可编辑参数：** '+'、'.join(r['parameters'])+'。',
            '**先看：** '+'；'.join(f'[{byid[i]["title"]}]({byid[i]["url"]})' for i in r['sources'])+'。',
            '**后续验收：** '+r['accept'], '**迁移边界：** '+r['risk'],
            '**现有 Skill 接入候选：** '+'、'.join(f'[{name}](C:/Users/MAINTAINER/.codex/skills/{name}/SKILL.md)' for name in r['skills'])+'。这些是能力分工候选，不表示此轮已执行接入。\n']
    lines += ['## 建议顺序\n',
        '1. 先做一个有完整时间线的异火缠绕，验证路径、局部火舌、离体散发及近距轮廓。它能复用到灵剑和炉底丹火。',
        '2. 接一把灵剑，确认原动作与刀刃挂点，再制作充能、挥剑拖尾和离体剑气。',
        '3. 在单剑轨迹正确后扩展万剑归宗，先完成小阵列的列阵、锁定、分批齐射，再扩大数量。',
        '4. 炼丹炉用多阶段事件控制复用丹火、药气、螺旋路径、溶解与符阵，最后补成功和失败分支。',
        '此顺序是为了先建立能复用的运动与材质模块。研究材料中提到的 Niagara、VFX Graph、Shader Graph 和 Blueprint 不能直接导入 Forge，需要重新实现轨迹、网格、材质和生命周期。',
        '## 种类拓展\n\n以下 48 项是组合设计方向，不代表找到 48 套同名现成包，也不代表它们已实现。对应基础方法和原始作品在后面的来源表中。']
    for group, directions in DIRECTIONS.items():
        lines.append(f'- **{group}（{len(directions)}）**：'+'、'.join(directions)+'。')
    lines += ['\n## 性能取舍\n',
        '结合此前第三人称近距、全屏和积累后移动的卡顿反馈，建议把清晰主体交给网格、带状几何和受控粒子。烟、发光、扭曲与局部体积独立开关，避免所有层都铺满屏幕。',
        '按近距、普通视距、远距分别控制数量和覆盖；技能结束、失去目标和传送时回收残留。先验证单技能，再验证剑阵、多炉及多个施法者同屏。',
        '以后每个原型都应同时记录 CPU/GPU 帧耗时、同屏数量、透明覆盖和视觉对照；本轮没有测得这些新效果的帧率，不能承诺上述设计已经消除卡顿。',
        '## 使用范围与许可\n',
        '作者原作主要用于学习造型、节奏和构图；教程用于学习技术；只有确有许可且检查到相应实现的源码才是直接复用候选。未声明许可、All rights reserved、商业包均不因为公开展示而成为可复制资产。',
        'mattatz 体积火仓库有 MIT 根许可证，但本轮尚未读其具体 shader；Cyanilux 主页对帖内代码/Graph/C# 声明 CC0，教程文字和其他资产范围应分别检查。AsaelFernandez/Shaders 虽标 MIT，当前目录只有 README 与 LICENSE，没有 shader 实现，资料库将其列为作品。',
        '没有下载、购买或打包外部商业素材；没有新建 Skill。等实际完成跨场景验证后，再决定将通用轨迹、阶段控制和预算策略写入已有 Skill，或为独立后端建立新 Skill。',
        '## 全部来源\n']
    group_titles={'fire':'异火与灵气','sword':'剑阵、灵剑与剑气','alchemy':'炼丹炉与药液','extension':'元素、符阵与空间拓展','community':'中文社区原创参考'}
    for g,title in group_titles.items():
        lines.append(f'### {title}\n')
        for s in sources:
            if s['group'] != g: continue
            lines += [f'**{s["id"]} · [{s["title"]}]({s["url"]})** — {s["author"]} / {s["platform"]} / {s["kind"]}',
                f'- 种类：{s["category"]}',f'- 学什么：{s["learn"]}',f'- 核查：{s["evidence"]}',f'- 许可：{s["license"]}\n']
    (ROOT / '修仙特效学习路线.md').write_text('\n\n'.join(lines),encoding='utf-8')
    print(json.dumps({'sources':len(sources),'groups':dict(groups),'directions':data['directionCount']},ensure_ascii=False))

if __name__ == '__main__':
    main()
