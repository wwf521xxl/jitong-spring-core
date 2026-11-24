/* =========================================================
   计件通 · 交互、弹窗、导出与启动
   ========================================================= */
'use strict';

const APP_VERSION = SERVER_MODE ? '2.0.9' : '1.0.3';

async function hardReload() {
  try {
    if ('serviceWorker' in navigator) {
      const regs = await navigator.serviceWorker.getRegistrations();
      await Promise.all(regs.map((reg) => reg.unregister()));
    }
    if (window.caches) {
      const keys = await caches.keys();
      await Promise.all(keys.map((key) => caches.delete(key)));
    }
  } catch (err) {
    console.warn('清理缓存失败', err);
  } finally {
    const url = new URL(location.href);
    url.searchParams.set('v', Date.now().toString());
    location.replace(url.toString());
  }
}

/* ------------------------- 生产版 API ------------------------- */
async function api(path, options = {}) {
  const method = options.method || 'GET';
  const init = { method, credentials: 'same-origin', headers: { ...(options.headers || {}) } };
  if (options.body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(options.body);
  }
  let response;
  try {
    response = await fetch(path, init);
  } catch (err) {
    const error = new Error('无法连接服务器，请检查网络或服务是否启动');
    error.status = 0;
    throw error;
  }
  let data = {};
  try { data = await response.json(); } catch (_) { /* 空响应 */ }
  if (!response.ok) {
    const detail = data.detail;
    const message = typeof detail === 'string' ? detail : (detail?.message || '请求失败');
    const error = new Error(message);
    error.status = response.status;
    error.code = typeof detail === 'object' ? detail?.code : '';
    throw error;
  }
  return data;
}

function applyBootstrap(data) {
  if (!data) return;
  db = {
    employees: data.employees || [],
    jobs: data.jobs || [],
    records: data.records || [],
    corrections: data.corrections || [],
    skills: data.skills || [],
    routes: data.routes || [],
    settings: data.settings || { duplicateMinutes: 10, tolerance: 0.05 },
  };
  if (data.user) state.employee = data.user;
}

async function serverBoot() {
  try {
    const data = await api('/api/bootstrap');
    applyBootstrap(data);
    return true;
  } catch (err) {
    if (err.status === 401) return false;
    console.error('生产数据加载失败', err);
    state.loginError = err.message || '无法加载数据';
    return false;
  }
}

async function serverLogin(account, pin) {
  const button = $('[data-action="server-login"]');
  const errorBox = $('#login-error');
  if (!account || !pin) {
    if (errorBox) { errorBox.hidden = false; errorBox.textContent = '请输入工号和 PIN / 密码'; }
    return;
  }
  if (button) { button.disabled = true; button.textContent = '正在登录…'; }
  if (errorBox) errorBox.hidden = true;
  try {
    const data = await api('/api/login', { method: 'POST', body: { account, pin } });
    applyBootstrap(data);
    state.loginError = '';
    state.screen = data.user?.isAdmin ? 'admin' : 'home';
    closeModal();
    render();
    window.scrollTo(0, 0);
  } catch (err) {
    if (errorBox) {
      errorBox.hidden = false;
      errorBox.textContent = err.status === 401 ? '工号或密码不正确' : (err.message || '登录失败');
    }
  } finally {
    if (button) { button.disabled = false; button.textContent = '登录'; }
  }
}

async function serverLogout() {
  try { await api('/api/logout', { method: 'POST' }); } catch (_) { /* 本地也要退出 */ }
}

async function openInitialAccounts() {
  let data;
  try {
    data = await api('/api/initial-accounts');
  } catch (err) {
    toast(err.message || '无法读取初始账号', 'error');
    return;
  }
  const employees = data.employees || [];
  const admin = data.admin;
  const rows = [];
  if (admin && admin.pin) {
    rows.push(`<button class="account" style="background:#eefaf4;border-color:#d3f0e1;color:var(--ink)" data-action="quick-login" data-account="admin" data-pin="${esc(admin.pin)}">
      <span class="avatar" style="background:#d9f5e7;color:#087a4b;border:0">李</span>
      <span class="account-info"><span class="account-name" style="color:var(--ink)">厂长 <span class="account-badge" style="background:#d9f5e7;color:#087a4b">管理员</span></span><span class="account-role" style="color:var(--muted)">admin · 密码 ${esc(admin.pin)}</span></span>
      ${icon('arrow')}
    </button>`);
  }
  for (const e of employees) {
    rows.push(`<button class="account" style="background:#f7f9fc;border-color:#e8eef5;color:var(--ink)" data-action="quick-login" data-account="${esc(e.account)}" data-pin="${esc(e.pin)}">
      <span class="avatar" style="background:#eaf2ff;color:#1967d2;border:0">${esc(e.name.slice(0, 1))}</span>
      <span class="account-info"><span class="account-name" style="color:var(--ink)">${esc(e.name)} <span style="font-size:9px;color:var(--muted)">工号 ${esc(e.account)}</span></span><span class="account-role" style="color:var(--muted)">${esc(e.role)} · PIN ${esc(e.pin)}</span></span>
      ${icon('arrow')}
    </button>`);
  }
  openModal(`${modalHead('初始账号 / 密码', '仅用于首次部署；点击任意一行直接登录')}
    <div class="modal-note" style="margin-bottom:10px">正式使用后员工会修改 PIN，这里显示的是初始文件内容，不一定是当前密码。公网部署请关闭账号显示。</div>
    <div class="account-list" style="max-height:58dvh;overflow:auto;padding-right:2px">
      ${rows.join('') || '<div class="admin-empty">初始账号文件已删除或没有数据</div>'}
    </div>
    <div class="modal-actions"><button class="btn btn-ghost" data-action="close-modal">关闭</button></div>`);
}

function serverLoginFromForm() {
  const account = $('#login-account')?.value.trim() || '';
  const pin = $('#login-pin')?.value || '';
  serverLogin(account, pin);
}

let cameraStream = null;
let cameraTimer = null;
let deferredInstallPrompt = null;

/* ------------------------- Toast / Modal ------------------------- */
function toast(message, type = '') {
  const root = $('#toast-root');
  if (!root) return;
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = message;
  root.appendChild(el);
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(5px)';
    setTimeout(() => el.remove(), 180);
  }, 2300);
}

function openModal(html, wide = false) {
  const root = $('#modal-root');
  root.innerHTML = `<div class="modal-backdrop" data-action="close-modal">
    <div class="modal-sheet ${wide ? 'wide' : ''}" role="dialog" aria-modal="true">${html}</div>
  </div>`;
  document.body.style.overflow = 'hidden';
}
function closeModal() {
  $('#modal-root').innerHTML = '';
  document.body.style.overflow = '';
}

function modalHead(title, desc = '') {
  return `<div class="modal-head"><div style="flex:1"><h3>${esc(title)}</h3>${desc ? `<p>${esc(desc)}</p>` : ''}</div><button class="icon-btn ghost" data-action="close-modal" aria-label="关闭">${icon('close')}</button></div>`;
}

/* ------------------------- 渲染 ------------------------- */
function render() {
  const app = $('#app');
  document.body.classList.toggle('admin-mode', state.screen === 'admin' && !!state.employee?.isAdmin);

  if (!state.employee) {
    app.innerHTML = loginHTML();
    return;
  }

  let html = '';
  switch (state.screen) {
    case 'home': html = homeHTML(); break;
    case 'scan': html = scanHTML(); break;
    case 'entry': html = scanHTML(); break;
    case 'success': html = successHTML(); break;
    case 'records': html = recordsHTML(); break;
    case 'stats': html = statsHTML(); break;
    case 'orders': html = ordersHTML(); break;
    case 'order-detail': html = orderDetailHTML(); break;
    case 'profile': html = profileHTML(); break;
    case 'admin':
      if (state.employee.isAdmin) { html = adminHTML(); }
      else { state.screen = 'home'; html = homeHTML(); }
      break;
    default: html = homeHTML();
  }
  app.innerHTML = html;
}

function go(screen, options = {}) {
  if (screen === 'admin' && !state.employee?.isAdmin) {
    toast('当前账号没有管理权限', 'warn');
    return;
  }
  if (state.screen === 'scan' && screen !== 'scan') stopCamera();
  state.screen = screen;
  if (options.filter) state.recordFilter = options.filter;
  if (options.job) state.detailJob = options.job;
  if (options.tab) state.adminTab = options.tab;
  closeModal();
  render();
  window.scrollTo({ top: 0, behavior: 'instant' });
}

/* ------------------------- 登录与身份 ------------------------- */
function loginAs(userId) {
  if (SERVER_MODE) return;
  const user = userById(userId);
  if (!user) return;
  state.employee = user;
  try { localStorage.setItem(SESSION_KEY, user.id); } catch (_) { /* 单文件/隐私模式下可能禁用 */ }
  closeModal();
  state.screen = user.isAdmin ? 'admin' : 'home';
  render();
  window.scrollTo(0, 0);
  setTimeout(() => toast(`已切换为 ${user.name}`, 'success'), 160);
}

