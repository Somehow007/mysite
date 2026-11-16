// T17 走查（DOM 级二次复核）：合集连载状态与进度
// 用法: node scripts/t17-walkthrough.mjs [baseUrl]
// 依赖 dev server + playwright；全部 /v1/* 走 route mock（502 兜底），
// 用确定性数据覆盖 verify 摸不到的边界：超计划照实、无计划、0 篇、清空→null。
// 覆盖：
//   A. 首页侧栏五形态（连载中/完结/无计划/超计划/0篇）文本+进度条宽度
//   B. /collections 列表卡五形态
//   C. 合集详情：进度区 + max-w-2xl 栏宽约束 + 超计划
//   D. 合集详情无计划：无进度区
//   E. 编辑表单：回填/状态切换/清空计划 → PUT body 三态断言（null 显式清除）
//   F. 移动端 390 溢出
//   G. paper-dark 主题
//   H. a11y：状态分段钮为 button 可聚焦、无计划不渲染 aria 进度条
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t17-walk')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()
const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
const jsonRes = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: 'ok', data }) })
const iPage = (records) => ({ records, current: 1, size: 10, total: records.length, pages: 1 })

const col = (over) => ({
  id: '1', title: '连载合集', description: 'desc', coverImage: null,
  authorId: '1', authorName: 'somehow', articleCount: 0,
  plannedCount: null, status: 0, visibility: 0, sortOrder: 0,
  totalViewCount: 0, createTime: '2026-01-01 00:00:00', updateTime: '2026-01-01 00:00:00',
  ...over,
})

const FIVE = [
  col({ id: '1', title: '连载中合集', articleCount: 3, plannedCount: 9, status: 0 }),
  col({ id: '2', title: '已完结合集', articleCount: 6, plannedCount: 6, status: 1 }),
  col({ id: '3', title: '无计划合集', articleCount: 4, plannedCount: null, status: 0 }),
  col({ id: '4', title: '超计划合集', articleCount: 12, plannedCount: 9, status: 1 }),
  col({ id: '5', title: '零篇合集', articleCount: 0, plannedCount: 5, status: 0 }),
]

async function newPage({ viewport = { width: 1440, height: 900 }, admin = false, theme = null } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(({ a, t }) => {
    try {
      if (a) {
        localStorage.setItem('mysite_access_token', JSON.stringify('fake-token'))
        localStorage.setItem('mysite_user_role', JSON.stringify('ADMIN'))
      }
      if (t) localStorage.setItem('mysite_theme_v2', t)
    } catch {}
  }, { a: admin, t: theme })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  await page.route('**/v1/**', route => route.fulfill({ status: 502, body: '{}' }))
  if (admin) {
    await page.route('**/v1/auth/me', route => route.fulfill(jsonRes({
      id: '1', username: 'admin', realName: '管理员', role: 'ADMIN',
    })))
  }
  return { ctx, page, errors }
}

const mockList = (page, records = FIVE) =>
  page.route('**/v1/collections?**', route => route.fulfill(jsonRes(iPage(records))))

const expectTexts = async (locator, pairs) => {
  const text = await locator.innerText().catch(() => '')
  for (const [name, s] of pairs) ok(name, text.includes(s), text.slice(0, 260).replace(/\n/g, ' | '))
}

const barWidths = locator => locator.locator('.bg-accent').evaluateAll(els =>
  els.map(e => e.style.width).filter(Boolean))

