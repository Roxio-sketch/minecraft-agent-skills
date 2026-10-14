const $ = id => document.getElementById(id);
const kindNames = {algorithm:'算法与论文实现',engine:'引擎与工具',minecraft:'Minecraft 光影'};
const statusNames = {implemented:'项目已实现',researched:'已研究 · 待接入',gap:'待验证缺口'};
const state = {data:null,feature:null,query:'',kind:'all',license:'all',focusReturn:null};
let toastTimer;

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}
function text(value) {
  if (Array.isArray(value)) return value.map(text).join('；');
  if (value && typeof value === 'object') return Object.entries(value).map(([key,item]) => `${key}：${text(item)}`).join('；');
  return String(value ?? '未记录');
}
function url(value) {
  try { const parsed = new URL(value); return ['http:','https:'].includes(parsed.protocol) ? esc(parsed.href) : ''; }
  catch { return ''; }
}
function externalLink(value,label,className='') {
  const href = url(value);
  return href ? `<a href="${href}" target="_blank" rel="noopener noreferrer" class="${className}">${esc(label)} ↗</a>` : '';
}
function badge(status) { return `<span class="status-badge ${esc(status)}">${esc(statusNames[status] || status)}</span>`; }
function featureName(id) { return state.data.features.find(feature => feature.id === id)?.name || id; }
function licenseBadge(source) {
  const mode = source.license?.mode === 'open' ? 'open' : 'reference';
  return `<span class="license-badge ${mode}">${mode === 'open' ? '开放许可' : '仅参考研究'}</span>`;
}
function shortCost(value) {
  const full = text(value); const first = full.split(/[；;。\n]/)[0];
  return first.length > 11 ? `${first.slice(0,10)}…` : first;
}
function renderFeatures() {
  $('featureGrid').innerHTML = state.data.features.map((feature,index) => {
    const colors = (feature.palette || ['#304462','#ac8296']).map(color => /^#[0-9a-f]{3,8}$/i.test(color) ? color : '#304462');
    return `<button class="feature-card" data-feature="${esc(feature.id)}" aria-pressed="${state.feature === feature.id}" aria-label="筛选 ${esc(feature.name)} 的相关来源"><span class="feature-art" style="--sky-a:${colors[0]};--sky-b:${colors[1] || colors[0]}" aria-hidden="true"><span class="feature-number">${String(index + 1).padStart(2,'0')}</span></span><span class="feature-text"><strong>${esc(feature.name)}</strong><small>${esc(feature.summary)}</small><span class="feature-meta">${badge(feature.status)}<span title="${esc(text(feature.cost))}">${esc(shortCost(feature.cost))}</span></span></span></button>`;
  }).join('');
  $('featureGrid').insertAdjacentHTML('afterend','<p class="feature-art-note" id="featureArtNote">卡片色带为原创风格示意，不代表参考源码运行截图。</p>');
}
function renderFeatureDetail() {
  const feature = state.data.features.find(item => item.id === state.feature);
  document.querySelectorAll('[data-feature]').forEach(button => button.setAttribute('aria-pressed',String(button.dataset.feature === state.feature)));
  $('clearFeature').hidden = !feature;
  $('featureDetail').hidden = !feature;
  $('activeFeatureLabel').textContent = feature ? `效果筛选：${feature.name}` : '';
  if (!feature) { $('featureDetail').innerHTML = ''; return; }
  const authorLink=feature.status==='implemented'?'<a class="outline-button" href="./index.html?preset='+({atmosphere:'sunny',twilight:'sunset','sun-moon':'moonlit','thin-clouds':'overcast','volume-clouds':'cumulus',weather:'weatherstorm',fog:'fog','light-shafts':'shafts',rainbow:'rainbow',halos:'halo',special:'rift',cosmic:'cinematicblackhole'}[feature.id]||'sunny')+'">在工作台调整此效果 ↗</a>':'';
  $('featureDetail').innerHTML = `<div class="feature-detail-top"><h3 id="featureDetailTitle">${esc(feature.name)}</h3>${badge(feature.status)}</div><p>${esc(feature.summary)}</p><div class="detail-grid"><div><h4>算法方向</h4><p>${esc(text(feature.algorithm))}</p></div><div><h4>${feature.status==='implemented'?'当前可调参数':'候选可调参数'}</h4><div class="parameter-tags">${(feature.parameters || []).map(parameter => `<span>${esc(text(parameter))}</span>`).join('') || '<span>待补充</span>'}</div></div></div><div class="feature-integration"><strong>渲染预算：</strong>${esc(text(feature.cost))}<br><strong>Minecraft 接入：</strong>${esc(text(feature.integration))}</div>${authorLink}`;
}
function matches(source) {
  if (state.kind !== 'all' && source.kind !== state.kind) return false;
  if (state.license !== 'all' && source.license?.mode !== state.license) return false;
  if (state.feature && !(source.features || []).includes(state.feature) && !state.data.features.find(item => item.id === state.feature)?.sources?.includes(source.id)) return false;
  if (!state.query) return true;
  const haystack = `${text(source)} ${(source.features || []).map(featureName).join(' ')}`.toLocaleLowerCase();
  return state.query.toLocaleLowerCase().split(/\s+/).filter(Boolean).every(term => haystack.includes(term));
}
function renderSources() {
  const sources = state.data.sources.filter(matches);
  $('resultsCount').innerHTML = `显示 <strong>${sources.length}</strong> / ${state.data.sources.length} 个来源`;
  $('emptyState').hidden = sources.length > 0;
  const selected = state.data.features.find(item => item.id === state.feature);
  const unfilteredEmpty = !sources.length && selected && !state.query && state.kind === 'all' && state.license === 'all';
  $('emptyWorkbench').hidden = !(unfilteredEmpty && selected.status === 'implemented');
  $('emptyTitle').textContent = unfilteredEmpty && selected.status === 'implemented' ? '项目已有的原创渲染效果' : unfilteredEmpty && selected.status === 'gap' ? '这里仍是待验证的研究缺口' : '没有匹配的来源';
  $('emptyMessage').textContent = unfilteredEmpty && selected.status === 'implemented' ? '此类效果由项目独立实现，未列为外部参考源码。可直接回到工作台实时调整。' : unfilteredEmpty && selected.status === 'gap' ? '尚未收录满足当前精确源码证据要求的实现；下方算法方向用于后续核验。' : '试试更短的关键词，或放宽效果、类型和许可筛选。';
  $('sourceGrid').innerHTML = sources.map(source => `<article class="source-card"><div class="source-topline"><span>${esc(kindNames[source.kind] || source.kind)}</span>${licenseBadge(source)}</div><h3>${esc(source.name)}</h3><p>${esc(source.summary)}</p><div class="source-tags">${(source.features || []).map(id => `<span>${esc(featureName(id))}</span>`).join('')}</div><div class="source-bottom"><span>${(source.evidence || []).length} 条源码证据 · ${esc(source.license?.label || '许可待确认')}</span><button class="source-detail-button" data-source="${esc(source.id)}" aria-label="查看 ${esc(source.name)} 的源码详情">查看详情 <span aria-hidden="true">↗</span></button></div></article>`).join('');
}
function renderRoadmap() {
  $('roadmap').innerHTML = (state.data.roadmap || []).map((stage,index) => `<article class="roadmap-card"><span class="roadmap-index">${esc(stage.stage || String(index + 1).padStart(2,'0'))}</span><h3>${esc(stage.title)}</h3>${badge(stage.status||'researched')}<p>${esc(stage.reason)}</p><div class="roadmap-features">${(stage.features || []).map(id => `<span>${esc(featureName(id))}</span>`).join('')}</div></article>`).join('');
}
function listOrParagraph(value) {
  return Array.isArray(value) ? `<ul>${value.map(item => `<li>${esc(text(item))}</li>`).join('')}</ul>` : `<p>${esc(text(value))}</p>`;
}
function showSource(id,trigger) {
  const source = state.data.sources.find(item => item.id === id);
  if (!source) return;
  state.focusReturn = trigger;
  $('dialogBody').innerHTML = `<h2 id="dialogTitle">${esc(source.name)}</h2><div class="dialog-badges"><span>${esc(kindNames[source.kind] || source.kind)}</span>${licenseBadge(source)}<span>${esc(source.license?.label || '许可待确认')}</span></div><p class="dialog-summary">${esc(source.summary)}</p>${externalLink(source.url,'打开来源项目','dialog-link')}<section class="dialog-section"><h3>固定研究快照与许可</h3><dl class="source-metadata"><dt>Commit</dt><dd><code>${esc(text(source.commit))}</code></dd><dt>快照</dt><dd>${esc(text(source.snapshot))}</dd><dt>许可</dt><dd>${esc(text(source.license?.summary))}</dd></dl></section><section class="dialog-section"><h3>纹理与资源依赖</h3>${listOrParagraph(source.textureDependencies)}</section><section class="dialog-section"><h3>预算与 Minecraft 接入</h3><p><strong>预算：</strong>${esc(text(source.budget))}</p><p><strong>接入方向：</strong>${esc(text(source.integration))}</p></section><section class="dialog-section"><h3>精确源码证据</h3><div class="evidence-list">${(source.evidence || []).map(item => { const line = item.line ? `:${item.line}${item.endLine && item.endLine !== item.line ? `–${item.endLine}` : ''}` : ''; return `<article class="evidence-item"><div class="evidence-file"><span>${esc(item.file)}${esc(line)}</span>${externalLink(item.url,'查看原文')}</div><p>${esc(item.claim)}</p></article>`; }).join('') || '<p>暂无精确文件行证据。</p>'}</div></section><div class="validation-note">${esc(source.validation || '静态研究，未运行参考渲染器')}</div>`;
  $('sourceDialog').showModal();
  $('closeDialog').focus();
}
function resetFilters() {
  Object.assign(state,{query:'',kind:'all',license:'all',feature:null});
  $('searchInput').value = '';
  $('kindSelect').value = 'all';
  $('licenseSelect').value = 'all';
  renderFeatureDetail(); renderSources();
}
function toast(message) {
  clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false;
  toastTimer = setTimeout(() => { $('toast').hidden = true; },3500);
}
function exportIndex() {
  const blob = new Blob([JSON.stringify(state.data,null,2) + '\n'],{type:'application/json;charset=utf-8'});
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a'); anchor.href = objectUrl; anchor.download = `sky-research-${state.data.date || 'index'}.json`;
  document.body.append(anchor); anchor.click(); anchor.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl),1000);
  toast('已导出完整研究索引，包含全部效果、来源与证据。');
}
async function loadData() {
  $('loadError').hidden = true;
  try {
    const response = await fetch('./sky-library-data.json');
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    if (data.schema !== 'sky-research/1' || !Array.isArray(data.features) || !Array.isArray(data.sources)) throw new Error('索引结构与 sky-research/1 不匹配');
    state.data = data;
    document.querySelector('#featureArtNote')?.remove();
    renderFeatures(); renderFeatureDetail(); renderSources(); renderRoadmap();
    const openCount = data.sources.filter(source => source.license?.mode === 'open').length;
    $('libraryStats').innerHTML = `<span><strong>${data.features.length}</strong>类天空表现</span><span><strong>${data.sources.length}</strong>个源码来源</span><span><strong>${openCount}</strong>个开放许可</span>`;
    $('indexDate').textContent = `研究索引 · ${data.date}`;
    $('exportIndex').disabled = false;
  } catch (error) {
    $('loadError').hidden = false;
    $('loadErrorMessage').textContent = `请使用本项目启动器打开本地工作台，并确认 sky-library-data.json 与本页同在一个目录。${error.message}`;
    $('libraryStats').textContent = '本地研究索引尚未加载';
    $('resultsCount').textContent = '等待研究索引';
  }
}

