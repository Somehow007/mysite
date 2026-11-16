// T13 验证：默认主题切换到纸本 + 跟随系统
// 用法: node scripts/t13-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://127.0.0.1:5173）+ playwright。
// 覆盖：无存储×亮/暗系统、emulateMedia 实时跟随、手动选择保持、跟随系统入口、溢出。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t13')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

// theme: null=不注入（模拟清掉 localStorage）；字符串=注入存储值
async function newPage({ theme = null, colorScheme = 'light', viewport = { width: 1440, height: 900 }, storage = {} } = {}) {
  const ctx = await browser.newContext({ viewport, colorScheme })
  await ctx.addInitScript(({ t, s }) => {
    try {
      if (t != null) localStorage.setItem('mysite_theme_v2', t)
      for (const [k, v] of Object.entries(s)) localStorage.setItem(k, v)
    } catch {}
  }, { t: theme, s: storage })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  return { ctx, page, errors }
}

const themeOf = page => page.evaluate(() => document.documentElement.dataset.theme)
const storedOf = page => page.evaluate(() => localStorage.getItem('mysite_theme_v2'))
const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)

// ── A. 无存储值：跟随系统 ─────────────────────────────────
console.log('== A. 无存储 → 跟随系统 ==')
{
  // A1 亮色系统 → paper-light
  const { ctx, page } = await newPage({ colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  ok('亮系统首屏 data-theme=paper-light', (await themeOf(page)) === 'paper-light', await themeOf(page))
  ok('localStorage 未被回写', (await storedOf(page)) === null, await storedOf(page))
  ok('html.dark 未加', await page.evaluate(() => !document.documentElement.classList.contains('dark')))
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'a1-fresh-light-1440.png'), fullPage: false })
  await ctx.close()

  // A2 暗色系统 → paper-dark
  const { ctx: ctx2, page: page2 } = await newPage({ colorScheme: 'dark' })
  await page2.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page2.waitForTimeout(400)
  ok('暗系统首屏 data-theme=paper-dark', (await themeOf(page2)) === 'paper-dark', await themeOf(page2))
  ok('html.dark 已加', await page2.evaluate(() => document.documentElement.classList.contains('dark')))
  ok('colorScheme=dark', await page2.evaluate(() => document.documentElement.style.colorScheme === 'dark'))
  await page2.screenshot({ path: join(outDir, 'a2-fresh-dark-1440.png') })
  await ctx2.close()

  // A3 实时跟随：亮→暗→亮（emulateMedia 触发 matchMedia 变化）
  const { ctx: ctx3, page: page3 } = await newPage({ colorScheme: 'light' })
  await page3.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page3.waitForTimeout(300)
  ok('初始 paper-light', (await themeOf(page3)) === 'paper-light')
  await page3.emulateMedia({ colorScheme: 'dark' })
  await page3.waitForTimeout(300)
  ok('系统切暗后自动 paper-dark', (await themeOf(page3)) === 'paper-dark', await themeOf(page3))
  await page3.emulateMedia({ colorScheme: 'light' })
  await page3.waitForTimeout(300)
  ok('系统切回亮后自动 paper-light', (await themeOf(page3)) === 'paper-light', await themeOf(page3))
  ok('全程 localStorage 仍无值', (await storedOf(page3)) === null)
  await ctx3.close()
}