// ── A. 首页侧栏五形态 ────────────────────────────────────
console.log('== A. 首页侧栏（mock 五形态）==')
{
  const { ctx, page, errors } = await newPage()
  await mockList(page)
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const sidebar = page.locator('aside').last()
  await expectTexts(sidebar, [
    ['连载中 3/9', '3 / 9 篇 · 连载中'],
    ['已完结 6/6', '6 / 6 篇 · 已完结'],
    ['无计划只显篇数', '4 篇 · 连载中'],
    ['超计划照实 12/9', '12 / 9 篇 · 已完结'],
    ['0 篇有计划', '0 / 5 篇 · 连载中'],
  ])
  ok('无计划不出现「4 / 」', !(await sidebar.innerText()).includes('4 / '))
  const widths = await barWidths(sidebar)
  // 5 合集中无计划的 c3 不画条 → 4 条；33%/100%/100%/0%
  ok('进度条 4 条（无计划不渲染）', widths.length === 4, JSON.stringify(widths))
  ok('宽度含 33%(3/9)', widths.includes('33%'), JSON.stringify(widths))
  ok('超计划封顶 100%（非 133%）', !widths.includes('133%') && widths.filter(w => w === '100%').length === 2, JSON.stringify(widths))
  ok('0 篇 = 0% 空条', widths.includes('0%'), JSON.stringify(widths))
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'a-sidebar-5forms-1440.png') })
  await ctx.close()
}

// ── B. /collections 列表五形态 ───────────────────────────
console.log('== B. /collections 列表 ==')
{
  const { ctx, page, errors } = await newPage()
  await mockList(page)
  await page.goto(base + '/collections', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const main = page.locator('main')
  // 列表卡 = formatCollectionCount（「3 / 9 篇」）+ 独立状态 chip（「连载中/已完结」）
  await expectTexts(main, [
    ['列表计数 3/9', '3 / 9 篇'],
    ['列表计数 6/6', '6 / 6 篇'],
    ['列表无计划只显篇数', '4 篇'],
    ['列表超计划照实 12/9', '12 / 9 篇'],
    ['状态字已完结', '已完结'],
    ['状态字连载中', '连载中'],
  ])
  ok('无计划不出现「4 / 」', !(await main.innerText()).includes('4 / '))
  ok('卡片 5 张', await main.locator('a[href^="/collection/"]').count() >= 5)
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'b-collections-1440.png') })
  await ctx.close()
}

// ── C. 详情页：有计划 + 超计划 ───────────────────────────
console.log('== C. 合集详情 ==')
const detailOf = (over, articles = []) => col({ articles, ...over })
{
  const { ctx, page, errors } = await newPage()
  await page.route('**/v1/collections/4?**', route => route.fulfill(jsonRes(detailOf(
    { id: '4', title: '超计划合集', articleCount: 12, plannedCount: 9, status: 1 },
    [{ id: '11', title: '成员文', summary: 's', coverImage: null, authorName: 'somehow', authorId: '1', viewCount: 0, favoriteCount: 0, readingTime: 5, sortOrder: 0, createTime: '2026-01-01 00:00:00' }],
  ))))
  await page.goto(base + '/collection/4', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const main = page.locator('main')
  await expectTexts(main, [['详情超计划照实', '12 / 9 篇'], ['详情已完结', '已完结']])
  const bar = main.locator('.bg-accent').first()
  const w = await bar.evaluate(e => e.style.width).catch(() => null)
  ok('详情条 cap 100%', w === '100%', String(w))
  // max-w-2xl 约束：进度轨道容器宽度 ≤ 672px
  const trackW = await bar.evaluate(e => e.parentElement?.getBoundingClientRect().width ?? -1).catch(() => -1)
  ok('轨道栏宽 ≤672px (max-w-2xl)', trackW > 0 && trackW <= 672, String(trackW))
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'c-detail-overplan-1440.png') })
  await ctx.close()
}
// D. 详情页无计划
{
  const { ctx, page, errors } = await newPage()
  await page.route('**/v1/collections/3?**', route => route.fulfill(jsonRes(detailOf(
    { id: '3', title: '无计划合集', articleCount: 4, plannedCount: null, status: 0 },
  ))))
  await page.goto(base + '/collection/3', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const main = page.locator('main')
  await expectTexts(main, [['无计划只显篇数', '4 篇'], ['无计划连载中', '连载中']])
  ok('无计划不渲染进度条', (await main.locator('.bg-accent').count()) === 0)
  ok('无计划不出现「/」分隔', !(await main.innerText()).includes('4 / '))
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'd-detail-noplan-1440.png') })
  await ctx.close()
}