async function switchAccount() {
  stopCamera();
  if (SERVER_MODE) await serverLogout();
  try { localStorage.removeItem(SESSION_KEY); } catch (_) { /* ignore */ }
  state.employee = null;
  state.entry = null;
  state.screen = 'login';
  closeModal();
  document.body.classList.remove('admin-mode');
  render();
  window.scrollTo(0, 0);
}

function openAllAccounts() {
  openModal(`${modalHead('选择员工', '共 17 名员工 + 1 个管理账号')}
    <div class="account-list" style="max-height:58dvh;overflow:auto;padding-right:2px">
      ${EMPLOYEES.map((u) => `<button class="account" style="background:#f7f9fc;border-color:#e8eef5;color:var(--ink)" data-action="login" data-user="${u.id}">
        <span class="avatar" style="background:#eaf2ff;color:#1967d2;border:0">${esc(u.avatar)}</span>
        <span class="account-info"><span class="account-name" style="color:var(--ink)">${esc(u.name)}</span><span class="account-role" style="color:var(--muted)">${esc(u.role)}</span></span>${icon('arrow')}
      </button>`).join('')}
      <button class="account" style="background:#eefaf4;border-color:#d3f0e1;color:var(--ink)" data-action="login" data-user="A001">
        <span class="avatar" style="background:#d9f5e7;color:#087a4b;border:0">李</span>
        <span class="account-info"><span class="account-name" style="color:var(--ink)">厂长 <span class="account-badge" style="background:#d9f5e7;color:#087a4b">管理端</span></span><span class="account-role" style="color:var(--muted)">厂长 · 管理员</span></span>${icon('arrow')}
      </button>
    </div>`);
}

/* ------------------------- 扫码 / 相机 ------------------------- */
let autoReporting = false;

function openStepChooser(job, steps) {
  const list = (steps && steps.length) ? steps : (job.steps || []);
  const buttons = list.map((step) => {
    const name = step.name || stepName(job.line, step.no) || `第${step.no}道工序`;
    return `<button class="btn btn-soft btn-block" style="margin-bottom:8px" data-action="auto-step" data-job="${esc(job.id)}" data-step="${step.no}">
      ${esc(name)}
    </button>`;
  }).join('');
  openModal(`${modalHead('选择岗位', `${job.orderNo || job.id} · 请选择你实际完成的工序`)}
    <div style="padding:4px 0">${buttons}</div>
    <button class="btn btn-ghost btn-block" data-action="close-modal">取消</button>`);
}

async function startEntry(jobId, stepNo = null) {
  const job = jobById(jobId);
  if (!job) { toast('未找到该订单', 'error'); return; }
  if (isJobDone(job)) { toast('该订单已完成', 'warn'); return; }
  if (autoReporting) return;

  if (SERVER_MODE) {
    autoReporting = true;
    try {
      let chosen = stepNo;
      if (chosen === null || chosen === undefined) {
        const options = await api(`/api/jobs/${encodeURIComponent(job.id)}/auto-report/options`);
        const stepNos = options.stepNos || [];
        if (!stepNos.length) {
          toast('当前订单没有你可以报的工序', 'warn');
          return;
        }
        if (stepNos.length > 1) {
          openStepChooser(job, options.steps || []);
          return;
        }
        chosen = stepNos[0];
      }
      const data = await api(`/api/jobs/${encodeURIComponent(job.id)}/auto-report`, {
        method: 'POST',
        body: { stepNo: chosen },
      });
      applyBootstrap(data);
      if (data.group && !data.record) {
        closeModal();
        state.screen = 'home';
        render();
        toast(`已加入合报：${data.group.memberCount} 人，等待结束平分`, 'success');
        return;
      }
      state.lastRecord = data.record;
      state.entry = null;
      state.screen = 'success';
      closeModal();
      render();
      window.scrollTo(0, 0);
      if (navigator.vibrate) navigator.vibrate(25);
    } catch (err) {
      if (err.code === 'job_done' || err.code === 'step_done') {
        toast(err.message || '该工序已报工', 'warn');
        state.screen = 'orders';
        render();
      } else {
        toast(err.message || '自动报工失败', 'error');
      }
    } finally {
      autoReporting = false;
    }
    return;
  }

  const step = findEligibleStep(job, state.employee);
  const qty = Number(job.meters || job.plannedQty || 0);
  if (!step) { toast('当前订单没有你可以报的工序', 'warn'); return; }
  if (qty <= 0) { toast('订单没有填写米数/数量，不能自动报工', 'warn'); return; }
  const rec = {
    id: `R${Date.now().toString().slice(-6)}`,
    jobId: job.id,
    stepNo: step.no,
    employeeId: state.employee.id,
    qty: round3(qty),
    unit: step.unit,
    at: new Date().toISOString(),
    status: 'valid',
    note: '扫码自动报工',
    source: 'auto',
    synced: navigator.onLine,
    photo: null,
    device: '演示浏览器',
  };
  db.records.unshift(rec);
  saveDB();
  state.lastRecord = rec;
  state.entry = null;
  state.screen = 'success';
  closeModal();
  render();
  window.scrollTo(0, 0);
  if (navigator.vibrate) navigator.vibrate(25);
}

async function startCamera() {
  const view = $('#camera-view');
  const video = $('#scan-video');
  if (!view || !video) return;

  if (!navigator.mediaDevices?.getUserMedia) {
    toast('当前环境不支持相机，请使用演示流转卡', 'warn');
    return;
  }
  try {
    stopCamera();
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 1280 } },
      audio: false,
    });
    video.srcObject = cameraStream;
    await video.play();
    view.classList.add('camera-live');

    if ('BarcodeDetector' in window) {
      const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
      cameraTimer = setInterval(async () => {
        if (!cameraStream || video.readyState < 2) return;
        try {
          const codes = await detector.detect(video);
          if (codes.length && codes[0].rawValue) {
            handleScannedCode(codes[0].rawValue);
          }
        } catch (_) { /* 单帧识别失败不打断 */ }
      }, 500);
      toast('已打开相机，对准流转卡二维码', 'success');
    } else if (typeof window.jsQR === 'function') {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      cameraTimer = setInterval(() => {
        if (!cameraStream || video.readyState < 2 || !video.videoWidth) return;
        try {
          const maxSide = 720;
          const scale = Math.min(1, maxSide / Math.max(video.videoWidth, video.videoHeight));
          canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
          canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
          const result = window.jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
          if (result && result.data) handleScannedCode(result.data);
        } catch (_) { /* 单帧识别失败不打断 */ }
      }, 450);
      toast('已打开相机，对准流转卡二维码', 'success');
    } else {
      toast('相机已打开；当前浏览器不支持自动识别，请用演示流转卡', 'warn');
    }
  } catch (err) {
    console.warn('相机启动失败', err);
    toast('无法打开相机，请检查浏览器权限', 'error');
  }
}

function stopCamera() {
  if (cameraTimer) { clearInterval(cameraTimer); cameraTimer = null; }
  if (cameraStream) {
    cameraStream.getTracks().forEach((track) => track.stop());
    cameraStream = null;
  }
  const view = $('#camera-view');
  if (view) view.classList.remove('camera-live');
}

function handleScannedCode(raw) {
  const code = String(raw || '').trim();
  if (!code) return;
  const cleaned = code.replace(/^JOB:/i, '').replace(/^QJ-/i, 'QJ-');
  const job = db.jobs.find((j) =>
    j.qr.toLowerCase() === cleaned.toLowerCase()
    || j.orderNo.toLowerCase() === cleaned.toLowerCase()
    || j.id.toLowerCase() === cleaned.toLowerCase()
    || code.toLowerCase().includes(j.orderNo.toLowerCase())
    || code.toLowerCase().includes(j.qr.toLowerCase())
  );
  if (!job) { toast('未识别到系统内的流转卡', 'error'); return; }
  stopCamera();
  startEntry(job.id);
}

/* ------------------------- 报工输入 ------------------------- */
function setQty(value) {
  if (!state.entry) return;
  state.entry.qty = value;
  render();
}
function appendKey(key) {
  if (!state.entry) return;
  let q = state.entry.qty;
  if (key === '.') {
    if (q.includes('.')) return;
    q = q ? q + '.' : '0.';
  } else {
    if (q === '0') q = '';
    if (q.length >= 7) return;
    if (q.includes('.') && q.split('.')[1]?.length >= 2) return;
    q += key;
  }
  state.entry.qty = q;
  render();
}
function backspaceQty() {
  if (!state.entry) return;
  state.entry.qty = state.entry.qty.slice(0, -1);
  render();
}

