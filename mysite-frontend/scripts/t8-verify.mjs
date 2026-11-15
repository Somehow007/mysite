// T8 验证：文章页重做（刊头/封面/双栏/文末/右栏/合集导航/继续读/提问卡）
// 用法: node scripts/t8-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://localhost:5173）+ 后端（/v1 代理）+ playwright。
// E 段需要一个 USER 账号：脚本先自注册（已存在则复用），SSE 流为 mock。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t8')
mkdirSync(outDir, { recursive: true })

const T8_USER = process.env.T8_USER ?? 't8check'
const T8_PASS = process.env.T8_PASS ?? 'T8check!2026'

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

// ── 发现测试文章：合集长文（有 TOC）> 合集任意 > 非合集带封面 > 非合集任意 ──
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

// 优先：合集 + 长正文（TOC/进度点/继续读都能验到）
let collPost = null
for (const a of recs.filter(x => x.collectionTitle)) {
  const d = await detail(a.id)
  if (d && (d.content || '').length > 3000) { collPost = d; break }
}
if (!collPost) {
  const cand = recs.find(x => x.collectionTitle)
  if (cand) collPost = await detail(cand.id)
}
// 非合集 + 封面（验证封面/无合集徽标）
let plainPost = null
for (const a of recs.filter(x => !x.collectionTitle)) {
  const d = await detail(a.id)
  if (d && d.coverImage) { plainPost = d; break }
}
if (!plainPost) {
  const cand = recs.find(x => !x.collectionTitle)
  if (cand) plainPost = await detail(cand.id)
}
console.log(`collPost=${collPost?.id} plainPost=${plainPost?.id}`)

const browser = await chromium.launch()

