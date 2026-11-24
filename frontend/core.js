/* =========================================================
   计件通 · 核心数据与通用组件
   ========================================================= */
'use strict';

/* ------------------------- 常量 ------------------------- */
const SERVER_MODE = window.JITONG_MODE === 'server';
const DB_KEY = 'jitong_db_v3';
const SESSION_KEY = 'jitong_session_v3';
const DB_VERSION = 3;

const PROCESS_NAMES = [
  '排版', '写真打印', '写真过板', '写真覆膜', '裁切', '写真亚克力加工',
  '特殊亚克力排版', '亚克力排版', '打印', '过胶', 'UV雪弗板覆膜', '覆膜',
  '激光切割', 'UV雪弗板裁切', '雪弗板切割', '特殊亚克力加工',
];

function skillCatalog() {
  const catalog = (db.skills || [])
    .filter((s) => s.active !== false)
    .map((s) => ({ name: s.name, label: s.displayName || s.name }));
  if (catalog.length) return catalog;
  return PROCESS_NAMES.map((name) => ({ name, label: name }));
}

const LINES = {
  '写真': {
    key: 'photo', label: '写真线', short: '写真', color: '#1467e8', cls: 'line-photo',
    steps: ['排版', '打印', '过版覆膜', '裁切', '综合'],
    shortSteps: ['排版', '打印', '覆膜', '裁切', '综合'],
  },
  '亚克力': {
    key: 'acrylic', label: '亚克力线', short: '亚克力', color: '#f79009', cls: 'line-acrylic',
    steps: ['排图', 'UV打印', '过胶', '裁切', '综合'],
    shortSteps: ['排图', 'UV', '过胶', '裁切', '综合'],
  },
};
const ALL_STEPS = [1, 2, 3, 4, 5];
let db;

const EMPLOYEES = [
  { id: 'E001', name: '员工01', role: '综合岗位', avatar: '员', skills: [{ line: '写真', steps: [5] }, { line: '亚克力', steps: [5] }] },
  { id: 'E002', name: '员工02', role: '综合岗位', avatar: '员', skills: [{ line: '写真', steps: [5] }, { line: '亚克力', steps: [5] }] },
  { id: 'E003', name: '员工03', role: '综合岗位', avatar: '员', skills: [{ line: '写真', steps: [5] }, { line: '亚克力', steps: [5] }] },
  { id: 'E004', name: '员工04', role: '裁切（激光切割）', avatar: '员', skills: [{ line: '亚克力', steps: [4] }] },
  { id: 'E005', name: '员工05', role: '多能工（全线）', avatar: '员', skills: [{ line: '写真', steps: [1, 2, 3, 4, 5] }, { line: '亚克力', steps: [1, 2, 3, 4, 5] }] },
  { id: 'E006', name: '员工06', role: '裁切（激光切割）', avatar: '员', skills: [{ line: '写真', steps: [4] }, { line: '亚克力', steps: [4] }] },
  { id: 'E007', name: '员工07', role: '多能工（全线）', canCreateJob: true, avatar: '员', skills: [{ line: '写真', steps: [1, 2, 3, 4, 5] }, { line: '亚克力', steps: [1, 2, 3, 4, 5] }] },
  { id: 'E008', name: '员工08', role: 'UV打印', avatar: '员', skills: [{ line: '亚克力', steps: [2] }] },
  { id: 'E009', name: '员工09', role: '过膜过板', avatar: '员', skills: [{ line: '写真', steps: [3] }, { line: '亚克力', steps: [3] }] },
  { id: 'E010', name: '员工10', role: '过膜过板', avatar: '员', skills: [{ line: '写真', steps: [3] }, { line: '亚克力', steps: [3] }] },
  { id: 'E011', name: '员工11', role: '亚克力排图', canCreateJob: true, avatar: '员', skills: [{ line: '亚克力', steps: [1] }] },
  { id: 'E012', name: '员工12', role: '亚克力排图', canCreateJob: true, avatar: '员', skills: [{ line: '亚克力', steps: [1] }] },
  { id: 'E013', name: '员工13', role: '写真/异型（打印）', avatar: '员', skills: [{ line: '写真', steps: [2] }] },
  { id: 'E014', name: '员工14', role: '综合岗位', avatar: '员', skills: [{ line: '写真', steps: [5] }, { line: '亚克力', steps: [5] }] },
  { id: 'E015', name: '员工15', role: '综合岗位', avatar: '员', skills: [{ line: '写真', steps: [5] }, { line: '亚克力', steps: [5] }] },
  { id: 'E016', name: '员工16', role: '写真/异型（排版）', canCreateJob: true, avatar: '员', skills: [{ line: '写真', steps: [1] }] },
  { id: 'E017', name: '员工17', role: '裁切（激光切割）', avatar: '员', skills: [{ line: '写真', steps: [4] }, { line: '亚克力', steps: [4] }] },
];

