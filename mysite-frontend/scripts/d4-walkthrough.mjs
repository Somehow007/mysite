// D4 走查：页面骨架统一——PageHeader back prop / 根节点 flex gap-5 / ui-table-headrow / 合集单容器 / 衬线标题兜底
// 三主题（paper-light, paper-dark, classic-light）× 桌面全量矩阵 + 结构断言 + 交互 + paper-light 移动抽查。
// 用法：DASH_USER=xxx DASH_PASS=xxx node scripts/d4-walkthrough.mjs
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

// PageHeader 覆盖的 10 个后台路由（posts/new=PostEditor 自有布局除外；collections/:id/edit 单独验）
const HEADER_ROUTES = ['', 'collections', 'users', 'comments', 'categories', 'tags', 'images', 'knowledge', 'ai', 'settings']
const THEMES = ['paper-light', 'paper-dark', 'classic-light']
const IS_PAPER = t => t.startsWith('paper')
// font-family 栈末位 generic：'serif' 才算衬线（'sans-serif' 也含 serif 子串，不能用 /serif/i）
const serifOf = f => !!f && f.split(',').pop().trim().replace(/^["']+|["']+$/g, '') === 'serif'

let pass = 0, fail = 0, skip = 0
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${detail}`) }
}
const skipped = (name, why) => { skip++; console.log(`  SKIP ${name} (${why})`) }

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

const gotoDash = async (page, route) => {
  await page.goto(`${base}/dashboard${route}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
}

// 页面根 = .ui-page-header 的父元素（已断言其父为 MAIN 的直接子节点）
// 返回：标题 rect + 根节点全部「可见」子元素的相邻间距序列
const measurePage = page => page.evaluate(() => {
  const h = document.querySelector('.ui-page-header')
  if (!h) return null
  const root = h.parentElement
  const rootOk = root && root.parentElement && root.parentElement.tagName === 'MAIN'
  const title = h.querySelector('.ui-page-title')?.getBoundingClientRect()
  const kids = rootOk
    ? [...root.children].filter(el => {
        const r = el.getBoundingClientRect()
        const cs = getComputedStyle(el)
        return r.width > 0 && r.height > 0 && cs.display !== 'none' && cs.visibility !== 'hidden'
      })
    : []
  const gaps = []
  for (let i = 1; i < kids.length; i++) {
    const a = kids[i - 1].getBoundingClientRect()
    const b = kids[i].getBoundingClientRect()
    gaps.push({
      gap: +(b.top - a.bottom).toFixed(2),
      a: (kids[i - 1].className?.toString() || kids[i - 1].tagName).slice(0, 48),
      b: (kids[i].className?.toString() || kids[i].tagName).slice(0, 48),
    })
  }
  return {
    rootOk,
    rootClass: root?.className?.toString() ?? '',
    title: title ? { left: +title.left.toFixed(2), top: +title.top.toFixed(2) } : null,
    kids: kids.length,
    gaps,
  }
})

// 读 CSS 变量的 computed 值（建临时探针元素）
const probeVars = (page, vars) => page.evaluate(vs => {
  const out = {}
  const d = document.createElement('div')
  d.style.cssText = 'position:absolute;left:-9999px;top:0;'
  document.body.appendChild(d)
  for (const v of vs) {
    d.style.cssText = `position:absolute;left:-9999px;top:0;color:var(${v});border-bottom:1px solid var(${v});background:var(${v});`
    const cs = getComputedStyle(d)
    out[v] = { color: cs.color, border: cs.borderBottomColor, bg: cs.backgroundColor }
  }
  d.remove()
  return out
}, vars)

console.log('== §1 三主题 × 10 路由：标题对齐 + 根子节点间距 ==')
const titleMatrix = {}
for (const theme of THEMES) {
  const { context, page } = await newPage(theme)
  titleMatrix[theme] = {}
  for (const route of HEADER_ROUTES) {
    await gotoDash(page, `/${route}`)
    const m = await measurePage(page)
    const name = route || '(dashboard)'
    if (!m || !m.title) { fail++; console.log(`  FAIL ${theme} ${name}: 无 .ui-page-title`); continue }
    if (!m.rootOk) { fail++; console.log(`  FAIL ${theme} ${name}: 页面根不是 MAIN 直接子节点`); continue }
    titleMatrix[theme][name] = m.title
    const bad = m.gaps.filter(g => Math.abs(g.gap - 20) > 1)
    check(`${theme} ${name} 子元素间距=20px×${m.gaps.length}`,
      m.gaps.length > 0 && bad.length === 0,
      bad.length ? `异常间距 ${JSON.stringify(bad)}` : `kids=${m.kids} gaps=${m.gaps.map(g => g.gap).join(',')}`)
  }
  await context.close()
}
for (const theme of THEMES) {
  const entries = Object.entries(titleMatrix[theme] ?? {})
  const [bn, bt] = entries[0] ?? []
  for (const [n, t] of entries.slice(1)) {
    check(`${theme} 标题对齐 ${n}`,
      Math.abs(t.left - bt.left) <= 1 && Math.abs(t.top - bt.top) <= 1,
      `基准 ${bn}(${bt.left},${bt.top}) vs ${n}(${t.left},${t.top})`)
  }
}

console.log('== §2 结构断言（paper-light 桌面）==')
{
  const { context, page } = await newPage('paper-light')

  // 知识库页：根节点已去 max-w-6xl/mx-auto/p-6
  await gotoDash(page, '/knowledge')
  const kb = await page.evaluate(() => {
    const h = document.querySelector('.ui-page-header')
    return h?.parentElement?.className?.toString() ?? ''
  })
  check('knowledge 根=flex flex-col gap-5 min-w-0',
    /\bflex\b/.test(kb) && /flex-col/.test(kb) && /gap-5/.test(kb) && /min-w-0/.test(kb)
      && !/max-w-6xl|mx-auto|space-y-6|(^|\s)p-6(\s|$)/.test(kb), kb)

  // 设置页：PageHeader 副标题 + 两张 card-solid p-6（无 rounded-2xl）+ 内层间距 20
  await gotoDash(page, '/settings')
  const st = await page.evaluate(() => {
    const sub = document.querySelector('.ui-page-header p')?.textContent?.trim() ?? null
    const sections = [...document.querySelectorAll('main section.card-solid')]
    const sr = sections.map(s => s.getBoundingClientRect())
    return {
      subtitle: sub,
      count: sections.length,
      bad: sections.filter(s => !/\bp-6\b/.test(s.className) || /rounded-2xl/.test(s.className)).length,
      innerGap: sr.length === 2 ? +(sr[1].top - sr[0].bottom).toFixed(2) : null,
    }
  })
  check('settings 副标题', st.subtitle === '资料、账号安全与偏好', String(st.subtitle))
  check('settings 2×card-solid p-6 无 rounded-2xl', st.count === 2 && st.bad === 0, JSON.stringify(st))
  check('settings 内层间距=20px', st.innerGap !== null && Math.abs(st.innerGap - 20) <= 1, String(st.innerGap))

  // 合集管理：单容器 divide-y + 行内 ui-badge
  await gotoDash(page, '/collections')
  const cl = await page.evaluate(() => {
    const wrap = document.querySelector('.card-solid.divide-y.divide-border.overflow-hidden')
    return wrap
      ? { rows: wrap.children.length, badges: wrap.querySelectorAll('.ui-badge').length }
      : null
  })
  check('collections 单容器 card-solid divide-y', !!cl && cl.rows > 0, JSON.stringify(cl))
  if (cl && cl.badges > 0) check('collections 行内 Badge≥1', cl.badges > 0, String(cl.badges))
  else skipped('collections 行内 Badge', '无私有/已完结数据')

  // 分类管理：ui-table 容器（无 bg-bg-secondary）+ headrow + tabular-nums + 行内 Badge
  await gotoDash(page, '/categories')
  const cat = await page.evaluate(() => {
    const wrap = document.querySelector('main .ui-table.overflow-hidden')
    const head = document.querySelector('.ui-table-headrow')
    const spans = head ? [...head.querySelectorAll('span')] : []
    return {
      wrapCls: wrap?.className?.toString() ?? '',
      headCls: head?.className?.toString() ?? '',
      spanCls: spans.map(s => s.className),
      hasTabular: !!document.querySelector('main .tabular-nums'),
      rowBadges: document.querySelectorAll('main .ui-badge').length,
      rows: document.querySelectorAll('main .divide-y > div').length,
    }
  })
  check('categories 容器 ui-table 无 bg-bg-secondary',
    /ui-table/.test(cat.wrapCls) && !/bg-bg-secondary/.test(cat.wrapCls), cat.wrapCls)
  check('categories headrow hook+样式类',
    /ui-table-headrow/.test(cat.headCls) && cat.spanCls.length >= 3
      && cat.spanCls.every(c => /text-xs/.test(c) && /font-medium/.test(c) && /text-text-muted/.test(c)),
    JSON.stringify(cat.spanCls))
  check('categories 数字列 tabular-nums', cat.hasTabular)
  check('categories 行内状态 Badge', cat.rowBadges > 0 && cat.rows > 0, `badges=${cat.rowBadges} rows=${cat.rows}`)

  // 评论管理：原「单容器+divide-y」结构未破
  await gotoDash(page, '/comments')
  const cm = await page.evaluate(() => ({
    wrap: !!document.querySelector('main .card-solid.overflow-hidden .divide-y'),
  }))
  check('comments 列表结构保持', cm.wrap)

  // 合集编辑：/new 与 /:id/edit 均 PageHeader back + max-w-4xl + 返回回合集管理
  await gotoDash(page, '/collections/new')
  const editNew = await page.evaluate(() => {
    const h = document.querySelector('.ui-page-header')
    const btn = h?.querySelector('button')
    const root = h?.parentElement
    return {
      title: h?.querySelector('.ui-page-title')?.textContent?.trim(),
      hasBack: !!btn,
      rootCls: root?.className?.toString() ?? '',
      cards: document.querySelectorAll('main .card-solid').length,
    }
  })
  check('collection-new PageHeader back+标题', editNew.hasBack && editNew.title === '新建合集', JSON.stringify(editNew))
  check('collection-new 根 max-w-4xl', /max-w-4xl/.test(editNew.rootCls), editNew.rootCls)
  await page.click('.ui-page-header button')
  await page.waitForTimeout(800)
  check('collection-new back→合集管理', page.url().includes('/dashboard/collections') && !page.url().includes('/new'), page.url())

  const colRes = await fetch(`${api}/v1/collections?page=1&size=1`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  }).then(r => r.json()).catch(() => null)
  const colId = colRes?.data?.list?.[0]?.id ?? colRes?.data?.records?.[0]?.id
  if (colId) {
    await gotoDash(page, `/collections/${colId}/edit`)
    const editId = await page.evaluate(() => ({
      title: document.querySelector('.ui-page-title')?.textContent?.trim(),
      hasBack: !!document.querySelector('.ui-page-header button'),
      cards: document.querySelectorAll('main .card-solid').length,
    }))
    check('collection-edit PageHeader back+标题', editId.hasBack && editId.title === '编辑合集', JSON.stringify(editId))
    check('collection-edit 两块 card-solid', editId.cards >= 2, String(editId.cards))
    await page.click('.ui-page-header button')
    await page.waitForTimeout(800)
    check('collection-edit back→合集管理', page.url().includes('/dashboard/collections') && !page.url().includes('/edit'), page.url())
  } else {
    skipped('collection-edit', '未取到合集 id')
  }

  // AI 管理：切三个页签，根子节点间距保持 20
  await gotoDash(page, '/ai')
  for (const label of ['消费看板', '模型与 API']) {
    await page.click(`text=${label}`)
    await page.waitForTimeout(1200)
    const m = await measurePage(page)
    const bad = m?.gaps?.filter(g => Math.abs(g.gap - 20) > 1) ?? []
    check(`ai tab ${label} 间距=20px`, !!m && m.gaps.length > 0 && bad.length === 0,
      bad.length ? JSON.stringify(bad) : `kids=${m?.kids}`)
  }

  // 知识库选中详情面板：面板与上方网格间距 20（有 KB 数据才验）
  await gotoDash(page, '/knowledge')
  const kbBtn = page.locator('main .dash-hoverable').first()
  if (await kbBtn.count() > 0) {
    await kbBtn.click()
    await page.waitForTimeout(1200)
    const m = await measurePage(page)
    const bad = m?.gaps?.filter(g => Math.abs(g.gap - 20) > 1) ?? []
    check('knowledge 选中后详情面板间距=20px', !!m && bad.length === 0,
      bad.length ? JSON.stringify(bad) : `kids=${m?.kids}`)
  } else {
    skipped('knowledge 详情面板间距', '无知识库数据')
  }

  // dash-mode 生命周期：dashboard → 点「回到主页」→ class 摘除
  await gotoDash(page, '')
  check('dash-mode 挂载', await page.evaluate(() => document.documentElement.classList.contains('dash-mode')))
  await page.click('a[href="/"]')
  await page.waitForTimeout(1200)
  check('离开后台 dash-mode 摘除',
    !(await page.evaluate(() => document.documentElement.classList.contains('dash-mode')))
      && !page.url().includes('/dashboard'), page.url())

  await context.close()
}

