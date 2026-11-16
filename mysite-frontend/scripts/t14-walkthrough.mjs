// T14 走查（DOM 级二次复核）：文章置顶
// 用法: node scripts/t14-walkthrough.mjs [baseUrl]
// 依赖 dev server + playwright；全部后端交互走 route mock，无需真实 8081。
// 覆盖（首轮 verify 之外）：
//   A. 首页置顶头条：kicker「置顶 · 分类」、lead=置顶旧文、列表去重、lead 请求 sortField=topTime
//   B. 无置顶回落：kicker「最新」、lead=最新文
//   C. 分类筛选透传：lead 请求带 categorySlug+topTime；跨分类置顶不顶掉分类头条
//   D. /page/2 不发 lead 请求（isFirstPage 回归）
//   E. Dashboard：CREATOR 自己文章 pin 钮 toggle→PUT→状态徽标；PUT 失败回滚+toast
//   F. USER 自己文章：按钮按 canModify 语义可见（与编辑/删除一致，后端 role 门独立把关）
//   G. 移动 390 卡片置顶文字钮
//   H. 三主题（paper-light/paper-dark/classic-light）kicker + 溢出=0
//   I. 边界：DTO 缺 isTop 字段 → kicker「最新」不崩
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t14-walk')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()
const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)

function jsonRes(data) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: 'ok', data }) }
}
const iPage = (records, total = records.length) => ({ records, total, current: 1, size: 20, pages: 1 })

// ── 数据 ──
// PIN：老文章被置顶（createTime 最旧但 is_top=1）→ 唯一能区分 topTime 排序的形态
const PIN = {
  id: '1001', title: '置顶测试·数据库系统概论', summary: '被置顶的老文章',
  coverImage: null, authorId: '99', authorName: 'admin',
  categoryName: 'Spring Boot 系列', categorySlug: 'spring',
  viewCount: 300, favoriteCount: 2, readingTime: 8,
  published: 1, visibility: 0, isTop: 1,
  topTime: '2026-11-16T10:00:00', createTime: '2026-01-01T08:00:00', updateTime: '2026-01-01T08:00:00',
}
const NEW1 = {
  id: '1002', title: '最新一篇·React 19 解读', summary: '最新文章',
  coverImage: null, authorId: '99', authorName: 'admin',
  categoryName: '前端技术', categorySlug: 'frontend',
  viewCount: 900, favoriteCount: 5, readingTime: 6,
  published: 1, visibility: 0, isTop: 0,
  createTime: '2026-11-15T09:00:00', updateTime: '2026-11-15T09:00:00',
}
const OWN1 = {
  id: '1003', title: '自己的文章·JVM 调优笔记', summary: 'CREATOR 自己的文章',
  coverImage: null, authorId: '1', authorName: 'testuser',
  categoryName: 'Spring Boot 系列', categorySlug: 'spring',
  viewCount: 120, favoriteCount: 0, readingTime: 10,
  published: 1, visibility: 0, isTop: 0,
  createTime: '2026-11-10T09:00:00', updateTime: '2026-11-10T09:00:00',
}
const ALL = [PIN, NEW1, OWN1]
const CATS = [
  { id: 'c1', name: 'Spring Boot 系列', slug: 'spring', articleCount: 2 },
  { id: 'c2', name: '前端技术', slug: 'frontend', articleCount: 1 },
]

function sortList(list, sortField) {
  const arr = [...list]
  if (sortField === 'topTime') {
    // 模拟后端 ORDER BY is_top DESC, top_time DESC, create_time DESC
    return arr.sort((a, b) =>
      (b.isTop - a.isTop) ||
      (new Date(b.topTime || 0) - new Date(a.topTime || 0)) ||
      (new Date(b.createTime) - new Date(a.createTime)))
  }
  if (sortField === 'viewCount') return arr.sort((a, b) => b.viewCount - a.viewCount)
  return arr.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
}

