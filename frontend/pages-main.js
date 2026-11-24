/* ------------------------- 我的记录 ------------------------- */
function recordsHTML() {
  const emp = state.employee;
  const month = totalsForUser(emp.id, 'month');
  let list = db.records.filter((r) => r.employeeId === emp.id);
  if (state.recordFilter === 'valid') list = list.filter((r) => r.status === 'valid');
  if (state.recordFilter === 'pending') list = list.filter((r) => r.status === 'pending');
  if (state.recordFilter === 'void') list = list.filter((r) => r.status === 'void');

  const groups = new Map();
  list.forEach((r) => {
    const key = dateKey(new Date(r.at));
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(r);
  });
  const sorted = [...groups.entries()].sort((a, b) => b[0].localeCompare(a[0]));

  return `<section class="screen">
    ${topbar('我的记录', 'home', `<button class="icon-btn" data-action="export-my-records" aria-label="导出">${icon('download')}</button>`)}
    <div class="summary-strip">
      <div><span>本月笔数</span><b>${month.records.length}</b></div>
      <div><span>本月产量</span><b style="font-size:12px">${totalsText(month.totals)}</b></div>
      <div><span>待确认</span><b style="color:var(--orange)">${pendingRecords().filter((r) => r.employeeId === emp.id).length}</b></div>
    </div>
    <div class="filter-tabs">
      ${[['all', '全部'], ['valid', '已计入'], ['pending', '待确认'], ['void', '已冲销']].map(([key, label]) => `<button class="${state.recordFilter === key ? 'active' : ''}" data-action="filter-records" data-filter="${key}">${label}</button>`).join('')}
    </div>
    ${sorted.length ? sorted.map(([key, records]) => {
      const d = new Date(key + 'T00:00:00');
      const validTotal = unitTotals(records);
      const pendingTotal = records.filter((r) => r.status === 'pending').reduce((acc, r) => {
        acc[r.unit] = round3((acc[r.unit] || 0) + Number(r.qty || 0));
        return acc;
      }, {});
      const headTotal = records.every((r) => r.status === 'pending') ? `待确认 ${totalsText(pendingTotal)}` : `${totalsText(validTotal)}${Object.keys(pendingTotal).length ? `（待确认 ${totalsText(pendingTotal)}）` : ''}`;
      return `<div class="date-group">
        <div class="date-head"><strong>${dayLabel(records[0].at)}</strong><span>${d.getMonth() + 1}月${d.getDate()}日 · ${headTotal}</span></div>
        ${records.map((r) => {
          const job = jobById(r.jobId);
          return `<button class="card record-card ${lineClass(job?.line || '写真')}" data-action="record-detail" data-record="${r.id}" style="width:100%;text-align:left">
            <div class="record-top">
              <div class="rt-main">
                <strong>${esc(job?.orderNo || '未知订单')}</strong>
                <span>${esc(job?.remark || `${job?.line || '写真'}线`)} · ${esc(stepName(job?.line || '写真', r.stepNo))}</span>
              </div>
              <div class="qty">${fmtQty(r.qty)} <small>${esc(r.unit)}</small></div>
            </div>
            <div class="record-foot">
              <span class="time">${icon('clock')} ${timeLabel(r.at)}</span>
              ${statusBadge(r)}
              <span class="spacer"></span>
              <span class="link">查看详情 ›</span>
            </div>
          </button>`;
        }).join('')}
      </div>`;
    }).join('') : `<div style="padding:0 16px"><div class="empty"><div class="empty-icon">${icon('orders')}</div><strong>没有符合条件的记录</strong><span>换个筛选条件，或去扫码报工</span></div></div>`}
    ${bottomNav('records')}
  </section>`;
}

/* ------------------------- 我的统计 ------------------------- */
function periodStart(period) {
  const now = new Date();
  if (period === 'today') return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (period === 'week') {
    const day = now.getDay() || 7;
    return new Date(now.getFullYear(), now.getMonth(), now.getDate() - day + 1);
  }
  return new Date(now.getFullYear(), now.getMonth(), 1);
}

function statsHTML() {
  const emp = state.employee;
  const from = periodStart(state.statPeriod);
  const records = validRecords().filter((r) => r.employeeId === emp.id && new Date(r.at) >= from);
  const totals = unitTotals(records);
  const primary = primaryUnit(totals);
  const jobsCount = new Set(records.map((r) => r.jobId)).size;
  const dayCount = new Set(records.map((r) => dateKey(new Date(r.at)))).size || 1;

  const byProcess = new Map();
  records.forEach((r) => {
    const job = jobById(r.jobId);
    const key = `${job?.line || '写真'}|${r.stepNo}|${r.unit}`;
    if (!byProcess.has(key)) byProcess.set(key, { line: job?.line || '写真', stepNo: r.stepNo, unit: r.unit, qty: 0, count: 0 });
    const item = byProcess.get(key);
    item.qty = round3(item.qty + Number(r.qty));
    item.count += 1;
  });
  const rows = [...byProcess.values()].sort((a, b) => a.line.localeCompare(b.line) || a.stepNo - b.stepNo);
  const maxQty = Math.max(...rows.map((r) => r.qty), 1);
  const periodLabels = { today: '今日', week: '本周', month: '本月' };

  return `<section class="screen">
    ${topbar('我的统计', 'home', `<button class="icon-btn" data-action="export-my-records" aria-label="导出">${icon('download')}</button>`)}
    <div class="period-tabs">
      ${[['today', '今日'], ['week', '本周'], ['month', '本月']].map(([key, label]) => `<button class="${state.statPeriod === key ? 'active' : ''}" data-action="period" data-period="${key}">${label}</button>`).join('')}
    </div>
    <div class="stat-hero">
      <div class="label">${periodLabels[state.statPeriod]}个人产量</div>
      <div class="main"><b>${fmtQty(totals[primary] || 0)}</b><span>${esc(primary)}</span></div>
      <div class="sub">${records.length} 笔报工 · 覆盖 ${jobsCount} 个订单 · ${dayCount} 个工作日</div>
      <div class="unit-pills">
        ${Object.entries(totals).filter(([, v]) => v > 0).map(([unit, value]) => `<span>${esc(unit)} ${fmtQty(value)}</span>`).join('') || '<span>暂无产量</span>'}
      </div>
    </div>
    <div class="stat-grid">
      <div class="card stat-tile"><span>有效报工</span><b>${records.length}<small>笔</small></b></div>
      <div class="card stat-tile"><span>日均产量</span><b>${fmtQty((totals[primary] || 0) / dayCount)}<small>${esc(primary)}</small></b></div>
    </div>
    <div class="section">
      <div class="section-head"><div><div class="section-title">工序产出分布</div><div class="section-desc">仅统计本人已确认的有效记录</div></div></div>
      <div class="card bar-chart">
        ${rows.length ? rows.map((r) => `
          <div class="bar-row">
            <div class="bar-row-top"><span>${esc(r.line)}线 · ${esc(stepName(r.line, r.stepNo))}</span><b>${fmtQty(r.qty)} ${esc(r.unit)}</b></div>
            <div class="bar-track"><div class="bar-fill" style="width:${Math.max(4, Math.round((r.qty / maxQty) * 100))}%"></div></div>
          </div>`).join('') : '<div class="admin-empty">当前周期还没有有效报工</div>'}
      </div>
    </div>
    <div class="stat-note">${icon('help', 'icon-sm')} 统计口径：仅计入状态为「已计入」的记录；待确认和已冲销记录不参与。平方米、件、套为不同计量单位，不直接相加。</div>
    ${bottomNav('stats')}
  </section>`;
}