// ── E. 编辑表单：回填 / 切换 / 清空 → null ───────────────
console.log('== E. 编辑表单三态 ==')
{
  const { ctx, page, errors } = await newPage({ admin: true })
  let putBody = null
  await page.route('**/v1/collections/1', route => {
    if (route.request().method() === 'PUT') {
      putBody = route.request().postDataJSON()
      return route.fulfill(jsonRes(null))
    }
    return route.fulfill(jsonRes(detailOf({ id: '1', title: '连载合集', articleCount: 3, plannedCount: 9, status: 0 })))
  })
  await page.route('**/v1/collections/1?**', route => route.fulfill(jsonRes(detailOf({ id: '1', articleCount: 3, plannedCount: 9, status: 0 }))))
  await page.goto(base + '/dashboard/collections/1/edit', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)

  const plannedInput = page.locator('input[placeholder*="计划总篇数"]')
  ok('计划输入框回填 9', (await plannedInput.inputValue()) === '9', await plannedInput.inputValue())
  const btnOngoing = page.getByRole('button', { name: '连载中' })
  const btnFinished = page.getByRole('button', { name: '已完结' })
  ok('状态钮是 button（可聚焦）', (await btnOngoing.evaluate(e => e.tagName)) === 'BUTTON')
  await btnFinished.click()
  await page.waitForTimeout(150)
  ok('切到已完结后样式点亮', (await btnFinished.getAttribute('class') ?? '').includes('accent') ||
     (await btnFinished.getAttribute('class') ?? '').includes('bg-'), await btnFinished.getAttribute('class'))
  // 清空计划 → 保存 → PUT 应带 plannedCount:null
  await plannedInput.fill('')
  await page.getByRole('button', { name: /保存|更新|提交/ }).first().click()
  await page.waitForTimeout(600)
  ok('PUT 已发出', putBody != null)
  if (putBody) {
    ok('清空计划 → plannedCount 显式 null', 'plannedCount' in putBody && putBody.plannedCount === null, JSON.stringify(putBody))
    ok('status=1 已完结', putBody.status === 1, JSON.stringify(putBody))
  }
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'e-edit-form-1440.png') })
  await ctx.close()
}

// ── F. 移动端 390 ────────────────────────────────────────
console.log('== F. 移动端 390 ==')
{
  const { ctx, page, errors } = await newPage({ viewport: { width: 390, height: 844 } })
  await mockList(page)
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  ok('首页溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'f-mobile-home-390.png') })
  await page.route('**/v1/collections/4?**', route => route.fulfill(jsonRes(detailOf(
    { id: '4', title: '超计划合集', articleCount: 12, plannedCount: 9, status: 1 }))))
  await page.goto(base + '/collection/4', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  ok('详情溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'f-mobile-detail-390.png') })
  await ctx.close()
}

// ── G. paper-dark ────────────────────────────────────────
console.log('== G. paper-dark ==')
{
  const { ctx, page, errors } = await newPage({ theme: 'paper-dark' })
  await mockList(page)
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const sidebar = page.locator('aside').last()
  await expectTexts(sidebar, [['dark 连载中', '3 / 9 篇 · 连载中'], ['dark 已完结', '6 / 6 篇 · 已完结']])
  ok('dark 进度条渲染', (await barWidths(sidebar)).length === 4)
  ok('溢出=0', (await overflowOf(page)) === 0)
  ok('console 0 error', errors.length === 0, errors[0] ?? '')
  await page.screenshot({ path: join(outDir, 'g-dark-1440.png') })
  await ctx.close()
}

// ── H. a11y 细节 ─────────────────────────────────────────
console.log('== H. a11y ==')
{
  const { ctx, page } = await newPage()
  await mockList(page)
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  // 进度条纯装饰——无计划项不应出现任何 progressbar 语义或空轨道
  const sidebar = page.locator('aside').last()
  const noPlanRow = sidebar.locator('a', { hasText: '无计划合集' })
  ok('无计划行内无进度轨道', (await noPlanRow.locator('.bg-accent').count()) === 0)
  const finishedRow = sidebar.locator('a', { hasText: '已完结合集' })
  ok('已完结行条=100%', (await finishedRow.locator('.bg-accent').evaluate(e => e.style.width).catch(() => null)) === '100%')
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