async function submitRecord(forceDuplicate = false) {
  const entry = state.entry;
  if (!entry) return;
  const job = jobById(entry.jobId);
  const step = job?.steps.find((s) => s.no === entry.stepNo);
  if (!job || !step) return;
  const qty = Number(entry.qty);
  if (!Number.isFinite(qty) || qty <= 0) { toast('请输入本次完成数量', 'warn'); return; }
  const max = maxReportable(job, entry.stepNo);
  if (Number.isFinite(max) && max <= 0) { toast('上游尚未完成，暂时不能报工', 'warn'); return; }
  const now = Date.now();
  const duplicate = db.records.find((r) =>
    r.employeeId === state.employee.id && r.jobId === job.id && r.stepNo === entry.stepNo
    && r.status !== 'void' && Number(r.qty) === qty
    && now - new Date(r.at).getTime() < (db.settings.duplicateMinutes || 10) * 60000
  );
  if (duplicate && !forceDuplicate) {
    openModal(`${modalHead('疑似重复报工', '系统发现刚提交过相同数量，请确认不是重复操作')}
      <div class="detail-list">
        <div><span>订单</span><b>${esc(job.orderNo)}</b></div>
        <div><span>工序</span><b>${esc(stepName(job.line, entry.stepNo))}</b></div>
        <div><span>上次数量</span><b>${fmtQty(duplicate.qty)} ${esc(duplicate.unit)}</b></div>
        <div><span>上次时间</span><b>${timeLabel(duplicate.at)}</b></div>
      </div>
      <div class="modal-note">如果是真实的第二批产出，可以继续提交；系统会保留两笔记录和操作时间。</div>
      <div class="modal-actions">
        <button class="btn btn-ghost" data-action="close-modal">先不提交</button>
        <button class="btn btn-primary" data-action="submit-anyway">确认不是重复</button>
      </div>`);
    return;
  }

  if (SERVER_MODE) {
    const btn = $('[data-action="submit-record"]');
    if (btn) { btn.disabled = true; btn.textContent = '正在提交…'; }
    try {
      const data = await api('/api/records', {
        method: 'POST',
        body: {
          jobId: job.id,
          stepNo: entry.stepNo,
          qty,
          unit: entry.unit || step.unit,
          note: entry.note || '',
          photo: entry.photo || null,
          forceDuplicate: !!forceDuplicate,
          idempotencyKey: uid(),
          source: 'scan',
          deviceId: (navigator.userAgent || '').slice(0, 64),
        },
      });
      applyBootstrap(data);
      state.lastRecord = data.record;
      state.entry = null;
      state.screen = 'success';
      closeModal();
      render();
      window.scrollTo(0, 0);
      if (navigator.vibrate) navigator.vibrate(data.record?.status === 'pending' ? [30, 40, 30] : 25);
    } catch (err) {
      if (btn) { btn.disabled = false; btn.textContent = '确认提交'; }
      if (err.code === 'duplicate') {
        toast('疑似重复：刚提交过相同数量，请稍后再试', 'warn');
      } else {
        toast(err.message || '提交失败，请重试', 'error');
      }
    }
    return;
  }

  const over = qty > max + 0.0001;
  const rec = {
    id: `R${Date.now().toString().slice(-6)}`,
    jobId: job.id,
    stepNo: entry.stepNo,
    employeeId: state.employee.id,
    qty: round3(qty),
    unit: entry.unit || step.unit,
    at: new Date().toISOString(),
    status: over ? 'pending' : 'valid',
    note: over ? `超出可报数量（可报 ${fmtQty(max)} ${step.unit}）${entry.note ? '；' + entry.note : ''}` : (entry.note || ''),
    source: 'scan',
    synced: navigator.onLine,
    photo: entry.photo || null,
    device: '演示浏览器',
  };
  db.records.unshift(rec);
  saveDB();
  state.lastRecord = rec;
  state.entry = null;
  state.screen = 'success';
  closeModal();
  render();
  window.scrollTo(0, 0);
  if (navigator.vibrate) navigator.vibrate(rec.status === 'pending' ? [30, 40, 30] : 25);
}

/* ------------------------- 记录详情 / 更正 ------------------------- */
function openRecordDetail(recordId) {
  const r = db.records.find((x) => x.id === recordId);
  if (!r) return;
  const job = jobById(r.jobId);
  const emp = userById(r.employeeId);
  const canCorrect = r.status === 'valid' && r.employeeId === state.employee?.id;
  openModal(`${modalHead('报工详情', r.id)}
    <div class="detail-list">
      <div><span>员工</span><b>${esc(emp?.name || '')} · ${esc(emp?.role || '')}</b></div>
      <div><span>订单</span><b>${esc(job?.orderNo || '')}<br/><small style="color:var(--muted)">${esc(job?.remark || '')}</small></b></div>
      <div><span>产线 / 工序</span><b>${esc(job?.line || '')} · ${esc(stepName(job?.line || '写真', r.stepNo))}</b></div>
      <div><span>数量</span><b style="font-size:15px;color:var(--primary)">${fmtQty(r.qty)} ${esc(r.unit)}</b></div>
      <div><span>时间</span><b>${dayLabel(r.at)} ${timeLabel(r.at)}</b></div>
      <div><span>状态</span><b>${statusBadge(r)}</b></div>
      <div><span>同步</span><b>${SERVER_MODE ? '服务器已保存' : (r.synced === false ? '待联网同步' : '已同步到本机')}</b></div>
      ${r.note ? `<div><span>备注</span><b>${esc(r.note)}</b></div>` : ''}
    </div>
    ${r.photo ? `<img src="${r.photo}" alt="现场照片" style="width:100%;border-radius:10px;margin-top:10px" />` : ''}
    <div class="modal-note">${icon('shield', 'icon-sm')} 已计入记录不能删除；如有错误，请提交冲销申请，由厂长确认。</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">关闭</button>
      ${canCorrect ? `<button class="btn btn-soft" data-action="open-correction" data-record="${r.id}">申请冲销 / 更正</button>` : ''}
    </div>`);
}

function openCorrection(recordId) {
  const r = db.records.find((x) => x.id === recordId);
  if (!r) return;
  const job = jobById(r.jobId);
  openModal(`${modalHead('申请冲销 / 更正', '提交后由厂长确认，原记录会保留审计痕迹')}
    <div class="detail-list" style="margin-bottom:12px">
      <div><span>订单</span><b>${esc(job?.orderNo || '')}</b></div>
      <div><span>记录</span><b>${fmtQty(r.qty)} ${esc(r.unit)} · ${timeLabel(r.at)}</b></div>
    </div>
    <div class="form-field"><label>更正原因</label><select id="correction-reason">
      <option>数量录错</option><option>重复报工</option><option>订单/工序选错</option><option>其他</option>
    </select></div>
    <div class="form-field"><label>情况说明</label><textarea id="correction-note" placeholder="请说明实际情况，便于厂长核对"></textarea></div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-correction" data-record="${r.id}">提交申请</button>
    </div>`);
}

/* ------------------------- 新建订单 ------------------------- */
function openEditOrder(jobId) {
  const job = jobById(jobId);
  if (!job) { toast('订单不存在', 'error'); return; }
  openModal(`${modalHead('修改订单', `${job.orderNo} · ${job.line}线`)}
    <div class="detail-list" style="margin-bottom:10px">
      <div><span>产线</span><b>${esc(job.line)}</b></div>
      <div><span>单位</span><b>${esc(job.unit)}</b></div>
      <div><span>路线</span><b>${esc(job.routeName || '旧订单流程')}</b></div>
    </div>
    <div class="form-field"><label>订单号</label><input id="edit-order-no" maxlength="64" value="${esc(job.orderNo)}" /></div>
    <div class="form-field"><label>米数 / 数量</label><input id="edit-order-meters" type="number" min="0.01" step="0.01" value="${job.meters > 0 ? job.meters : ''}" /></div>
    <div class="form-field"><label>备注</label><textarea id="edit-order-remark" maxlength="500">${esc(job.remark || '')}</textarea></div>
    <div class="modal-note">已有报工记录的订单不能修改米数，只能修改订单号或备注。</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-edit-order" data-job="${job.id}">保存修改</button>
    </div>`);
}

async function saveEditOrder(jobId) {
  const orderNo = $('#edit-order-no')?.value.trim() || '';
  const meters = Number($('#edit-order-meters')?.value || 0);
  const remark = $('#edit-order-remark')?.value.trim() || '';
  if (!orderNo) { toast('订单号不能为空', 'warn'); return; }
  if (!Number.isFinite(meters) || meters <= 0) { toast('米数/数量必须大于 0', 'warn'); return; }
  const button = $('[data-action="save-edit-order"]');
  if (button) { button.disabled = true; button.textContent = '正在保存…'; }
  try {
    const data = await api(`/api/admin/jobs/${encodeURIComponent(jobId)}`, {
      method: 'PATCH',
      body: { orderNo, meters, remark },
    });
    applyBootstrap(data);
    closeModal();
    toast('订单已修改', 'success');
    render();
  } catch (err) {
    if (button) { button.disabled = false; button.textContent = '保存修改'; }
    toast(err.message || '修改失败', 'error');
  }
}

async function closeGroup(jobId, stepNo) {
  if (!confirm('确认结束这个合报岗位？系统会按实际参与人数平分数量。')) return;
  try {
    const data = await api(`/api/admin/jobs/${encodeURIComponent(jobId)}/groups/${stepNo}/close`, {
      method: 'POST',
      body: { reason: '厂长/建单人手动结束' },
    });
    applyBootstrap(data);
    toast('合报已结束，已按人数平分', 'success');
    render();
  } catch (err) {
    toast(err.message || '结束失败', 'error');
  }
}

