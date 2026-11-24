/* =========================================================
   计件通 · 员工端页面
   ========================================================= */
'use strict';

/* ------------------------- 登录 ------------------------- */
function serverLoginHTML() {
  return `<section class="screen no-nav login">
    <div class="brand-lockup">
      <div class="brand-mark">件</div>
      <div><div class="brand-name">计件通</div><div class="brand-tag">JIJIAN · 生产版</div></div>
    </div>
    <h1 class="login-title">登录<br/><em>计件通</em></h1>
    <p class="login-sub">工号由厂长分配。员工用手机扫码报工，厂长在电脑上查看全厂数据。</p>
    <div class="card" style="padding:18px;margin-top:24px;background:rgba(255,255,255,.97)">
      <div class="form-field"><label>工号 / 管理员账号</label>
        <input id="login-account" inputmode="text" autocomplete="username" placeholder="员工填 1–17，厂长填 admin" />
      </div>
      <div class="form-field"><label>PIN / 密码</label>
        <input id="login-pin" type="password" inputmode="text" autocapitalize="none" autocorrect="off" spellcheck="false" autocomplete="current-password" placeholder="向厂长领取初始 PIN" />
      </div>
      <div id="login-error" class="login-error" ${state.loginError ? '' : 'hidden'}>${esc(state.loginError || '')}</div>
      <button class="btn btn-primary btn-block" data-action="server-login" style="margin-top:6px">登录</button>
      <details class="login-help">
        <summary>第一次使用，账号在哪里？</summary>
        <p>厂长：账号 <b>admin</b>，初始密码在服务器 <code>data\管理员初始密码.txt</code>。</p>
        <p>员工：工号 1–17，初始 PIN 在 <code>data\员工初始账号.csv</code>，由厂长发放。</p>
        <p>登录没反应？点下面的“强制更新”。</p>
      </details>
      <button type="button" class="btn btn-soft btn-block" data-action="show-initial-accounts" style="margin-top:10px">查看初始账号 / 密码</button>
      <button type="button" class="login-force" onclick="window.forceJitongUpdate && window.forceJitongUpdate()">登录没反应？强制更新</button>
    </div>
    <div class="login-foot">${icon('shield')} 生产版 · 数据集中保存 · 一人一号</div>
  </section>`;
}

function loginHTML() {
  if (SERVER_MODE) return serverLoginHTML();
  const featured = ['E013', 'E004', 'E009', 'E001', 'A001'].map(userById);
  return `<section class="screen no-nav login">
    <div class="brand-lockup">
      <div class="brand-mark">件</div>
      <div><div class="brand-name">计件通</div><div class="brand-tag">JIJIAN · 工厂计件统计</div></div>
    </div>
    <h1 class="login-title">每一件，<br/>都<em>算得清</em>。</h1>
    <p class="login-sub">双线五工序，扫码即报工。工人看到自己的件数，厂长看到真实的生产进度。</p>
    <div class="login-panel">
      <div class="login-label"><span>选择演示身份</span><span>无需密码</span></div>
      <div class="account-list">
        ${featured.map((u, idx) => `<button class="account ${idx === 0 ? 'featured' : ''}" data-action="login" data-user="${u.id}">
          <span class="avatar">${esc(u.avatar)}</span>
          <span class="account-info">
            <span class="account-name">${esc(u.name)} ${u.isAdmin ? '<span class="account-badge">管理端</span>' : ''}</span>
            <span class="account-role">${esc(u.role)}</span>
          </span>${icon('arrow')}
        </button>`).join('')}
      </div>
      <div class="login-more"><button data-action="all-accounts">查看全部 ${EMPLOYEES.length} 名员工</button></div>
    </div>
    <div class="login-foot">${icon('shield')} 演示数据仅保存在本机浏览器${location.protocol === 'file:' ? ' · 单文件版' : ''}</div>
  </section>`;
}

