// T9 走查：移动端 dock + sheet DOM 级复核（t9-verify 之外的边角）
// 用法: node scripts/t9-walkthrough.mjs [baseUrl]
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t9')
mkdirSync(outDir, { recursive: true })
const MOBILE = { width: 390, height: 844 }

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

async function apiGet(path) {
  const r = await fetch(`${base}${path}`)
  return r.json()
}
const listJson = await apiGet('/v1/articles?current=1&size=50&published=1')
const recs = listJson?.data?.records ?? listJson?.data?.list ?? []
async function detail(id) {
  const j = await apiGet(`/v1/articles/${id}`)
  return j?.data ?? null
}
let target = null
for (const a of recs) {
  const d = await detail(a.id)
  if (d && (d.content || '').length > 3000) { target = d; break }
}
if (!target && recs.length) target = await detail(recs[0].id)
// 长 TOC 文章（176 标题）：sheet 内滚动/折叠/全展收的真实压力测试对象
let longPost = null
{
  let maxH = -1
  for (const a of recs) {
    const d = await detail(a.id)
    const n = ((d?.content || '').match(/^#{1,6}\s/gm) || []).length
    if (n > maxH) { maxH = n; longPost = d }
  }
}
// 最短文章（2 标题）：sheet 最小形态
let smallPost = null
{
  let minL = Infinity
  for (const a of recs) {
    const d = await detail(a.id)
    const l = (d?.content || '').length
    if (d && l < minL) { minL = l; smallPost = d }
  }
}
console.log(`target=${target?.id} long=${longPost?.id} small=${smallPost?.id}`)

const browser = await chromium.launch()
async function newPage(theme, viewport, opts = {}) {
  const ctx = await browser.newContext({ viewport, hasTouch: opts.touch ?? false })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  return { ctx, page, errors }
}
async function gotoPost(page, id) {
  await page.goto(`${base}/post/${id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-title', { timeout: 15000 })
  await page.waitForTimeout(1000)
}

if (!target) { ok('找到测试文章', false) }

// ── A. 状态清理：离开文章页 body 类移除 ─────────────────────
{
  console.log('== A. body 类生命周期 ==')
  const { ctx, page } = await newPage('paper-light', MOBILE)
  await gotoPost(page, target.id)
  ok('post 页 has-post-dock', await page.evaluate(() => document.body.classList.contains('has-post-dock')))
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  ok('离开后类移除', !(await page.evaluate(() => document.body.classList.contains('has-post-dock'))))
  ok('archive 无 dock', await page.locator('.post-dock').count() === 0)
  // 返回文章页 → 类再挂上（重挂载路径）
  await page.goto(`${base}/post/${target.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-dock', { timeout: 10000 })
  await page.waitForTimeout(800)
  ok('返回后类再挂', await page.evaluate(() => document.body.classList.contains('has-post-dock')))
  await ctx.close()
}

// ── B. sheet 内滚动 / 折叠组 / 已读%联动（长文 176 标题）─────
{
  console.log('== B. sheet 内容与进度 ==')
  const { ctx, page } = await newPage('paper-light', MOBILE)
  await gotoPost(page, longPost.id)

  // 已读% 初始 → 滚动 → dock 副标变化
  const pctBefore = await page.locator('.post-dock-btn').first().innerText()
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight * 0.4))
  await page.waitForTimeout(600)
  const pctAfter = await page.locator('.post-dock-btn').first().innerText()
  ok('已读% 随滚动更新', pctBefore !== pctAfter, `${pctBefore}→${pctAfter}`)

  await page.locator('.post-dock-btn').nth(0).click()
  await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
  await page.waitForTimeout(400)

  // sheet 内列表独立滚动：滚到底部，scrollTop 应 >0（176 项远超 78vh）
  const scrolled = await page.evaluate(() => {
    const body = document.querySelector('.toc-sheet-body')
    body.scrollTop = body.scrollHeight
    return body.scrollTop
  })
  ok('sheet 列表可滚动', scrolled > 100, `scrollTop=${scrolled}`)

  const totalLi = await page.locator('.toc-sheet-body li').count()
  ok('sheet 目录项数量级', totalLi > 50, `got ${totalLi}`)

  // 折叠组：CollapseTransition 用 grid-rows 收起（DOM 不卸载）→ 断言 .collapsed 类
  const collapsedBefore = await page.locator('.toc-sheet-body .collapse-wrapper.collapsed').count()
  const firstToggle = page.locator('.toc-sheet-body .toc-toggle').first()
  ok('存在可折叠组', await firstToggle.count() > 0)
  await firstToggle.click()
  await page.waitForTimeout(400)
  const collapsedAfter = await page.locator('.toc-sheet-body .collapse-wrapper.collapsed').count()
  ok('折叠组收起', collapsedAfter > collapsedBefore, `${collapsedBefore}→${collapsedAfter}`)
  await firstToggle.click()
  await page.waitForTimeout(400)
  ok('折叠组展开', await page.locator('.toc-sheet-body .collapse-wrapper.collapsed').count() === collapsedBefore)

  // 全展收按钮：展开态点击 → collapsed 数 >0；再点回 0
  const allBtn = page.locator('.toc-sheet-header-acts button').first()
  await allBtn.click()
  await page.waitForTimeout(400)
  const allCollapsed = await page.locator('.toc-sheet-body .collapse-wrapper.collapsed').count()
  ok('全展收收起', allCollapsed > 0, `collapsed=${allCollapsed}`)
  await allBtn.click()
  await page.waitForTimeout(400)
  ok('全展收还原', await page.locator('.toc-sheet-body .collapse-wrapper.collapsed').count() === collapsedBefore)

  // sheet 开着时 dock 被遮挡（elementFromPoint 命中 sheet/mask 而非 dock 按钮）
  const hit = await page.evaluate(() => {
    const btn = document.querySelector('.post-dock-ask')
    const r = btn.getBoundingClientRect()
    const el = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
    return { tag: el?.tagName, cls: el?.className?.toString().slice(0, 60) }
  })
  ok('sheet 期间 dock 不可点', !(hit.cls ?? '').includes('post-dock'), JSON.stringify(hit))
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  await ctx.close()
}

// ── C. tablet 800：dock 问这篇开桌面面板；BackToTop 隐藏 ─────
{
  console.log('== C. tablet 800 ==')
  const { ctx, page } = await newPage('paper-light', { width: 800, height: 1024 })
  await page.route('**/v1/rag/chat/stream**', route => route.fulfill({
    status: 200, contentType: 'text/event-stream', body: 'data: {"type":"done"}\n\n',
  }))
  await gotoPost(page, target.id)
  // 滚动 >300 → BackToTop 应隐藏（dock 覆盖 <lg）
  await page.evaluate(() => window.scrollTo(0, 800))
  await page.waitForTimeout(500)
  ok('tablet post BackToTop 隐藏', await page.locator('button[aria-label="返回顶部"]').count() === 0)
  await page.locator('.post-dock-ask').click()
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 })
  await page.waitForTimeout(500) // 等 zoom 进场动画结束（scale 0.96→1）
  const box = await page.locator('.chat-panel').boundingBox()
  ok('tablet 聊天=桌面面板(非满宽sheet)', box && Math.abs(box.width - 520) <= 2 && Math.abs(box.height - 680) <= 2, JSON.stringify(box))
  await page.keyboard.press('Escape').catch(() => {})
  await page.waitForTimeout(300)
  await ctx.close()
}