// articles 查询 mock：模拟后端过滤+排序语义；record 收集请求 URL 供断言
async function mockArticles(page, { withPin = true, record = [] }) {
  const handler = (route) => {
    const url = new URL(route.request().url())
    const p = url.searchParams
    const req = { sortField: p.get('sortField'), categorySlug: p.get('categorySlug'), isTop: p.get('isTop'), current: p.get('current') }
    record.push(req)
    let list = withPin ? [...ALL] : ALL.map(a => ({ ...a, isTop: 0, topTime: null }))
    if (req.categorySlug) list = list.filter(a => a.categorySlug === req.categorySlug)
    if (req.isTop !== null) list = list.filter(a => a.isTop === Number(req.isTop))
    list = sortList(list, req.sortField || 'createTime')
    const size = Number(p.get('size') || 10), current = Number(p.get('current') || 1)
    const records = list.slice((current - 1) * size, current * size)
    return route.fulfill(jsonRes({ records, total: list.length, current, size, pages: Math.max(1, Math.ceil(list.length / size)) }))
  }
  await page.route('**/v1/articles', handler)
  return page.route('**/v1/articles?**', handler)
}

// 基础 mocks（每页都要）：site/categories/collections/kb/me
async function baseMocks(page, { user = null, withPin = true, record = [] } = {}) {
  await page.route('**/v1/**', route => route.fulfill({ status: 502, body: '{}' }))
  await page.route('**/v1/site/config', route => route.fulfill(jsonRes({
    title: 'MySite', subtitle: '一个极简的个人博客', author: 'Admin', authorBio: 'bio', authorTagline: 'Java 后端',
  })))
  await page.route('**/v1/categories**', route => route.fulfill(jsonRes(CATS)))
  await page.route('**/v1/collections**', route => route.fulfill(jsonRes(iPage([], 0))))
  await page.route('**/v1/rag/knowledge-bases', route => route.fulfill(jsonRes([])))
  if (user) {
    await page.route('**/v1/auth/me', route => route.fulfill(jsonRes({
      id: '1', username: 'testuser', realName: '测试用户', role: user,
    })))
  }
  await mockArticles(page, { withPin, record })
}

// user: null=匿名；字符串=登录态角色（注入 token + user_role + /me）
async function newPage({ viewport = { width: 1440, height: 900 }, user = null, theme = null } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(({ u, t }) => {
    try {
      if (u) {
        localStorage.setItem('mysite_access_token', JSON.stringify('fake-token'))
        localStorage.setItem('mysite_refresh_token', JSON.stringify('fake-refresh'))
        localStorage.setItem('mysite_user_role', JSON.stringify(u))
      }
      if (t) localStorage.setItem('mysite_theme_v2', t)
    } catch {}
  }, { u: user, t: theme })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  return { ctx, page, errors }
}

const kickerOf = page => page.locator('section article .text-accent').first().textContent().catch(() => null)

// ── A. 首页置顶头条 ─────────────────────────────────
console.log('== A. 首页置顶头条 ==')
{
  const record = []
  const { ctx, page, errors } = await newPage()
  await baseMocks(page, { record })
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('section article', { timeout: 10000 })
  await page.waitForTimeout(600)

  const kicker = (await kickerOf(page))?.trim()
  ok('kicker=置顶·分类', kicker === '置顶 · Spring Boot 系列', kicker)
  const leadTitle = (await page.locator('section article h2').first().textContent().catch(() => ''))?.trim()
  ok('lead=置顶旧文', leadTitle === PIN.title, leadTitle)
  // 列表去重：下方列表不再出现置顶文
  // 列表区 section.min-w-0（区别于 HomeLead 的 section）：置顶文不应重复出现
  const listText = await page.locator('section.min-w-0').innerText().catch(() => '')
  ok('列表已去重置顶文', !listText.includes(PIN.title), listText.slice(0, 80))
  // lead 请求确实带了 sortField=topTime
  ok('lead 请求 sortField=topTime', record.some(r => r.sortField === 'topTime'), JSON.stringify(record))
  // 最受欢迎回归
  ok('最受欢迎栏在', await page.getByText('最受欢迎').count() >= 1)
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, 'a-pinned-lead-1440.png') })
  await ctx.close()
}

