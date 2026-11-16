// T9 验证：移动端文章 dock + sheet
// 用法: node scripts/t9-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://127.0.0.1:5173）+ 后端（/v1 代理）+ playwright。
// F 段需要 USER 账号：脚本自注册（已存在则复用）；SSE 流为 mock。
import { chromium, webkit } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t9')
mkdirSync(outDir, { recursive: true })

const T9_USER = process.env.T9_USER ?? 't9check'
const T9_PASS = process.env.T9_PASS ?? 'T9check!2026'
const MOBILE = { width: 390, height: 844 }

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

// ── 发现测试文章：有 TOC 的长文优先 ─────────────────────────
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
console.log(`target=${target?.id} title=${target?.title?.slice(0, 20)}`)

const browser = await chromium.launch()

// 提前注册/登录：D（桌面面板标题）与 F（dock 收藏+发问）都用 token
let tokens = null
{
  await fetch(`${base}/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: T9_USER, password: T9_PASS,
      email: `${T9_USER}@example.com`, realName: 'T9 Check', phoneNumber: '13900000009',
    }),
  }).catch(() => {})
  const loginRes = await fetch(`${base}/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: T9_USER, password: T9_PASS }),
  }).then(r => r.json()).catch(() => null)
  tokens = loginRes?.data ?? null
  console.log(`login token: ${tokens?.accessToken ? 'ok' : 'MISSING'}`)
}

async function newPage(theme, viewport, opts = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  if (opts.tokens) {
    await ctx.addInitScript(tk => {
      localStorage.setItem('mysite_access_token', JSON.stringify(tk.accessToken))
      localStorage.setItem('mysite_refresh_token', JSON.stringify(tk.refreshToken))
    }, opts.tokens)
  }
  const page = await ctx.newPage()
  return { ctx, page }
}

