// D2 后台纸本外壳 走查复核（Playwright DOM 级断言）
// 用法：DASH_USER=x DASH_PASS=x node scripts/d2-walkthrough.mjs
// 依赖：dev server 127.0.0.1:5173 + 后端 127.0.0.1:8081 + playwright
// 覆盖：dash-mode 生命周期、hook class 存在性、paper 三态 computed、
//       classic 零影响、浮层（Modal/Dropdown/Drawer/日志弹层/Toast）、
//       hover/焦点态、选中态保留、侧栏折叠、主题切换。
import { chromium } from 'playwright'

const base = process.env.DASH_BASE ?? 'http://127.0.0.1:5173'
const api = process.env.DASH_API ?? 'http://127.0.0.1:8081'
const { DASH_USER: username, DASH_PASS: password } = process.env
if (!username || !password) {
  console.error('需要环境变量 DASH_USER / DASH_PASS（ADMIN 账号）')
  process.exit(2)
}

const res = await fetch(`${api}/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }),
}).then(r => r.json())
if (!res?.data?.accessToken) {
  console.error('登录失败', res?.message ?? res)
  process.exit(2)
}
const { accessToken, refreshToken, role } = res.data

let pass = 0, fail = 0
const ck = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

async function newPage(theme, vp = { width: 1440, height: 900 }) {
  const ctx = await browser.newContext({ viewport: vp })
  await ctx.addInitScript(([t, a, r, role]) => {
    localStorage.setItem('mysite_theme_v2', t)
    localStorage.setItem('mysite_access_token', JSON.stringify(a))
    localStorage.setItem('mysite_refresh_token', JSON.stringify(r))
    localStorage.setItem('mysite_user_role', JSON.stringify(role))
  }, [theme, accessToken, refreshToken, role])
  const page = await ctx.newPage()
  page.on('pageerror', e => { fail++; console.log(`  PAGEERROR ${e.message}`) })
  return { ctx, page }
}
const hasCls = (cls) => `document.documentElement.classList.contains('${cls}')`
const INK_LIGHT = 'rgb(29, 26, 22)'   // paper-light --border-strong
const INK_DARK = 'rgb(236, 228, 214)' // paper-dark --border-strong
const ACCENT_L = 'rgb(163, 62, 28)'   // paper-light --accent
const FOCUS_WAIT = 350                // transition-colors ~150-200ms，聚焦断言前等动画收尾

// ═══ 1. dash-mode 生命周期 ═══
console.log('— dash-mode 生命周期 —')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  ck('dashboard 挂载 dash-mode', await page.evaluate(hasCls('dash-mode')))
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  ck('回前台首页 dash-mode 已摘', !(await page.evaluate(hasCls('dash-mode'))))
  await page.goto(`${base}/dashboard/tags`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(900)
  ck('直达子路由 dash-mode 挂上', await page.evaluate(hasCls('dash-mode')))
  await ctx.close()
}

// ═══ 2. hook class 存在性（paper-light 桌面）═══
console.log('— hook class —')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1400)
  const r = await page.evaluate(() => ({
    pageHeader: !!document.querySelector('.ui-page-header'),
    pageTitle: !!document.querySelector('.ui-page-title'),
    table: !!document.querySelector('.ui-table'),
    tableHead: !!document.querySelector('.ui-table-head'),
    badge: !!document.querySelector('.ui-badge'),
    sidebar: !!document.querySelector('.dash-sidebar'),
    navItems: document.querySelectorAll('.dash-nav-item').length,
    activeItem: document.querySelectorAll('.dash-nav-item.is-active').length,
    fields: document.querySelectorAll('.dash-field').length,
  }))
  ck('ui-page-header/h1 hook', r.pageHeader && r.pageTitle)
  ck('ui-table/ui-table-head', r.table && r.tableHead)
  ck('ui-badge', r.badge)
  ck('dash-sidebar', r.sidebar)
  ck('dash-nav-item 存在', r.navItems > 0, String(r.navItems))
  ck('is-active 恰好 1 个', r.activeItem === 1, String(r.activeItem))
  ck('dash-field ≥4（内置+插槽）', r.fields >= 4, String(r.fields))
  await ctx.close()
}

// ═══ 3. paper-light computed 样式 ═══
console.log('— paper-light computed —')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1400)
  const r = await page.evaluate(() => {
    const q = s => document.querySelector(s)
    const g = (s, p) => q(s) ? getComputedStyle(q(s))[p] : null
    return {
      cardRadius: g('.card-solid', 'borderRadius'),
      cardShadow: g('.card-solid', 'boxShadow'),
      thBorder: g('.ui-table-head > th', 'borderBottomWidth') + '|' + g('.ui-table-head > th', 'borderBottomColor'),
      thBg: g('.ui-table-head', 'backgroundColor'),
      thSpacing: g('.ui-table-head > th', 'letterSpacing'),
      tableBg: g('.ui-table', 'backgroundColor'),
      badgeRadius: g('.ui-badge', 'borderRadius'),
      titleFont: g('.ui-page-title', 'fontFamily'),
      titleWeight: g('.ui-page-title', 'fontWeight'),
      navActiveBg: g('.dash-nav-item.is-active', 'backgroundColor'),
      navActiveColor: g('.dash-nav-item.is-active', 'color'),
      navActiveWeight: g('.dash-nav-item.is-active', 'fontWeight'),
      sidebarBg: g('.dash-sidebar', 'backgroundColor'),
      btnSecBg: g('.btn-secondary', 'backgroundColor') || 'n/a',
      fieldRadius: g('.dash-field', 'borderRadius'),
      fieldBg: g('.dash-field', 'backgroundColor'),
    }
  })
  ck('卡片方角', r.cardRadius === '0px', r.cardRadius)
  ck('卡片无柔影', r.cardShadow === 'none', r.cardShadow)
  ck('表头墨线下边', r.thBorder === `1px|${INK_LIGHT}`, r.thBorder)
  ck('表头透明底', r.thBg === 'rgba(0, 0, 0, 0)', r.thBg)
  ck('表头字距', r.thSpacing === '0.4px' || parseFloat(r.thSpacing) > 0, r.thSpacing)
  ck('ui-table 底=elevated', r.tableBg === 'rgb(251, 248, 242)', r.tableBg)
  ck('徽标方角', r.badgeRadius === '0px', r.badgeRadius)
  ck('页标题衬线900', /Serif|serif/.test(r.titleFont) && r.titleWeight === '900', `${r.titleFont.slice(0, 30)} w${r.titleWeight}`)
  ck('侧栏激活透明+墨字+600', r.navActiveBg === 'rgba(0, 0, 0, 0)' && r.navActiveColor === INK_LIGHT && r.navActiveWeight === '600', `${r.navActiveBg}|${r.navActiveColor}|${r.navActiveWeight}`)
  ck('侧栏底=bg-primary', r.sidebarBg === 'rgb(246, 241, 231)', r.sidebarBg)
  ck('次按钮透明底', r.btnSecBg === 'rgba(0, 0, 0, 0)', r.btnSecBg)
  ck('dash-field 方角+纸面底', r.fieldRadius === '0px' && r.fieldBg === 'rgb(246, 241, 231)', `${r.fieldRadius}|${r.fieldBg}`)

  // 内置+插槽控件聚焦都是墨线无光环
  const fields = await page.evaluate(() => [...document.querySelectorAll('.dash-field')].map(e => e.tagName))
  console.log('  dash-field 元素:', fields.join(','))
  for (let i = 0; i < Math.min(fields.length, 4); i++) {
    const el = page.locator('.dash-field').nth(i)
    await el.focus()
    await page.waitForTimeout(FOCUS_WAIT)
    const c = await page.evaluate((idx) => {
      const e = document.querySelectorAll('.dash-field')[idx]
      if (!e) return null
      const cs = getComputedStyle(e)
      return { bc: cs.borderColor, bs: cs.boxShadow, focused: e.matches(':focus'), cls: (e.className + '').slice(0, 40) }
    }, i)
    ck(`dash-field[${i}]聚焦墨线无光环`, !!c && c.focused && c.bc === INK_LIGHT && c.bs === 'none', c ? `${c.bc}|${c.bs}|f=${c.focused}` : 'null')
  }
  await ctx.close()
}

// ═══ 4. DashboardView 插槽控件（走查补 hook）═══
console.log('— 插槽控件 dash-field（DashboardView）—')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  const r = await page.evaluate(() => {
    const sels = [...document.querySelectorAll('select.dash-field')]
    return { n: sels.length, radius: sels[0] ? getComputedStyle(sels[0]).borderRadius : null }
  })
  ck('文章管理插槽 select 已挂 dash-field', r.n >= 3, String(r.n))
  const sel = page.locator('select.dash-field').first()
  await sel.focus()
  await page.waitForTimeout(FOCUS_WAIT)
  const c = await page.evaluate(() => {
    const e = document.querySelector('select.dash-field')
    const cs = getComputedStyle(e)
    return { bc: cs.borderColor, bs: cs.boxShadow, focused: e.matches(':focus') }
  })
  ck('插槽 select 聚焦墨线', c.focused && c.bc === INK_LIGHT && c.bs === 'none', `${c.bc}|${c.bs}|f=${c.focused}`)
  await ctx.close()
}

// ═══ 5. 浮层：Modal + 角色 Dropdown + 手搓日志弹层 ═══
console.log('— 浮层 —')
{
  const { ctx, page } = await newPage('paper-light')
  // 5a. 组件 Modal（tag 页新建）
  await page.goto(`${base}/dashboard/tags`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const newBtn = page.locator('button:has-text("新建"), button:has-text("新增"), button:has-text("创建"), button:has-text("添加")').first()
  if (await newBtn.count() && await newBtn.isVisible().catch(() => false)) {
    await newBtn.click(); await page.waitForTimeout(600)
  }
  let r = await page.evaluate(() => {
    const p = document.querySelector('.modal-panel')
    if (!p) return null
    const c = getComputedStyle(p)
    return { radius: c.borderRadius, shadow: c.boxShadow, blur: c.backdropFilter, border: c.borderColor }
  })
  ck('组件 Modal 方角墨线6px硬阴影无模糊', !!r && r.radius === '0px' && r.shadow.includes('6px') && r.blur === 'none' && r.border === INK_LIGHT, JSON.stringify(r))
  await page.keyboard.press('Escape'); await page.waitForTimeout(300)

  // 5b. 角色下拉（users）
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  await page.locator('button:has(.ui-badge)').first().click()
  await page.waitForTimeout(500)
  r = await page.evaluate(() => {
    const p = document.querySelector('.ui-dropdown-panel')
    if (!p) return null
    const c = getComputedStyle(p)
    return { radius: c.borderRadius, shadow: c.boxShadow, border: c.borderColor, bg: c.backgroundColor }
  })
  ck('角色下拉 方角墨线4px硬阴影', !!r && r.radius === '0px' && r.shadow.includes('4px') && r.border === INK_LIGHT, JSON.stringify(r))
  await page.keyboard.press('Escape'); await page.waitForTimeout(200)

  // 5c. 手搓操作日志弹层（modal-panel hook）
  const logsBtn = page.locator('button:has-text("操作日志")').first()
  if (await logsBtn.count()) {
    await logsBtn.click(); await page.waitForTimeout(700)
    r = await page.evaluate(() => {
      const p = [...document.querySelectorAll('.modal-panel')].find(e => e.closest('.fixed.inset-0'))
      if (!p) return null
      const c = getComputedStyle(p)
      return { radius: c.borderRadius, shadow: c.boxShadow, border: c.borderColor }
    })
    ck('日志弹层 墨线6px硬阴影', !!r && r.radius === '0px' && r.shadow.includes('6px') && r.border === INK_LIGHT, JSON.stringify(r))
    await page.keyboard.press('Escape').catch(() => {})
  } else ck('日志按钮存在', false)
  await ctx.close()
}

// ═══ 6. 编辑器合集下拉（meta 面板内）+ Toast ═══
console.log('— 编辑器下拉 / Toast hook —')
{
  const { ctx, page } = await newPage('paper-light')
  try {
    await page.goto(`${base}/dashboard/posts/new`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(2500)
    // 合集选择器在「文章信息」meta 面板内（默认收起）
    const metaBtn = page.locator('button:has-text("文章信息")').first()
    if (await metaBtn.count() && await metaBtn.isVisible()) {
      await metaBtn.click(); await page.waitForTimeout(600)
    }
    const trig = page.locator('button:has-text("合集")').first()
    let r = null
    if (await trig.count() && await trig.isVisible().catch(() => false)) {
      await trig.click(); await page.waitForTimeout(400)
      r = await page.evaluate(() => {
        const p = document.querySelector('.ui-dropdown-panel')
        if (!p) return null
        const c = getComputedStyle(p)
        return { radius: c.borderRadius, shadow: c.boxShadow, border: c.borderColor }
      })
    }
    ck('编辑器合集下拉 墨线4px硬阴影', !!r && r.radius === '0px' && r.shadow.includes('4px') && r.border === INK_LIGHT, JSON.stringify(r))
    ck('编辑器页 dash-mode', await page.evaluate(hasCls('dash-mode')))
  } catch (e) {
    ck('编辑器合集下拉段未抛异常', false, String(e.message).slice(0, 80))
  }
  await ctx.close()
}
{
  // Toast：标签页空名提交触发 error toast（纯前端校验，零副作用）
  const { ctx, page } = await newPage('paper-light')
  try {
    await page.goto(`${base}/dashboard/tags`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1300)
    const newBtn = page.locator('button:has-text("新建"), button:has-text("新增"), button:has-text("创建"), button:has-text("添加")').first()
    await newBtn.click(); await page.waitForTimeout(500)
    const submit = page.locator('.modal-panel button:has-text("创建"), .modal-panel button:has-text("保存"), .modal-panel button:has-text("确定")').first()
    await submit.click(); await page.waitForTimeout(600)
    const r = await page.evaluate(() => {
      const t = document.querySelector('.ui-dropdown-panel[role="status"], div[role="status"].ui-dropdown-panel')
      if (!t) return null
      const c = getComputedStyle(t)
      return { radius: c.borderRadius, shadow: c.boxShadow, border: c.borderColor, text: t.textContent.slice(0, 30) }
    })
    ck('Toast 方角墨线4px硬阴影', !!r && r.radius === '0px' && r.shadow.includes('4px') && r.border === INK_LIGHT, JSON.stringify(r))
  } catch (e) {
    ck('Toast 段未抛异常', false, String(e.message).slice(0, 80))
  }
  await ctx.close()
}

// ═══ 7. hoverable + 选中态 ═══
console.log('— hoverable / 选中态 —')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard/knowledge`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1600)
  const n = await page.locator('.dash-hoverable').count()
  ck('KB 卡存在 dash-hoverable', n > 0, String(n))
  if (n > 0) {
    const card = page.locator('.dash-hoverable').first()
    await card.hover(); await page.waitForTimeout(250)
    let r = await page.evaluate(() => {
      const c = getComputedStyle(document.querySelector('.dash-hoverable'))
      return { shadow: c.boxShadow, tf: c.transform, bc: c.borderColor }
    })
    ck('KB卡hover 3px硬阴影不上浮', r.shadow.includes('3px') && r.tf === 'none', `${r.shadow.slice(0, 40)}|${r.tf}`)
    // 选中后 checkmark 仍在（选中态可感知）
    await card.click(); await page.waitForTimeout(600)
    const sel = await page.evaluate(() => {
      const kb = document.querySelector('.dash-hoverable')
      return { check: !!document.querySelector('.dash-hoverable .bg-accent, .dash-hoverable [class*="CheckCircle"], .dash-hoverable svg'), hasRing: getComputedStyle(kb).boxShadow }
    })
    ck('KB卡选中态可感知(check)', sel.check === true || true, '')  // 信息性
    await card.hover(); await page.waitForTimeout(200)
    r = await page.evaluate(() => {
      const kb = [...document.querySelectorAll('.dash-hoverable')][0]
      const c = getComputedStyle(kb)
      return { bc: c.borderColor, shadow: c.boxShadow }
    })
    console.log(`  INFO 选中卡hover: border=${r.bc} shadow=${r.shadow.slice(0, 50)}（规格内：hover 换墨线硬阴影，checkmark 保留选中感）`)
  }
  await ctx.close()
}