// ── B. 无置顶回落「最新」 ─────────────────────────────────
console.log('== B. 无置顶回落 ==')
{
  const { ctx, page, errors } = await newPage()
  await baseMocks(page, { withPin: false })
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('section article', { timeout: 10000 })
  await page.waitForTimeout(600)
  const kicker = (await kickerOf(page))?.trim()
  ok('kicker=最新·分类', kicker === '最新 · 前端技术', kicker)
  const leadTitle = (await page.locator('section article h2').first().textContent().catch(() => ''))?.trim()
  ok('lead=最新文', leadTitle === NEW1.title, leadTitle)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── C. 分类筛选透传 ─────────────────────────────────
console.log('== C. 分类筛选透传 ==')
{
  const record = []
  const { ctx, page, errors } = await newPage()
  await baseMocks(page, { record })
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('section article', { timeout: 10000 })
  // 选「前端技术」——置顶文属于 spring，此处应回落到该分类最新 NEW1
  await page.getByRole('button', { name: /前端技术/ }).first().click()
  await page.waitForTimeout(800)
  const leadReq = record.filter(r => r.sortField === 'topTime').at(-1)
  ok('lead 请求带 categorySlug+topTime', leadReq?.categorySlug === 'frontend', JSON.stringify(leadReq))
  const kicker = (await kickerOf(page))?.trim()
  ok('分类头条=最新（跨分类置顶不抢占）', kicker === '最新 · 前端技术', kicker)
  const leadTitle = (await page.locator('section article h2').first().textContent().catch(() => ''))?.trim()
  ok('分类 lead=该分类最新文', leadTitle === NEW1.title, leadTitle)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── D. /page/2 不发 lead 请求 ─────────────────────────────────
console.log('== D. /page/2 无 lead 请求 ==')
{
  const record = []
  const { ctx, page, errors } = await newPage()
  await baseMocks(page, { record })
  await page.goto(`${base}/page/2`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  ok('page2 无 topTime 请求', !record.some(r => r.sortField === 'topTime'), JSON.stringify(record))
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── E. Dashboard：CREATOR 置顶开关 + 失败回滚 ─────────────────────────────────
console.log('== E. Dashboard 置顶开关 ==')
{
  const record = []
  const { ctx, page, errors } = await newPage({ user: 'CREATOR' })
  await baseMocks(page, { user: 'CREATOR', record })
  const putCalls = []
  await page.route('**/v1/articles/*/top', route => {
    putCalls.push(route.request().postDataJSON())
    return route.fulfill(jsonRes(null))
  })

  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const ownRow = page.locator('tr', { hasText: 'JVM 调优笔记' })
  ok('CREATOR 自己文章有置顶钮', await ownRow.locator('button[title="置顶"]').count() === 1)
  // CREATOR 看不到他人文章（前端按 authorId 过滤）→ 他人行不存在
  ok('非管理员他人文章被过滤', await page.locator('tr', { hasText: 'React 19 解读' }).count() === 0)

  // 点击置顶
  await ownRow.locator('button[title="置顶"]').click()
  await page.waitForTimeout(500)
  ok('PUT 携带 isTop=1', putCalls.at(-1)?.isTop === 1, JSON.stringify(putCalls.at(-1)))
  ok('按钮变「取消置顶」', await ownRow.locator('button[title="取消置顶"]').count() === 1)
  ok('状态列出现置顶标记', await ownRow.locator('svg.text-accent').count() >= 1)
  // 再点取消
  await ownRow.locator('button[title="取消置顶"]').click()
  await page.waitForTimeout(500)
  ok('PUT 携带 isTop=0', putCalls.at(-1)?.isTop === 0, JSON.stringify(putCalls.at(-1)))
  ok('按钮回「置顶」', await ownRow.locator('button[title="置顶"]').count() === 1)
  ok('状态列置顶标记消失', await ownRow.locator('svg.text-accent').count() === 0)

  // 失败回滚：PUT 500 → 状态不翻转 + toast
  await page.unroute('**/v1/articles/*/top')
  await page.route('**/v1/articles/*/top', route => route.fulfill({ status: 500, body: '{}' }))
  await ownRow.locator('button[title="置顶"]').click()
  await page.waitForTimeout(600)
  ok('失败后按钮保持「置顶」', await ownRow.locator('button[title="置顶"]').count() === 1)
  // toast 内容为原始错误信息（与既有 handler 同口径），断言容器出现任意 toast
  ok('失败 toast 出现', await page.locator('.fixed.bottom-6 > div').first().isVisible().catch(() => false))

  ok('横向溢出=0', (await overflowOf(page)) === 0)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, 'e-dashboard-1440.png') })
  await ctx.close()
}

// ── F. USER 自己文章按钮可见性（canModify=作者本人 与编辑/删除同语义） ─────────────────────────────────
console.log('== F. USER 自己文章 ==')
{
  const { ctx, page, errors } = await newPage({ user: 'USER' })
  await baseMocks(page, { user: 'USER' })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const ownRow = page.locator('tr', { hasText: 'JVM 调优笔记' })
  ok('USER 自己文章也渲染操作钮', await ownRow.locator('button[title="置顶"]').count() === 1)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── G. 移动 390 卡片文字钮 ─────────────────────────────────
console.log('== G. 移动端卡片 ==')
{
  const { ctx, page, errors } = await newPage({ viewport: { width: 390, height: 844 }, user: 'CREATOR' })
  await baseMocks(page, { user: 'CREATOR' })
  const putCalls = []
  await page.route('**/v1/articles/*/top', route => {
    putCalls.push(route.request().postDataJSON())
    return route.fulfill(jsonRes(null))
  })
  await page.goto(`${base}/dashboard`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1000)
  const pinBtn = page.getByRole('button', { name: '置顶', exact: true })
  ok('移动卡片有置顶文字钮', await pinBtn.count() >= 1, String(await pinBtn.count()))
  if (await pinBtn.count() >= 1) {
    await pinBtn.first().click()
    await page.waitForTimeout(500)
    ok('移动端 PUT isTop=1', putCalls.at(-1)?.isTop === 1)
    ok('按钮变「取消置顶」', await page.getByRole('button', { name: '取消置顶', exact: true }).count() >= 1)
  }
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, 'g-dashboard-390.png') })
  await ctx.close()
}

