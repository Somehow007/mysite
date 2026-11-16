// T8 走查：交互与动态行为复核（与 t8-verify 静态断言互补）
// 覆盖：TOC 滚动联动/已读进度、分享 toast、匿名收藏跳登录、登录态双按钮计数同步、
// snav 翻页后滚动回顶+正文刷新、移动端目录抽屉开合、剩余 5 主题溢出、
// 无目录/无封面/无标签文章降级、console error。
import { chromium } from 'playwright'
import { mkdirSync } from 'fs'
import { join } from 'path'

const BASE = process.env.T8_BASE ?? 'http://127.0.0.1:5173'
const API = process.env.T8_API ?? 'http://localhost:8081'
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t8-walk')
mkdirSync(outDir, { recursive: true })

const W_USER = process.env.T8_USER ?? 't8walk'
const W_PASS = process.env.T8_PASS ?? 'T8walk!2026'

let pass = 0, fail = 0
function ok(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

async function apiJson(path, opts = {}) {
  const res = await fetch(`${API}${path}`, opts)
  return res.json().catch(() => null)
}

const listJson = await apiJson('/v1/articles?current=1&size=50&published=1')
const list = listJson?.data?.records ?? listJson?.data?.list ?? []
async function detail(id) { return (await apiJson(`/v1/articles/${id}`))?.data ?? null }

// 合集文章优先选正文长的（滚动联动检查需要足够页高）
let collPost = null
for (const c of list.filter(a => a.collectionId)) {
  const d = await detail(c.id)
  if (d && (d.content || '').length > 3000) { collPost = d; break }
}
if (!collPost) {
  const cand = list.find(a => a.collectionId)
  if (cand) collPost = await detail(cand.id)
}
// 无封面文章：走查「不渲染 .post-cover」降级分支用
let plainPost = null
for (const c of list.filter(a => !a.coverImage)) {
  const d = await detail(c.id)
  if (d && !d.coverImage) { plainPost = d; break }
}
if (!collPost || !plainPost) {
  console.error(`找不到测试文章 collPost=${collPost?.id} plainPost=${plainPost?.id}`)
  process.exit(1)
}
console.log(`collPost=${collPost.id}(${collPost.title}) plainPost=${plainPost.id}(${plainPost.title})`)

const browser = await chromium.launch()

function newCtx(opts = {}) {
  return browser.newContext({
    viewport: { width: 1440, height: 900 },
    ...opts,
  })
}
async function newPage(ctx, theme = 'paper-light') {
  const p = await ctx.newPage()
  p._errs = []
  p.on('pageerror', e => p._errs.push(`pageerror: ${e.message}`))
  p.on('console', m => { if (m.type() === 'error') p._errs.push(`console: ${m.text()}`) })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  return p
}
const overflow = () => Math.max(0, document.documentElement.scrollWidth - window.innerWidth)

// ── A. 滚动联动：TOC active + 已读进度 ──
console.log('== A. 滚动联动 ==')
{
  const ctx = await newCtx()
  const page = await newPage(ctx)
  await page.goto(`${BASE}/post/${collPost.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-rail .toc', { timeout: 15000 })
  await page.waitForTimeout(800)

  // 滚到中部
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight * 0.45))
  await page.waitForTimeout(700)
  const activeCount = await page.locator('.post-rail .toc [data-active="true"], .post-rail .toc [aria-current="true"]').count()
  const pctText = await page.locator('.post-rail-pct').innerText().catch(() => '')
  const pctMatch = pctText.match(/已读 (\d+)%/)
  ok('TOC 有激活项', activeCount >= 1, `active=${activeCount}`)
  ok('已读进度在 20~95 之间', pctMatch && +pctMatch[1] > 20 && +pctMatch[1] < 100, pctText)
  const remMatch = pctText.match(/剩约 (\d+) 分钟/)
  ok('剩余分钟数存在', !!remMatch, pctText)

  // 顶部进度条出现
  const barW = await page.evaluate(() => document.querySelector('.fixed.top-0.left-0.h-\\[2px\\]')?.style.width)
  ok('顶部进度条已显示', !!barW && parseFloat(barW) > 0, String(barW))

  // TOC 点击 → 滚动到锚点
  const tocLinks = page.locator('.post-rail .toc button')
  const n = await tocLinks.count()
  if (n > 1) {
    const target = await tocLinks.nth(1).innerText()
    await tocLinks.nth(1).click()
    await page.waitForTimeout(900)
    const y = await page.evaluate(() => window.scrollY)
    ok('TOC 点击产生滚动', y > 100, `target=${target} y=${y}`)
  }
  ok('console 无错误', page._errs.length === 0, page._errs[0] ?? '')
  await ctx.close()
}

// ── B. 分享 + 匿名收藏 + snav 翻页 ──
console.log('== B. 分享/收藏/snav 翻页 ==')
{
  const ctx = await newCtx()
  const page = await newPage(ctx)
  await ctx.grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.goto(`${BASE}/post/${collPost.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-end-acts .fav-bar', { timeout: 15000 })
  await page.waitForTimeout(600)

  // 分享 → clipboard
  await page.locator('.post-end-acts .fav-bar').last().click()
  await page.waitForTimeout(700)
  const clip = await page.evaluate(() => navigator.clipboard.readText()).catch(() => '')
  ok('分享复制链接', clip.includes(`/post/${collPost.id}`), clip.slice(0, 80))
  const toast = await page.locator('[role=status]').first().innerText().catch(() => '')
  ok('分享 toast', toast.includes('复制'), toast.slice(0, 60))

  // 匿名收藏 → 登录跳转
  await page.locator('.post-end-acts .fav-bar').first().click()
  await page.waitForURL(/login/, { timeout: 8000 })
  ok('匿名收藏跳登录', page.url().includes('/login'), page.url())
  ok('redirect 回本篇', decodeURIComponent(page.url()).includes(`/post/${collPost.id}`), page.url())
  await page.goBack()
  await page.waitForSelector('.post-title', { timeout: 15000 })

  // snav 下一篇 → 滚动回顶 + 标题变化
  const nextLink = page.locator('.post-snav-pn .pn-next')
  if (await nextLink.count()) {
    const nextTitle = (await nextLink.locator('b').innerText()).trim()
    await page.evaluate(() => window.scrollTo(0, 99999))
    await nextLink.click()
    await page.waitForSelector('.post-title', { timeout: 15000 })
    await page.waitForTimeout(1200)
    const y = await page.evaluate(() => window.scrollY)
    const h1 = (await page.locator('.post-title').innerText()).trim()
    ok('snav 下一篇导航', h1 === nextTitle, `h1=${h1} expect=${nextTitle}`)
    ok('翻页后滚动回顶', y < 150, `y=${y}`)
    // 徽标/进度点应更新
    const badge = await page.locator('.series-tag').innerText().catch(() => '')
    ok('翻页后徽标刷新', /第 \d+ \/ \d+ 篇/.test(badge), badge)
  }
  ok('console 无错误', page._errs.length === 0, page._errs[0] ?? '')
  await ctx.close()
}

// ── C. 登录态双收藏按钮同步 + 移动端抽屉 ──
console.log('== C. 登录态收藏同步 + 移动抽屉 ==')
{
  // 注册走查账号
  const reg = await apiJson('/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: W_USER, password: W_PASS,
      email: `${W_USER}@example.com`, realName: 'T8 Walk', phoneNumber: '13800000002',
    }),
  })
  const loginRes = await apiJson('/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: W_USER, password: W_PASS }),
  })
  const tokens = loginRes?.data
  ok('走查账号可登录', !!tokens?.accessToken || !!tokens?.access_token, JSON.stringify(reg?.code))

  const ctx = await newCtx()
  const page = await newPage(ctx)
  await page.goto(BASE, { waitUntil: 'domcontentloaded' })
  await page.evaluate(t => {
    localStorage.setItem('mysite_access_token', JSON.stringify(t.accessToken ?? t.access_token))
    const rt = t.refreshToken ?? t.refresh_token
    if (rt) localStorage.setItem('mysite_refresh_token', JSON.stringify(rt))
  }, tokens ?? {})
  await page.goto(`${BASE}/post/${collPost.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-end-acts .fav-bar', { timeout: 15000 })
  // 等 userStore.init() 的 /v1/auth/me 回来（isLoggedIn=!!user），否则收藏被当成未登录
  await page.waitForSelector('a[href="/login"]', { state: 'hidden', timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(500)

  const before = (await page.locator('.post-end-acts .fav-bar').first().innerText()).trim()
  await page.locator('.post-end-acts .fav-bar').first().click()
  await page.waitForTimeout(1500)
  const afterEnd = (await page.locator('.post-end-acts .fav-bar').first().innerText()).trim()
  const afterHead = (await page.locator('.post-by-acts .fav-bar').first().innerText()).trim()
  ok('登录收藏切换成功', afterEnd !== before, `before=${before} after=${afterEnd}`)
  ok('刊头/文末计数同步', afterEnd === afterHead, `head=${afterHead} end=${afterEnd}`)
  // 再点一次恢复
  await page.locator('.post-end-acts .fav-bar').first().click()
  await page.waitForTimeout(1200)
  ok('console 无错误', page._errs.length === 0, page._errs[0] ?? '')
  await ctx.close()

  // 移动端抽屉
  const mctx = await newCtx({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const mp = await newPage(mctx)
  await mp.goto(`${BASE}/post/${collPost.id}`, { waitUntil: 'domcontentloaded' })
  await mp.waitForSelector('.post-title', { timeout: 15000 })
  await mp.waitForTimeout(800)
  const fab = mp.locator('button[aria-label="打开文章目录"]')
  ok('移动目录 FAB 存在', await fab.count() > 0, '')
  if (await fab.count()) {
    await fab.click()
    await mp.waitForTimeout(700)
    const drawer = mp.locator('[aria-label="文章目录"]:visible').last()
    const dCount = await drawer.count()
    ok('目录抽屉打开', dCount > 0, `drawer=${dCount}`)
    if (dCount) {
      const item = drawer.locator('button:visible').filter({ hasText: /./ }).first()
      if (await item.count()) {
        const itemText = await item.innerText()
        await item.click()
        await mp.waitForTimeout(800)
        const y = await mp.evaluate(() => window.scrollY)
        ok('抽屉项点击滚动', y > 0, `item=${itemText} y=${y}`)
        const drawerGone = await drawer.first().isVisible().catch(() => false)
        ok('点击后抽屉关闭', !drawerGone, '')
      }
    }
  }
  ok('移动 console 无错误', mp._errs.length === 0, mp._errs[0] ?? '')
  await mctx.close()
}

// ── D. 剩余主题溢出 + console ──
console.log('== D. 剩余 5 主题溢出 ==')
for (const theme of ['aurora', 'rose-garden', 'ocean-breeze', 'warm-sunset', 'liquid-glass']) {
  const ctx = await newCtx()
  const page = await newPage(ctx, theme)
  await page.goto(`${BASE}/post/${collPost.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-title', { timeout: 15000 })
  await page.waitForTimeout(700)
  const ov = await page.evaluate(overflow)
  ok(`溢出=0 ${theme}`, ov === 0, `ov=${ov}`)
  ok(`console 无错 ${theme}`, page._errs.length === 0, page._errs[0] ?? '')
  await page.screenshot({ path: join(outDir, `d-${theme}.png`) })
  await ctx.close()
}

// ── E. 降级场景：无目录文章 / 无合集 ──
console.log('== E. 降级场景 ==')
{
  const ctx = await newCtx()
  const page = await newPage(ctx)
  // 「test」是合集第一篇、标题为 test，内容可能很短
  const testPost = collPost ? (await apiJson(`/v1/articles/${collPost.id}/navigation`))?.data?.collectionArticles?.[0] : null
  if (testPost) {
    await page.goto(`${BASE}/post/${testPost.id}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.post-title', { timeout: 15000 })
    await page.waitForTimeout(800)
    ok('无目录文章页正常渲染', true)
    const askCard = await page.locator('.ask-mini').count()
    ok('无 TOC 时提问卡仍在', askCard === 1, `ask=${askCard}`)
    const ov = await page.evaluate(overflow)
    ok('无目录页溢出=0', ov === 0, `ov=${ov}`)
    await page.screenshot({ path: join(outDir, 'e-short-post.png'), fullPage: true })
  }
  // 无封面文章不渲染 .post-cover
  if (plainPost) {
    await page.goto(`${BASE}/post/${plainPost.id}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.post-title', { timeout: 15000 })
    const covers = await page.locator('.post-cover').count()
    ok('无封面不渲染', covers === 0, `covers=${covers}`)
  }
  ok('console 无错误', page._errs.length === 0, page._errs[0] ?? '')
  await ctx.close()
}

await browser.close()
console.log(`\n== RESULT: ${pass} pass, ${fail} fail ==`)
process.exit(fail ? 1 : 0)
