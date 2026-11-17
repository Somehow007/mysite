// 11-17 生产反馈走查：主题入口常驻 + 控制台菜单即时渲染 + fetchArticles 等 ensureUser
// 全 API mock（page.route），无需后端；/v1/auth/me 人为延迟 2.5s 验证水合先于 /me。
// 用法：node scripts/theme-nav-walkthrough.mjs   （需 vite dev server @5173）
import { chromium } from 'playwright'

const base = process.env.WALK_BASE ?? 'http://127.0.0.1:5173'
const ME_DELAY = 2500

let pass = 0, fail = 0, skip = 0
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${detail}`) }
}
const skipped = (name, why) => { skip++; console.log(`  SKIP ${name} (${why})`) }

const ok = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: null, data }) })
const pageData = { records: [], current: 1, size: 20, total: 0, pages: 0 }

const siteConfig = {
  title: '走查站', description: '', author: '', url: '', commentEnabled: true,
  navigation: [
    { label: '首页', path: '/' },
    { label: '归档', path: '/archive' },
    { label: '关于', path: '/about' },
  ],
}

function mockUser(role) {
  return {
    id: '1001', username: 'walkthrough', realName: '走查用户', role,
    avatar: null, email: 'w@example.com', phoneNumber: '', sex: 0,
  }
}

// /v1/** 统一 mock；auth/me 延迟 ME_DELAY 并记录时间点
async function mockApi(page, role, timing) {
  await page.route('**/v1/**', async route => {
    const url = route.request().url()
    const fulfill = (data) => route.fulfill(ok(data))
    if (url.includes('/v1/auth/me')) {
      timing.meStart = Date.now()
      await new Promise(r => setTimeout(r, ME_DELAY))
      timing.meEnd = Date.now()
      return fulfill(mockUser(role))
    }
    if (url.includes('/v1/site/config')) return fulfill(siteConfig)
    if (url.includes('/v1/articles')) { timing.articlesStart = Date.now(); return fulfill(pageData) }
    if (url.includes('/v1/categories')) return fulfill([])
    if (url.includes('/v1/collections')) return fulfill(pageData)
    return fulfill(null)
  })
}

const browser = await chromium.launch()

async function newPage({ theme = 'classic-light', role = 'ADMIN', profile = true, viewport } = {}) {
  const context = await browser.newContext({ viewport: viewport ?? { width: 1440, height: 900 } })
  const timing = {}
  const page = await context.newPage()
  page.on('pageerror', e => { fail++; console.log(`  FAIL PAGEERROR ${e.message}`) })
  await context.addInitScript(([t, r, prof]) => {
    // mysite_theme_v2 走 useStorage 'any' 序列化：原始串不加引号；已存在不覆盖（init script 每次导航都跑，覆盖会吃掉测试中的切换）
    if (!localStorage.getItem('mysite_theme_v2')) localStorage.setItem('mysite_theme_v2', t)
    localStorage.setItem('mysite_access_token', JSON.stringify('mock-token'))
    localStorage.setItem('mysite_refresh_token', JSON.stringify('mock-refresh'))
    localStorage.setItem('mysite_user_role', JSON.stringify(r))
    if (prof) {
      localStorage.setItem('mysite_user_profile', JSON.stringify({
        id: '1001', username: 'walkthrough', realName: '走查用户', role: r,
        avatar: null, email: 'w@example.com', phoneNumber: '', sex: 0,
      }))
    }
  }, [theme, role, profile])
  await mockApi(page, role, timing)
  return { context, page, timing }
}

const sidebarTexts = page => page.evaluate(() =>
  document.querySelector('aside .dash-sidebar, aside')?.innerText ?? '')

// ── §1 控制台侧栏：/me 延迟 2.5s，水合 profile 后菜单应立即完整 ──
console.log('== §1 侧栏即时渲染（/me 延迟中断言）==')
{
  const { context, page, timing } = await newPage({ role: 'ADMIN' })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  // 等首帧渲染但远早于 /me 返回（ME_DELAY=2500）
  await page.waitForTimeout(700)
  const mePending = timing.meStart && !timing.meEnd
  const nav = await sidebarTexts(page)
  const need = ['文章管理', '写文章', '合集管理', '用户管理', '评论管理', '分类管理', '标签管理', '图片管理', '知识库', 'AI 管理']
  const missing = need.filter(t => !nav.includes(t))
  check('/me 仍在途时侧栏已是完整 admin 菜单', mePending && missing.length === 0,
    `mePending=${mePending} missing=${JSON.stringify(missing)}`)
  const footer = await page.evaluate(() => document.querySelector('aside')?.innerText ?? '')
  check('侧栏 footer 显示水合身份（走查用户/系统管理员）',
    footer.includes('走查用户') && footer.includes('系统管理员'), footer.slice(-120))
  // 等 /me 落地后菜单不回退
  await page.waitForTimeout(ME_DELAY)
  const nav2 = await sidebarTexts(page)
  check('/me 返回后菜单保持完整', need.every(t => nav2.includes(t)))
  await context.close()
}

// ── §2 fetchArticles 等 ensureUser：CREATOR 角色下 /v1/articles 晚于 /me ──
console.log('== §2 fetchArticles 等待 ensureUser ==')
{
  const { context, page, timing } = await newPage({ role: 'CREATOR' })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(ME_DELAY + 800)
  check('articles 请求晚于 /me 返回（角色就绪后才取数）',
    timing.meEnd && timing.articlesStart && timing.articlesStart >= timing.meEnd - 100,
    JSON.stringify(timing))
  const nav = await sidebarTexts(page)
  check('CREATOR 侧栏含内容组、无系统组',
    nav.includes('文章管理') && nav.includes('合集管理') && !nav.includes('用户管理'),
    nav.slice(0, 200))
  await context.close()
}

// ── §3 主题入口：前台页 classic-light 下直切钮+调色板并存，切 paper-light 后跳 dashboard 验证持久 ──
console.log('== §3 主题入口常驻 + 切换（前台 AppHeader）==')
{
  const { context, page } = await newPage({ role: 'ADMIN' })
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const header = page.locator('header')
  const quickToggle = header.locator('button[aria-label*="切换到"]')
  const paletteBtn = header.locator('button[aria-label*="当前主题"]')
  check('variant 主题：日/月直切钮存在', await quickToggle.count() === 1)
  check('variant 主题：调色板入口常驻', await paletteBtn.count() === 1)

  await paletteBtn.click()
  const menu = page.locator('[role="menu"]:visible').last()
  await menu.waitFor({ state: 'visible', timeout: 3000 })
  const menuText = await menu.textContent()
  check('面板含 跟随系统 + 主题网格', /跟随系统/.test(menuText ?? '') && /纸本·昼/.test(menuText ?? '') && /经典·白/.test(menuText ?? ''))

  await menu.locator('button', { hasText: '纸本·昼' }).first().click()
  await page.waitForTimeout(500)
  const st = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme'),
    stored: localStorage.getItem('mysite_theme_v2'),
  }))
  check('切到 paper-light：data-theme + 存储同步',
    st.theme === 'paper-light' && String(st.stored).includes('paper-light'), JSON.stringify(st))

  // 跳 dashboard：主题持久 + dash-mode + paper 组合下侧栏仍完整
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const st2 = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme'),
    dashMode: document.documentElement.classList.contains('dash-mode'),
    nav: document.querySelector('aside')?.innerText ?? '',
  }))
  check('dashboard 下 paper-light 持久 + dash-mode + 菜单完整',
    st2.theme === 'paper-light' && st2.dashMode && st2.nav.includes('用户管理'),
    JSON.stringify({ theme: st2.theme, dashMode: st2.dashMode }))
  await context.close()
}

// ── §4 设置页「外观」分区 ──
console.log('== §4 SettingsView 外观分区 ==')
{
  const { context, page } = await newPage({ role: 'ADMIN' })
  await page.goto(`${base}/dashboard/settings`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(900)
  const texts = await page.evaluate(() => document.body.innerText)
  check('设置页含「外观」分区 + 主题网格', /外观/.test(texts) && /纸本·昼/.test(texts) && /跟随系统/.test(texts))
  await context.close()
}

// ── §5 移动端菜单「主题外观」─
console.log('== §5 移动端菜单主题入口 ==')
{
  const { context, page } = await newPage({ role: 'ADMIN', viewport: { width: 390, height: 844 } })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  // dashboard 用 DashboardLayout（无 AppHeader）；主题入口走前台页移动菜单
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  await page.locator('header button.lg\\:hidden').first().click()
  await page.waitForTimeout(400)
  const t2 = await page.evaluate(() => document.body.innerText)
  check('前台移动菜单含「主题外观」行', /主题外观/.test(t2))
  if (/主题外观/.test(t2)) {
    await page.locator('button', { hasText: '主题外观' }).first().click()
    await page.waitForTimeout(400)
    const t3 = await page.evaluate(() => document.body.innerText)
    check('点击后内嵌主题网格出现', /纸本·昼/.test(t3) && /跟随系统/.test(t3))
  }
  await context.close()
}

// ── §6 无 profile 缓存的回退：仅 user_role 有值时 /me 前菜单仍缺（确认水合确实来自 profile）──
console.log('== §6 对照：无 profile 缓存时菜单等待 /me ==')
{
  const { context, page, timing } = await newPage({ role: 'ADMIN', profile: false })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(700)
  const mePending = timing.meStart && !timing.meEnd
  const nav = await sidebarTexts(page)
  const hasAdminLinks = nav.includes('用户管理') || nav.includes('分类管理')
  check('/me 未回 + 无缓存 → 管理链接暂缺（证明 §1 收益来自水合）',
    mePending && !hasAdminLinks, `mePending=${mePending} nav=${nav.slice(0, 120)}`)
  await context.close()
}

await browser.close()
console.log(`THEME-NAV WALKTHROUGH DONE: ${pass} pass / ${fail} fail / ${skip} skip`)
process.exit(fail === 0 ? 0 : 1)