// ── H. 三主题 kicker + 溢出 ─────────────────────────────────
console.log('== H. 三主题 ==')
for (const theme of ['paper-light', 'paper-dark', 'classic-light']) {
  const { ctx, page, errors } = await newPage({ theme })
  await baseMocks(page)
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('section article', { timeout: 10000 })
  await page.waitForTimeout(600)
  const kicker = (await kickerOf(page))?.trim()
  ok(`${theme} kicker=置顶`, kicker === '置顶 · Spring Boot 系列', kicker)
  ok(`${theme} 溢出=0`, (await overflowOf(page)) === 0)
  ok(`${theme} 无 pageerror`, errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, `h-${theme}-1440.png`) })
  await ctx.close()
}

// ── I. 边界：DTO 缺 isTop → 「最新」不崩 ─────────────────────────────────
console.log('== I. 边界 isTop 缺失 ==')
{
  const { ctx, page, errors } = await newPage()
  await page.route('**/v1/**', route => route.fulfill({ status: 502, body: '{}' }))
  await page.route('**/v1/site/config', route => route.fulfill(jsonRes({ title: 'MySite', subtitle: 's', author: 'A' })))
  await page.route('**/v1/categories**', route => route.fulfill(jsonRes(CATS)))
  await page.route('**/v1/collections**', route => route.fulfill(jsonRes(iPage([], 0))))
  await page.route('**/v1/rag/knowledge-bases', route => route.fulfill(jsonRes([])))
  const listHandler = (route) => {
    const url = new URL(route.request().url())
    const sortField = url.searchParams.get('sortField')
    let list = [NEW1, OWN1].map(a => { const { isTop, ...rest } = a; return rest }) // 完全去掉字段
    if (sortField === 'viewCount') list = [...list].sort((a, b) => b.viewCount - a.viewCount)
    else list = [...list].sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
    return route.fulfill(jsonRes({ records: list, total: 2, current: 1, size: 10, pages: 1 }))
  }
  await page.route('**/v1/articles', listHandler)
  await page.route('**/v1/articles?**', listHandler)
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('section article', { timeout: 10000 })
  await page.waitForTimeout(600)
  const kicker = (await kickerOf(page))?.trim()
  ok('无 isTop 字段 → kicker=最新', kicker === '最新 · 前端技术', kicker)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
