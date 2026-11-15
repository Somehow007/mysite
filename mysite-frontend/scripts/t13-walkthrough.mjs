// T13 走查：默认主题=跟随系统的 DOM 级复核
// 用法: node scripts/t13-walkthrough.mjs [baseUrl]
// 依赖 dev server（默认 http://localhost:5173）+ playwright。
// 覆盖 t13-verify 未查的面：首屏一致性(防闪)、dark/colorScheme 状态一致、
// 跨标签页 storage 同步、面板交互细节、页面级回归、边界值。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t13-walkthrough')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

async function newPage({ theme = null, colorScheme = 'light', viewport = { width: 1440, height: 900 } } = {}) {
  const ctx = await browser.newContext({ viewport, colorScheme })
  await ctx.addInitScript(t => {
    try { if (t != null) localStorage.setItem('mysite_theme_v2', t) } catch {}
  }, theme)
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  return { ctx, page, errors }
}

const snap = page => page.evaluate(() => ({
  theme: document.documentElement.dataset.theme,
  dark: document.documentElement.classList.contains('dark'),
  cs: document.documentElement.style.colorScheme,
  stored: localStorage.getItem('mysite_theme_v2'),
  transitioning: document.documentElement.classList.contains('theme-transitioning'),
}))
const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
const isDarkId = id => id.includes('dark') || id === 'aurora'