$('searchForm').addEventListener('submit',event => event.preventDefault());
$('searchInput').addEventListener('input',event => { state.query = event.target.value.trim(); if (state.data) renderSources(); });
$('kindSelect').addEventListener('change',event => { state.kind = event.target.value; if (state.data) renderSources(); });
$('licenseSelect').addEventListener('change',event => { state.license = event.target.value; if (state.data) renderSources(); });
$('featureGrid').addEventListener('click',event => { const button = event.target.closest('[data-feature]'); if (!button || !state.data) return; state.feature = state.feature === button.dataset.feature ? null : button.dataset.feature; renderFeatureDetail(); renderSources(); });
$('clearFeature').addEventListener('click',() => { state.feature = null; renderFeatureDetail(); renderSources(); });
$('resetFilters').addEventListener('click',resetFilters);
$('sourceGrid').addEventListener('click',event => { const button = event.target.closest('[data-source]'); if (button) showSource(button.dataset.source,button); });
$('closeDialog').addEventListener('click',() => $('sourceDialog').close());
$('sourceDialog').addEventListener('click',event => { if (event.target === $('sourceDialog')) { const rect = $('sourceDialog').getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) $('sourceDialog').close(); } });
$('sourceDialog').addEventListener('close',() => state.focusReturn?.focus());
$('exportIndex').addEventListener('click',exportIndex);
$('retryLoad').addEventListener('click',loadData);
document.addEventListener('keydown',event => { if (event.key === '/' && !['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName) && !$('sourceDialog').open) { event.preventDefault(); $('searchInput').focus(); } });
loadData();