const ADMIN = {
  id: 'A001', name: '厂长', role: '厂长 · 管理员', avatar: '厂', isAdmin: true,
  skills: [{ line: '写真', steps: [1, 2, 3, 4, 5] }, { line: '亚克力', steps: [1, 2, 3, 4, 5] }],
};
const USERS = [...EMPLOYEES, ADMIN];

/* ------------------------- 工具 ------------------------- */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const pad2 = (n) => String(n).padStart(2, '0');
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[ch]));
const icon = (name, cls = '') => `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const uid = () => (crypto.randomUUID ? crypto.randomUUID() : `id-${Date.now()}-${Math.random().toString(16).slice(2)}`);

function dateKey(d = new Date()) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}
function dateLabel(d = new Date()) {
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getMonth() + 1}月${d.getDate()}日 周${week}`;
}
function timeLabel(iso) {
  const d = new Date(iso);
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}
function dayLabel(iso) {
  const d = new Date(iso);
  const today = new Date();
  const key = dateKey(d);
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (key === dateKey(today)) return '今天';
  if (key === dateKey(yesterday)) return '昨天';
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}
function daysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d;
}
function at(days, hour, minute) {
  const d = daysAgo(days);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}
function dueLabel(value) {
  if (!value) return '未设置';
  if (value === dateKey()) return '今天';
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (value === dateKey(tomorrow)) return '明天';
  const d = new Date(value + 'T00:00:00');
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}
function greeting() {
  const h = new Date().getHours();
  if (h < 6) return '凌晨好';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
}
function fmtQty(n) {
  const num = Number(n);
  if (!Number.isFinite(num)) return '0';
  return num.toFixed(2).replace(/\.?0+$/, '');
}
function round3(n) { return Math.round((Number(n) + Number.EPSILON) * 1000) / 1000; }
function lineInfo(line) { return LINES[line] || LINES['写真']; }
function lineClass(line) { return line === '亚克力' ? 'line-acrylic' : 'line-photo'; }
function stepName(line, no) { return lineInfo(line).steps[no - 1] || ''; }
function shortStepName(line, no) { return lineInfo(line).shortSteps[no - 1] || ''; }
function userById(id) { return (db?.employees || []).find((u) => u.id === id) || USERS.find((u) => u.id === id); }
function jobById(id) { return db.jobs.find((j) => j.id === id); }

function canReport(user, line, stepNo) {
  if (!user) return false;
  if (user.isAdmin) return true;
  return user.skills.some((s) => s.line === line && s.steps.includes(Number(stepNo)));
}
function skillChips(user) {
  if (!user) return '';
  if (user.isAdmin) return '<span>全线 · 全工序</span>';
  return user.skills.map((s) => `<span>${esc(s.line)} · ${s.steps.map((n) => shortStepName(s.line, n)).join('/')}</span>`).join('');
}