/* ------------------------- 订单 ------------------------- */
function filterJobs() {
  let jobs = [...db.jobs];
  if (state.orderTab === 'active') jobs = jobs.filter((j) => !isJobDone(j));
  if (state.orderTab === 'done') jobs = jobs.filter((j) => isJobDone(j));
  const q = state.orderSearch.trim().toLowerCase();
  if (q) jobs = jobs.filter((j) => `${j.orderNo} ${j.remark || ''}`.toLowerCase().includes(q));
  return jobs.sort((a, b) => (isJobDone(a) ? 1 : 0) - (isJobDone(b) ? 1 : 0));
}

function orderListHTML() {
  const jobs = filterJobs();
  if (!jobs.length) {
    return `<div class="empty"><div class="empty-icon">${icon('box')}</div><strong>没有找到订单</strong><span>试试订单号、产品名或客户名</span></div>`;
  }
  return `<div class="job-list" style="padding:0 16px">${jobs.map((job) => jobCardHTML(job)).join('')}</div>`;
}

function ordersHTML() {
  const activeCount = db.jobs.filter((j) => !isJobDone(j)).length;
  return `<section class="screen">
    ${topbar('订单进度', 'home')}
    <div class="search-box">${icon('search')}<input id="order-search" placeholder="搜索订单号 / 产品 / 客户" value="${esc(state.orderSearch)}" autocomplete="off" /></div>
    <div class="order-tabs">
      ${[['active', `进行中 ${activeCount}`], ['done', '已完成'], ['all', '全部']].map(([key, label]) => `<button class="${state.orderTab === key ? 'active' : ''}" data-action="order-tab" data-tab="${key}">${label}</button>`).join('')}
    </div>
    <div id="order-list">${orderListHTML()}</div>
    ${bottomNav('orders')}
  </section>`;
}

/* ------------------------- 订单详情 ------------------------- */
function qrGridHTML(seed) {
  const n = 21;
  const cells = Array.from({ length: n * n }, () => false);
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  const rnd = () => {
    h ^= h << 13; h ^= h >>> 17; h ^= h << 5;
    return (h >>> 0) / 4294967296;
  };
  const idx = (x, y) => y * n + x;
  const setFinder = (ox, oy) => {
    for (let y = 0; y < 7; y++) {
      for (let x = 0; x < 7; x++) {
        const edge = x === 0 || x === 6 || y === 0 || y === 6;
        const core = x >= 2 && x <= 4 && y >= 2 && y <= 4;
        cells[idx(ox + x, oy + y)] = edge || core;
      }
    }
    for (let y = -1; y <= 7; y++) {
      for (let x = -1; x <= 7; x++) {
        const nx = ox + x, ny = oy + y;
        if (nx < 0 || ny < 0 || nx >= n || ny >= n) continue;
        if (x === -1 || y === -1 || x === 7 || y === 7) cells[idx(nx, ny)] = false;
      }
    }
  };
  setFinder(0, 0); setFinder(14, 0); setFinder(0, 14);
  for (let i = 0; i < 14; i++) if (i !== 6) { cells[idx(8, i)] = i % 2 === 0; cells[idx(i, 8)] = i % 2 === 0; }
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      const inFinder = (x < 8 && y < 8) || (x > 12 && y < 8) || (x < 8 && y > 12);
      if (!inFinder) cells[idx(x, y)] = rnd() > 0.48;
    }
  }
  return `<div class="qr-grid">${cells.map((on) => `<i class="${on ? 'on' : ''}"></i>`).join('')}</div>`;
}

function realQRSVG(text) {
  if (typeof window.qrcode !== 'function') return '';
  try {
    const qr = window.qrcode(0, 'M');
    qr.addData(text);
    qr.make();
    return `<div class="qr-svg">${qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true })}</div>`;
  } catch (err) {
    console.warn('二维码生成失败', err);
    return '';
  }
}

function qrVisual(job) {
  return realQRSVG(job.qr) || qrGridHTML(job.qr);
}