// ── B. 手动选择保持原样 ────────────────────────────────────
console.log('== B. 手动选择不被系统改写 ==')
for (const [stored, scheme, want] of [
  ['classic-light', 'dark', 'classic-light'],
  ['rose-garden', 'dark', 'rose-garden'],
  ['paper-dark', 'light', 'paper-dark'],
]) {
  const { ctx, page } = await newPage({ theme: stored, colorScheme: scheme })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(300)
  ok(`存储=${stored} + ${scheme}系统 → ${want}`, (await themeOf(page)) === want, await themeOf(page))
  await ctx.close()
}
// 手动选择后 emulateMedia 切换也不动
{
  const { ctx, page } = await newPage({ theme: 'classic-light', colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(300)
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.waitForTimeout(300)
  ok('classic-light 存储下系统切暗不改写', (await themeOf(page)) === 'classic-light', await themeOf(page))
  await ctx.close()
}

// ── C. 面板交互：选主题写存储 / 跟随系统清存储 ──────────────
console.log('== C. 主题面板交互 ==')
{
  // 需要一个无 variant 主题才能看到面板（AppHeader 混合方案：有 variant 只显示明暗钮）
  const { ctx, page } = await newPage({ theme: 'rose-garden', colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  const paletteBtn = page.locator('button[aria-label^="当前主题"]').first()
  await paletteBtn.click()
  await page.waitForTimeout(200)
  const followRow = page.locator('button[role="menuitem"]', { hasText: '跟随系统' })
  ok('面板出现「跟随系统」入口', await followRow.count() === 1)
  ok('非跟随态下入口无激活样式', !(await followRow.getAttribute('aria-current')))
  // 选纸本·昼 → 写存储
  await page.locator('button[role="menuitem"]', { hasText: '纸本·昼' }).first().click()
  await page.waitForTimeout(400)
  ok('选纸本·昼写入存储', (await storedOf(page)) === 'paper-light', await storedOf(page))
  ok('data-theme=paper-light', (await themeOf(page)) === 'paper-light')
  await ctx.close()
}
{
  // 跟随系统 → 手动选 classic-light → 再从面板？（classic 有 variant 看不到面板）
  // 改为：无存储（跟随）时用 rose-garden 不可达面板——直接验证 followSystem() 行为经由 rose-garden 路径
  const { ctx, page } = await newPage({ theme: 'ocean-breeze', colorScheme: 'dark' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  await page.locator('button[aria-label^="当前主题"]').first().click()
  await page.waitForTimeout(200)
  await page.locator('button[role="menuitem"]', { hasText: '跟随系统' }).click()
  await page.waitForTimeout(400)
  ok('点「跟随系统」清掉存储', (await storedOf(page)) === null, await storedOf(page))
  ok('清存储后按系统暗色解析 paper-dark', (await themeOf(page)) === 'paper-dark', await themeOf(page))
  // 再切系统亮 → paper-light（证明回到了跟随态）
  await page.emulateMedia({ colorScheme: 'light' })
  await page.waitForTimeout(300)
  ok('回到跟随态后系统切亮 → paper-light', (await themeOf(page)) === 'paper-light', await themeOf(page))
  await ctx.close()
}

// ── D. 视觉回归：三主题截图 + 溢出 ─────────────────────────
console.log('== D. 三主题视觉 ==')
for (const [theme, scheme, name] of [
  [null, 'light', 'default-paper-light'],
  [null, 'dark', 'default-paper-dark'],
  ['classic-light', 'light', 'classic-light'],
]) {
  const { ctx, page } = await newPage({ theme, colorScheme: scheme, viewport: { width: 390, height: 844 } })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  ok(`${name} 390 溢出=0`, (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, `d-${name}-390.png`) })
  await ctx.close()
}
// 桌面 paper-light 移动端 dock 无关回归：post 页抽查一篇文章
{
  const { ctx, page } = await newPage({ colorScheme: 'light' })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  const firstPost = page.locator('a[href^="/post/"]').first()
  if (await firstPost.count()) {
    await firstPost.click()
    await page.waitForTimeout(800)
    ok('文章页 data-theme=paper-light', (await themeOf(page)) === 'paper-light', await themeOf(page))
    ok('文章页 1440 溢出=0', (await overflowOf(page)) === 0)
    await page.screenshot({ path: join(outDir, 'd-post-paper-light-1440.png') })
  } else {
    ok('文章页跳转（无文章链接，跳过）', true)
  }
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