/* ------------------------- 数据 ------------------------- */
function buildSeed() {
  const stamp = dateKey().replace(/-/g, '');
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const afterTomorrow = new Date();
  afterTomorrow.setDate(afterTomorrow.getDate() + 2);

  const jobs = [
    {
      id: 'J001', orderNo: `TB${stamp}001`, line: '写真',
      product: '奶茶店灯箱海报', spec: '1200 × 2400 mm · 10 张',
      customer: '淘宝 · 茶屿设计', plannedQty: 28.8, unit: 'm²',
      due: dateKey(), priority: '加急', status: 'active', qr: `QJ-${stamp}-001`,
      steps: [
        { no: 1, planned: 28.8, unit: 'm²' }, { no: 2, planned: 28.8, unit: 'm²' },
        { no: 3, planned: 28.8, unit: 'm²' }, { no: 4, planned: 28.8, unit: 'm²' },
        { no: 5, planned: 10, unit: '件' },
      ],
    },
    {
      id: 'J002', orderNo: `TB${stamp}002`, line: '亚克力',
      product: '商场楼层导视牌', spec: '600 × 900 mm · 12 块',
      customer: '淘宝 · 方寸标识', plannedQty: 12, unit: '件',
      due: dateKey(tomorrow), priority: '正常', status: 'active', qr: `QJ-${stamp}-002`,
      steps: ALL_STEPS.map((no) => ({ no, planned: 12, unit: '件' })),
    },
    {
      id: 'J003', orderNo: `TB${stamp}003`, line: '写真',
      product: '展会背胶画面', spec: '1000 × 2000 mm · 8 张',
      customer: '线下 · 云栖会展', plannedQty: 15.6, unit: 'm²',
      due: dateKey(afterTomorrow), priority: '正常', status: 'active', qr: `QJ-${stamp}-003`,
      steps: [
        { no: 1, planned: 15.6, unit: 'm²' }, { no: 2, planned: 15.6, unit: 'm²' },
        { no: 3, planned: 15.6, unit: 'm²' }, { no: 4, planned: 15.6, unit: 'm²' },
        { no: 5, planned: 6, unit: '件' },
      ],
    },
    {
      id: 'J004', orderNo: `TB${stamp}004`, line: '亚克力',
      product: '新品展示架', spec: '300 × 400 × 120 mm · 6 套',
      customer: '淘宝 · 木马陈列', plannedQty: 6, unit: '套',
      due: dateKey(daysAgo(1)), priority: '正常', status: 'done', qr: `QJ-${stamp}-004`,
      steps: ALL_STEPS.map((no) => ({ no, planned: 6, unit: '套' })),
    },
  ];

  let seq = 0;
  const rec = (jobId, stepNo, employeeId, qty, unit, iso, status = 'valid', note = '') => ({
    id: `R${String(++seq).padStart(4, '0')}`,
    jobId, stepNo, employeeId, qty: Number(qty), unit, at: iso, status, note,
    source: 'scan', synced: true, photo: false,
  });

  const records = [
    rec('J001', 1, 'E007', 28.8, 'm²', at(0, 8, 5)),
    rec('J001', 2, 'E013', 12, 'm²', at(0, 8, 42)),
    rec('J001', 2, 'E013', 6, 'm²', at(0, 9, 30)),
    rec('J001', 2, 'E013', 2, 'm²', at(0, 9, 55), 'pending', '客户临时加印，补录待确认'),
    rec('J002', 1, 'E011', 12, '件', at(0, 8, 15)),
    rec('J002', 2, 'E008', 12, '件', at(0, 8, 55)),
    rec('J002', 3, 'E009', 12, '件', at(0, 9, 20)),
    rec('J002', 4, 'E004', 4, '件', at(0, 9, 45)),
    rec('J004', 1, 'E011', 6, '套', at(1, 10, 10)),
    rec('J004', 2, 'E008', 6, '套', at(1, 11, 0)),
    rec('J004', 3, 'E009', 6, '套', at(1, 13, 30)),
    rec('J004', 4, 'E017', 6, '套', at(1, 15, 0)),
    rec('J004', 5, 'E001', 6, '套', at(1, 16, 20)),
    rec('J003', 1, 'E016', 15.6, 'm²', at(2, 9, 0)),
    rec('J003', 2, 'E013', 15.6, 'm²', at(2, 11, 0)),
    rec('J003', 3, 'E010', 15.6, 'm²', at(2, 14, 0)),
    rec('J004', 5, 'E014', 2, '套', at(3, 16, 40), 'void', '重复报工，已冲销'),
  ];

  return {
    version: DB_VERSION,
    createdAt: new Date().toISOString(),
    employees: USERS,
    jobs,
    records,
    corrections: [],
    settings: { tolerance: 0.05, duplicateMinutes: 10 },
  };
}