// ── D. 匿名 dock 问这篇 → 面板内登录提示 ────────────────────
{
  console.log('== D. 匿名问这篇 ==')
  const { ctx, page } = await newPage('paper-light', MOBILE)
  await gotoPost(page, target.id)
  await page.locator('.post-dock-ask').click()
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 })
  ok('匿名可开面板', true)
  ok('匿名登录提示条', await page.locator('.chat-note', { hasText: '登录后可保存对话历史' }).count() === 1)
  ok('不跳登录页', !page.url().includes('/login'), page.url())
  await page.locator('.chat-panel button[aria-label="关闭"]').click()
  await page.waitForTimeout(300)
  await ctx.close()
}

// ── E. 短文章：sheet 最小形态 + 条目点击 ─────────────────────
{
  console.log('== E. 短文章 sheet ==')
  if (smallPost) {
    const { ctx, page } = await newPage('paper-light', MOBILE)
    await gotoPost(page, smallPost.id)
    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    await page.waitForTimeout(400)
    const items = await page.locator('.toc-sheet-body li').count()
    ok('短文 sheet 目录项>0', items > 0, `got ${items}`)
    // 点第一条目 → sheet 关闭（短文锚点可能不滚动，只验关闭）
    await page.locator('.toc-sheet-body li > button').first().click()
    await page.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 })
    ok('短文条目点击关闭', true)
    await ctx.close()
  } else {
    ok('找到短文章', false)
  }
}