async function completeOrder(jobId) {
  if (!confirm('确认完成订单？系统会先结束未关闭的合报组，再检查所有工序是否完成。')) return;
  try {
    const data = await api(`/api/admin/jobs/${encodeURIComponent(jobId)}/complete`, { method: 'POST' });
    applyBootstrap(data);
    toast('订单已完成', 'success');
    state.screen = 'orders';
    render();
  } catch (err) {
    toast(err.message || '完成失败', 'error');
  }
}

async function voidJob(jobId, orderNo) {
  if (!confirm(`确认作废订单 ${orderNo}？\n\n作废后不能再报工，相关报工记录会标记作废，但保留审计。`)) return;
  try {
    const data = await api(`/api/admin/jobs/${encodeURIComponent(jobId)}/void`, {
      method: 'POST',
      body: { reason: '厂长后台作废' },
    });
    applyBootstrap(data);
    toast('订单已作废', 'success');
    render();
  } catch (err) {
    toast(err.message || '作废失败', 'error');
  }
}

function openNewOrder() {
  openModal(`${modalHead('新建生产订单', '排版人员建单；创建后自动生成流转码')}
    <div class="form-field"><label>产线</label><select id="new-line"><option>写真</option><option>亚克力</option></select></div>
    <div class="form-field"><label>计量单位</label><select id="new-unit"><option>m²</option><option>件</option><option>套</option><option>块</option></select></div>
    <div class="form-field" id="new-route-wrap"><label>工艺路线</label><select id="new-route"></select></div>
    <div class="form-field"><label>米数 / 数量 *</label><input id="new-meters" type="number" min="0.01" step="0.01" placeholder="如 120.5" inputmode="decimal" /></div>
    <div class="form-field"><label>订单号（留空自动生成）</label><input id="new-order-no" placeholder="如 TB20261106009" maxlength="64" /></div>
    <div class="form-field"><label>备注</label><textarea id="new-remark" placeholder="如客户、工艺要求、加急说明" maxlength="500"></textarea></div>
    <div class="modal-note">路线由厂长在「路线管理」中维护；旧订单继续使用原路线。</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-order">${icon('plus')} 创建并生成流转码</button>
    </div>`, true);
  const lineSelect = $('#new-line');
  const unitSelect = $('#new-unit');
  const routeWrap = $('#new-route-wrap');
  const syncRoute = () => {
    const line = lineSelect?.value || '写真';
    const routes = (db.routes || []).filter((r) => r.line === line);
    const routeSelect = $('#new-route');
    if (routeSelect) {
      routeSelect.innerHTML = routes.length
        ? routes.map((r) => `<option value="${esc(r.key)}">${esc(r.name)}：${(r.steps || []).map((s) => esc(s.displayName || s.name)).join(' → ')}</option>`).join('')
        : '<option value="">该产线没有路线，请先在路线管理中添加</option>';
    }
  };
  if (lineSelect) lineSelect.addEventListener('change', syncRoute);
  syncRoute();
}

async function saveNewOrder() {
  const unit = $('#new-unit')?.value || 'm²';
  const line = $('#new-line')?.value || (unit === 'm²' ? '写真' : '亚克力');
  const orderNo = $('#new-order-no')?.value.trim() || '';
  const remark = $('#new-remark')?.value.trim() || '';
  const meters = Number($('#new-meters')?.value || 0);
  const routeKey = $('#new-route')?.value || 'A';
  if (!Number.isFinite(meters) || meters <= 0) { toast('请填写订单米数/数量', 'warn'); return; }

  if (SERVER_MODE) {
    const button = $('[data-action="save-order"]');
    if (button) { button.disabled = true; button.textContent = '正在创建…'; }
    try {
      const data = await api('/api/jobs', {
        method: 'POST',
        body: { unit, line, orderNo, remark, meters, routeKey },
      });
      applyBootstrap(data);
      closeModal();
      if (state.employee?.isAdmin) state.adminTab = 'orders';
      else state.screen = 'orders';
      render();
      toast('订单已创建，流转码已生成', 'success');
    } catch (err) {
      if (button) { button.disabled = false; button.textContent = '创建并生成流转码'; }
      toast(err.message || '创建订单失败', 'error');
    }
    return;
  }

  const stamp = dateKey().replace(/-/g, '');
  const seq = String(db.jobs.length + 1).padStart(3, '0');
  const localOrderNo = orderNo || `TB${stamp}${seq}`;
  const job = {
    id: `J${Date.now()}`,
    orderNo: localOrderNo,
    line: unit === 'm²' ? '写真' : '亚克力',
    product: '',
    spec: '',
    customer: '',
    plannedQty: 0,
    meters,
    unit,
    due: '',
    priority: '正常',
    remark,
    status: 'active',
    qr: `QJ-${stamp}-${seq}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`,
    steps: ALL_STEPS.map((no) => ({ no, planned: 0, unit })),
  };
  db.jobs.unshift(job);
  saveDB();
  closeModal();
  if (state.employee?.isAdmin) state.adminTab = 'orders';
  else state.screen = 'orders';
  render();
  toast('订单已创建，流转码已生成', 'success');
}

/* ------------------------- 二维码打印 ------------------------- */
function openPrintQR(jobId) {
  const job = jobById(jobId);
  if (!job) return;
  openModal(`${modalHead('打印流转卡', '建议 70 × 100 mm 热敏标签；二维码需保持清晰')}
    <div class="print-area">
      <div class="print-card">
        <div class="pc-brand">计件通 · 生产流转卡</div>
        <h3>${esc(job.orderNo)}</h3>
        <div class="pc-code">${esc(job.remark || `${job.line}线 · ${job.unit}`)}</div>
        <div class="qr-large">${qrVisual(job)}</div>
        <div class="pc-spec">${esc(job.line)}线 · 单位 ${esc(job.unit)}${job.meters > 0 ? ` · ${fmtQty(job.meters)} 米` : ''}</div>
        <div class="pc-spec">${job.plannedQty > 0 ? `计划 ${fmtQty(job.plannedQty)} ${esc(job.unit)}` : '不设计划量 · 按实际报工'}</div>
        <div class="pc-tip">扫码报工 · 一卡走完五道工序</div>
      </div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">关闭</button>
      <button class="btn btn-primary" data-action="do-print">${icon('print')} 打印</button>
    </div>`, true);
}

/* ------------------------- 导出 ------------------------- */
function csvCell(value) {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}
function downloadCSV(filename, rows) {
  const csv = '\uFEFF' + rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast('已导出 ' + filename, 'success');
}

function recordRows(records) {
  return [['日期', '时间', '订单号', '产线', '工序', '员工', '数量', '单位', '状态', '备注'], ...records.map((r) => {
    const job = jobById(r.jobId);
    const emp = userById(r.employeeId);
    const d = new Date(r.at);
    return [
      dateKey(d), timeLabel(r.at), job?.orderNo || '', job?.line || '',
      stepName(job?.line || '写真', r.stepNo), emp?.name || '', r.qty, r.unit,
      r.status === 'valid' ? '已计入' : r.status === 'pending' ? '待确认' : '已冲销',
      r.note || '',
    ];
  })];
}

function exportRecords(scope = 'all') {
  let records = [...db.records];
  if (scope === 'today') records = records.filter((r) => new Date(r.at).toDateString() === new Date().toDateString());
  if (scope === 'month') records = records.filter((r) => new Date(r.at).getMonth() === new Date().getMonth() && new Date(r.at).getFullYear() === new Date().getFullYear());
  downloadCSV(`计件明细_${scope}_${dateKey()}.csv`, recordRows(records));
}

function exportMyRecords() {
  const records = db.records.filter((r) => r.employeeId === state.employee.id);
  downloadCSV(`我的计件_${state.employee.name}_${dateKey()}.csv`, recordRows(records));
}

function exportSummary() {
  const map = new Map();
  validRecords().forEach((r) => {
    const job = jobById(r.jobId);
    const key = `${r.employeeId}|${job?.line}|${r.stepNo}|${r.unit}`;
    if (!map.has(key)) map.set(key, { emp: userById(r.employeeId)?.name || '', line: job?.line || '', step: stepName(job?.line || '写真', r.stepNo), qty: 0, unit: r.unit, count: 0 });
    const item = map.get(key);
    item.qty = round3(item.qty + Number(r.qty));
    item.count += 1;
  });
  const rows = [['员工', '产线', '工序', '数量', '单位', '笔数'], ...[...map.values()].sort((a, b) => a.emp.localeCompare(b.emp, 'zh')).map((x) => [x.emp, x.line, x.step, x.qty, x.unit, x.count])];
  downloadCSV(`员工产量汇总_${dateKey()}.csv`, rows);
}

function exportOrders() {
  const rows = [['订单号', '备注', '产线', '单位', '米数', '当前工序', '完成工序数', '状态'], ...db.jobs.map((j) => [
    j.orderNo, j.remark || '', j.line, j.unit, j.meters || 0, stepName(j.line, currentStepNo(j)), completedStepCount(j), isJobDone(j) ? '已完成' : '进行中',
  ])];
  downloadCSV(`订单进度_${dateKey()}.csv`, rows);
}