/* ------------------------- 首页 ------------------------- */
function homeHTML() {
  const emp = state.employee;
  const today = totalsForUser(emp.id, 'today');
  const month = totalsForUser(emp.id, 'month');
  const pending = pendingRecords().filter((r) => r.employeeId === emp.id);
  const primary = primaryUnit(today.totals);
  const primaryValue = today.totals[primary] || 0;
  const otherUnits = Object.entries(today.totals).filter(([unit, val]) => unit !== primary && val > 0);
  const active = activeJobs().slice(0, 3);
  const recent = db.records.filter((r) => r.employeeId === emp.id && r.status !== 'void').slice(0, 3);
  const offLine = !navigator.onLine;

  return `<section class="screen">
    <header class="home-hero">
      <div class="hero-top">
        <div class="brand-mini"><div class="brand-mark">件</div><strong>计件通</strong></div>
        <div class="hero-actions">
          <button class="icon-btn on-dark" data-action="sync-info" aria-label="同步状态">${icon(offLine ? 'wifi-off' : 'sync')}</button>
          <button class="hero-avatar" data-action="go" data-screen="profile" aria-label="个人中心">${esc(emp.avatar)}</button>
        </div>
      </div>
      <div class="hero-greeting">
        <h1>${greeting()}，<span>${esc(emp.name)}</span></h1>
        <p><i class="dot"></i> ${esc(emp.role)} · ${dateLabel()} · ${offLine ? '离线模式' : '已连接'}</p>
      </div>
      <div class="hero-metrics">
        <div class="hero-metric">
          <div class="hm-label">${icon('box', 'icon-sm')} 今日个人产量</div>
          <div class="hm-value"><b>${fmtQty(primaryValue)}</b><span>${esc(primary)}</span></div>
          <div class="hm-foot">${otherUnits.length ? otherUnits.map(([u, v]) => `${fmtQty(v)} ${u}`).join(' · ') : (primaryValue ? '数据已计入' : '今天还没有报工')}</div>
        </div>
        <div class="hero-metric">
          <div class="hm-label">${icon('orders', 'icon-sm')} 今日报工</div>
          <div class="hm-value"><b>${today.records.length}</b><span>笔</span></div>
          <div class="hm-foot">本月 ${month.records.length} 笔 · 累计 ${totalsText(month.totals)}</div>
        </div>
      </div>
    </header>

    <button class="scan-card" data-action="go" data-screen="scan">
      <span class="scan-icon">${icon('qr')}</span>
      <span class="scan-copy"><strong>扫码报工</strong><span>对准流转卡二维码 · 自动报工</span></span>
      <span class="scan-arrow">${icon('arrow')}</span>
    </button>

    ${emp.canCreateJob ? `<button class="card" style="margin:12px 16px 0;padding:12px 14px;display:flex;align-items:center;gap:10px;text-align:left;width:calc(100% - 32px)" data-action="new-order">
      <span class="qi-icon" style="width:32px;height:32px;border-radius:9px;display:grid;place-items:center;background:#eaf2ff;color:#1467e8">${icon('plus')}</span>
      <span style="flex:1"><strong style="display:block;font-size:12px">新建订单</strong><span style="display:block;font-size:9.5px;color:var(--muted)">排版人员专用 · 生成流转码</span></span>
      ${icon('arrow')}
    </button>` : ''}

    <div class="quick-row">
      <button class="quick-item" data-action="go" data-screen="records">
        <span class="qi-icon" style="background:#eaf2ff;color:#1467e8">${icon('orders')}</span><strong>我的记录</strong><span>逐笔可查</span>
      </button>
      <button class="quick-item" data-action="go" data-screen="orders">
        <span class="qi-icon" style="background:#fff4e5;color:#b25a00">${icon('box')}</span><strong>订单进度</strong><span>五道工序</span>
      </button>
      <button class="quick-item" data-action="go" data-screen="stats">
        <span class="qi-icon" style="background:#e8f8f0;color:#067647">${icon('chart')}</span><strong>我的统计</strong><span>日 / 周 / 月</span>
      </button>
    </div>

    ${pending.length ? `<button class="pending-card" data-action="go" data-screen="records" data-filter="pending">
      ${icon('alert')}<span>你有 <b>${pending.length}</b> 笔记录待厂长确认，暂未计入产量</span><span class="spacer"></span>${icon('arrow', 'icon-sm')}
    </button>` : ''}

    <div class="section">
      <div class="section-head">
        <div><div class="section-title">进行中订单</div><div class="section-desc">按工序流转，实时看到做到哪一步</div></div>
        <button class="section-link" data-action="go" data-screen="orders">全部 ${icon('arrow')}</button>
      </div>
      <div class="job-list">
        ${active.length ? active.map((job) => jobCardHTML(job)).join('') : '<div class="empty"><strong>没有进行中的订单</strong><span>新订单会显示在这里</span></div>'}
      </div>
    </div>

    <div class="section">
      <div class="section-head">
        <div><div class="section-title">我的最近报工</div><div class="section-desc">每一笔都有时间、订单和工序</div></div>
        <button class="section-link" data-action="go" data-screen="records">全部 ${icon('arrow')}</button>
      </div>
      <div class="card" style="padding:4px 14px">
        ${recent.length ? `<div class="timeline-list">${recent.map((r) => {
          const job = jobById(r.jobId);
          const dotColor = r.status === 'pending' ? '#f79009' : (job?.line === '亚克力' ? '#f79009' : '#1467e8');
          const ring = r.status === 'pending' ? '#fff4e5' : (job?.line === '亚克力' ? '#fff4e5' : '#eaf2ff');
          return `<button class="timeline-item" style="width:100%;text-align:left" data-action="record-detail" data-record="${r.id}">
            <span class="timeline-dot" style="background:${dotColor};box-shadow:0 0 0 4px ${ring}"></span>
            <span class="timeline-main"><strong>${esc(job?.orderNo || '未知订单')} · ${esc(stepName(job?.line || '写真', r.stepNo))}</strong><p>${dayLabel(r.at)} ${timeLabel(r.at)} · ${r.status === 'pending' ? '待确认' : '已计入'}</p></span>
            <span class="timeline-qty">${fmtQty(r.qty)}<span>${esc(r.unit)}</span></span>
          </button>`;
        }).join('')}</div>` : '<div class="empty" style="border:0;background:transparent"><strong>今天还没有报工</strong><span>扫描流转卡，开始第一笔</span></div>'}
      </div>
    </div>

    <div class="section" style="margin-top:18px">
      <div class="stat-note">${icon('shield', 'icon-sm')} 计件通只统计各工序完成数量，不计算工资、不涉及薪酬分配。数据以厂长确认后的有效记录为准。</div>
    </div>
    ${bottomNav('home')}
  </section>`;
}