function orderDetailHTML() {
  const job = jobById(state.detailJob) || db.jobs[0];
  if (!job) return ordersHTML();
  const line = lineInfo(job.line);
  const eligible = findEligibleStep(job, state.employee);
  const done = isJobDone(job);
  return `<section class="screen">
    ${topbar('订单详情', 'orders', `<button class="icon-btn" data-action="print-qr" data-job="${job.id}" aria-label="打印流转卡">${icon('print')}</button>`)}
    <div class="detail-hero ${lineClass(job.line)}">
      <div style="display:flex;align-items:center;gap:7px">
        ${lineChip(job.line)}
        ${done ? '<span class="chip chip-green"><i class="dot"></i>已完成</span>' : ''}
      </div>
      <h2>${esc(job.orderNo)}</h2>
      <div class="spec">${esc(job.remark || '无备注')}</div>
      <div class="dh-meta">
        <div><span>单位</span><b>${esc(job.unit)}</b></div>
        <div><span>米数</span><b>${job.meters > 0 ? `${fmtQty(job.meters)} 米` : '未填写'}</b></div>
        <div><span>工序进度</span><b>${completedStepCount(job)}/${job.steps.length}</b></div>
        <div><span>产线</span><b>${esc(job.line)}</b></div>
      </div>
    </div>
    <div class="card qr-card">
      <div class="qr-box">${qrVisual(job)}</div>
      <div class="qr-copy">
        <strong>流转卡 · 扫码报工</strong>
        <span>同一张卡走完五道工序，扫码自动识别订单与可报数量。</span>
        <code>${esc(job.qr)}</code>
      </div>
      <button class="icon-btn" data-action="print-qr" data-job="${job.id}" aria-label="打印">${icon('print')}</button>
    </div>
    <div class="card flow-card">
      <div class="section-head" style="margin-bottom:6px"><div><div class="section-title">工序流转</div><div class="section-desc">数量以已确认记录为准</div></div></div>
      <div class="flow-list">
        ${job.steps.map((s) => {
          const d = stepDone(job, s.no);
          const complete = stepComplete(job, s);
          const active = !done && s.no === currentStepNo(job);
          const recs = recordsForStep(job, s.no).filter((r) => r.status !== 'void');
          return `<div class="flow-item ${complete ? 'done' : ''} ${active ? 'active' : ''}">
            <div class="flow-dot">${complete ? icon('check') : s.no}</div>
            <div class="flow-main">
              <div class="fm-top">
                <strong>${esc(s.name || stepName(job.line, s.no))}</strong>
                ${active ? '<span class="chip chip-blue" style="height:17px;font-size:8px">当前</span>' : ''}
                <span class="fm-qty">${fmtQty(d)} <span>/ ${fmtQty(s.planned)} ${esc(s.unit)}</span></span>
              </div>
              <p>${recs.length ? `${recs.length} 笔报工 · ${recs.map((r) => userById(r.employeeId)?.name || '').filter(Boolean).slice(0, 3).join('、')}${recs.length > 3 ? ' 等' : ''}` : '暂无报工记录'}</p>
              ${recs.length ? `<div class="flow-records">${recs.slice(0, 4).map((r) => `<span>${esc(userById(r.employeeId)?.name || '')} ${fmtQty(r.qty)}${esc(r.unit)}</span>`).join('')}</div>` : ''}
              ${s.group ? `<div style="margin-top:6px;font-size:10px;color:${s.group.status === 'open' ? 'var(--orange)' : 'var(--muted)'}">
                合报${s.group.status === 'open' ? '中' : '已结束'} · ${s.group.memberCount} 人
                ${s.group.memberCount ? ` · 每人约 ${fmtQty(s.group.perQty)} ${esc(s.group.unit)}` : ''}
                <div style="color:var(--muted);margin-top:2px">${(s.group.members || []).map((m) => esc(m.name)).join('、')}</div>
                ${s.group.status === 'open' && (state.employee?.isAdmin || job.createdBy === state.employee?.id) ? `<div style="margin-top:4px"><button class="btn btn-soft btn-sm" data-action="close-group" data-job="${job.id}" data-step="${s.no}">结束岗位</button></div>` : ''}
              </div>` : ''}
            </div>
          </div>`;
        }).join('')}
      </div>
    </div>
    <div class="detail-cta">
      ${eligible ? `<button class="btn btn-primary btn-block" data-action="scan-job" data-job="${job.id}">${icon('scan')} 报「${esc(stepName(job.line, eligible.no))}」 · 剩余 ${fmtQty(maxReportable(job, eligible.no))} ${esc(eligible.unit)}</button>`
        : `<button class="btn btn-ghost btn-block" disabled>${done ? '订单已完成' : '当前没有你可报的工序'}</button>`}
      <div class="submit-hint">扫码或点击按钮进入报工 · 记录不可删除，只能申请冲销</div>
      ${!done && (state.employee?.isAdmin || job.createdBy === state.employee?.id) ? `<button class="btn btn-primary btn-block" style="margin-top:8px" data-action="complete-order" data-job="${job.id}">完成订单</button>` : ''}
    </div>
  </section>`;
}