console.log('== §3 主题 computed（每主题 /categories + /posts/new）==')
for (const theme of THEMES) {
  const { context, page } = await newPage(theme)
  await gotoDash(page, '/categories')
  const vars = await probeVars(page, ['--border-strong', '--border', '--text-secondary', '--text-muted', '--bg-code'])
  const catCss = await page.evaluate(() => {
    const head = document.querySelector('.ui-table-headrow')
    const span = head?.querySelector('span')
    const badge = document.querySelector('main .ui-badge')
    const title = document.querySelector('.ui-page-title')
    const cs = el => el ? getComputedStyle(el) : null
    return {
      headBg: cs(head)?.backgroundColor,
      headBorder: cs(head)?.borderBottomColor,
      headBorderW: cs(head)?.borderBottomWidth,
      spanColor: cs(span)?.color,
      spanSize: cs(span)?.fontSize,
      spanWeight: cs(span)?.fontWeight,
      badgeRadius: cs(badge)?.borderRadius,
      titleFamily: cs(title)?.fontFamily,
      titleWeight: cs(title)?.fontWeight,
    }
  })
  const paper = IS_PAPER(theme)
  check(`${theme} headrow 底=透明(paper)/bg-code(classic)`,
    paper ? catCss.headBg === 'rgba(0, 0, 0, 0)' : catCss.headBg !== 'rgba(0, 0, 0, 0)', catCss.headBg)
  check(`${theme} headrow 下边色=${paper ? 'border-strong' : 'border'}`,
    catCss.headBorder === (paper ? vars['--border-strong'].bg : vars['--border'].bg),
    `${catCss.headBorder} vs ${paper ? vars['--border-strong'].bg : vars['--border'].bg}`)
  check(`${theme} headrow 文字色=${paper ? 'text-secondary' : 'text-muted'}`,
    catCss.spanColor === (paper ? vars['--text-secondary'].color : vars['--text-muted'].color),
    `${catCss.spanColor} vs ${paper ? vars['--text-secondary'].color : vars['--text-muted'].color}`)
  check(`${theme} headrow 文字 12px/500`, catCss.spanSize === '12px' && catCss.spanWeight === '500', `${catCss.spanSize}/${catCss.spanWeight}`)
  check(`${theme} 徽标圆角=${paper ? '0px' : '非0'}`,
    paper ? catCss.badgeRadius === '0px' : catCss.badgeRadius !== '0px', catCss.badgeRadius)
  // 分类页没有 .card-solid，到合集页量（列表容器与分页卡都是 card-solid）
  await gotoDash(page, '/collections')
  const cardRadius = await page.evaluate(() => {
    const el = document.querySelector('main .card-solid')
    return el ? getComputedStyle(el).borderRadius : null
  })
  check(`${theme} card-solid 圆角=${paper ? '0px' : '14px'}`,
    cardRadius === (paper ? '0px' : '14px'), String(cardRadius))
  check(`${theme} 页标题衬线=${paper}`,
    serifOf(catCss.titleFamily) === paper && (!paper || catCss.titleWeight === '900'),
    `${catCss.titleFamily} w${catCss.titleWeight}`)

  await gotoDash(page, '/posts/new')
  const ed = await page.evaluate(() => {
    const h1 = document.querySelector('h1.ui-page-title')
    if (!h1) return null
    const cs = getComputedStyle(h1)
    return { family: cs.fontFamily, weight: cs.fontWeight }
  })
  check(`${theme} PostEditor h1 ui-page-title 衬线=${paper}`,
    !!ed && serifOf(ed.family) === paper && (!paper || ed.weight === '900'), JSON.stringify(ed))
  await context.close()
}

console.log('== §4 paper-light 移动端抽查（390px）==')
{
  const { context, page } = await newPage('paper-light', { width: 390, height: 844 })
  for (const route of ['collections', 'categories', 'knowledge', 'settings', 'posts/new']) {
    await gotoDash(page, `/${route}`)
    const m = await page.evaluate(() => ({
      overflow: document.documentElement.scrollWidth - window.innerWidth,
      hasTitle: !!document.querySelector('.ui-page-title'),
    }))
    check(`mobile ${route || 'posts'} 无横向溢出+标题存在`, m.overflow <= 0 && m.hasTitle, JSON.stringify(m))
  }
  await context.close()
}

await browser.close()
console.log(`D4 WALKTHROUGH DONE: ${pass} pass / ${fail} fail / ${skip} skip`)
process.exit(fail === 0 ? 0 : 1)