// ── F. 色值实测：paper-dark 浅 dock / classic 深 dock ────────
{
  console.log('== F. dock 色值 ==')
  for (const [theme, wantLight] of [['paper-dark', true], ['classic-light', false]]) {
    const { ctx, page } = await newPage(theme, MOBILE)
    await gotoPost(page, target.id)
    const bg = await page.locator('.post-dock').evaluate(el => {
      const c = getComputedStyle(el).backgroundColor.match(/\d+/g).map(Number)
      return (c[0] * 299 + c[1] * 587 + c[2] * 114) / 1000
    })
    ok(`${theme} dock ${wantLight ? '浅底' : '深底'}`, wantLight ? bg > 200 : bg < 80, `lum=${bg.toFixed(0)}`)
    // 主题切换后 sheet 跟随（token 驱动）
    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    await page.waitForTimeout(400)
    const sheetBg = await page.locator('.toc-sheet').evaluate(el => getComputedStyle(el).backgroundColor)
    await page.evaluate(t => { document.documentElement.dataset.theme = t }, theme === 'paper-dark' ? 'paper-light' : 'classic-dark')
    await page.waitForTimeout(200)
    const sheetBg2 = await page.locator('.toc-sheet').evaluate(el => getComputedStyle(el).backgroundColor)
    ok(`${theme} sheet 主题跟随`, sheetBg !== sheetBg2, `${sheetBg}→${sheetBg2}`)
    await ctx.close()
  }
}

// ── G. 横屏矮视口 + 长标题截断 ───────────────────────────────
{
  console.log('== G. 矮视口 ==')
  const { ctx, page } = await newPage('paper-light', { width: 844, height: 390 })
  await gotoPost(page, target.id)
  const dockBox = await page.locator('.post-dock').boundingBox()
  ok('矮视口 dock 在屏内', dockBox && dockBox.y < 390 && dockBox.y + dockBox.height > 0, JSON.stringify(dockBox))
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok('矮视口溢出=0', overflow === 0, `overflow=${overflow}`)
  await page.screenshot({ path: join(outDir, 'g-landscape.png') })
  await ctx.close()
}

// ── H. console/pageerror 汇总（最后一个 ctx 的 errors）──────
{
  const { ctx, page, errors } = await newPage('paper-light', MOBILE)
  await gotoPost(page, target.id)
  await page.locator('.post-dock-btn').nth(0).click()
  await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
  await page.waitForTimeout(500)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  ok('复核路径无 pageerror', errors.length === 0, errors.join(' | '))
  await ctx.close()
}

await browser.close()
console.log(`\n== RESULT: ${pass} pass, ${fail} fail ==`)
process.exit(fail ? 1 : 0)