function loadDB() {
  if (SERVER_MODE) {
    return { employees: [], jobs: [], records: [], corrections: [], settings: { duplicateMinutes: 10, tolerance: 0.05 } };
  }
  try {
    const raw = localStorage.getItem(DB_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && parsed.version === DB_VERSION && Array.isArray(parsed.jobs)) {
        parsed.corrections = parsed.corrections || [];
        parsed.settings = parsed.settings || { tolerance: 0.05, duplicateMinutes: 10 };
        return parsed;
      }
    }
  } catch (err) {
    console.warn('读取本地数据失败，将重建演示数据', err);
  }
  const seeded = buildSeed();
  try {
    localStorage.setItem(DB_KEY, JSON.stringify(seeded));
  } catch (err) {
    console.warn('浏览器禁用了本地存储，本次使用内存数据', err);
  }
  return seeded;
}
function saveDB() {
  if (SERVER_MODE) return;
  try { localStorage.setItem(DB_KEY, JSON.stringify(db)); }
  catch (err) { console.warn('保存失败', err); toast('本地存储空间不足，请先导出备份', 'error'); }
}
function resetDB() {
  if (SERVER_MODE) return;
  try { localStorage.removeItem(DB_KEY); } catch (_) { /* ignore */ }
  db = buildSeed();
  saveDB();
}

db = loadDB();

/* ------------------------- 状态 ------------------------- */
const state = {
  screen: 'home',
  employee: null,
  entry: null,
  lastRecord: null,
  detailJob: 'J001',
  recordFilter: 'all',
  statPeriod: 'today',
  orderTab: 'active',
  orderSearch: '',
  adminTab: 'overview',
  adminOrderFilter: 'all',
  loginError: '',
};

/* ------------------------- 业务计算 ------------------------- */
function validRecords() { return db.records.filter((r) => r.status === 'valid'); }
function pendingRecords() { return db.records.filter((r) => r.status === 'pending'); }
function recordsForStep(job, stepNo) {
  return db.records.filter((r) => r.jobId === job.id && r.stepNo === Number(stepNo) && r.status !== 'void');
}
function stepDone(job, stepNo) {
  return db.records.filter((r) => r.jobId === job.id && r.stepNo === Number(stepNo) && r.status === 'valid')
    .reduce((sum, r) => sum + Number(r.qty || 0), 0);
}
function stepDoneByUnit(job, stepNo) {
  const step = job.steps.find((s) => s.no === Number(stepNo));
  if (!step) return 0;
  return db.records.filter((r) => r.jobId === job.id && r.stepNo === Number(stepNo) && r.status === 'valid' && r.unit === step.unit)
    .reduce((sum, r) => sum + Number(r.qty || 0), 0);
}
function stepPlanned(job, stepNo) { return job.steps.find((s) => s.no === Number(stepNo))?.planned || 0; }
function stepUnit(job, stepNo) { return job.steps.find((s) => s.no === Number(stepNo))?.unit || job.unit; }
function stepHasPlan(step) { return Number(step?.planned || 0) > 0; }
function stepComplete(job, step) {
  const done = stepDone(job, step.no);
  return stepHasPlan(step) ? done + 0.0001 >= Number(step.planned) : done > 0;
}

function maxReportable(job, stepNo) {
  const step = job.steps.find((s) => s.no === Number(stepNo));
  if (!step) return 0;
  const done = stepDoneByUnit(job, step.no);
  const plannedLeft = stepHasPlan(step) ? Math.max(0, Number(step.planned) - done) : null;
  if (step.no === 1) return plannedLeft === null ? Infinity : round3(plannedLeft);
  const upStep = job.steps.find((s) => s.no === step.no - 1);
  if (!upStep) return plannedLeft === null ? Infinity : round3(plannedLeft);
  const upDone = stepDoneByUnit(job, upStep.no);
  if (upStep.unit !== step.unit) return plannedLeft === null ? Infinity : round3(plannedLeft);
  let available = Math.max(0, upDone - done);
  if (plannedLeft !== null) available = Math.min(available, plannedLeft);
  return round3(available);
}

