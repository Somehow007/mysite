// D3 走查：语义色 token / StatsCard neutral / Badge outline / 角色与调用类型徽标
// 三主题（paper-light, paper-dark, classic-light）× 桌面，外加 paper-light 移动抽查。
// 用法：DASH_USER=xxx DASH_PASS=xxx node scripts/d3-walkthrough.mjs
// 注意：走查窗口内 t14check 被临时置为 CREATOR+禁用，用于覆盖条件分支；跑完需还原。
import { chromium } from 'playwright'

const base = process.env.DASH_BASE ?? 'http://127.0.0.1:5173'
const api = process.env.DASH_API ?? 'http://127.0.0.1:8081'
const { DASH_USER: username, DASH_PASS: password } = process.env
if (!username || !password) { console.error('需要 DASH_USER / DASH_PASS'); process.exit(2) }

const res = await fetch(`${api}/v1/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }),
}).then(r => r.json())
if (!res?.data?.accessToken) { console.error('登录失败', res?.message ?? res); process.exit(2) }
const { accessToken, refreshToken, role } = res.data

// 每主题期望值：computed rgb / 徽标圆角（dash-mode paper 方角，classic 保留 pill）
const THEMES = {
  'paper-light': {
    success: 'rgb(42, 107, 67)', warning: 'rgb(133, 87, 15)',
    danger: 'rgb(168, 38, 27)', info: 'rgb(54, 89, 122)',
    border: 'rgb(217, 207, 189)', badgeRadius: '0px',
  },
  'paper-dark': {
    success: 'rgb(111, 191, 138)', warning: 'rgb(224, 168, 74)',
    danger: 'rgb(240, 123, 110)', info: 'rgb(143, 176, 207)',
    border: 'rgb(58, 51, 42)', badgeRadius: '0px',
  },
  'classic-light': {
    success: 'rgb(16, 185, 129)', warning: 'rgb(245, 158, 11)',
    danger: 'rgb(239, 68, 68)', info: 'rgb(59, 130, 246)',
    border: 'rgb(229, 231, 235)', badgeRadius: 'pill',
  },
}

let pass = 0, fail = 0, skip = 0
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${detail}`) }
}
const skipped = (name, why) => { skip++; console.log(`  SKIP ${name} (${why})`) }
const has = (cls, token) => cls.includes(token)

const browser = await chromium.launch()

async function newPage(theme, viewport = { width: 1440, height: 900 }) {
  const context = await browser.newContext({ viewport })
  await context.addInitScript(([t, a, r, role]) => {
    localStorage.setItem('mysite_theme_v2', t)
    localStorage.setItem('mysite_access_token', JSON.stringify(a))
    localStorage.setItem('mysite_refresh_token', JSON.stringify(r))
    localStorage.setItem('mysite_user_role', JSON.stringify(role))
  }, [theme, accessToken, refreshToken, role])
  const page = await context.newPage()
  page.on('pageerror', e => { fail++; console.log(`  FAIL PAGEERROR ${e.message}`) })
  return { context, page }
}

// 采集页面上的 ui-badge 全部信息
const collectBadges = page => page.evaluate(() =>
  [...document.querySelectorAll('span.ui-badge')].map(b => ({
    text: b.textContent.trim(),
    cls: [...b.classList].join(' '),
    color: getComputedStyle(b).color,
    bg: getComputedStyle(b).backgroundColor,
    radius: getComputedStyle(b).borderRadius,
    bw: getComputedStyle(b).borderTopWidth,
    bc: getComputedStyle(b).borderTopColor,
  })))

// 采集 StatsCard（label + icon class + value class）
const collectStats = page => page.evaluate(() =>
  [...document.querySelectorAll('div.card-solid')]
    .map(c => {
      const icon = [...c.querySelectorAll('div')].find(d =>
        d.classList.contains('w-10') && d.classList.contains('h-10'))
      const labelEl = [...c.querySelectorAll('div')].find(d =>
        d.className.includes('12.5px') || d.className.includes('text-\\[12\\.5px\\]'))
      const valEl = c.querySelector('.tabular-nums')
      return {
        label: labelEl?.textContent?.trim() ?? '',
        iconCls: icon ? [...icon.classList].join(' ') : null,
        valCls: valEl ? [...valEl.classList].join(' ') : null,
        val: valEl?.textContent?.trim() ?? '',
      }
    })
    .filter(c => c.iconCls))