/* ------------------------- 扫码 ------------------------- */
function scanHTML() {
  const emp = state.employee;
  const jobs = activeJobs();
  return `<section class="screen no-nav scan-screen" id="scan-screen">
    <header class="scan-head">
      <button class="icon-btn on-dark" data-action="go" data-screen="home" aria-label="返回">${icon('left')}</button>
      <div class="topbar-title">扫码报工</div>
      <button class="icon-btn on-dark" data-action="camera" aria-label="打开相机">${icon('camera')}</button>
    </header>
    <div class="scan-stage">
      <div class="camera-view" id="camera-view">
        <video id="scan-video" playsinline muted></video>
        <div class="scan-placeholder"><div class="fake-card"></div><span>将流转卡二维码放入框内</span></div>
        <span class="corner tl"></span><span class="corner tr"></span><span class="corner bl"></span><span class="corner br"></span>
        <div class="scan-line"></div>
      </div>
      <div class="scan-hint"><strong>扫一扫，自动报工</strong><p>自动确定你的工序，按订单数量提交</p></div>
    </div>
    <div class="scan-bottom">
      <div class="scan-bottom-head"><strong>选择演示流转卡</strong><span>${jobs.length} 个进行中</span></div>
      <div class="demo-scan-list">
        ${jobs.map((job) => {
          const step = findEligibleStep(job, emp);
          return `<button class="demo-scan" data-action="scan-job" data-job="${job.id}">
            <span class="qr-mini">${icon('qr')}</span>
            <span class="ds-main"><strong>${esc(job.orderNo)}</strong><span>${esc(job.remark || `${job.line}线 · ${job.unit}`)} · ${step ? `可报 ${esc(stepName(job.line, step.no))}` : '暂无可报工序'}</span></span>
            ${icon('arrow')}
          </button>`;
        }).join('')}
      </div>
      <div class="manual-row">
        <input id="manual-code" placeholder="输入订单号 / 流转码，如 TB…" autocomplete="off" />
        <button class="btn btn-soft btn-sm" data-action="manual-code">查找</button>
      </div>
    </div>
  </section>`;
}