/* ------------------------- 我的 ------------------------- */
function profileHTML() {
  const emp = state.employee;
  const month = totalsForUser(emp.id, 'month');
  const pending = pendingRecords().filter((r) => r.employeeId === emp.id).length;
  return `<section class="screen">
    ${topbar('我的', 'home')}
    <div class="profile-head">
      <div class="avatar">${esc(emp.avatar)}</div>
      <div class="ph-main">
        <h2>${esc(emp.name)}</h2>
        <p>${esc(emp.role)} · 工号 ${esc(String(emp.employeeNo ?? emp.id))}</p>
        <div class="skill-chips">${skillChips(emp)}</div>
      </div>
      ${emp.isAdmin ? '<span class="ph-badge">管理员</span>' : ''}
    </div>
    <div class="card profile-stats">
      <div><b>${month.records.length}</b><span>本月报工</span></div>
      <div><b>${Object.keys(month.totals).length}</b><span>计量单位</span></div>
      <div><b>${pending}</b><span>待确认</span></div>
    </div>
    <div class="card menu-card">
      <button class="menu-row" data-action="go" data-screen="records">
        <span class="mi">${icon('orders')}</span><span class="mm"><strong>我的计件记录</strong><span>每一笔都可追溯、可申请更正</span></span>${icon('arrow')}
      </button>
      <button class="menu-row" data-action="messages">
        <span class="mi">${icon('bell')}</span><span class="mm"><strong>消息与提醒</strong><span>${pending ? `${pending} 笔记录待确认` : '暂无新消息'}</span></span>${icon('arrow')}
      </button>
      <button class="menu-row" data-action="sync-info">
        <span class="mi">${icon('sync')}</span><span class="mm"><strong>数据与同步</strong><span>${SERVER_MODE ? '服务器集中保存 · 多设备共享' : (navigator.onLine ? '已连接 · 数据保存在本机' : '离线 · 报工将暂存本机')}</span></span>${icon('arrow')}
      </button>
      ${emp.isAdmin ? `<button class="menu-row" data-action="go" data-screen="admin">
        <span class="mi" style="background:#e8f8f0;color:#067647">${icon('shield')}</span><span class="mm"><strong>管理后台</strong><span>订单、异常、人员与报表</span></span>${icon('arrow')}
      </button>` : ''}
      <button class="menu-row" data-action="help">
        <span class="mi">${icon('help')}</span><span class="mm"><strong>使用帮助</strong><span>3 步报工、常见问题与${SERVER_MODE ? '生产版' : '演示'}说明</span></span>${icon('arrow')}
      </button>
      ${SERVER_MODE ? `<button class="menu-row" data-action="change-password">
        <span class="mi">${icon('lock')}</span><span class="mm"><strong>修改登录密码</strong><span>首次登录建议立即修改</span></span>${icon('arrow')}
      </button>` : ''}
      <button class="menu-row" data-action="switch-account">
        <span class="mi">${icon('logout')}</span><span class="mm"><strong>切换账号</strong><span>换一名员工体验不同岗位</span></span>${icon('arrow')}
      </button>
    </div>
    <div class="version">${SERVER_MODE ? '计件通生产版 · v2.0.0 · 数据集中保存' : '计件通 MVP · v1.0.3 · 数据保存在本机浏览器'}</div>
    ${bottomNav('profile')}
  </section>`;
}

/* ------------------------- 管理端 ------------------------- */
function adminTabMeta(tab) {
  const map = {
    overview: ['生产概览', '今天工厂发生了什么，一屏看清'],
    orders: ['订单与流转卡', '创建订单、打印流转码、查看工序进度'],
    exceptions: ['异常与确认', '超出可报、补录与冲销申请'],
    staff: ['人员与技能', '双线五工序的人员覆盖情况'],
    reports: ['报表与导出', '按员工、工序、订单导出明细'],
  };
  return map[tab] || map.overview;
}

function adminShell(content) {
  const tab = state.adminTab;
  const [title, desc] = adminTabMeta(tab);
  const nav = [
    ['overview', 'chart', '生产概览'],
    ['orders', 'orders', '订单管理'],
    ['exceptions', 'alert', '异常确认'],
    ['staff', 'users', '人员技能'],
    ['reports', 'download', '报表导出'],
  ];
  return `<section class="admin-shell">
    <aside class="admin-side">
      <div class="admin-brand">
        <div class="brand-mark">件</div>
        <div><strong>计件通</strong><span>工厂管理端</span></div>
      </div>
      <nav class="admin-nav">
        ${nav.map(([key, ic, label]) => `<button class="${tab === key ? 'active' : ''}" data-action="admin-tab" data-tab="${key}">${icon(ic)}${label}${key === 'exceptions' && pendingRecords().length ? ` <span style="margin-left:auto;background:#f79009;color:#fff;font-size:8px;padding:1px 5px;border-radius:8px">${pendingRecords().length}</span>` : ''}</button>`).join('')}
      </nav>
      <div class="admin-side-foot">
        <strong>${esc(state.employee.name)} · 管理员</strong>
        <span>数据统计截至 ${dateLabel()} ${timeLabel(new Date().toISOString())}</span>
      </div>
    </aside>
    <main class="admin-main">
      <header class="admin-top">
        <div><h1>${title}</h1><p>${desc}</p></div>
        <div class="admin-top-actions">
          <span class="admin-date">${icon('clock')} 今日 · ${dateLabel()}</span>
          <button class="icon-btn" data-action="switch-account" title="切换账号">${icon('logout')}</button>
        </div>
      </header>
      ${content}
    </main>
  </section>`;
}

function todayValid() {
  return validRecords().filter((r) => new Date(r.at).toDateString() === new Date().toDateString());
}

function lineToday(line) {
  return todayValid().filter((r) => jobById(r.jobId)?.line === line);
}

function stepToday(line, stepNo) {
  return todayValid().filter((r) => jobById(r.jobId)?.line === line && r.stepNo === Number(stepNo));
}

function stepProgress(line, stepNo) {
  const jobs = db.jobs.filter((j) => j.line === line && !isJobDone(j));
  let done = 0, planned = 0;
  jobs.forEach((j) => {
    const s = j.steps.find((x) => x.no === stepNo);
    if (!s) return;
    done += stepDoneByUnit(j, stepNo);
    planned += Number(s.planned);
  });
  return planned ? Math.min(100, Math.round((done / planned) * 100)) : 0;
}

function pendingCorrections() {
  return (db.corrections || []).filter((c) => c.status === 'pending');
}