// ── A. 首屏一致性：DOMContentLoaded 与 Vue 接管后 data-theme 不得跳变 ──
console.log('== A. 首屏防闪 ==')
for (const [scheme, want] of [['light', 'paper-light'], ['dark', 'paper-dark']]) {
  const { ctx, page } = await newPage({ colorScheme: scheme })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  const atDCL = await snap(page)
  await page.waitForTimeout(1200) // 等 Vue mount + watch immediate + 任何二次 apply
  const after = await snap(page)
  ok(`${scheme} DCL 即 ${want}`, atDCL.theme === want, JSON.stringify(atDCL))
  ok(`${scheme} Vue 接管后不跳变`, after.theme === atDCL.theme && after.dark === atDCL.dark, JSON.stringify(after))
  await ctx.close()
}
// 存储主题同样不跳变
{
  const { ctx, page } = await newPage({ theme: 'classic-dark', colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  const atDCL = await snap(page)
  await page.waitForTimeout(1000)
  const after = await snap(page)
  ok('classic-dark 存储 DCL/接管后一致', atDCL.theme === 'classic-dark' && after.theme === 'classic-dark')
  ok('classic-dark dark class 一致', atDCL.dark === true && after.dark === true)
  await ctx.close()
}

// ── B. dark class / colorScheme 与 data-theme 全组合一致 ──
console.log('== B. dark/colorScheme 状态一致 ==')
for (const id of ['paper-light', 'paper-dark', 'classic-light', 'classic-dark', 'aurora', 'rose-garden', 'ocean-breeze', 'warm-sunset', 'liquid-glass', 'liquid-glass-dark']) {
  const { ctx, page } = await newPage({ theme: id })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  const s = await snap(page)
  ok(`${id}: data-theme/dark/colorScheme 一致`,
    s.theme === id && s.dark === isDarkId(id) && s.cs === (isDarkId(id) ? 'dark' : 'light'),
    JSON.stringify(s))
  await ctx.close()
}

// ── C. 跨标签页 storage 事件同步 ──
console.log('== C. 跨标签页同步 ==')
{
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const p1 = await ctx.newPage()
  const p2 = await ctx.newPage()
  await p1.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await p2.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await p2.waitForTimeout(400)
  ok('两页初始均 paper-light', (await snap(p1)).theme === 'paper-light' && (await snap(p2)).theme === 'paper-light')
  // p1 手动写存储（等价于 p1 内 setTheme 的 localStorage 副作用）
  await p1.evaluate(() => localStorage.setItem('mysite_theme_v2', 'rose-garden'))
  await p2.waitForTimeout(500)
  ok('p1 写 rose-garden → p2 跟随', (await snap(p2)).theme === 'rose-garden', JSON.stringify(await snap(p2)))
  // p1 清除 → p2 回跟随系统
  await p1.evaluate(() => localStorage.removeItem('mysite_theme_v2'))
  await p2.waitForTimeout(500)
  ok('p1 清除 → p2 回 paper-light（亮系统）', (await snap(p2)).theme === 'paper-light', JSON.stringify(await snap(p2)))
  await ctx.close()
}

// ── D. 面板交互细节 ──
console.log('== D. 面板交互 ==')
{
  // D1 跟随系统态点顶栏明暗钮 → 落到具体 paper 变体（退出跟随，预期行为记录）
  const { ctx, page } = await newPage({ colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  const moonBtn = page.locator('button[aria-label="切换到暗色"]').first()
  ok('跟随系统态顶栏为明暗钮（无面板入口）', await moonBtn.count() === 1)
  await moonBtn.click()
  await page.waitForTimeout(600)
  let s = await snap(page)
  ok('点暗色 → 落 paper-dark 并写存储（退出跟随）', s.theme === 'paper-dark' && s.stored === 'paper-dark', JSON.stringify(s))
  ok('theme-transitioning 已清理', !s.transitioning)
  await page.screenshot({ path: join(outDir, 'd1-after-dark-toggle.png') })
  await ctx.close()
}
{
  // D2 无 variant 主题下面板可见，「跟随系统」行为纯动作项（无激活态——走查发现死代码已修）
  const { ctx, page } = await newPage({ theme: 'rose-garden', colorScheme: 'dark' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  await page.locator('button[aria-label^="当前主题"]').first().click()
  await page.waitForTimeout(400) // GSAP 入场
  const menu = page.locator('[role="menu"]')
  ok('面板渲染', await menu.count() === 1)
  const followRow = page.locator('button[role="menuitem"]', { hasText: '跟随系统' })
  ok('跟随系统行存在', await followRow.count() === 1)
  const rowHtml = await followRow.innerHTML()
  ok('跟随系统行无激活态残留（无 Check/aria-current）',
    !(await followRow.getAttribute('aria-current')) && !rowHtml.includes('lucide-check'), rowHtml.slice(0, 120))
  // Esc 关闭
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  ok('Esc 关闭面板', (await page.locator('[role="menu"]').count()) === 0)
  // 重开 → 点跟随系统 → 清存储 + 顶栏从面板钮换回明暗钮
  await page.locator('button[aria-label^="当前主题"]').first().click()
  await page.waitForTimeout(400)
  await page.locator('button[role="menuitem"]', { hasText: '跟随系统' }).click()
  await page.waitForTimeout(600)
  const s2 = await snap(page)
  ok('点跟随系统 → 存储清空 + paper-dark（暗系统）', s2.stored === null && s2.theme === 'paper-dark', JSON.stringify(s2))
  ok('顶栏回到明暗钮（palette 钮消失）', await page.locator('button[aria-label^="当前主题"]').count() === 0)
  ok('顶栏出现切换暗色钮', await page.locator('button[aria-label="切换到亮色"]').count() === 1)
  await page.screenshot({ path: join(outDir, 'd2-after-follow-system.png') })
  await ctx.close()
}
{
  // D3 点面板外关闭
  const { ctx, page } = await newPage({ theme: 'ocean-breeze' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  await page.locator('button[aria-label^="当前主题"]').first().click()
  await page.waitForTimeout(300)
  await page.mouse.click(700, 400)
  await page.waitForTimeout(400)
  ok('点面板外关闭', (await page.locator('[role="menu"]').count()) === 0)
  await ctx.close()
}

// ── E. 页面级回归（paper-light 默认）──
console.log('== E. 页面级回归 ==')
const pages = ['/', '/about', '/login', '/collections', '/nonexistent-path-t13']
for (const path of pages) {
  const { ctx, page, errors } = await newPage({ colorScheme: 'light' })
  await page.goto(base + path, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(900)
  const s = await snap(page)
  ok(`${path} paper-light`, s.theme === 'paper-light', s.theme)
  ok(`${path} 溢出=0`, (await overflowOf(page)) === 0)
  ok(`${path} 无 pageerror`, errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, `e${path === '/' ? '-home' : path.replaceAll('/', '-')}.png`) })
  await ctx.close()
}
// 文章页 + 404(不存在id) —— 需要真实数据，失败则跳过不判负
{
  const { ctx, page } = await newPage({ colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const href = await page.locator('a[href^="/post/"]').first().getAttribute('href').catch(() => null)
  if (href) {
    await page.goto(base + href, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1000)
    ok('文章页 paper-light', (await snap(page)).theme === 'paper-light')
    ok('文章页溢出=0', (await overflowOf(page)) === 0)
    await page.screenshot({ path: join(outDir, 'e-post.png') })
  } else ok('文章页（无链接跳过）', true)
  await ctx.close()
}
{
  const { ctx, page } = await newPage({ colorScheme: 'light' })
  await page.goto(base + '/post/999999999999999999', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const s = await snap(page)
  ok('不存在文章 → 仍 paper-light（错误态不掉主题）', s.theme === 'paper-light', s.theme)
  ok('不存在文章页溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'e-post-404.png') })
  await ctx.close()
}
// 移动端首页
{
  const { ctx, page } = await newPage({ colorScheme: 'dark', viewport: { width: 390, height: 844 } })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const s = await snap(page)
  ok('390 暗系统 → paper-dark', s.theme === 'paper-dark', s.theme)
  ok('390 溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'e-home-390-dark.png') })
  await ctx.close()
}

// ── F. 边界值 ──
console.log('== F. 边界值 ==')
{
  // F1 非法存储值（手动篡改场景）：记录行为，不判负
  const { ctx, page } = await newPage({ theme: 'bogus-theme' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  const s = await snap(page)
  console.log(`  INFO 非法值 'bogus-theme' → data-theme=${s.theme}（存量行为：透传不校验）`)
  await ctx.close()
}
{
  // F2 空串存储值：inline script 按 falsy 当无存储→paper；Vue 侧 '' ?? 不落空→data-theme=''
  //    若两者不一致 = 首屏闪变 bug。实测记录。
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', '') } catch {} })
  const page = await ctx.newPage()
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  const atDCL = await snap(page)
  await page.waitForTimeout(1000)
  const after = await snap(page)
  console.log(`  INFO 空串存储：DCL theme='${atDCL.theme}' → Vue 后='${after.theme}' stored='${after.stored}'`)
  ok('空串不产生 DCL/Vue 跳变', atDCL.theme === after.theme, `${atDCL.theme} → ${after.theme}`)
  await ctx.close()
}
{
  // F3 localStorage 不可用（隐私模式模拟）：try/catch 已在 addInitScript；
  //    直接禁用 storage 验证应用不崩
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => {
    Object.defineProperty(window, 'localStorage', { get() { throw new Error('denied') } })
  })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', e => errs.push(String(e)))
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' }).catch(e => errs.push('goto: ' + e))
  await page.waitForTimeout(800)
  const s = await page.evaluate(() => document.documentElement.dataset.theme).catch(() => 'EVAL_FAIL')
  console.log(`  INFO localStorage 禁用：theme=${s} pageerror=${errs.length}`)
  ok('localStorage 禁用不白屏（有 data-theme）', typeof s === 'string' && s.length > 0, s)
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