/* ------------------------- 报工 ------------------------- */
function entryHTML() {
  const emp = state.employee;
  const entry = state.entry;
  if (!entry) return homeHTML();
  const job = jobById(entry.jobId);
  if (!job) return homeHTML();
  const step = job.steps.find((s) => s.no === entry.stepNo);
  const line = lineInfo(job.line);
  const allowed = canReport(emp, job.line, step.no);
  const done = stepDoneByUnit(job, step.no);
  const upstreamDone = step.no > 1 ? stepDoneByUnit(job, step.no - 1) : stepPlanned(job, step.no);
  const upstreamUnit = step.no > 1 ? stepUnit(job, step.no - 1) : step.unit;
  const max = maxReportable(job, step.no);
  const qty = Number(entry.qty || 0);
  const over = Number.isFinite(max) && qty > max + 0.0001;
  const blocked = Number.isFinite(max) && max <= 0;
  const unit = entry.unit || step.unit;
  const quickValues = Number.isFinite(max)
    ? [...new Set([round3(max * 0.25), round3(max * 0.5), max].filter((v) => v > 0))]
    : [1, 5, 10];
  const quickLabels = Number.isFinite(max) ? ['1/4', '1/2', '全部'] : ['1', '5', '10'];

  if (!allowed) {
    return `<section class="screen no-nav">
      ${topbar('无法报工', 'scan')}
      <div style="padding:40px 24px;text-align:center">
        <div class="empty"><div class="empty-icon">${icon('lock')}</div><strong>你的技能不包含该工序</strong><span>「${esc(job.orderNo)}」需要「${esc(stepName(job.line, step.no))}」权限。<br/>请联系厂长在人员管理中授权。</span></div>
        <button class="btn btn-primary btn-block" style="margin-top:16px" data-action="go" data-screen="scan">返回扫码</button>
      </div>
    </section>`;
  }

  return `<section class="screen no-nav entry-screen">
    ${topbar('确认报工', 'scan', `<button class="icon-btn" data-action="entry-help" aria-label="报工说明">${icon('help')}</button>`)}
    <div class="entry-hero ${lineClass(job.line)}">
      <div class="entry-hero-top">${lineChip(job.line, `第 ${step.no}/5 道`)}</div>
      <h2>${esc(job.orderNo)}</h2>
      <div class="entry-meta">
        <span>${icon('edit')} ${esc(job.remark || '无备注')}</span><span>${icon('box')} 单位 ${esc(job.unit)}</span>${job.meters > 0 ? `<span>${icon('chart')} ${fmtQty(job.meters)} 米</span>` : ''}
      </div>
    </div>

    <div class="card stepper-card">
      <div class="stepper">
        ${job.steps.map((s) => {
          const isDone = stepComplete(job, s);
          return `<div class="step ${isDone ? 'done' : ''} ${s.no === step.no ? 'active' : ''}">
            <div class="step-dot">${isDone ? icon('check') : s.no}</div><div class="step-label">${esc(shortStepName(job.line, s.no))}</div>
          </div>`;
        }).join('')}
      </div>
    </div>

    <div class="card qty-card">
      <div class="qty-summary">
        <div><span>计划</span><b>${stepHasPlan(step) ? `${fmtQty(step.planned)} ${esc(step.unit)}` : '未设置'}</b></div>
        <div><span>上游已转</span><b>${step.no === 1 ? '—' : `${fmtQty(upstreamDone)} ${esc(upstreamUnit)}`}</b></div>
        <div><span>本工序已报</span><b>${fmtQty(done)} ${esc(step.unit)}</b></div>
      </div>
      <div class="qty-input-row">
        <div class="qty-input">
          <div class="label">${icon('edit', 'icon-sm')} 本次完成数量 ${blocked ? '· 上游未完成' : ''}<button class="qty-clear" data-action="clear-qty">清空</button></div>
          <div class="qty-value"><input id="qty-input" readonly value="${esc(entry.qty)}" placeholder="0" aria-label="本次完成数量" /><span class="unit">${esc(unit)}</span></div>
        </div>
        <div class="unit-toggle"><button class="active">${esc(unit)}</button></div>
      </div>
      ${blocked ? `<div class="qty-warn">${icon('alert')} 上游「${esc(stepName(job.line, step.no - 1))}」还没有完成数量，暂时不能报工。请让上游先扫码报工。</div>`
        : over ? `<div class="qty-warn">${icon('alert')} 超出可报数量 ${fmtQty(max)} ${esc(unit)}，提交后将进入「待厂长确认」，确认前不计入产量。</div>`
          : Number.isFinite(max)
            ? `<div class="qty-ok">${icon('check-circle')} 可报范围 0 – ${fmtQty(max)} ${esc(unit)}；提交后立即计入你的今日产量。</div>`
            : `<div class="qty-ok">${icon('check-circle')} 本单未设计划量，按实际完成数量报工；提交后立即计入你的今日产量。</div>`}
      ${!blocked ? `<div class="quick-qty">${quickValues.map((v, idx) => `<button data-action="quick-qty" data-value="${v}">${quickLabels[idx]} · ${fmtQty(v)}</button>`).join('')}</div>` : ''}
    </div>

    ${entry.note ? `<div class="note-preview">${icon('edit')}<span>${esc(entry.note)}</span><span class="spacer"></span><button data-action="open-note">修改</button></div>` : ''}
    ${entry.photo ? `<div class="note-preview">${icon('camera')}<img class="photo-thumb" src="${entry.photo}" alt="现场照片"/><span>已添加现场照片</span><span class="spacer"></span><button data-action="remove-photo">移除</button></div>` : ''}

    <div class="note-row">
      <button class="note-btn ${entry.note ? 'has-value' : ''}" data-action="open-note">${icon('edit')} ${entry.note ? '已填写备注' : '添加备注（可选）'}</button>
      <button class="photo-btn ${entry.photo ? 'done' : ''}" data-action="take-photo" aria-label="拍照">${icon('camera')}</button>
      <input type="file" id="photo-file" accept="image/*" capture="environment" hidden />
    </div>

    <div class="keypad">
      ${[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => `<button class="key" data-action="key" data-key="${n}">${n}</button>`).join('')}
      <button class="key" data-action="key" data-key=".">.</button>
      <button class="key" data-action="key" data-key="0">0</button>
      <button class="key fn" data-action="backspace" aria-label="退格">${icon('backspace')}</button>
    </div>

    <div class="submit-wrap">
      <button class="btn btn-primary btn-block" data-action="submit-record" ${(!qty || qty <= 0 || blocked) ? 'disabled' : ''}>
        ${over ? '提交并申请例外' : '确认提交'} ${qty > 0 ? `· ${fmtQty(qty)} ${esc(unit)}` : ''}
      </button>
      <div class="submit-hint">提交即代表你确认该工序已完成 · <button data-action="entry-help">报工遇到问题？</button></div>
    </div>
  </section>`;
}