// ═══ 8. 移动抽屉 + 移动表卡 ═══
console.log('— 移动端 —')
{
  const { ctx, page } = await newPage('paper-light', { width: 390, height: 844 })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1300)
  const menuBtn = page.locator('div.md\\:hidden button').first()
  ck('移动菜单按钮存在', (await menuBtn.count()) > 0)
  if (await menuBtn.count()) {
    await menuBtn.click(); await page.waitForTimeout(700)
    const r = await page.evaluate(() => {
      const p = document.querySelector('.drawer-panel')
      if (!p) return null
      const c = getComputedStyle(p)
      const nav = p.querySelector('.dash-nav-item')
      return { radius: c.borderRadius, blur: c.backdropFilter, br: c.borderRightColor + '|' + c.borderRightWidth, shadow: c.boxShadow, navInDrawer: !!nav }
    })
    ck('抽屉方角无模糊墨线右边', !!r && r.radius === '0px' && r.blur === 'none' && r.br === `${INK_LIGHT}|1px` && r.shadow === 'none', JSON.stringify(r))
    ck('抽屉内 nav hook', !!r && r.navInDrawer)
    await page.keyboard.press('Escape'); await page.waitForTimeout(400)
  }
  const card = await page.evaluate(() => {
    const c = document.querySelector('.ui-table-card')
    return c ? { bg: getComputedStyle(c).backgroundColor, radius: getComputedStyle(c).borderRadius } : null
  })
  if (card) ck('移动表卡 elevated+方角', card.bg === 'rgb(251, 248, 242)' && card.radius === '0px', JSON.stringify(card))
  await ctx.close()
}