for (const [theme, T] of Object.entries(THEMES)) {
  console.log(`\n=== ${theme} /dashboard/users ===`)
  const { context, page } = await newPage(theme)
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)

  // ── 统计卡 ──
  const stats = await collectStats(page)
  const byLabel = Object.fromEntries(stats.map(s => [s.label, s]))
  for (const label of ['用户总数', '管理员', '创作者']) {
    check(`${label} icon neutral`, byLabel[label]?.iconCls?.includes('bg-bg-secondary'), byLabel[label]?.iconCls)
  }
  const disabled = byLabel['禁用']
  const disabledVal = parseInt(disabled?.val ?? '0', 10) || 0
  if (disabledVal > 0) {
    check('禁用 icon danger(>0)', disabled.iconCls.includes('bg-danger-subtle') && disabled.iconCls.includes('text-danger'), disabled.iconCls)
    check('禁用 value text-danger', disabled.valCls.includes('text-danger'), disabled.valCls)
  } else {
    check('禁用 icon neutral(=0)', disabled?.iconCls?.includes('bg-bg-secondary'), disabled?.iconCls)
    check('禁用 value 无着色', !disabled?.valCls?.includes('text-danger'), disabled?.valCls)
  }

  // ── 徽标 ──
  const badges = await collectBadges(page)
  const badgeByText = t => badges.find(b => b.text === t)

  const adminB = badgeByText('系统管理员')
  check('ADMIN badge accent', !!adminB && has(adminB.cls, 'bg-accent-subtle') && has(adminB.cls, 'text-accent'), adminB?.cls)
  const creatorB = badgeByText('创作者')
  if (creatorB) {
    check('CREATOR badge outline', has(creatorB.cls, 'border') && has(creatorB.cls, 'bg-transparent') && has(creatorB.cls, 'text-text-secondary'), creatorB.cls)
    check('CREATOR badge border=theme', creatorB.bc === T.border && creatorB.bw === '1px', `${creatorB.bc}/${creatorB.bw}`)
  } else skipped('CREATOR badge', '无 CREATOR 用户')
  const userB = badgeByText('普通用户')
  check('USER badge muted', !!userB && has(userB.cls, 'bg-bg-code') && has(userB.cls, 'text-text-muted'), userB?.cls)

  // 徽标圆角：paper+dash-mode 方角(0px)，classic 保留 pill（Tailwind v4 rounded-full
  // 编译为 calc(infinity*1px)，computed 是天文数字而非 9999px，按 >=9999 判）
  const anyBadge = badges[0]
  const radiusOk = T.badgeRadius === 'pill'
    ? parseFloat(anyBadge?.radius ?? '0') >= 9999
    : anyBadge?.radius === T.badgeRadius
  check('badge radius', radiusOk, `${anyBadge?.radius} (${T.badgeRadius})`)

  // 状态徽标 computed 颜色 = 主题语义色
  const okB = badgeByText('正常')
  check('正常 badge color=success', okB?.color === T.success, `${okB?.color} != ${T.success}`)
  const disB = badgeByText('禁用')
  if (disB) {
    check('禁用 badge color=danger', disB.color === T.danger, `${disB.color} != ${T.danger}`)
  } else skipped('禁用 status badge', '无禁用用户')

  await context.close()

  // ── /dashboard 文章管理：手写状态徽标 computed 色 ──
  console.log(`\n=== ${theme} /dashboard (posts) ===`)
  const c2 = await newPage(theme)
  await c2.page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await c2.page.waitForTimeout(1200)
  const pubSpans = await c2.page.evaluate(() =>
    [...document.querySelectorAll('span')]
      .filter(s => ['已发布', '草稿'].includes(s.textContent.trim()) && s.className.includes('subtle'))
      .map(s => ({ text: s.textContent.trim(), color: getComputedStyle(s).color })))
  const pub = pubSpans.find(s => s.text === '已发布')
  const draft = pubSpans.find(s => s.text === '草稿')
  check('已发布 color=success', pub?.color === T.success, `${pub?.color}`)
  if (draft) check('草稿 color=warning', draft.color === T.warning, `${draft.color}`)
  else skipped('草稿 badge', '无草稿')
  await c2.context.close()

  // ── /dashboard/ai：调用类型徽标 + 统计卡 ──
  console.log(`\n=== ${theme} /dashboard/ai ===`)
  const c3 = await newPage(theme)
  await c3.page.goto(`${base}/dashboard/ai`, { waitUntil: 'domcontentloaded' })
  await c3.page.waitForTimeout(1200)
  const aiBadges = await collectBadges(c3.page)
  const cb = t => aiBadges.find(b => b.text === t)
  check('CHAT badge accent', has(cb('对话生成')?.cls ?? '', 'bg-accent-subtle'))
  for (const [t, label] of [['查询改写', 'REWRITE'], ['意图分类', 'CLASSIFY'], ['向量化', 'EMBED'], ['精排', 'RERANK']]) {
    const b = cb(t)
    if (b) check(`${label} badge outline`, has(b.cls, 'border') && has(b.cls, 'bg-transparent'), b.cls)
    else skipped(`${label} badge`, '无该类型日志')
  }
  // 消费看板统计卡
  await c3.page.click('text=消费看板')
  await c3.page.waitForTimeout(1500)
  const aiStats = await collectStats(c3.page)
  const aiByLabel = Object.fromEntries(aiStats.map(s => [s.label, s]))
  check('费用 icon accent', aiByLabel['费用']?.iconCls?.includes('bg-accent-subtle'), aiByLabel['费用']?.iconCls)
  check('Token icon neutral', aiByLabel['Token']?.iconCls?.includes('bg-bg-secondary'), aiByLabel['Token']?.iconCls)
  check('调用次数 icon neutral', aiByLabel['调用次数']?.iconCls?.includes('bg-bg-secondary'), aiByLabel['调用次数']?.iconCls)
  check('成功率 icon semantic', ['bg-success-subtle', 'bg-warning-subtle', 'bg-danger-subtle']
    .some(c => aiByLabel['成功率']?.iconCls?.includes(c)), aiByLabel['成功率']?.iconCls)
  await c3.context.close()
}

// ── paper-light 移动视口抽查 ──
console.log('\n=== paper-light mobile 390px /dashboard/users ===')
{
  const { context, page } = await newPage('paper-light', { width: 390, height: 844 })
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  check('mobile no h-overflow', overflow <= 0, `overflow=${overflow}`)
  const badges = await collectBadges(page)
  check('mobile badges rendered', badges.length > 0, `count=${badges.length}`)
  const stats = await collectStats(page)
  check('mobile stats rendered', stats.length >= 4, `count=${stats.length}`)
  await context.close()
}

await browser.close()
console.log(`\n${pass} pass, ${fail} fail, ${skip} skip`)
process.exit(fail ? 1 : 0)