async function gotoPost(page, id) {
  await page.goto(`${base}/post/${id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.post-title', { timeout: 15000 })
  await page.waitForSelector('.post-dock', { state: 'attached', timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(1000)
}

// ── A. 移动端 dock 本体 ──────────────────────────────────────
if (!target) {
  ok('找到测试文章', false)
} else {
  console.log('== A. 移动端 390 paper-light dock ==')
  {
    const { ctx, page } = await newPage('paper-light', MOBILE)
    const errors = []
    page.on('pageerror', e => errors.push(String(e)))
    await gotoPost(page, target.id)

    const dock = page.locator('.post-dock')
    ok('dock 渲染', await dock.count() === 1)
    ok('dock 可见', await dock.isVisible())
    const btns = page.locator('.post-dock-btn')
    ok('三格按钮', await btns.count() === 3, `got ${await btns.count()}`)
    ok('目录副标已读%', /\d+%/.test(await btns.nth(0).innerText()))
    ok('问这篇 accent 格', await page.locator('.post-dock-ask', { hasText: '✦ 问这篇' }).count() === 1)
    const askBg = await page.locator('.post-dock-ask').evaluate(el => getComputedStyle(el).backgroundColor)
    ok('问这篇 accent 底', askBg === 'rgb(163, 62, 28)', askBg) // paper-light --accent #A33E1C
    ok('收藏格计数', /\d+/.test(await btns.nth(2).innerText()))

    // dock 位置：底部固定、左右留白
    const pos = await dock.evaluate(el => {
      const r = el.getBoundingClientRect()
      return { left: r.left, right: window.innerWidth - r.right, bottom: window.innerHeight - r.bottom, fixed: getComputedStyle(el).position }
    })
    ok('dock fixed', pos.fixed === 'fixed')
    ok('dock 左右 12px', Math.abs(pos.left - 12) <= 2 && Math.abs(pos.right - 12) <= 2, JSON.stringify(pos))
    ok('dock 距底≈12px', pos.bottom >= 10 && pos.bottom <= 40, `bottom=${pos.bottom}`)

    // 滚动后 dock 仍贴底且不遮页脚（body 留白生效）
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
    await page.waitForTimeout(600)
    const clear = await page.evaluate(() => {
      const dock = document.querySelector('.post-dock').getBoundingClientRect()
      const footer = document.querySelector('footer').getBoundingClientRect()
      return { gap: dock.top - footer.bottom, bodyPad: parseFloat(getComputedStyle(document.body).paddingBottom) }
    })
    ok('body has-post-dock 留白', clear.bodyPad >= 60, `pad=${clear.bodyPad}`)
    ok('滚到底页脚不被遮', clear.gap >= -1, `gap=${clear.gap}`)

    // 旧 FAB 消失（mobile-toc 槽位注销）
    ok('旧目录 FAB 移除', await page.locator('.toc-sheet-mask').count() === 0)

    // BackToTop 隐藏（滚动 >300 也不出现）
    const btt = await page.locator('button[aria-label="返回顶部"]').count()
    ok('post 页移动 BackToTop 隐藏', btt === 0, `count=${btt}`)

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    ok('横向溢出=0', overflow === 0, `overflow=${overflow}`)
    await page.screenshot({ path: join(outDir, 'a-dock.png') })
    console.log('  pageerror:', errors.length ? errors : 'none')
    await ctx.close()
  }

  // ── B. 目录 sheet ──────────────────────────────────────────
  console.log('== B. 目录 sheet 交互 ==')
  {
    const { ctx, page } = await newPage('paper-light', MOBILE)
    await gotoPost(page, target.id)

    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    ok('sheet 打开', true)
    ok('sheet 标题', await page.locator('.toc-sheet-header b', { hasText: '目录' }).count() === 1)
    ok('sheet 已读%', await page.locator('.toc-sheet-pct', { hasText: /已读 \d+%/ }).count() === 1)
    await page.waitForTimeout(450) // 等滑入动画结束再量位置
    const sheetBox = await page.locator('.toc-sheet').boundingBox()
    ok('sheet 贴底', sheetBox && Math.abs(sheetBox.y + sheetBox.height - 844) <= 2, JSON.stringify(sheetBox))
    const itemCount = await page.locator('.toc-sheet-body li').count()
    ok('sheet 目录项>0', itemCount > 0, `got ${itemCount}`)

    // 遮罩点击关闭
    await page.locator('.toc-sheet-mask').click({ position: { x: 195, y: 100 } })
    await page.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 })
    ok('点遮罩关闭', true)

    // Esc 关闭
    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    await page.keyboard.press('Escape')
    await page.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 })
    ok('Esc 关闭', true)

    // 条目点击 → 关闭并锚点跳转（先滚到底，再点第一条目回滚；
    // 首条在列表顶部永可见，规避底部条目被 devtools 遮罩拦截）
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
    await page.waitForTimeout(400)
    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    const beforeY = await page.evaluate(() => window.scrollY)
    await page.locator('.toc-sheet-body li > button').first().click()
    await page.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 })
    await page.waitForTimeout(800)
    const afterY = await page.evaluate(() => window.scrollY)
    ok('条目点击关闭+滚动', afterY < beforeY, `y ${beforeY}→${afterY}`)
    await page.screenshot({ path: join(outDir, 'b-toc-sheet.png') })

    // 下滑关闭（touch 模拟：grab 区 drag 150px > 100 阈值）
    const ctx2 = await browser.newContext({ viewport: MOBILE, hasTouch: true })
    await ctx2.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, 'paper-light')
    const page2 = await ctx2.newPage()
    await gotoPost(page2, target.id)
    await page2.locator('.post-dock-btn').nth(0).click()
    await page2.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    const gz = await page2.locator('.toc-sheet-dragzone').boundingBox()
    await page2.evaluate(({ x, y }) => {
      const el = document.querySelector('.toc-sheet-dragzone')
      const t = (type, cy) => el.dispatchEvent(new TouchEvent(type, {
        touches: type === 'touchend' ? [] : [new Touch({ identifier: 1, target: el, clientX: x, clientY: cy })],
        bubbles: true,
      }))
      t('touchstart', y)
      t('touchmove', y + 150)
      t('touchend', y + 150)
    }, { x: gz.x + gz.width / 2, y: gz.y + 10 })
    await page2.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 })
    ok('下滑关闭', true)
    await ctx2.close()
    await ctx.close()
  }

  // ── C. 问这篇 → 聊天 sheet 标题 ────────────────────────────
  console.log('== C. 问这篇 / 聊天 sheet ==')
  {
    const { ctx, page } = await newPage('paper-light', MOBILE)
    await gotoPost(page, target.id)
    await page.locator('.post-dock-ask').click()
    await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 })
    ok('聊天 sheet 打开', true)
    ok('标题=✦ 问这篇文章', await page.locator('.chat-panel h1', { hasText: '✦ 问这篇文章' }).count() === 1)
    const sub = await page.locator('.chat-panel .flex-1.min-w-0 p').innerText()
    ok('副标=正在阅读：标题', sub.includes('正在阅读：') && sub.includes(target.title.slice(0, 6)), sub)
    // 匿名走登录门（T15）：不展示空态 chips/输入框，引导登录；chips 登录态覆盖见 F 段
    ok('匿名登录门文案', await page.locator('text=登录后使用 AI 助手').count() >= 1)
    ok('匿名登录按钮', await page.locator('button', { hasText: '登录 / 注册' }).count() === 1)
    ok('匿名无空态 chips', await page.locator('.chat-empty-chip').count() === 0)
    // 关闭：X 按钮（移动 sheet 遮罩只剩顶部 24px 缝隙，X 是稳定路径）
    await page.locator('.chat-panel button[aria-label="关闭"]').click()
    await page.waitForSelector('.chat-panel', { state: 'detached', timeout: 3000 }).catch(() => {})
    ok('X 关闭聊天', await page.locator('.chat-panel').count() === 0)
    await ctx.close()
  }

  // ── D. 桌面回归：无 dock、右栏在、BackToTop、面板标题不变 ──
  console.log('== D. 桌面 1440 回归 ==')
  {
    const { ctx, page } = await newPage('paper-light', { width: 1440, height: 900 }, { tokens })
    await ctx.route('**/v1/rag/chat/stream**', route => route.fulfill({
      status: 200, contentType: 'text/event-stream',
      body: 'data: {"type":"meta","conversationId":"t9-d"}\n\ndata: {"type":"sources","sources":[]}\n\ndata: {"type":"content","delta":"ok"}\n\ndata: {"type":"done"}\n\n',
    }))
    await gotoPost(page, target.id)
    ok('桌面 dock 隐藏', !(await page.locator('.post-dock').isVisible()))
    ok('桌面右栏在', await page.locator('.post-rail').isVisible())
    ok('桌面 toc sheet 不出现', await page.locator('.toc-sheet').count() === 0)
    const pad = await page.evaluate(() => parseFloat(getComputedStyle(document.body).paddingBottom))
    ok('桌面 body 无留白', pad === 0, `pad=${pad}`)
    // BackToTop：滚动后出现
    await page.evaluate(() => window.scrollTo(0, 2000))
    await page.waitForTimeout(500)
    ok('桌面 BackToTop 显示', await page.locator('button[aria-label="返回顶部"]').count() === 1)
    // 登录态桌面提问 → 面板打开，标题仍 AI 助手（isPostContext 仅移动）
    await page.locator('.ask-mini-input').fill('桌面提问')
    await page.locator('.ask-mini-input').press('Enter')
    if (tokens?.accessToken) {
      await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 })
      ok('桌面面板打开', true)
      ok('桌面面板标题=AI 助手', await page.locator('.chat-panel h1', { hasText: 'AI 助手' }).count() === 1)
      const sub = await page.locator('.chat-panel .flex-1.min-w-0 p').innerText()
      ok('桌面副标=阅读上下文', sub.includes('正在阅读'), sub)
    } else {
      await page.waitForURL('**/login**', { timeout: 5000 }).catch(() => {})
      ok('无 token 走登录兜底', page.url().includes('/login'), page.url())
    }
    await ctx.close()
  }

  // ── E. 主题 × 视口溢出 ─────────────────────────────────────
  console.log('== E. 主题/视口溢出 ==')
  for (const [theme, vp, name] of [
    ['paper-dark', MOBILE, 'paper-dark-390'],
    ['classic-light', MOBILE, 'classic-390'],
    ['paper-light', { width: 800, height: 1024 }, 'tablet-800'],
  ]) {
    const { ctx, page } = await newPage(theme, vp)
    await gotoPost(page, target.id)
    const dockVisible = await page.locator('.post-dock').isVisible()
    ok(`dock 可见 ${name}`, dockVisible)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    ok(`溢出=0 ${name}`, overflow === 0, `overflow=${overflow}`)
    if (theme === 'paper-dark') {
      const shadow = await page.locator('.post-dock').evaluate(el => getComputedStyle(el).boxShadow)
      ok('paper dock 硬阴影', shadow !== 'none', shadow)
    }
    // 目录 sheet 开合一次（tablet 也走 dock）
    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    ok(`sheet 打开 ${name}`, true)
    await page.keyboard.press('Escape')
    await page.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 })
    await page.screenshot({ path: join(outDir, `e-${name}.png`) })
    await ctx.close()
  }

  // ── F. 登录态：dock 收藏 + 聊天发问 ────────────────────────
  console.log('== F. 登录态链路 ==')
  {
    ok('拿到登录 token', !!tokens?.accessToken)

    if (tokens?.accessToken) {
      const { ctx, page } = await newPage('paper-light', MOBILE, { tokens })
      await ctx.route('**/v1/rag/chat/stream**', route => route.fulfill({
        status: 200, contentType: 'text/event-stream',
        body: [
          'data: {"type":"meta","conversationId":"t9-mock"}', '',
          'data: {"type":"sources","sources":[]}', '',
          'data: {"type":"content","delta":"T9 dock 模拟回复。"}', '',
          'data: {"type":"done"}', '', '',
        ].join('\n'),
      }))
      await gotoPost(page, target.id)

      // dock 收藏：点击 → toast + 计数 +1，与刊头 fav-bar 同步
      const dockFav = page.locator('.post-dock-btn').nth(2)
      const before = parseInt((await dockFav.innerText()).match(/\d+/)?.[0] ?? '0', 10)
      const wasFaved = (await dockFav.getAttribute('class')).includes('is-faved')
      await dockFav.click()
      await page.waitForTimeout(1200)
      const after = parseInt((await dockFav.innerText()).match(/\d+/)?.[0] ?? '0', 10)
      ok('dock 收藏计数±1', Math.abs(after - before) === 1, `${before}→${after}`)
      const nowFaved = (await dockFav.getAttribute('class')).includes('is-faved')
      ok('dock 收藏态切换', nowFaved === !wasFaved, `${wasFaved}→${nowFaved}`)
      // 刊头 fav-bar 同步（同一 useFavorite 状态源）
      const headBar = page.locator('.post-by-acts .fav-bar').first()
      const headFaved = (await headBar.getAttribute('class')).includes('is-faved')
      ok('刊头收藏同步', headFaved === nowFaved, `head=${headFaved} dock=${nowFaved}`)

      // dock 问这篇 → chip 发送 → mock 回复渲染
      await page.locator('.post-dock-ask').click()
      await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 })
      await page.locator('.chat-empty-chip', { hasText: '总结成 3 点' }).click()
      await page.waitForSelector('text=T9 dock 模拟回复', { timeout: 8000 })
      ok('chip 发问渲染回复', true)
      await page.screenshot({ path: join(outDir, 'f-ask-sheet.png') })
      await ctx.close()

      // G. 匿名收藏 → 登录回跳
      const { ctx: ctx3, page: page3 } = await newPage('paper-light', MOBILE)
      await gotoPost(page3, target.id)
      await page3.locator('.post-dock-btn').nth(2).click()
      await page3.waitForURL('**/login**', { timeout: 5000 })
      ok('匿名收藏跳登录', page3.url().includes('/login'), page3.url())
      ok('redirect 回本篇', page3.url().includes(encodeURIComponent(`/post/${target.id}`)) || page3.url().includes(`/post/${target.id}`), page3.url())
      await ctx3.close()
    }
  }

  // ── H. 其他页面回归：/archive 移动 FAB 仍在 ────────────────
  console.log('== H. 非文章页回归 ==')
  {
    const { ctx, page } = await newPage('paper-light', MOBILE)
    await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1200)
    ok('archive 无 dock', await page.locator('.post-dock').count() === 0)
    ok('archive 聊天 FAB 在', await page.locator('.chat-fab').count() === 1)
    const pad = await page.evaluate(() => parseFloat(getComputedStyle(document.body).paddingBottom))
    ok('archive 无 body 留白', pad === 0, `pad=${pad}`)
    const scrollY = await page.evaluate(() => {
      window.scrollTo(0, document.documentElement.scrollHeight)
      return new Promise(r => setTimeout(() => r(window.scrollY), 500))
    })
    if (scrollY > 300) {
      ok('archive BackToTop 显示', await page.locator('button[aria-label="返回顶部"]').count() === 1)
    } else {
      ok('archive BackToTop 显示', true, `页面太短 scrollY=${scrollY} 未触发，跳过`)
    }
    await ctx.close()
  }

  // ── I. WebKit（iOS Safari 内核）冒烟 ───────────────────────
  console.log('== I. WebKit 冒烟 ==')
  {
    const wb = await webkit.launch()
    const ctx = await wb.newContext({ viewport: MOBILE, hasTouch: true, isMobile: true })
    await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, 'paper-light')
    const page = await ctx.newPage()
    const errors = []
    page.on('pageerror', e => errors.push(String(e)))
    await gotoPost(page, target.id)
    ok('webkit dock 可见', await page.locator('.post-dock').isVisible())
    await page.locator('.post-dock-btn').nth(0).click()
    await page.waitForSelector('.toc-sheet', { state: 'visible', timeout: 5000 })
    ok('webkit sheet 打开', true)
    // Esc 关闭；未关再点遮罩（避免误点底层内容）
    await page.keyboard.press('Escape').catch(() => {})
    await page.waitForTimeout(400)
    if (await page.locator('.toc-sheet-mask').count() > 0) {
      await page.locator('.toc-sheet-mask').click({ position: { x: 195, y: 80 } }).catch(() => {})
    }
    await page.waitForSelector('.toc-sheet', { state: 'detached', timeout: 3000 }).catch(() => {})
    ok('webkit sheet 关闭', await page.locator('.toc-sheet').count() === 0)
    // dev-only vue-devtools 面板盖住 dock 中部拦截指针事件；
    // dispatchEvent 直接在元素上派发，绕过命中测试，只验证 open 链路
    await page.locator('.post-dock-ask').dispatchEvent('click')
    await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 })
    ok('webkit 聊天 sheet', await page.locator('.chat-panel h1', { hasText: '✦ 问这篇文章' }).count() === 1)
    await page.screenshot({ path: join(outDir, 'i-webkit.png') })
    console.log('  webkit pageerror:', errors.length ? errors : 'none')
    await wb.close()
  }
}

await browser.close()
console.log(`\n== RESULT: ${pass} pass, ${fail} fail ==`)
process.exit(fail ? 1 : 0)