function adminOverviewHTML() {
  const today = todayValid();
  const totals = unitTotals(today);
  const primary = primaryUnit(totals);
  const active = activeJobs();
  const pending = pendingRecords();
  const lineStats = ['写真', '亚克力'].map((line) => {
    const recs = lineToday(line);
    const t = unitTotals(recs);
    const jobs = active.filter((j) => j.line === line);
    const avg = jobs.length ? Math.round(jobs.reduce((s, j) => s + jobProgress(j), 0) / jobs.length) : 0;
    return { line, recs, totals: t, jobs: jobs.length, avg };
  });

  return `
    <div class="kpi-grid">
      <div class="kpi"><div class="k-top"><span class="k-label">今日总产出</span><span class="k-icon">${icon('box')}</span></div><b>${fmtQty(totals[primary] || 0)} <small>${esc(primary)}</small></b><div class="k-foot">${Object.keys(totals).length > 1 ? Object.entries(totals).filter(([u]) => u !== primary).map(([u, v]) => `${fmtQty(v)} ${u}`).join(' · ') : '已确认有效记录'}</div></div>
      <div class="kpi"><div class="k-top"><span class="k-label">今日报工</span><span class="k-icon">${icon('orders')}</span></div><b>${today.length} <small>笔</small></b><div class="k-foot"><em>+${pending.length}</em> 笔待确认</div></div>
      <div class="kpi"><div class="k-top"><span class="k-label">进行中订单</span><span class="k-icon">${icon('box')}</span></div><b>${active.length} <small>单</small></b><div class="k-foot">${db.jobs.filter((j) => isJobDone(j)).length} 单已完成</div></div>
      <div class="kpi"><div class="k-top"><span class="k-label">待处理异常</span><span class="k-icon" style="background:#fff4e5;color:#b25a00">${icon('alert')}</span></div><b style="color:${pending.length ? '#b25a00' : 'var(--ink)'}">${pending.length + pendingCorrections().length} <small>条</small></b><div class="k-foot">${pending.length} 笔超报 / 补录</div></div>
    </div>
    <div class="admin-grid">
      <div style="display:flex;flex-direction:column;gap:12px">
        <div class="panel">
          <div class="panel-head"><h3>双线今日产出</h3><span>按已确认记录</span></div>
          <div class="panel-body">
            <div class="line-summary">
              ${lineStats.map((s) => `<div class="line-summary-row">
                <span class="line-badge"><span class="chip ${s.line === '亚克力' ? 'chip-amber' : 'chip-blue'}">${esc(s.line)}</span></span>
                <div class="ls-track"><div class="ls-fill ${s.line === '亚克力' ? 'amber' : 'blue'}" style="width:${Math.max(3, s.avg)}%"></div></div>
                <span class="ls-num">${totalsText(s.totals)}</span>
              </div>`).join('')}
            </div>
            <div style="display:flex;justify-content:space-between;margin-top:10px;font-size:9px;color:var(--muted)">
              <span>写真线 ${lineStats[0].jobs} 单进行中 · 平均 ${lineStats[0].avg}%</span>
              <span>亚克力线 ${lineStats[1].jobs} 单进行中 · 平均 ${lineStats[1].avg}%</span>
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-head"><h3>工序今日报工</h3><span>旧五道汇总 · 订单进度看右侧路线</span></div>
          <div class="panel-body" style="padding-top:0">
            <table class="process-table">
              <thead><tr><th>产线 / 工序</th><th>今日数量</th><th>笔数</th><th>在制进度</th></tr></thead>
              <tbody>
                ${['写真', '亚克力'].flatMap((line) => ALL_STEPS.map((no) => {
                  const recs = stepToday(line, no);
                  const t = unitTotals(recs);
                  const pct = stepProgress(line, no);
                  return `<tr><td><span style="color:${line === '亚克力' ? '#b25a00' : '#0c5ed7'};font-weight:600">${esc(line)}</span> · ${esc(stepName(line, no))}</td><td class="num">${totalsText(t)}</td><td class="num">${recs.length}</td><td><div style="display:flex;align-items:center;gap:6px"><div class="mini-bar"><i style="width:${pct}%;background:${line === '亚克力' ? '#f79009' : '#2e90fa'}"></i></div><span class="num" style="font-size:9px;color:var(--muted)">${pct}%</span></div></td></tr>`;
                })).join('')}
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div style="display:flex;flex-direction:column;gap:12px">
        <div class="panel">
          <div class="panel-head"><h3>待确认异常</h3><span>${pending.length} 笔</span></div>
          <div class="panel-body">
            <div class="exception-list">
              ${pending.slice(0, 4).map((r) => {
                const job = jobById(r.jobId);
                const emp = userById(r.employeeId);
                return `<div class="exception-item">
                  <div class="ex-top">${icon('alert', 'icon-sm')}<strong>${esc(emp?.name || '')} · ${fmtQty(r.qty)} ${esc(r.unit)}</strong><span class="chip chip-amber" style="height:17px;font-size:8px">超报/补录</span></div>
                  <p>${esc(job?.orderNo || '')} · ${esc(stepName(job?.line || '写真', r.stepNo))} · ${timeLabel(r.at)}<br/>${esc(r.note || '无备注')}</p>
                  <div class="ex-actions"><button class="primary" data-action="approve-record" data-record="${r.id}">确认计入</button><button data-action="reject-record" data-record="${r.id}">驳回</button></div>
                </div>`;
              }).join('') || '<div class="admin-empty">暂无待确认记录</div>'}
            </div>
          </div>
        </div>
        <div class="panel">
          <div class="panel-head"><h3>进行中订单</h3><span>${active.length} 单</span></div>
          <div class="panel-body" style="padding-top:0">
            ${active.slice(0, 5).map((j) => `<div class="admin-order-row">
              <div class="ao-main"><strong>${esc(j.orderNo)}</strong><span>${esc(j.routeName || j.remark || `${j.line}线 · ${j.unit}`)}</span>
                <div style="font-size:9px;color:var(--muted);margin-top:3px">${j.steps.map((s) => `<span style="color:${stepComplete(j, s) ? 'var(--green)' : 'var(--muted)'}">${stepComplete(j, s) ? '✓' : '○'} ${esc(s.name || stepName(j.line, s.no))}</span>`).join(' → ')}</div>
              </div>
              <div class="ao-progress">${progressBar(j)}</div>
              <div class="ao-pct">${jobProgress(j)}%</div>
            </div>`).join('') || '<div class="admin-empty">暂无进行中订单</div>'}
          </div>
        </div>
      </div>
    </div>`;
}