function currentStepNo(job) {
  for (const step of job.steps) {
    if (!stepComplete(job, step)) return step.no;
  }
  return job.steps.length || 1;
}
function completedStepCount(job) {
  return job.steps.filter((s) => stepComplete(job, s)).length;
}
function jobProgress(job) {
  const total = job.steps.length || 1;
  return Math.round((completedStepCount(job) / total) * 100);
}
function isJobDone(job) { return completedStepCount(job) === job.steps.length; }

function findEligibleStep(job, user) {
  for (const step of job.steps) {
    if (!canReport(user, job.line, step.no)) continue;
    if (stepComplete(job, step)) continue;
    if (step.no === 1) return step;
    if (stepDone(job, step.no - 1) > 0) return step;
  }
  return null;
}

function unitTotals(records) {
  const totals = {};
  for (const r of records) {
    if (r.status !== 'valid') continue;
    totals[r.unit] = round3((totals[r.unit] || 0) + Number(r.qty || 0));
  }
  return totals;
}
function totalsText(totals) {
  const entries = Object.entries(totals).filter(([, v]) => v > 0);
  if (!entries.length) return '0';
  return entries.map(([unit, value]) => `${fmtQty(value)} ${unit}`).join(' · ');
}
function primaryUnit(totals) {
  for (const unit of ['m²', '件', '套', '块']) if ((totals[unit] || 0) > 0) return unit;
  return Object.keys(totals)[0] || '件';
}

function periodStart(period) {
  const now = new Date();
  if (period === 'today') return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (period === 'week') {
    const day = now.getDay() || 7;
    return new Date(now.getFullYear(), now.getMonth(), now.getDate() - day + 1);
  }
  return new Date(now.getFullYear(), now.getMonth(), 1);
}

function totalsForUser(userId, period = 'today') {
  const from = periodStart(period);
  const records = validRecords().filter((r) => r.employeeId === userId && new Date(r.at) >= from);
  return { records, totals: unitTotals(records) };
}
function todayRecordsFor(userId) {
  return db.records.filter((r) => r.employeeId === userId && new Date(r.at).toDateString() === new Date().toDateString());
}
function activeJobs() { return db.jobs.filter((j) => j.status !== 'void' && !isJobDone(j)); }
function jobsForLine(line) { return db.jobs.filter((j) => j.line === line && j.status !== 'void'); }

/* ------------------------- 通用组件 ------------------------- */
function topbar(title, back = 'home', right = '') {
  return `<header class="topbar">
    <button class="icon-btn ghost" data-action="go" data-screen="${back}" aria-label="返回">${icon('left')}</button>
    <div><div class="topbar-title">${esc(title)}</div></div>
    <div class="spacer"></div>${right}
  </header>`;
}

function bottomNav(active) {
  const items = [['home', 'home', '首页'], ['orders', 'orders', '订单'], ['stats', 'chart', '统计'], ['profile', 'user', '我的']];
  return `<nav class="bottom-nav">
    ${items.map(([key, ic, label]) => `<button class="nav-item ${active === key ? 'active' : ''}" data-action="go" data-screen="${key}">${icon(ic)}<span>${label}</span></button>`).join('')}
  </nav>`;
}

function lineChip(line, extra = '') {
  return `<span class="chip ${line === '亚克力' ? 'chip-amber' : 'chip-blue'}">${esc(line)}线${extra ? ' · ' + esc(extra) : ''}</span>`;
}
function statusBadge(record) {
  if (record.status === 'pending') return '<span class="chip chip-amber"><i class="dot"></i>待确认</span>';
  if (record.status === 'void') return '<span class="chip chip-red"><i class="dot"></i>已冲销</span>';
  if (record.synced === false) return '<span class="chip chip-gray"><i class="dot"></i>待同步</span>';
  return '<span class="chip chip-green"><i class="dot"></i>已计入</span>';
}
function progressBar(job) {
  return `<div class="progress-track"><div class="progress-fill" style="width:${jobProgress(job)}%"></div></div>`;
}