/* ------------------------- 成功 ------------------------- */
function successHTML() {
  const r = state.lastRecord;
  if (!r) return homeHTML();
  const job = jobById(r.jobId);
  const emp = state.employee;
  const today = totalsForUser(emp.id, 'today');
  const pending = r.status === 'pending';
  return `<section class="screen no-nav success-screen">
    <div class="success-body">
      <div class="success-ring ${pending ? 'pending' : ''}"><div class="inner">${icon(pending ? 'clock' : 'check')}</div></div>
      <h1>${pending ? '已提交，待确认' : '报工成功'}</h1>
      <p class="sub">${pending ? '这笔记录超出可报范围，已转厂长确认' : '数量已计入你的今日产量'}</p>
      <div class="success-ticket">
        <div class="success-row"><span>订单</span><b>${esc(job?.orderNo || '')}<br/><small style="color:var(--muted);font-weight:400">${esc(job?.remark || '')}</small></b></div>
        <div class="success-row"><span>工序</span><b>${esc(job?.line || '')} · ${esc(stepName(job?.line || '写真', r.stepNo))}</b></div>
        <div class="success-row"><span>本次数量</span><b class="big">${fmtQty(r.qty)} ${esc(r.unit)}</b></div>
        <div class="success-row"><span>提交时间</span><b>${dayLabel(r.at)} ${timeLabel(r.at)}</b></div>
        <div class="success-row"><span>记录编号</span><b style="font-family:var(--num)">${esc(r.id)}</b></div>
      </div>
      ${pending ? `<div class="success-note">${icon('alert')} 待确认记录不计入产量，也不参与统计。厂长确认后会通知你。</div>` : ''}
      <div class="success-actions">
        <button class="btn btn-primary btn-block" data-action="go" data-screen="scan">${icon('scan')} 继续扫码报工</button>
        <button class="btn btn-ghost btn-block" data-action="go" data-screen="records">查看我的记录</button>
        <button class="btn btn-ghost btn-block" data-action="go" data-screen="home">回首页</button>
      </div>
      <div style="margin-top:18px;font-size:10px;color:var(--muted)">今日累计 ${totalsText(today.totals)} · ${today.records.length} 笔</div>
    </div>
  </section>`;
}