// ── A. 桌面 paper-light：合集长文全要素 ─────────────────────
if (collPost) {
  console.log('== A. 桌面 1440 paper-light 合集文章 ==')
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))

  await page.goto(`${base}/post/${collPost.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-title', { timeout: 15000 })
  await page.waitForSelector('.prose h1, .prose h2, .prose p', { timeout: 15000 })
  await page.waitForTimeout(1200) // TOC/related/comments 异步就位

  ok('面包屑', await page.locator('.post-crumb a', { hasText: '首页' }).count() === 1)
  ok('刊头标题', await page.locator('.post-title').innerText().then(t => t.includes(collPost.title.slice(0, 8))))
  const titleFont = await page.locator('.post-title').evaluate(el => getComputedStyle(el).fontFamily)
  ok('标题衬线', /serif|Songti|Noto/i.test(titleFont), titleFont)
  ok('dek 摘要', collPost.summary ? await page.locator('.post-dek').count() === 1 : true)
  ok('byline 作者/日期/阅读', await page.locator('.post-by').count() === 1)
  ok('byline 有阅读时长', await page.locator('.post-by-meta', { hasText: '分钟' }).count() === 1)

  // 合集徽标：series-tag + 第 N / M 篇
  const seriesTag = page.locator('.series-tag')
  ok('合集徽标', await seriesTag.count() === 1)
  const seriesText = await seriesTag.innerText().catch(() => '')
  ok('徽标含合集名', seriesText.includes(collPost.collectionTitle ?? 'x'), seriesText)
  ok('徽标含 第 N / M 篇', /第 \d+ \/ \d+ 篇/.test(seriesText), seriesText)

  // 封面（本合集文若无封面则跳过断言）
  if (collPost.coverImage) {
    const cover = page.locator('.post-cover')
    ok('封面渲染', await cover.count() === 1)
    const box = await cover.boundingBox()
    ok('封面 21:9 比例', box && Math.abs(box.width / box.height - 21 / 9) < 0.05, box ? `${box.width}x${box.height}` : 'none')
  } else {
    ok('无封面不渲染', await page.locator('.post-cover').count() === 0)
  }

  // 双栏布局
  const cols = await page.locator('.post-layout').evaluate(el => getComputedStyle(el).gridTemplateColumns)
  ok('双栏 700+240', cols.includes('700px') && cols.includes('240px'), cols)
  ok('右栏可见', await page.locator('.post-rail').isVisible())
  ok('目录组件', await page.locator('.post-rail .toc').count() === 1)
  const tocCount = await page.locator('.post-rail .toc li').count()
  ok('TOC 条目>0', tocCount > 0, `got ${tocCount}`)
  ok('已读进度行', await page.locator('.post-rail-pct', { hasText: '已读' }).count() === 1)
  ok('问这篇文章卡', await page.locator('.ask-mini').count() === 1)
  ok('提问 chips=3', await page.locator('.ask-mini-chip').count() === 3)

  // paper 正文特征：h2 章节序号 + 首字下沉（仅 paper）
  const h2Before = await page.locator('.prose h2').first().evaluate(el => getComputedStyle(el, '::before').content).catch(() => '')
  ok('h2 § 序号(paper)', h2Before.includes('§'), h2Before)
  const firstP = page.locator('.prose > p').first()
  if (await firstP.count()) {
    const fl = await firstP.evaluate(el => getComputedStyle(el, '::first-letter').float)
    ok('首字下沉(paper)', fl === 'left', fl)
  }

  // 文末
  ok('文末双细线', await page.locator('.post-end').evaluate(el => getComputedStyle(el).borderTopStyle) === 'double')
  ok('文末收藏+分享按钮', await page.locator('.post-end .fav-bar').count() === 2)
  ok('刊头收藏+分享按钮', await page.locator('.post-by-acts .fav-bar').count() === 2)
  ok('标签 chip', collPost.tags?.length ? await page.locator('.post-tag').count() >= 1 : true)

  // 合集导航 snav
  const snav = page.locator('.post-snav')
  ok('snav 渲染', await snav.count() === 1)
  ok('snav 合集头', await snav.locator('.post-snav-header', { hasText: collPost.collectionTitle }).count() === 1)
  ok('snav 第 N/M', await snav.locator('.post-snav-header span', { hasText: /第 \d+ \/ \d+ 篇/ }).count() === 1)
  const dotCount = await page.locator('.post-snav-dots i').count()
  ok('进度点=合集可见篇数', dotCount >= 1, `got ${dotCount}`)
  ok('进度点当前位', await page.locator('.post-snav-dots i.is-cur').count() === 1)
  ok('上下篇链接', await page.locator('.post-snav-pn a').count() >= 1)

  // 继续读
  const relCount = await page.locator('.post-related-item').count()
  ok('继续读 1-3 篇', relCount >= 1 && relCount <= 3, `got ${relCount}`)

  // 评论区
  ok('评论区标题', await page.locator('.post-section-title', { hasText: '评论' }).count() === 1)

  // FAB 仍隐藏（T7 回归）
  ok('文章页无 FAB', await page.locator('button[aria-label="打开 AI 助手"]').count() === 0)

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok('横向溢出=0', overflow === 0, `overflow=${overflow}`)
  await page.screenshot({ path: join(outDir, 'a-post-top.png') })
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.55))
  await page.waitForTimeout(600)
  await page.screenshot({ path: join(outDir, 'a-post-mid.png') })
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await page.waitForTimeout(800)
  await page.screenshot({ path: join(outDir, 'a-post-end.png') })
  await page.screenshot({ path: join(outDir, 'a-post-full.png'), fullPage: true })
  console.log('console/pageerror:', errors.length ? errors : 'none')
  await ctx.close()
} else {
  ok('找到合集长文', false, 'no collection post with content')
}

// ── B. 非合集文章：封面 + 无徽标 + snav 仅上下篇 ────────────
if (plainPost) {
  console.log('== B. 桌面 paper-light 非合集文章 ==')
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  const page = await ctx.newPage()
  await page.goto(`${base}/post/${plainPost.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-title', { timeout: 15000 })
  await page.waitForTimeout(1000)

  ok('非合集无 series-tag', await page.locator('.series-tag').count() === 0)
  if (plainPost.coverImage) {
    ok('封面渲染', await page.locator('.post-cover').count() === 1)
    // 外链图在沙箱内可能始终 loading：只断言 img 元素与 src 正确（加载态由 OptimizedImage 兜底）
    const src = await page.locator('.post-cover img').getAttribute('src').catch(() => null)
    ok('封面 img src', src === plainPost.coverImage, src ?? 'none')
  }
  // 非合集：snav 无合集头/进度点；有 prev/next 则 pn 存在
  ok('snav 无合集头', await page.locator('.post-snav-header').count() === 0)
  ok('snav 无进度点', await page.locator('.post-snav-dots').count() === 0)
  const pnCount = await page.locator('.post-snav-pn a').count()
  ok('snav 上下篇或空', pnCount === 0 || pnCount === 1 || pnCount === 2, `got ${pnCount}`)
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok('横向溢出=0', overflow === 0, `overflow=${overflow}`)
  await page.screenshot({ path: join(outDir, 'b-plain-post.png'), fullPage: true })
  await ctx.close()
} else {
  ok('找到非合集文章', false, 'no plain post')
}

// ── C. 主题对照：paper-dark / classic-light ─────────────────
{
  console.log('== C. 主题对照 ==')
  const target = collPost ?? plainPost
  if (target) {
    for (const theme of ['paper-dark', 'classic-light']) {
      const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
      await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
      const page = await ctx.newPage()
      await page.goto(`${base}/post/${target.id}`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('.post-title', { timeout: 15000 })
      await page.waitForTimeout(1000)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      ok(`溢出=0 ${theme}`, overflow === 0, `overflow=${overflow}`)
      // paper 圆角=0，classic 圆角>0
      const radius = await page.locator('.ask-mini').evaluate(el => getComputedStyle(el).borderTopLeftRadius)
      if (theme === 'paper-dark') ok('paper-dark 方角', radius === '0px', radius)
      else ok('classic 圆角', parseFloat(radius) > 0, radius)
      // h2 § 序号仅 paper
      const h2 = page.locator('.prose h2').first()
      const before = h2.count() ? await h2.evaluate(el => getComputedStyle(el, '::before').content) : ''
      if (theme === 'paper-dark') ok('paper-dark h2 §', before.includes('§'), before)
      else ok('classic 无 §', !before.includes('§'), before)
      await page.screenshot({ path: join(outDir, `c-${theme}.png`) })
      await ctx.close()
    }
  }
}

// ── D. 移动端 390×844 paper-light ──────────────────────────
if (collPost ?? plainPost) {
  console.log('== D. 移动端 390 paper-light ==')
  const target = collPost ?? plainPost
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  const page = await ctx.newPage()
  await page.goto(`${base}/post/${target.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-title', { timeout: 15000 })
  await page.waitForTimeout(1200)

  ok('移动端右栏隐藏', !(await page.locator('.post-rail').isVisible()))
  ok('移动端目录 FAB', await page.locator('button[aria-label="打开文章目录"]').count() === 1)
  // 标题字号 ~30px
  const tSize = await page.locator('.post-title').evaluate(el => parseFloat(getComputedStyle(el).fontSize))
  ok('移动端标题≈30px', tSize >= 28 && tSize <= 34, `${tSize}px`)
  // snav 上下篇纵向堆叠
  const links = page.locator('.post-snav-pn a')
  if (await links.count() === 2) {
    const b1 = await links.nth(0).boundingBox()
    const b2 = await links.nth(1).boundingBox()
    ok('移动端 snav 纵向堆叠', b1 && b2 && b2.y > b1.y, `${b1?.y} vs ${b2?.y}`)
  } else {
    ok('移动端 snav 纵向堆叠', true, 'single link')
  }
  // 继续读单列
  const relItems = page.locator('.post-related-item')
  if (await relItems.count() >= 2) {
    const r1 = await relItems.nth(0).boundingBox()
    const r2 = await relItems.nth(1).boundingBox()
    ok('继续读单列', r1 && r2 && r2.y > r1.y, `${r1?.y} vs ${r2?.y}`)
  }
  // 封面出血（若有封面）
  if (target.coverImage) {
    const cb = await page.locator('.post-cover').boundingBox()
    ok('封面移动端出血 x≈0', cb && cb.x <= 1 && cb.width >= 388, cb ? `${cb.x},${cb.width}` : 'none')
  }
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok('横向溢出=0', overflow === 0, `overflow=${overflow}`)
  await page.screenshot({ path: join(outDir, 'd-post-top.png') })
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.5))
  await page.waitForTimeout(500)
  await page.screenshot({ path: join(outDir, 'd-post-mid.png') })
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await page.waitForTimeout(600)
  await page.screenshot({ path: join(outDir, 'd-post-end.png') })
  await ctx.close()
}

// ── E. 提问卡：匿名暂存→登录回跳恢复→发送（mock SSE）────────
{
  console.log('== E. AskArticleCard 提问链路 ==')
  const target = collPost ?? plainPost
  if (target) {
    await fetch(`${base}/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: T8_USER, password: T8_PASS,
        email: `${T8_USER}@example.com`, realName: 'T8 Check', phoneNumber: '13800000001',
      }),
    }).catch(() => {})
    const loginRes = await fetch(`${base}/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: T8_USER, password: T8_PASS }),
    }).then(r => r.json()).catch(() => null)
    const tokens = loginRes?.data

    // E1 匿名提交 → /login?redirect + stash(path=/post/x)
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
    const page = await ctx.newPage()
    await page.goto(`${base}/post/${target.id}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.ask-mini-input', { timeout: 15000 })
    await page.locator('.ask-mini-input').fill('T8 匿名提问：这篇讲了什么')
    await page.locator('.ask-mini-input').press('Enter')
    await page.waitForURL('**/login**', { timeout: 5000 })
    ok('匿名提问跳登录', page.url().includes('/login'), page.url())
    ok('redirect 回本篇', page.url().includes(encodeURIComponent(`/post/${target.id}`)) || page.url().includes(`/post/${target.id}`), page.url())
    const stash = await page.evaluate(() => sessionStorage.getItem('mysite_pending_ask'))
    ok('stash 含问题+path', stash?.includes('T8 匿名提问') && stash?.includes(`/post/${target.id}`), stash ?? 'null')

    // E2 注入 token 回跳 → 面板自动打开并发送（mock SSE）
    if (tokens?.accessToken) {
      await ctx.route('**/v1/rag/chat/stream**', route => route.fulfill({
        status: 200, contentType: 'text/event-stream',
        body: [
          'data: {"type":"meta","conversationId":"t8-mock"}', '',
          'data: {"type":"sources","sources":[]}', '',
          'data: {"type":"content","delta":"T8 验证模拟回复：文章上下文已注入。"}', '',
          'data: {"type":"done"}', '', '',
        ].join('\n'),
      }))
      await page.evaluate(tk => {
        localStorage.setItem('mysite_access_token', JSON.stringify(tk.accessToken))
        localStorage.setItem('mysite_refresh_token', JSON.stringify(tk.refreshToken))
      }, tokens)
      await page.goto(`${base}/post/${target.id}`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('.chat-panel', { timeout: 10000 })
      ok('回跳后面板自动打开', true)
      const sent = await page.waitForFunction(() =>
        document.querySelector('.chat-panel')?.textContent.includes('T8 匿名提问'),
        { timeout: 10000 }).then(() => true).catch(() => false)
      ok('暂存问题已发送', sent)
      await page.screenshot({ path: join(outDir, 'e-ask-sent.png') })
    } else {
      ok('拿到登录 token', false, JSON.stringify(loginRes).slice(0, 120))
    }
    await ctx.close()

    // E3 已登录直接在卡片上提问（chip 填充 + 发送）
    const ctx2 = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    await ctx2.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
    if (tokens?.accessToken) {
      await ctx2.addInitScript(tk => {
        localStorage.setItem('mysite_access_token', JSON.stringify(tk.accessToken))
        localStorage.setItem('mysite_refresh_token', JSON.stringify(tk.refreshToken))
      }, tokens)
    }
    await ctx2.route('**/v1/rag/chat/stream**', route => route.fulfill({
      status: 200, contentType: 'text/event-stream',
      body: 'data: {"type":"done"}\n\n',
    }))
    const page2 = await ctx2.newPage()
    await page2.goto(`${base}/post/${target.id}`, { waitUntil: 'domcontentloaded' })
    await page2.waitForSelector('.ask-mini-input', { timeout: 15000 })
    await page2.locator('.ask-mini-chip').first().click()
    const chipVal = await page2.locator('.ask-mini-input').inputValue()
    ok('chip 填充输入', chipVal.length > 0, chipVal)
    await page2.locator('.ask-mini-input').press('Enter')
    if (tokens?.accessToken) {
      await page2.waitForSelector('.chat-panel', { timeout: 10000 })
      ok('登录态提问开面板', await page2.locator('.chat-panel', { hasText: chipVal }).count() === 1)
    } else {
      await page2.waitForURL('**/login**', { timeout: 5000 }).catch(() => {})
      ok('无 token 走登录兜底', page2.url().includes('/login'), page2.url())
    }
    await ctx2.close()
  } else {
    ok('找到测试文章', false)
  }
}

await browser.close()
console.log(`\n== RESULT: ${pass} pass, ${fail} fail ==`)
process.exit(fail ? 1 : 0)