function jobCardHTML(job, opts = {}) {
  const current = currentStepNo(job);
  const done = isJobDone(job);
  const eligible = state.employee ? findEligibleStep(job, state.employee) : null;
  return `<button class="card job-card ${lineClass(job.line)}" data-action="${opts.action || 'open-job'}" data-job="${job.id}">
    <div class="job-top">
      <div class="job-title"><strong>${esc(job.orderNo)}</strong><span>${esc(job.remark || `${job.line}线 · ${job.unit}`)}${job.meters > 0 ? ` · ${fmtQty(job.meters)} 米` : ''}</span></div>
      ${done ? '<span class="chip chip-green"><i class="dot"></i>已完成</span>' : lineChip(job.line)}
    </div>
    <div class="job-progress">
      <div class="progress-meta"><span>${done ? '全部工序完成' : `当前：${esc(job.steps.find((s) => s.no === current)?.name || stepName(job.line, current))}`}</span><b>${completedStepCount(job)}/${job.steps.length} 工序</b></div>
      ${progressBar(job)}
    </div>
    <div class="mini-steps">
      ${job.steps.map((step) => {
        const complete = stepComplete(job, step);
        const active = !done && step.no === current;
        return `<span class="mini-step ${complete ? 'done' : ''} ${active ? 'active' : ''}"><i></i>${esc(step.name || stepName(job.line, step.no))}</span>`;
      }).join('')}
    </div>
    ${eligible ? `<div class="job-foot-hint">${icon('scan')} 你可报「${esc(eligible.name || stepName(job.line, eligible.no))}」</div>` : ''}
  </button>`;
}

/* 少量页面级样式补充 */
const pageStyle = document.createElement('style');
pageStyle.textContent = `
  .job-foot-hint { display:flex; align-items:center; gap:4px; margin-top:9px; padding-top:8px; border-top:1px dashed #edf1f5; font-size:9.5px; color:var(--primary); font-weight:600; }
  .job-foot-hint .icon { width:12px; height:12px; }
  .hero-avatar { width:34px; height:34px; border-radius:10px; display:grid; place-items:center; background:rgba(255,255,255,.14); border:1px solid rgba(255,255,255,.18); color:#fff; font-size:12px; font-weight:700; }
  .pending-card { margin:10px 16px 0; padding:11px 13px; border-radius:12px; background:#fff8ec; border:1px solid #ffe2b8; display:flex; align-items:center; gap:9px; color:#8a4b00; font-size:10.5px; width:calc(100% - 32px); text-align:left; }
  .pending-card .icon { width:16px; height:16px; flex:0 0 auto; }
  .pending-card b { font-family:var(--num); }
  .pending-card .spacer { flex:1; }
  .note-preview { margin:10px 16px 0; display:flex; gap:8px; align-items:center; padding:7px 9px; border-radius:9px; background:#f4f8fd; border:1px solid #e2edfb; font-size:10px; color:#46617f; }
  .note-preview .icon { width:14px; height:14px; color:var(--primary); }
  .note-preview .spacer { flex:1; }
  .note-preview button { font-size:9.5px; color:var(--primary); }
  .photo-thumb { width:28px; height:28px; border-radius:6px; object-fit:cover; }
  .success-ring.pending .inner { background:var(--orange); box-shadow:0 8px 22px rgba(247,144,9,.28); }
  .success-ring.pending { background:var(--orange-soft); }
  .success-ring.pending::before { border-color:#ffe3bd; }
  .still *, .still *::before, .still *::after { animation:none!important; transition:none!important; }
  .admin-empty { padding:24px 12px; text-align:center; color:var(--muted); font-size:10px; }
  .gap-note { margin-top:9px; padding:8px 10px; border-radius:7px; background:#fff6f6; border:1px solid #ffe1e1; color:#a53434; font-size:9px; line-height:1.55; }
  .matrix .gap-head { color:var(--red); background:#fff6f6; font-weight:700; }
  .qr-svg { width:100%; height:100%; }
  .qr-svg svg { width:100%; height:100%; display:block; }
  .job-list { display:flex; flex-direction:column; gap:10px; }
`;
document.head.appendChild(pageStyle);