function adminOrdersHTML() {
  let jobs = [...db.jobs].filter((j) => j.status !== 'void');
  if (state.adminOrderFilter === 'active') jobs = jobs.filter((j) => !isJobDone(j));
  if (state.adminOrderFilter === 'done') jobs = jobs.filter((j) => isJobDone(j));
  return `
    <div class="panel">
      <div class="panel-head">
        <h3>全部订单 <span style="color:var(--muted);font-weight:400">${jobs.length} 单</span></h3>
        <div class="admin-list-toolbar">
          <select data-action="admin-order-filter">
            <option value="all" ${state.adminOrderFilter === 'all' ? 'selected' : ''}>全部状态</option>
            <option value="active" ${state.adminOrderFilter === 'active' ? 'selected' : ''}>进行中</option>
            <option value="done" ${state.adminOrderFilter === 'done' ? 'selected' : ''}>已完成</option>
          </select>
          <button class="btn btn-primary btn-sm" data-action="new-order">+ 新建订单</button>
        </div>
      </div>
      <div class="panel-body" style="padding-top:0">
        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead><tr><th>订单 / 备注</th><th>产线</th><th>工序进度</th><th>单位</th><th>米数</th><th>状态</th><th></th></tr></thead>
            <tbody>
              ${jobs.map((j) => `<tr>
                <td><div class="t-name"><span class="t-avatar" style="background:${j.line === '亚克力' ? '#fff4e5' : '#eaf2ff'};color:${j.line === '亚克力' ? '#b25a00' : '#1967d2'}">${esc(j.orderNo.slice(0, 1))}</span><div><strong>${esc(j.orderNo)}</strong><div class="t-sub">${esc(j.remark || '无备注')}</div></div></div></td>
                <td><span class="chip ${j.line === '亚克力' ? 'chip-amber' : 'chip-blue'}">${esc(j.line)}</span></td>
                <td><div style="display:flex;align-items:center;gap:7px"><div class="ao-progress" style="width:74px">${progressBar(j)}</div><span class="num" style="font-size:9px">${completedStepCount(j)}/${j.steps.length}</span></div></td>
                <td class="num">${esc(j.unit)}</td>
                <td class="num">${j.meters > 0 ? fmtQty(j.meters) : '—'}</td>
                <td>${j.status === 'void' ? '<span class="status-dot" style="color:var(--red)">已作废</span>' : (isJobDone(j) ? '<span class="status-dot ok">已完成</span>' : '<span class="status-dot warn">进行中</span>')}</td>
                <td><button class="btn btn-ghost btn-sm" data-action="print-qr" data-job="${j.id}">${icon('print')} 流转卡</button>
                  <button class="btn btn-ghost btn-sm" data-action="edit-order" data-job="${j.id}">修改</button>
                  ${!isJobDone(j) ? `<button class="btn btn-ghost btn-sm" style="color:var(--red);margin-left:4px" data-action="void-job" data-job="${j.id}" data-order="${esc(j.orderNo)}">作废</button>` : ''}
                </td>
              </tr>`).join('')}
            </tbody>
          </table>
        </div>
      </div>
    </div>`;
}

function adminExceptionsHTML() {
  const pending = pendingRecords();
  const corrections = pendingCorrections();
  return `
    <div class="admin-grid" style="grid-template-columns:1.2fr .8fr">
      <div class="panel">
        <div class="panel-head"><h3>待确认报工 <span style="color:var(--muted);font-weight:400">${pending.length} 笔</span></h3><span>确认后计入有效产量</span></div>
        <div class="panel-body">
          ${pending.length ? pending.map((r) => {
            const job = jobById(r.jobId);
            const emp = userById(r.employeeId);
            return `<div class="exception-item" style="margin-bottom:9px">
              <div class="ex-top">${icon('alert', 'icon-sm')}<strong>${esc(emp?.name || '')} · ${esc(job?.orderNo || '')}</strong><span class="chip chip-amber" style="height:17px;font-size:8px">待确认</span></div>
              <p>${esc(stepName(job?.line || '写真', r.stepNo))} · <b>${fmtQty(r.qty)} ${esc(r.unit)}</b> · ${dayLabel(r.at)} ${timeLabel(r.at)}<br/>${esc(r.note || '无备注')}</p>
              <div class="ex-actions"><button class="primary" data-action="approve-record" data-record="${r.id}">确认计入</button><button data-action="reject-record" data-record="${r.id}">驳回并冲销</button></div>
            </div>`;
          }).join('') : '<div class="admin-empty">没有待确认报工，数据很干净</div>'}
        </div>
      </div>
      <div class="panel">
        <div class="panel-head"><h3>更正 / 冲销申请</h3><span>${corrections.length} 条</span></div>
        <div class="panel-body">
          ${corrections.length ? corrections.map((c) => {
            const r = db.records.find((x) => x.id === c.recordId);
            const job = r ? jobById(r.jobId) : null;
            const emp = r ? userById(r.employeeId) : null;
            return `<div class="exception-item">
              <div class="ex-top">${icon('edit', 'icon-sm')}<strong>${esc(emp?.name || '')} · ${esc(job?.orderNo || '')}</strong></div>
              <p>${esc(c.reason)} · ${esc(c.note || '')}<br/>原记录 ${fmtQty(r?.qty || 0)} ${esc(r?.unit || '')} · ${dayLabel(c.at)}</p>
              <div class="ex-actions"><button class="primary" data-action="approve-correction" data-correction="${c.id}">同意冲销</button><button data-action="reject-correction" data-correction="${c.id}">驳回</button></div>
            </div>`;
          }).join('') : '<div class="admin-empty">没有更正申请</div>'}
        </div>
      </div>
    </div>
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>防错规则</h3><span>系统自动执行，异常一律留痕</span></div>
      <div class="panel-body">
        <div class="detail-list">
          <div><span>超出上游可报数量</span><b>转厂长确认，确认前不计入</b></div>
          <div><span>同一人 / 订单 / 工序短时间重复</span><b>提示疑似重复，需二次确认</b></div>
          <div><span>非本工序技能人员报工</span><b>默认拦截，需管理员授权</b></div>
          <div><span>已计入记录</span><b>不可删除，只能发起冲销申请</b></div>
        </div>
      </div>
    </div>`;
}

function staffEmployees() { return (db.employees || EMPLOYEES).filter((e) => !e.isAdmin); }

