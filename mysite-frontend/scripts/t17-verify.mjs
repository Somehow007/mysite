// T17 验证：合集连载状态与进度（plannedCount/status）
// 用法: node scripts/t17-verify.mjs [baseUrl] [token]
// 依赖 dev server（默认 http://localhost:5173）+ 已迁移 DB + 新后端（8081）。
// token: admin accessToken（不传则跳过后台页面验证）
import { chromium } from 'playwright'
import { mkdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const tokenArg = process.argv[3] ?? ''
let token = tokenArg
if (!token) {
  try { token = readFileSync('/tmp/t17-token.txt', 'utf8').trim() } catch {}
}
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t17')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

async function newPage({ viewport = { width: 1440, height: 900 }, storage = {}, theme = null } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(({ s, t }) => {
    try {
      for (const [k, v] of Object.entries(s)) localStorage.setItem(k, v)
      if (t != null) localStorage.setItem('mysite_theme_v2', t)
    } catch {}
  }, { s: storage, t: theme })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  return { ctx, page, errors }
}

const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
// 路由守卫 requiresAdmin 读 localStorage 的 user_role（UX 层），需一并注入
const authed = token ? { mysite_access_token: JSON.stringify(token), mysite_user_role: JSON.stringify('ADMIN') } : {}

// ── A. 首页侧栏：连载状态 + 进度条 ─────────────────────────
console.log('== A. 首页连载合集侧栏 ==')
for (const [theme, name] of [['paper-light', 'paper'], ['classic-light', 'classic'], ['paper-dark', 'dark']]) {
  const { ctx, page, errors } = await newPage({ storage: authed, theme })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const sidebar = page.locator('aside').last()
  const text = await sidebar.innerText().catch(() => '')
  ok(`${name} 侧栏含「3 / 9 篇 · 连载中」`, text.includes('3 / 9 篇 · 连载中'), text.slice(0, 200))
  ok(`${name} 侧栏含「1 / 1 篇 · 已完结」`, text.includes('1 / 1 篇 · 已完结'), text.slice(0, 200))
  // 进度条宽度：c1=33%, c2=100%
  const bars = await sidebar.locator('.bg-accent').evaluateAll(els =>
    els.map(e => ({ w: e.style.width, visible: e.offsetParent !== null })))
  const widths = bars.map(b => b.w).filter(Boolean)
  ok(`${name} 进度条宽度 33%/100%`, widths.includes('33%') && widths.includes('100%'), JSON.stringify(widths))
  ok(`${name} 溢出=0`, (await overflowOf(page)) === 0)
  ok(`${name} console 0 error`, errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, `a-home-${name}-1440.png`) })
  await ctx.close()
}

// ── B. /collections 合集列表 ──────────────────────────────
console.log('== B. /collections ==')
{
  const { ctx, page, errors } = await newPage({ storage: authed, theme: 'paper-light' })
  await page.goto(base + '/collections', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const text = await page.locator('main').innerText().catch(async () => page.locator('body').innerText())
  ok('列表含「3 / 9 篇」', text.includes('3 / 9 篇'), text.slice(0, 300))
  ok('列表含「1 / 1 篇」', text.includes('1 / 1 篇'), text.slice(0, 300))
  ok('列表含连载中/已完结状态', text.includes('连载中') && text.includes('已完结'))
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'b-collections-1440.png'), fullPage: true })
  await ctx.close()
}

// ── C. /collection/:id 详情页（匿名口径：可见文章数）────────
console.log('== C. 合集详情 ==')
{
  const { ctx, page, errors } = await newPage({ theme: 'paper-light' })
  await page.goto(base + '/collection/1', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const text = await page.locator('body').innerText()
  ok('详情含「3 / 9 篇 · 连载中」（匿名可见口径）', text.includes('3 / 9 篇 · 连载中'), text.slice(0, 300))
  const bar = await page.locator('.bg-accent').first().evaluate(e => e.style.width).catch(() => '')
  ok('详情进度条 33%', bar === '33%', bar)
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'c-collection-detail-1440.png') })
  await ctx.close()
}