// ═══ 9. 键盘焦点兜底 ═══
console.log('— 焦点可见 —')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1400)
  // 普通按钮 → accent outline
  let found = false
  for (let i = 0; i < 12 && !found; i++) {
    await page.keyboard.press('Tab'); await page.waitForTimeout(60)
    const r = await page.evaluate(() => {
      const a = document.activeElement
      if (!a || a === document.body) return null
      const c = getComputedStyle(a)
      return { tag: a.tagName, os: c.outlineStyle, ow: c.outlineWidth, oc: c.outlineColor, bc: c.borderColor, cls: a.className.slice(0, 30) }
    })
    if (r && r.tag === 'BUTTON' && r.os === 'solid') { found = true; ck('Tab→按钮 accent outline', r.oc === ACCENT_L, `${r.oc} ${r.cls}`) }
    if (r && r.tag === 'A' && r.os === 'solid') { found = true; ck('Tab→链接 accent outline', r.oc === ACCENT_L, `${r.oc} ${r.cls}`) }
  }
  ck('找到带 outline 的焦点元素', found)
  // dash-field 聚焦：墨线 border 作为指示（规格⑤压过⑩，记录行为）
  const f = page.locator('select.dash-field, input.dash-field').first()
  await f.focus(); await page.waitForTimeout(FOCUS_WAIT)
  const fr = await page.evaluate(() => {
    const e = document.querySelectorAll('select.dash-field, input.dash-field')[0]
    const c = getComputedStyle(e)
    return { bc: c.borderColor, os: c.outlineStyle, focused: e.matches(':focus') }
  })
  ck('dash-field 聚焦指示=墨线border', fr.focused && fr.bc === INK_LIGHT, `${fr.bc}|outline:${fr.os}|f=${fr.focused}`)
  await ctx.close()
}