/* ------------------------- 其他弹窗 ------------------------- */
function openHelp() {
  if (SERVER_MODE) {
    openModal(`${modalHead('计件通 · 生产版使用说明', '数据保存在服务器，所有手机共享')}
      <div style="font-size:11px;line-height:1.8;color:var(--ink-2)">
        <p><b>3 步报工</b>：扫码 / 输入订单号 → 输入数量 → 确认提交。</p>
        <p style="margin-top:8px"><b>一人一号</b>：工号由厂长分配，请勿共用账号。忘记 PIN 请联系厂长重置。</p>
        <p style="margin-top:8px"><b>数据规则</b>：超报、补录进入待厂长确认；已计入记录只能申请冲销，不能删除。</p>
        <p style="margin-top:8px"><b>网络</b>：生产数据在服务器上，换手机或换浏览器登录后数据仍在。</p>
      </div>
      <div class="modal-note">当前版本 v${APP_VERSION} · 服务器生产模式</div>
      <div class="modal-actions">
        <button class="btn btn-ghost" data-action="close-modal">知道了</button>
        <button class="btn btn-soft" data-action="hard-reload">清除缓存并刷新</button>
      </div>`);
    return;
  }
  openModal(`${modalHead('计件通 · 演示说明', '这是一个可操作的高保真 MVP')}
    <div style="font-size:11px;line-height:1.8;color:var(--ink-2)">
      <p><b>3 步报工</b>：扫码 → 输入数量 → 确认提交。首页会立即更新你的今日产量。</p>
      <p style="margin-top:8px"><b>双线五工序</b>：写真线、亚克力线独立流转；每条线都有排版/打印/覆膜或过胶/裁切/综合五道工序。</p>
      <p style="margin-top:8px"><b>数据规则</b>：超报、补录会进入「待厂长确认」，确认前不计入产量；已计入记录只能申请冲销，不能删除。</p>
      <p style="margin-top:8px"><b>演示数据</b>：17 名员工来自花名册；4 个订单、16 笔报工记录保存在本机浏览器。可在管理端「报表导出」中恢复演示数据。</p>
    </div>
    <div class="modal-note">当前版本 v${APP_VERSION}。正式上线需要接入后端、账号体系、真实二维码与权限审批；演示版用于确认流程和界面。</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">知道了</button>
      <button class="btn btn-soft" data-action="hard-reload">清除缓存并刷新</button>
      <button class="btn btn-primary" data-action="reset-demo">恢复数据</button>
    </div>`);
}

function openSyncInfo() {
  if (SERVER_MODE) {
    openModal(`${modalHead('数据与同步', '生产模式：数据集中保存在服务器')}
      <div class="detail-list">
        <div><span>运行方式</span><b>服务器生产版</b></div>
        <div><span>数据位置</span><b>服务器数据库</b></div>
        <div><span>报工记录</span><b>${db.records.length} 笔（当前窗口）</b></div>
        <div><span>待确认</span><b>${pendingRecords().length} 笔</b></div>
        <div><span>多设备</span><b>所有手机共享同一份数据</b></div>
      </div>
      <div class="modal-note">正式部署请开启 HTTPS，并由管理员每日备份 data 目录；普通员工无需手动同步。</div>
      <div class="modal-actions"><button class="btn btn-primary" data-action="close-modal">知道了</button></div>`);
    return;
  }
  openModal(`${modalHead('数据与同步', navigator.onLine ? '当前已连接网络' : '当前离线')}
    <div class="detail-list">
      <div><span>运行方式</span><b>本机演示版</b></div>
      <div><span>数据位置</span><b>浏览器 localStorage</b></div>
      <div><span>报工记录</span><b>${db.records.length} 笔</b></div>
      <div><span>待确认</span><b>${pendingRecords().length} 笔</b></div>
      <div><span>离线报工</span><b>${navigator.onLine ? '已连接' : '暂存本机，联网后同步'}</b></div>
    </div>
    <div class="modal-note">演示版不会把数据上传到服务器。正式版应使用 HTTPS API，并给每笔记录生成幂等键，避免重复提交。</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">关闭</button>
      <button class="btn btn-soft" data-action="export-backup">导出数据备份</button>
    </div>`);
}

function openMessages() {
  const pending = pendingRecords();
  openModal(`${modalHead('消息与提醒', '系统通知与待办')}
    <div class="timeline-list">
      ${pending.length ? `<div class="timeline-item"><span class="timeline-dot" style="background:#f79009;box-shadow:0 0 0 4px #fff4e5"></span><span class="timeline-main"><strong>你有 ${pending.length} 笔报工待厂长确认</strong><p>确认后会计入今日产量与统计</p></span></div>` : ''}
      <div class="timeline-item"><span class="timeline-dot" style="background:#12b76a;box-shadow:0 0 0 4px #e8f8f0"></span><span class="timeline-main"><strong>数据已保存在本机</strong><p>${dateLabel()} ${timeLabel(new Date().toISOString())}</p></span></div>
      <div class="timeline-item"><span class="timeline-dot" style="background:#1467e8;box-shadow:0 0 0 4px #eaf2ff"></span><span class="timeline-main"><strong>报工小贴士</strong><p>扫码后核对订单与工序，再输入实际完成数量</p></span></div>
    </div>
    <div class="modal-actions"><button class="btn btn-primary" data-action="close-modal">知道了</button></div>`);
}

function openEntryHelp() {
  openModal(`${modalHead('报工遇到问题？', '先看这 4 种常见情况')}
    <div class="detail-list">
      <div><span>提示上游未完成</span><b>让上一道工序先报工</b></div>
      <div><span>提示无可报工序</span><b>联系厂长补充技能授权</b></div>
      <div><span>数量填错</span><b>提交后在记录里申请冲销</b></div>
      <div><span>扫不出二维码</span><b>手动输入订单号或流转码</b></div>
    </div>
    <div class="modal-note">厂长可在管理端「异常确认」中处理超报、补录和冲销申请。</div>
    <div class="modal-actions"><button class="btn btn-primary" data-action="close-modal">知道了</button></div>`);
}

function openChangePassword() {
  openModal(`${modalHead('修改登录密码', '至少 6 位；修改后下次登录使用新密码')}
    <div class="form-field"><label>原 PIN / 密码</label><input id="old-pin" type="password" autocomplete="current-password" /></div>
    <div class="form-field"><label>新 PIN / 密码</label><input id="new-pin" type="password" autocomplete="new-password" /></div>
    <div class="form-field"><label>再次输入新密码</label><input id="new-pin2" type="password" autocomplete="new-password" /></div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-password">确认修改</button>
    </div>`);
}

async function saveSkill(name, displayName, method = 'POST', oldName = '') {
  try {
    const data = await api(method === 'POST' ? '/api/admin/skills' : `/api/admin/skills/${encodeURIComponent(oldName)}`, {
      method,
      body: { name, displayName },
    });
    applyBootstrap(data);
    toast('工序已保存', 'success');
    render();
  } catch (err) { toast(err.message || '保存失败', 'error'); }
}

function openAddSkill() {
  const name = prompt('新工序名称');
  if (name) saveSkill(name.trim(), name.trim());
}

function openRenameSkill(name, displayName) {
  const value = prompt('修改显示名称（底层工序不变）', displayName || name);
  if (value && value.trim()) saveSkill(name, value.trim(), 'PATCH', name);
}

async function deleteSkill(name) {
  if (!confirm(`确认删除工序“${name}”？已被员工或订单使用时不能删除。`)) return;
  try {
    const data = await api(`/api/admin/skills/${encodeURIComponent(name)}`, { method: 'DELETE' });
    applyBootstrap(data);
    toast('工序已删除', 'success');
    render();
  } catch (err) { toast(err.message || '删除失败', 'error'); }
}

function openRouteEditor(routeKey) {
  const route = (db.routes || []).find((r) => r.key === routeKey) || { line: '写真', name: '', steps: [] };
  openModal(`${modalHead(routeKey ? '修改路线' : '新增路线', '工序用顿号或逗号分隔，顺序即生产顺序')}
    <div class="form-field"><label>产线</label><select id="route-line"><option ${route.line === '写真' ? 'selected' : ''}>写真</option><option ${route.line === '亚克力' ? 'selected' : ''}>亚克力</option></select></div>
    <div class="form-field"><label>路线名称</label><input id="route-name" value="${esc(route.name)}" /></div>
    <div class="form-field"><label>工序顺序</label><textarea id="route-steps" placeholder="排版、打印、覆膜">${esc((route.steps || []).map((s) => s.name || s.displayName).join('、'))}</textarea></div>
    <div class="modal-actions"><button class="btn btn-ghost" data-action="close-modal">取消</button><button class="btn btn-primary" data-action="save-route" data-key="${esc(routeKey || '')}">保存路线</button></div>`);
}