function qualifiedCount(line, stepNo) {
  return staffEmployees().filter((e) => e.skills.some((s) => s.line === line && s.steps.includes(stepNo))).length;
}

function staffMatrixHTML(line) {
  const info = lineInfo(line);
  return `<div class="panel">
    <div class="panel-head"><h3>${esc(info.label)}</h3><span>${staffEmployees().filter((e) => e.skills.some((s) => s.line === line)).length} 人可参与</span></div>
    <div class="panel-body">
      <div class="matrix" style="grid-template-columns:86px repeat(5,1fr)">
        <div class="mh">员工</div>
        ${info.shortSteps.map((name, idx) => `<div class="mh ${qualifiedCount(line, idx + 1) === 0 ? 'gap-head' : ''}">${name}<br/><span style="font-weight:400;font-size:7px">${qualifiedCount(line, idx + 1)}人</span></div>`).join('')}
        ${staffEmployees().map((e) => `<div class="name" title="${esc(e.role)}">${esc(e.name)}</div>${ALL_STEPS.map((no) => {
          const on = e.skills.some((s) => s.line === line && s.steps.includes(no));
          return `<div class="${on ? 'on' : ''}">${on ? '✓' : '—'}</div>`;
        }).join('')}`).join('')}
      </div>
      ${qualifiedCount(line, 4) === 0 ? '<div class="gap-note">⚠ 写真裁切暂无明确责任人。建议在人员技能中补充授权，否则订单会卡在第 4 道工序。</div>' : ''}
      ${line === '亚克力' && qualifiedCount(line, 3) === 0 ? '<div class="gap-note">⚠ 亚克力过胶暂无明确责任人，建议补充授权。</div>' : ''}
    </div>
  </div>`;
}

function adminStaffHTML() {
  return `
    <div class="admin-grid" style="grid-template-columns:1fr 1fr">
      ${staffMatrixHTML('写真')}
      ${staffMatrixHTML('亚克力')}
    </div>
    ${SERVER_MODE ? `
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>建单权限</h3><span>只有这里勾选的人可以新建订单</span></div>
      <div class="panel-body" style="display:flex;align-items:center;gap:7px;flex-wrap:wrap">
        ${staffEmployees().map((e) => `<button class="btn ${e.canCreateJob ? 'btn-primary' : 'btn-ghost'} btn-sm" data-action="toggle-create-permission" data-employee="${e.id}" data-value="${e.canCreateJob ? '0' : '1'}">${esc(e.name)} · ${e.canCreateJob ? '可建单' : '不可建单'}</button>`).join('')}
      </div>
      <div class="panel-body" style="padding-top:0;font-size:9px;color:var(--muted)">取消最后一名建单人员时系统会拦截，避免没人能开单。管理员始终不能建单。</div>
    </div>
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>工序与路线</h3><span>旧订单不变；修改只影响以后新建的订单</span></div>
      <div class="panel-body" style="display:flex;gap:7px;flex-wrap:wrap">
        <button class="btn btn-primary btn-sm" data-action="add-skill">+ 添加工序</button>
        <button class="btn btn-primary btn-sm" data-action="add-route">+ 添加路线</button>
      </div>
      <div class="panel-body">
        <div style="font-size:11px;font-weight:600;margin-bottom:5px">工序</div>
        ${(db.skills || []).map((s) => `<span style="display:inline-flex;align-items:center;gap:4px;border:1px solid var(--line);border-radius:7px;padding:4px 6px;margin:2px;font-size:10px">${esc(s.displayName)}${s.active ? '' : '（停用）'}<button class="btn btn-ghost btn-sm" style="padding:1px 4px" data-action="rename-skill" data-name="${esc(s.name)}" data-display="${esc(s.displayName)}">改名</button><button class="btn btn-ghost btn-sm" style="color:var(--red);padding:1px 4px" data-action="delete-skill" data-name="${esc(s.name)}">删除</button></span>`).join('')}
        <div style="font-size:11px;font-weight:600;margin:10px 0 5px">路线</div>
        ${(db.routes || []).map((r) => `<div style="border-top:1px solid var(--line);padding:6px 0;font-size:10px"><b>${esc(r.name)}</b> · ${esc(r.line)} · ${(r.steps || []).map((s) => esc(s.displayName || s.name)).join(' → ')} <button class="btn btn-ghost btn-sm" data-action="edit-route" data-key="${esc(r.key)}">修改</button><button class="btn btn-ghost btn-sm" style="color:var(--red)" data-action="delete-route" data-key="${esc(r.key)}">删除</button></div>`).join('')}
      </div>
    </div>
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>人员管理</h3><span>新增员工、停用员工；停用后历史记录保留</span></div>
      <div class="panel-body" style="display:flex;align-items:center;gap:7px;flex-wrap:wrap">
        <button class="btn btn-primary btn-sm" data-action="add-employee">+ 添加员工</button>
        ${staffEmployees().map((e) => `<span style="display:inline-flex;align-items:center;gap:5px;border:1px solid var(--line);border-radius:8px;padding:5px 7px;font-size:10px">${esc(e.name)} · ${esc(e.role)} <button class="btn btn-ghost btn-sm" style="padding:2px 5px" data-action="rename-employee" data-employee="${e.id}">改名</button><button class="btn btn-ghost btn-sm" style="color:var(--red);padding:2px 5px" data-action="deactivate-employee" data-employee="${e.id}" data-name="${esc(e.name)}">停用</button></span>`).join('')}
      </div>
    </div>
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>工序技能授权</h3><span>按工序名称勾选，扫码时按这里判断</span></div>
      <div class="panel-body" style="display:flex;align-items:center;gap:7px;flex-wrap:wrap">
        ${staffEmployees().map((e) => `<button class="btn btn-soft btn-sm" data-action="edit-skills" data-employee="${e.id}">${esc(e.name)} · ${(e.processSkills || []).length} 项</button>`).join('')}
      </div>
      <div class="panel-body" style="padding-top:0;font-size:9px;color:var(--muted)">修改后立即生效；已有报工记录不受影响。</div>
    </div>
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>账号与 PIN</h3><span>员工忘记 PIN 时重置，新 PIN 只显示一次</span></div>
      <div class="panel-body" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
        <select id="pin-employee" style="height:36px;border:1px solid var(--line);border-radius:9px;padding:0 10px;font-size:11px">
          ${staffEmployees().map((e) => `<option value="${e.id}">${esc(e.name)}（工号 ${esc(String(e.employeeNo ?? e.id))}）</option>`).join('')}
        </select>
        <button class="btn btn-soft btn-sm" data-action="open-reset-pin">${icon('lock')} 重置 PIN</button>
        <span style="font-size:9px;color:var(--muted)">管理员密码可在「我的 → 修改登录密码」中修改。</span>
      </div>
    </div>` : ''}
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>岗位盘点说明</h3><span>来自 17 人花名册</span></div>
      <div class="panel-body">
        <div class="detail-list">
          <div><span>综合岗位</span><b>5 人 · 可跨两条线，报工时必须选择产线</b></div>
          <div><span>激光裁切</span><b>4 人 · 员工17、员工06兼写真裁切</b></div>
          <div><span>写真裁切</span><b style="color:var(--green)">2 人（员工17、员工06）· 已授权</b></div>
          <div><span>亚克力过胶</span><b>2 人（过膜过板兼）· 建议确认</b></div>
          <div><span>全线多能工</span><b>2 人（员工05、员工07）· 所有工序</b></div>
          <div><span>权限原则</span><b>一人一账号，不共用；代报需管理员审批</b></div>
        </div>
      </div>
    </div>`;
}