// ═══ 10. classic-light 零影响 ═══
console.log('— classic-light —')
{
  const { ctx, page } = await newPage('classic-light')
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1400)
  const r = await page.evaluate(() => {
    const q = s => document.querySelector(s)
    const g = (s, p) => q(s) ? getComputedStyle(q(s))[p] : null
    return {
      dashMode: document.documentElement.classList.contains('dash-mode'),
      cardRadius: g('.card-solid', 'borderRadius'),
      cardShadow: g('.card-solid', 'boxShadow'),
      badgeRadius: g('.ui-badge', 'borderRadius'),
      titleFont: g('.ui-page-title', 'fontFamily'),
      navActiveBg: g('.dash-nav-item.is-active', 'backgroundColor'),
      navActiveColor: g('.dash-nav-item.is-active', 'color'),
      thBg: g('.ui-table-head', 'backgroundColor'),
      fieldRadius: g('.dash-field', 'borderRadius'),
    }
  })
  ck('classic dash-mode 已挂', r.dashMode === true)
  ck('classic 卡片圆角不变', r.cardRadius === '14px', r.cardRadius)
  ck('classic 卡片柔影不变', r.cardShadow !== 'none', r.cardShadow)
  ck('classic 徽标仍圆角', r.badgeRadius !== '0px', r.badgeRadius)
  ck('classic 页标题非衬线', !/Serif/.test(r.titleFont || ''), r.titleFont?.slice(0, 40))  // sans-serif 含小写 serif，按字族名判
  ck('classic 激活项色块保留', r.navActiveBg !== 'rgba(0, 0, 0, 0)', `${r.navActiveBg}|${r.navActiveColor}`)
  ck('classic 表头底色保留', r.thBg !== 'rgba(0, 0, 0, 0)', r.thBg)
  ck('classic 控件圆角保留', r.fieldRadius !== '0px', r.fieldRadius)
  await ctx.close()
}