async function saveRoute(routeKey) {
  const line = $('#route-line')?.value;
  const name = $('#route-name')?.value.trim();
  const steps = ($('#route-steps')?.value || '').split(/[、,，\n]+/).map((s) => s.trim()).filter(Boolean);
  if (!name || !steps.length) { toast('路线名称和工序不能为空', 'warn'); return; }
  try {
    const data = await api(routeKey ? `/api/admin/routes/${encodeURIComponent(routeKey)}` : '/api/admin/routes', {
      method: routeKey ? 'PATCH' : 'POST', body: { line, name, steps },
    });
    applyBootstrap(data);
    closeModal();
    toast('路线已保存', 'success');
    render();
  } catch (err) { toast(err.message || '保存失败', 'error'); }
}

async function deleteRoute(routeKey) {
  if (!confirm('确认删除这条路线？已被订单使用时不能删除。')) return;
  try {
    const data = await api(`/api/admin/routes/${encodeURIComponent(routeKey)}`, { method: 'DELETE' });
    applyBootstrap(data);
    toast('路线已删除', 'success');
    render();
  } catch (err) { toast(err.message || '删除失败', 'error'); }
}

function openRenameEmployee(employeeId) {
  const employee = staffEmployees().find((e) => e.id === employeeId);
  if (!employee) return;
  openModal(`${modalHead('修改员工姓名', '姓名修改后，历史记录仍关联同一员工')}
    <div class="form-field"><label>姓名</label><input id="edit-emp-name" maxlength="40" value="${esc(employee.name)}" /></div>
    <div class="form-field"><label>岗位</label><input id="edit-emp-role" maxlength="60" value="${esc(employee.role)}" /></div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-employee-name" data-employee="${employee.id}">保存</button>
    </div>`);
}

async function saveEmployeeName(employeeId) {
  const name = $('#edit-emp-name')?.value.trim() || '';
  const role = $('#edit-emp-role')?.value.trim() || '员工';
  if (!name) { toast('姓名不能为空', 'warn'); return; }
  const button = $('[data-action="save-employee-name"]');
  if (button) { button.disabled = true; button.textContent = '正在保存…'; }
  try {
    const data = await api(`/api/admin/employees/${encodeURIComponent(employeeId)}`, {
      method: 'PATCH',
      body: { name, role },
    });
    applyBootstrap(data);
    closeModal();
    toast('员工姓名已修改', 'success');
    render();
  } catch (err) {
    if (button) { button.disabled = false; button.textContent = '保存'; }
    toast(err.message || '修改失败', 'error');
  }
}

function openAddEmployee() {
  const boxes = skillCatalog().map(({ name, label }) => `<label style="display:flex;align-items:center;gap:6px;padding:5px 7px;border:1px solid var(--line);border-radius:7px;font-size:10px">
    <input type="checkbox" data-new-process="${esc(name)}" /><span>${esc(label)}</span></label>`).join('');
  openModal(`${modalHead('添加员工', '创建后立即可以登录；停用不会删除历史记录')}
    <div class="form-field"><label>姓名</label><input id="new-emp-name" maxlength="40" placeholder="例如 张三" /></div>
    <div class="form-field"><label>工号</label><input id="new-emp-no" type="number" min="1" max="9999" placeholder="例如 18" /></div>
    <div class="form-field"><label>初始 PIN</label><input id="new-emp-pin" maxlength="6" placeholder="6 位" /></div>
    <div class="form-field"><label>岗位</label><input id="new-emp-role" maxlength="60" placeholder="例如 综合岗位" /></div>
    <label style="display:flex;align-items:center;gap:7px;margin:8px 0;font-size:11px"><input id="new-emp-creator" type="checkbox" /> 允许建单</label>
    <div style="font-size:11px;margin:8px 0 4px">工序技能</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;max-height:45vh;overflow:auto">${boxes}</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-employee">创建员工</button>
    </div>`);
}

async function saveNewEmployee() {
  const name = $('#new-emp-name')?.value.trim() || '';
  const employeeNo = Number($('#new-emp-no')?.value || 0);
  const pin = $('#new-emp-pin')?.value.trim() || '';
  const role = $('#new-emp-role')?.value.trim() || '员工';
  const canCreateJob = !!$('#new-emp-creator')?.checked;
  const processSkills = [...document.querySelectorAll('#modal-root [data-new-process]:checked')].map((el) => el.dataset.newProcess);
  const button = $('[data-action="save-employee"]');
  if (button) { button.disabled = true; button.textContent = '正在创建…'; }
  try {
    const data = await api('/api/admin/employees', {
      method: 'POST',
      body: { name, employeeNo, pin, role, canCreateJob, processSkills },
    });
    applyBootstrap(data);
    closeModal();
    toast('员工已添加', 'success');
    render();
  } catch (err) {
    if (button) { button.disabled = false; button.textContent = '创建员工'; }
    toast(err.message || '添加失败', 'error');
  }
}

async function deactivateEmployee(employeeId, name) {
  if (!confirm(`确认停用员工 ${name}？\n\n停用后不能登录和扫码，但历史报工记录保留。`)) return;
  try {
    const data = await api(`/api/admin/employees/${encodeURIComponent(employeeId)}/deactivate`, { method: 'POST' });
    applyBootstrap(data);
    toast('员工已停用', 'success');
    render();
  } catch (err) {
    toast(err.message || '停用失败', 'error');
  }
}

async function renameSkillInline(name, employeeId) {
  const value = prompt(`修改“${name}”的显示名称（底层工序不变）`, name);
  if (!value || !value.trim() || value.trim() === name) return;
  try {
    const data = await api(`/api/admin/skills/${encodeURIComponent(name)}`, {
      method: 'PATCH',
      body: { name, displayName: value.trim() },
    });
    applyBootstrap(data);
    toast('名称已修改', 'success');
    openEditSkills(employeeId);
  } catch (err) {
    toast(err.message || '改名失败', 'error');
  }
}

function openEditSkills(employeeId) {
  const employee = staffEmployees().find((e) => e.id === employeeId);
  if (!employee) return;
  const selected = new Set(employee.processSkills || []);
  const boxes = skillCatalog().map(({ name, label }) => `<div style="display:flex;align-items:center;gap:5px;border:1px solid var(--line);border-radius:8px;padding:5px 6px">
    <label style="display:flex;align-items:center;gap:7px;font-size:11px;flex:1">
      <input type="checkbox" data-process="${esc(name)}" ${selected.has(name) ? 'checked' : ''} />
      <span>${esc(label)}</span>
    </label>
    <button class="btn btn-ghost btn-sm" style="padding:2px 5px;font-size:9px" data-action="rename-skill-inline" data-name="${esc(name)}" data-employee="${employee.id}">改名</button>
  </div>`).join('');
  openModal(`${modalHead('修改工序技能', `${employee.name} · 勾选后立即生效`)}
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:7px;max-height:55vh;overflow:auto;padding:2px 0">${boxes}</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="save-skills" data-employee="${employee.id}">保存技能</button>
    </div>`);
}

async function saveProcessSkills(employeeId) {
  const processes = [...document.querySelectorAll('#modal-root [data-process]:checked')].map((el) => el.dataset.process);
  const button = $('[data-action="save-skills"]');
  if (button) { button.disabled = true; button.textContent = '正在保存…'; }
  try {
    const data = await api(`/api/admin/employees/${encodeURIComponent(employeeId)}/process-skills`, {
      method: 'PUT',
      body: { processes },
    });
    applyBootstrap(data);
    closeModal();
    toast('工序技能已更新', 'success');
    render();
  } catch (err) {
    if (button) { button.disabled = false; button.textContent = '保存技能'; }
    toast(err.message || '保存失败', 'error');
  }
}

function openResetPin() {
  const employeeId = $('#pin-employee')?.value;
  const employee = staffEmployees().find((e) => e.id === employeeId);
  if (!employee) { toast('请选择员工', 'warn'); return; }
  openModal(`${modalHead('重置员工 PIN', `将重置「${employee.name}」的登录 PIN`)}
    <div class="detail-list">
      <div><span>员工</span><b>${esc(employee.name)}</b></div>
      <div><span>工号</span><b>${esc(String(employee.employeeNo ?? employee.id))}</b></div>
    </div>
    <div class="modal-note">重置后旧 PIN 立即失效，新 PIN 只显示一次，请当面交给员工。</div>
    <div class="modal-actions">
      <button class="btn btn-ghost" data-action="close-modal">取消</button>
      <button class="btn btn-primary" data-action="do-reset-pin" data-employee="${employee.id}">确认重置</button>
    </div>`);
}

function openRejectRecord(recordId) {
  openModal(`${modalHead('驳回报工', '驳回后该记录将冲销，不计入任何统计')}
    <div class="form-field"><label>驳回原因</label><select id="reject-reason">
      <option>数量与实际不符</option><option>重复报工</option><option>订单/工序错误</option><option>无有效依据</option>
    </select></div>
    <div class="form-field"><label>补充说明</label><textarea id="reject-note" placeholder="可填写复核依据"></textarea></div>
    <div class="modal-actions"><button class="btn btn-ghost" data-action="close-modal">取消</button><button class="btn btn-primary" data-action="do-reject-record" data-record="${recordId}">确认驳回</button></div>`);
}