function adminReportsHTML() {
  const today = todayValid();
  const month = validRecords().filter((r) => new Date(r.at).getMonth() === new Date().getMonth());
  return `
    <div class="kpi-grid">
      <div class="kpi"><div class="k-top"><span class="k-label">今日明细</span><span class="k-icon">${icon('orders')}</span></div><b>${today.length} <small>笔</small></b><div class="k-foot">${totalsText(unitTotals(today))}</div></div>
      <div class="kpi"><div class="k-top"><span class="k-label">本月明细</span><span class="k-icon">${icon('chart')}</span></div><b>${month.length} <small>笔</small></b><div class="k-foot">${totalsText(unitTotals(month))}</div></div>
      <div class="kpi"><div class="k-top"><span class="k-label">员工人数</span><span class="k-icon">${icon('users')}</span></div><b>${staffEmployees().length} <small>人</small></b><div class="k-foot">当前花名册</div></div>
      <div class="kpi"><div class="k-top"><span class="k-label">异常记录</span><span class="k-icon">${icon('alert')}</span></div><b>${pendingRecords().length + pendingCorrections().length} <small>条</small></b><div class="k-foot">待厂长处理</div></div>
    </div>
    <div class="admin-grid" style="grid-template-columns:1.2fr .8fr">
      <div class="panel">
        <div class="panel-head"><h3>报表导出</h3><span>CSV · Excel 可直接打开</span></div>
        <div class="panel-body">
          <div class="admin-report-grid">
            <div class="report-card"><strong>报工明细（今日）</strong><p>员工、订单、产线、工序、数量、单位、时间、状态。</p><button class="btn btn-soft btn-sm" data-action="export-records" data-scope="today">${icon('download')} 导出今日</button></div>
            <div class="report-card"><strong>报工明细（本月）</strong><p>适合财务/厂长做月度复核与归档。</p><button class="btn btn-soft btn-sm" data-action="export-records" data-scope="month">${icon('download')} 导出本月</button></div>
            <div class="report-card"><strong>员工产量汇总</strong><p>按员工 + 产线 + 工序汇总，不含薪资。</p><button class="btn btn-soft btn-sm" data-action="export-summary">${icon('download')} 导出汇总</button></div>
            <div class="report-card"><strong>订单进度清单</strong><p>五道工序完成量、当前工序、交期与状态。</p><button class="btn btn-soft btn-sm" data-action="export-orders">${icon('download')} 导出订单</button></div>
          </div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-head"><h3>统计口径</h3><span>只统计，不算工资</span></div>
        <div class="panel-body">
          <div class="detail-list">
            <div><span>有效产量</span><b>仅 status=已计入</b></div>
            <div><span>待确认</span><b>不计入，厂长确认后生效</b></div>
            <div><span>面积 / 件数</span><b>分单位统计，不直接相加</b></div>
            <div><span>上游校验</span><b>同单位累计 ≤ 上游已转</b></div>
            <div><span>数据保留</span><b>建议正式版保留 3 年</b></div>
          </div>
        </div>
      </div>
    </div>
    ${SERVER_MODE ? `
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>生产数据</h3><span>由服务器统一保存</span></div>
      <div class="panel-body" style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <button class="btn btn-ghost btn-sm" data-action="help">${icon('help')} 查看生产版说明</button>
        <span style="font-size:9px;color:var(--muted)">当前窗口显示最近 45 天数据；每日备份由管理员在服务器 data 目录完成。</span>
      </div>
    </div>` : `
    <div class="panel" style="margin-top:12px">
      <div class="panel-head"><h3>演示与数据</h3><span>仅影响本机浏览器</span></div>
      <div class="panel-body" style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
        <button class="btn btn-ghost btn-sm" data-action="reset-demo">${icon('sync')} 恢复演示数据</button>
        <button class="btn btn-ghost btn-sm" data-action="help">${icon('help')} 查看演示说明</button>
        <span style="font-size:9px;color:var(--muted)">演示数据含 17 名员工、4 个订单、17 笔报工记录。</span>
      </div>
    </div>`}`;
}

function adminHTML() {
  if (!state.employee?.isAdmin) return homeHTML();
  let content = '';
  if (state.adminTab === 'overview') content = adminOverviewHTML();
  else if (state.adminTab === 'orders') content = adminOrdersHTML();
  else if (state.adminTab === 'exceptions') content = adminExceptionsHTML();
  else if (state.adminTab === 'staff') content = adminStaffHTML();
  else if (state.adminTab === 'reports') content = adminReportsHTML();
  return adminShell(content);
}