// ── D. 移动端 390×844 ────────────────────────────────────
console.log('== D. 移动端 ==')
{
  const { ctx, page, errors } = await newPage({ storage: authed, theme: 'paper-light', viewport: { width: 390, height: 844 } })
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const text = await page.locator('body').innerText()
  ok('移动端侧栏含进度文案', text.includes('3 / 9 篇 · 连载中') && text.includes('已完结'))
  ok('移动端溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'd-home-390.png'), fullPage: true })
  await ctx.close()
}

// ── E. 后台合集管理列表 ──────────────────────────────────
console.log('== E. 后台合集管理 ==')
if (token) {
  const { ctx, page, errors } = await newPage({ storage: authed, theme: 'classic-light' })
  await page.goto(base + '/dashboard/collections', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  const text = await page.locator('body').innerText()
  ok('管理列表含「3 / 9 篇」', text.includes('3 / 9 篇'), text.slice(0, 300))
  ok('管理列表含「1 / 1 篇」', text.includes('1 / 1 篇'))
  ok('管理列表含「已完结」chip', text.includes('已完结'))
  ok('管理列表含「0 篇 · 连载中」（无计划）', text.includes('0 篇 · 连载中'), text.slice(0, 300))
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'e-manage-1440.png'), fullPage: true })
  await ctx.close()
} else {
  ok('后台验证（无 token 跳过）', true)
}

// ── F. 合集编辑表单：回填 + 保存 ──────────────────────────
console.log('== F. 合集编辑表单 ==')
if (token) {
  const { ctx, page, errors } = await newPage({ storage: authed, theme: 'classic-light' })
  await page.goto(base + '/dashboard/collections/1/edit', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  const plannedInput = page.getByPlaceholder('计划总篇数（可选）')
  const planned = await plannedInput.inputValue().catch(() => '')
  ok('计划篇数回填=9', planned === '9', planned)
  const finishedBtn = page.locator('button', { hasText: '已完结' }).first()
  const ongoingBtn = page.locator('button', { hasText: '连载中' }).first()
  ok('连载中为选中态', (await ongoingBtn.getAttribute('class') ?? '').includes('bg-accent'))
  // 改成 已完结 + 计划 12，保存，API 复核，再还原
  await finishedBtn.click()
  await plannedInput.fill('12')
  await page.locator('button', { hasText: '保存修改' }).click()
  await page.waitForTimeout(2000)
  const check = await page.evaluate(async (t) => {
    const r = await fetch('/v1/collections/1', { headers: { Authorization: 'Bearer ' + t } })
    const j = await r.json()
    return { plannedCount: j.data?.plannedCount, status: j.data?.status }
  }, token)
  ok('保存后 API plannedCount=12 status=1', check.plannedCount === 12 && check.status === 1, JSON.stringify(check))
  // 还原：planned=9 status=0
  await page.evaluate(async (t) => {
    await fetch('/v1/collections/1', {
      method: 'PUT',
      headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' },
      body: JSON.stringify({ plannedCount: 9, status: 0 }),
    })
  }, token)
  const restored = await page.evaluate(async (t) => {
    const r = await fetch('/v1/collections/1', { headers: { Authorization: 'Bearer ' + t } })
    const j = await r.json()
    return { plannedCount: j.data?.plannedCount, status: j.data?.status }
  }, token)
  ok('已还原 plannedCount=9 status=0', restored.plannedCount === 9 && restored.status === 0, JSON.stringify(restored))
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'f-edit-1440.png') })
  await ctx.close()
} else {
  ok('编辑表单验证（无 token 跳过）', true)
}

// ── G. 新建表单字段存在 ──────────────────────────────────
console.log('== G. 新建合集表单 ==')
if (token) {
  const { ctx, page } = await newPage({ storage: authed, theme: 'classic-light' })
  await page.goto(base + '/dashboard/collections/new', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const text = await page.locator('body').innerText()
  ok('含「连载计划」区块', text.includes('连载计划'))
  ok('含连载中/已完结按钮', text.includes('连载中') && text.includes('已完结'))
  ok('含计划篇数输入框', await page.locator('input[type="number"]').count() >= 2) // sortOrder + plannedCount
  await page.screenshot({ path: join(outDir, 'g-new-1440.png') })
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