/* ------------------------- 动作分发 ------------------------- */
async function handleAction(action, el) {
  switch (action) {
    case 'login': loginAs(el.dataset.user); break;
    case 'server-login': serverLoginFromForm(); break;
    case 'show-initial-accounts': openInitialAccounts(); break;
    case 'quick-login': closeModal(); serverLogin(el.dataset.account, el.dataset.pin); break;
    case 'all-accounts': openAllAccounts(); break;
    case 'switch-account': switchAccount(); break;
    case 'go':
      go(el.dataset.screen, { filter: el.dataset.filter, job: el.dataset.job, tab: el.dataset.tab });
      break;
    case 'open-job':
      state.detailJob = el.dataset.job;
      go('order-detail');
      break;
    case 'scan-job':
      await startEntry(el.dataset.job);
      break;
    case 'auto-step': {
      const jobId = el.dataset.job;
      const stepNo = Number(el.dataset.step);
      closeModal();
      await startEntry(jobId, stepNo);
      break;
    }
    case 'manual-code': {
      const code = $('#manual-code')?.value.trim();
      if (!code) { toast('请输入订单号或流转码', 'warn'); return; }
      handleScannedCode(code);
      break;
    }
    case 'camera': startCamera(); break;
    case 'key': appendKey(el.dataset.key); break;
    case 'backspace': backspaceQty(); break;
    case 'clear-qty': setQty(''); break;
    case 'quick-qty': setQty(el.dataset.value); break;
    case 'open-note':
      openModal(`${modalHead('添加备注', '写清补印、返工、客户加急等情况')}
        <div class="form-field"><textarea id="note-text" placeholder="例如：客户临时加印 2 平方米">${esc(state.entry?.note || '')}</textarea></div>
        <div class="modal-actions"><button class="btn btn-ghost" data-action="close-modal">取消</button><button class="btn btn-primary" data-action="save-note">保存备注</button></div>`);
      break;
    case 'save-note':
      if (state.entry) state.entry.note = $('#note-text')?.value.trim() || '';
      closeModal();
      render();
      break;
    case 'take-photo':
      $('#photo-file')?.click();
      break;
    case 'remove-photo':
      if (state.entry) state.entry.photo = null;
      render();
      break;
    case 'submit-record': submitRecord(); break;
    case 'submit-anyway': closeModal(); submitRecord(true); break;
    case 'entry-help': openEntryHelp(); break;
    case 'record-detail': openRecordDetail(el.dataset.record); break;
    case 'open-correction': openCorrection(el.dataset.record); break;
    case 'save-correction': {
      const r = db.records.find((x) => x.id === el.dataset.record);
      if (!r) return;
      const reason = $('#correction-reason')?.value || '其他';
      const note = $('#correction-note')?.value.trim() || '';
      if (SERVER_MODE) {
        try {
          const data = await api(`/api/records/${encodeURIComponent(r.id)}/corrections`, {
            method: 'POST', body: { reason, note },
          });
          applyBootstrap(data);
          closeModal();
          toast('冲销申请已提交给厂长', 'success');
          render();
        } catch (err) {
          toast(err.message || '提交失败', 'error');
        }
        break;
      }
      db.corrections = db.corrections || [];
      db.corrections.unshift({
        id: `C${Date.now()}`, recordId: r.id, employeeId: r.employeeId,
        reason, note, status: 'pending', at: new Date().toISOString(),
      });
      saveDB();
      closeModal();
      toast('冲销申请已提交给厂长', 'success');
      render();
      break;
    }
    case 'filter-records': state.recordFilter = el.dataset.filter; render(); break;
    case 'period': state.statPeriod = el.dataset.period; render(); break;
    case 'order-tab': state.orderTab = el.dataset.tab; render(); break;
    case 'admin-tab': state.adminTab = el.dataset.tab; closeModal(); render(); break;
    case 'new-order': openNewOrder(); break;
    case 'save-order': saveNewOrder(); break;
    case 'print-qr': openPrintQR(el.dataset.job); break;
    case 'void-job': voidJob(el.dataset.job, el.dataset.order); break;
    case 'do-print': window.print(); break;
    case 'approve-record': {
      const r = db.records.find((x) => x.id === el.dataset.record);
      if (!r) return;
      if (SERVER_MODE) {
        try {
          const data = await api(`/api/admin/records/${encodeURIComponent(r.id)}/approve`, { method: 'POST' });
          applyBootstrap(data);
          toast('已确认计入产量', 'success');
          render();
        } catch (err) {
          toast(err.message || '确认失败', 'error');
        }
        break;
      }
      r.status = 'valid';
      r.approvedBy = '厂长';
      r.approvedAt = new Date().toISOString();
      saveDB();
      toast('已确认计入产量', 'success');
      render();
      break;
    }
    case 'reject-record': openRejectRecord(el.dataset.record); break;
    case 'do-reject-record': {
      const r = db.records.find((x) => x.id === el.dataset.record);
      if (!r) return;
      const reason = `${$('#reject-reason')?.value || ''}${$('#reject-note')?.value ? '，' + $('#reject-note').value : ''}`;
      if (SERVER_MODE) {
        try {
          const data = await api(`/api/admin/records/${encodeURIComponent(r.id)}/reject`, {
            method: 'POST', body: { reason: reason || '厂长驳回' },
          });
          applyBootstrap(data);
          closeModal();
          toast('已驳回并冲销', 'warn');
          render();
        } catch (err) {
          toast(err.message || '驳回失败', 'error');
        }
        break;
      }
      r.status = 'void';
      r.note = `${r.note || ''} · 驳回：${reason}`;
      saveDB();
      closeModal();
      toast('已驳回并冲销', 'warn');
      render();
      break;
    }
    case 'approve-correction': {
      const c = db.corrections.find((x) => x.id === el.dataset.correction);
      if (!c) return;
      if (SERVER_MODE) {
        try {
          const data = await api(`/api/admin/corrections/${encodeURIComponent(c.id)}/approve`, { method: 'POST' });
          applyBootstrap(data);
          toast('已同意冲销', 'success');
          render();
        } catch (err) { toast(err.message || '操作失败', 'error'); }
        break;
      }
      const r = db.records.find((x) => x.id === c.recordId);
      if (r) r.status = 'void';
      c.status = 'approved';
      saveDB();
      toast('已同意冲销', 'success');
      render();
      break;
    }
    case 'reject-correction': {
      const c = db.corrections.find((x) => x.id === el.dataset.correction);
      if (!c) return;
      if (SERVER_MODE) {
        try {
          const data = await api(`/api/admin/corrections/${encodeURIComponent(c.id)}/reject`, { method: 'POST' });
          applyBootstrap(data);
          toast('已驳回冲销申请', 'warn');
          render();
        } catch (err) { toast(err.message || '操作失败', 'error'); }
        break;
      }
      c.status = 'rejected';
      saveDB();
      toast('已驳回冲销申请', 'warn');
      render();
      break;
    }
    case 'export-records': exportRecords(el.dataset.scope || 'all'); break;
    case 'export-my-records': exportMyRecords(); break;
    case 'export-summary': exportSummary(); break;
    case 'export-orders': exportOrders(); break;
    case 'export-backup': {
      const blob = new Blob([JSON.stringify(db, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `计件通备份_${dateKey()}.json`; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      toast('备份已导出', 'success');
      break;
    }
    case 'sync-info': openSyncInfo(); break;
    case 'messages': openMessages(); break;
    case 'help': openHelp(); break;
    case 'reset-demo':
      if (SERVER_MODE) { toast('生产模式不能恢复演示数据', 'warn'); break; }
      openModal(`${modalHead('恢复演示数据？', '当前所有的报工、冲销和订单修改都会被清空')}
        <div class="modal-note">只会影响本机浏览器中的演示数据，不会影响任何服务器或原始 Excel。</div>
        <div class="modal-actions"><button class="btn btn-ghost" data-action="close-modal">取消</button><button class="btn btn-primary" data-action="do-reset">确认恢复</button></div>`);
      break;
    case 'do-reset':
      resetDB();
      state.entry = null;
      state.lastRecord = null;
      state.adminTab = 'overview';
      closeModal();
      state.screen = state.employee?.isAdmin ? 'admin' : 'home';
      render();
      toast('演示数据已恢复', 'success');
      break;
    case 'change-password': openChangePassword(); break;
    case 'save-password': {
      const oldPin = $('#old-pin')?.value || '';
      const newPin = $('#new-pin')?.value || '';
      const newPin2 = $('#new-pin2')?.value || '';
      if (newPin.length < 6) { toast('新密码至少 6 位', 'warn'); break; }
      if (newPin !== newPin2) { toast('两次输入的新密码不一致', 'warn'); break; }
      try {
        await api('/api/change-password', { method: 'POST', body: { old: oldPin, new: newPin } });
        closeModal();
        toast('密码已修改，请记住新密码', 'success');
      } catch (err) {
        toast(err.message || '修改失败', 'error');
      }
      break;
    }
    case 'toggle-create-permission': {
      const employeeId = el.dataset.employee;
      const allowed = el.dataset.value === '1';
      const employee = staffEmployees().find((e) => e.id === employeeId);
      if (!allowed && !confirm(`确定取消「${employee?.name || employeeId}」的建单权限？`)) break;
      el.disabled = true;
      try {
        const data = await api(`/api/admin/employees/${encodeURIComponent(employeeId)}/create-permission`, {
          method: 'POST',
          body: { canCreateJob: allowed },
        });
        applyBootstrap(data);
        render();
        toast(allowed ? '已授予建单权限' : '已取消建单权限', 'success');
      } catch (err) {
        el.disabled = false;
        toast(err.message || '修改权限失败', 'error');
      }
      break;
    }
    case 'edit-order': openEditOrder(el.dataset.job); break;
    case 'save-edit-order': saveEditOrder(el.dataset.job); break;
    case 'close-group': closeGroup(el.dataset.job, Number(el.dataset.step)); break;
    case 'complete-order': completeOrder(el.dataset.job); break;
    case 'add-skill': openAddSkill(); break;
    case 'rename-skill': openRenameSkill(el.dataset.name, el.dataset.display); break;
    case 'delete-skill': deleteSkill(el.dataset.name); break;
    case 'add-route': openRouteEditor(''); break;
    case 'edit-route': openRouteEditor(el.dataset.key); break;
    case 'save-route': saveRoute(el.dataset.key); break;
    case 'delete-route': deleteRoute(el.dataset.key); break;
    case 'add-employee': openAddEmployee(); break;
    case 'rename-employee': openRenameEmployee(el.dataset.employee); break;
    case 'save-employee-name': saveEmployeeName(el.dataset.employee); break;
    case 'save-employee': saveNewEmployee(); break;
    case 'deactivate-employee': deactivateEmployee(el.dataset.employee, el.dataset.name); break;
    case 'rename-skill-inline': renameSkillInline(el.dataset.name, el.dataset.employee); break;
    case 'edit-skills': openEditSkills(el.dataset.employee); break;
    case 'save-skills': saveProcessSkills(el.dataset.employee); break;
    case 'open-reset-pin': openResetPin(); break;
    case 'do-reset-pin': {
      const employeeId = el.dataset.employee;
      const button = $('[data-action="do-reset-pin"]');
      if (button) { button.disabled = true; button.textContent = '正在重置…'; }
      try {
        const data = await api(`/api/admin/employees/${encodeURIComponent(employeeId)}/reset-pin`, { method: 'POST' });
        openModal(`${modalHead('PIN 已重置', '请当面交给员工，只显示这一次')}
          <div class="detail-list">
            <div><span>员工</span><b>${esc(staffEmployees().find((e) => e.id === employeeId)?.name || employeeId)}</b></div>
            <div><span>新 PIN</span><b style="font-size:20px;color:var(--primary);font-family:var(--num)">${esc(data.pin)}</b></div>
          </div>
          <div class="modal-note">员工用新 PIN 登录后，建议立即自行修改。</div>
          <div class="modal-actions">
            <button class="btn btn-ghost" data-action="copy-pin" data-pin="${esc(data.pin)}">复制 PIN</button>
            <button class="btn btn-primary" data-action="close-modal">完成</button>
          </div>`);
      } catch (err) {
        if (button) { button.disabled = false; button.textContent = '确认重置'; }
        toast(err.message || '重置失败', 'error');
      }
      break;
    }
    case 'copy-pin':
      if (navigator.clipboard?.writeText) {
        navigator.clipboard.writeText(el.dataset.pin).then(() => toast('PIN 已复制', 'success')).catch(() => toast('复制失败，请手动记录', 'warn'));
      } else {
        toast('请手动记录 PIN', 'warn');
      }
      break;
    case 'hard-reload': hardReload(); break;
    case 'close-modal': closeModal(); break;
    case 'install':
      if (deferredInstallPrompt) {
        deferredInstallPrompt.prompt();
        deferredInstallPrompt = null;
      }
      break;
    default:
      console.warn('未处理的动作', action);
  }
}

/* ------------------------- 事件 ------------------------- */
document.addEventListener('click', async (event) => {
  const actionEl = event.target.closest('[data-action]');
  if (!actionEl) return;
  const action = actionEl.dataset.action;
  // 点击弹窗内部不应触发背景遮罩的关闭动作
  if (action === 'close-modal' && actionEl.classList.contains('modal-backdrop') && event.target.closest('.modal-sheet')) return;
  if (!['SELECT', 'INPUT', 'TEXTAREA'].includes(actionEl.tagName)) event.preventDefault();
  try {
    await handleAction(action, actionEl);
  } catch (err) {
    console.error('操作失败', action, err);
    toast('操作没有完成：' + (err?.message || '请刷新页面后重试'), 'error');
  }
});

document.addEventListener('input', (event) => {
  if (event.target.id === 'order-search') {
    state.orderSearch = event.target.value;
    const list = $('#order-list');
    if (list) list.innerHTML = orderListHTML();
  }
});

document.addEventListener('change', (event) => {
  if (event.target.matches('[data-action="admin-order-filter"]')) {
    state.adminOrderFilter = event.target.value;
    render();
  }
  if (event.target.id === 'photo-file') {
    const file = event.target.files?.[0];
    if (!file || !state.entry) return;
    if (!file.type.startsWith('image/')) { toast('请选择图片文件', 'warn'); return; }
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const maxSide = 720;
        const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
        state.entry.photo = canvas.toDataURL('image/jpeg', 0.62);
        render();
        toast('已添加现场照片', 'success');
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  }
});

document.addEventListener('keydown', (event) => {
  if (SERVER_MODE && event.key === 'Enter' && ['login-account', 'login-pin'].includes(event.target.id)) {
    event.preventDefault();
    serverLoginFromForm();
    return;
  }
  if (event.key === 'Enter') {
    if (event.target.id === 'manual-code') {
      event.preventDefault();
      handleScannedCode(event.target.value);
    }
    if (event.target.id === 'order-search') event.target.blur();
  }
  if (event.key === 'Escape') closeModal();
});

window.addEventListener('online', () => {
  toast('网络已恢复，本机数据仍在', 'success');
  if (['home', 'profile', 'scan'].includes(state.screen)) render();
});
window.addEventListener('offline', () => {
  toast('已进入离线模式，报工可继续', 'warn');
  if (['home', 'profile', 'scan'].includes(state.screen)) render();
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden && state.screen === 'scan') stopCamera();
});

/* ------------------------- 启动 ------------------------- */
function applyDemoFromQuery() {
  const params = new URLSearchParams(location.search);
  const demo = params.get('demo');
  if (!demo) return false;

  const adminMode = demo.startsWith('admin');
  state.employee = adminMode ? ADMIN : userById('E013');
  if (demo === 'login') {
    state.employee = null;
    state.screen = 'login';
    return true;
  }
  if (demo === 'entry') {
    state.screen = 'entry';
    state.entry = {
      jobId: params.get('job') || 'J001',
      stepNo: Number(params.get('step') || 2),
      unit: params.get('unit') || 'm²',
      qty: params.get('qty') || '10.8',
      note: params.get('note') || '',
      photo: null,
      dupConfirmed: false,
    };
  } else if (demo === 'success') {
    state.lastRecord = {
      id: 'R20261106018', jobId: 'J001', stepNo: 2, employeeId: 'E013',
      qty: 10.8, unit: 'm²', at: new Date().toISOString(), status: 'valid', note: '',
    };
    state.screen = 'success';
  } else if (demo === 'detail') {
    state.detailJob = params.get('job') || 'J001';
    state.screen = 'order-detail';
  } else if (demo === 'admin' || demo === 'admin-orders' || demo === 'admin-exceptions' || demo === 'admin-staff' || demo === 'admin-reports') {
    state.screen = 'admin';
    state.adminTab = demo === 'admin' ? 'overview' : demo.replace('admin-', '');
  } else {
    state.screen = demo;
  }
  return true;
}

function setupPwa() {
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    deferredInstallPrompt = event;
    const banner = $('#install-banner');
    if (banner) banner.hidden = false;
  });
  $('#install-close')?.addEventListener('click', () => { $('#install-banner').hidden = true; });
  $('#install-btn')?.addEventListener('click', () => {
    if (deferredInstallPrompt) deferredInstallPrompt.prompt();
    $('#install-banner').hidden = true;
  });
  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('./sw.js').catch((err) => console.warn('SW 注册失败', err));
  }
}

async function init() {
  if (new URLSearchParams(location.search).get('still') === '1') {
    document.documentElement.classList.add('still');
  }

  if (SERVER_MODE) {
    const ok = await serverBoot();
    if (!ok) {
      state.employee = null;
      state.screen = 'login';
    } else {
      state.screen = state.employee?.isAdmin ? 'admin' : 'home';
    }
    render();
    setupPwa();
    return;
  }

  const demoApplied = applyDemoFromQuery();
  if (!demoApplied) {
    let saved = null;
    try { saved = localStorage.getItem(SESSION_KEY); } catch (_) { /* ignore */ }
    const user = userById(saved);
    if (user) {
      state.employee = user;
      state.screen = user.isAdmin ? 'admin' : 'home';
    } else {
      state.screen = 'login';
    }
  }
  render();
  setupPwa();
}

init();