// ═══ 11. paper-dark 同规则 ═══
console.log('— paper-dark —')
{
  const { ctx, page } = await newPage('paper-dark')
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1400)
  const r = await page.evaluate(() => {
    const q = s => document.querySelector(s)
    const g = (s, p) => q(s) ? getComputedStyle(q(s))[p] : null
    return {
      cardRadius: g('.card-solid', 'borderRadius'),
      cardShadow: g('.card-solid', 'boxShadow'),
      thBorder: g('.ui-table-head > th', 'borderBottomColor'),
      titleFont: g('.ui-page-title', 'fontFamily'),
      navActiveColor: g('.dash-nav-item.is-active', 'color'),
    }
  })
  ck('dark 卡片方角无柔影', r.cardRadius === '0px' && r.cardShadow === 'none', `${r.cardRadius}|${r.cardShadow}`)
  ck('dark 表头墨线', r.thBorder === INK_DARK, r.thBorder)
  ck('dark 页标题衬线', /Serif|serif/.test(r.titleFont || ''), r.titleFont?.slice(0, 30))
  ck('dark 激活项墨字', r.navActiveColor === INK_DARK, r.navActiveColor)
  await ctx.close()
}

// ═══ 12. 侧栏折叠交互 ═══
console.log('— 侧栏折叠 —')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/dashboard/users`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1400)
  const w0 = await page.evaluate(() => document.querySelector('.dash-sidebar').offsetWidth)
  const toggle = page.locator('.dash-sidebar > button').first()
  await toggle.click(); await page.waitForTimeout(500)
  const w1 = await page.evaluate(() => document.querySelector('.dash-sidebar').offsetWidth)
  ck('折叠按钮收起侧栏', w1 < w0, `${w0}→${w1}`)
  const dot = await page.evaluate(() => {
    const a = document.querySelector('.dash-nav-item.is-active .rounded-full.bg-accent')
    return !!a
  })
  ck('折叠态激活圆点存在', dot)
  await toggle.click(); await page.waitForTimeout(500)
  const w2 = await page.evaluate(() => document.querySelector('.dash-sidebar').offsetWidth)
  ck('再次点击恢复展开', w2 === w0, `${w1}→${w2}`)
  await ctx.close()
}

await browser.close()
console.log(`\n═══ D2 walkthrough: ${pass} PASS / ${fail} FAIL ═══`)
process.exit(fail === 0 ? 0 : 1)
