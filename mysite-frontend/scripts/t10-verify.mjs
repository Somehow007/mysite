// T10 验证：选中文字问 AI（SelectionPopover）
// 用法: node scripts/t10-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://localhost:5173）+ 后端（/v1 代理）。
// C/D/E/F 段需要登录：脚本自注册 t10check 账号；SSE 不发送（只验证面板/引用条/聚焦）。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t10')
mkdirSync(outDir, { recursive: true })

const T10_USER = process.env.T10_USER ?? 't10check'
const T10_PASS = process.env.T10_PASS ?? 'T10check!2026'
const DESKTOP = { width: 1440, height: 900 }

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}
const sleep = ms => new Promise(r => setTimeout(r, ms))

// ── 发现测试文章：长文 + 含代码块优先 ─────────────────────────
async function apiGet(path) {
  const r = await fetch(`${base}${path}`)
  return r.json()
}
const listJson = await apiGet('/v1/articles?current=1&size=50&published=1')
const recs = listJson?.data?.records ?? listJson?.data?.list ?? []

let target = null
for (const a of recs) {
  const j = await apiGet(`/v1/articles/${a.id}`)
  const d = j?.data
  if (d && (d.content || '').length > 5000 && (d.content || '').includes('```')) {
    target = d
    break
  }
}
if (!target && recs.length) {
  const j = await apiGet(`/v1/articles/${recs[0].id}`)
  target = j?.data ?? null
}
console.log(`target=${target?.id} title=${target?.title?.slice(0, 24)}`)
if (!target) {
  console.log('no article, abort')
  process.exit(1)
}
const postUrl = `${base}/post/${target.id}`

const browser = await chromium.launch()

// 注册/登录（已存在则复用）
let tokens = null
{
  await fetch(`${base}/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: T10_USER, password: T10_PASS,
      email: `${T10_USER}@example.com`, realName: 'T10 Check', phoneNumber: '13900000010',
    }),
  }).catch(() => {})
  const loginRes = await fetch(`${base}/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: T10_USER, password: T10_PASS }),
  }).then(r => r.json()).catch(() => null)
  tokens = loginRes?.data ?? null
  console.log(`login token: ${tokens?.accessToken ? 'ok' : 'MISSING'}`)
}

async function newPage(theme = 'paper-light', viewport = DESKTOP, opts = {}) {
  const ctx = await browser.newContext({
    viewport,
    permissions: opts.clipboard ? ['clipboard-read', 'clipboard-write'] : [],
    hasTouch: !!opts.touch,
    isMobile: !!opts.touch,
  })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  if (opts.tokens) {
    await ctx.addInitScript(tk => {
      localStorage.setItem('mysite_access_token', JSON.stringify(tk.accessToken))
      localStorage.setItem('mysite_refresh_token', JSON.stringify(tk.refreshToken))
    }, opts.tokens)
  }
  const page = await ctx.newPage()
  page.errors = []
  page.on('pageerror', e => page.errors.push(String(e)))
  return { ctx, page }
}

async function gotoPost(page) {
  await page.goto(postUrl, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose', { timeout: 20000 })
  await page.waitForSelector('.post-title', { timeout: 15000 }).catch(() => {})
  await sleep(800)
}

// 在页面里构造选区；返回 { sample, len } 或 { error }
async function makeSelection(page, mode) {
  return page.evaluate((m) => {
    const prose = document.querySelector('.prose')
    const sel = window.getSelection()
    sel.removeAllRanges()
    const r = document.createRange()
    const textNodes = (root) => {
      const out = []
      const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
      let n
      while ((n = w.nextNode())) if (n.textContent.trim()) out.push(n)
      return out
    }
    const finish = () => {
      // 选区起点滚进视口：popover 对视口外选区正确隐藏，测试需保证目标可见
      const startEl = r.startContainer.nodeType === 1
        ? r.startContainer
        : r.startContainer.parentElement
      startEl?.scrollIntoView({ block: 'center', behavior: 'instant' })
      sel.addRange(r)
      const t = sel.toString().replace(/\r\n?/g, '\n').trim()
      return { sample: t.replace(/\s+/g, ' ').slice(0, 50), len: t.length }
    }
    if (m === 'title') {
      const h = document.querySelector('.post-title')
      if (!h) return { error: 'no title' }
      r.selectNodeContents(h)
      return finish()
    }
    if (m === 'code') {
      const code = prose.querySelector('pre code')
      if (!code) return { error: 'no code' }
      const ns = textNodes(code).filter(n => n.textContent.trim().length >= 20)
      const t = ns[0]
      if (!t) return { error: 'no code text' }
      r.setStart(t, 0)
      r.setEnd(t, Math.min(40, t.textContent.length))
      return finish()
    }
    const ps = [...prose.querySelectorAll('p')].filter(p => p.textContent.trim().length > 8)
    if (!ps.length) return { error: 'no paragraphs' }
    if (m === 'short') {
      const t = textNodes(ps[0])[0]
      r.setStart(t, 0)
      r.setEnd(t, Math.min(3, t.textContent.length))
      return finish()
    }
    if (m === 'first-p') {
      // 找第一个足够长的文本节点，避免 setEnd 越界
      let t = null
      for (const p of ps) {
        for (const n of textNodes(p)) {
          if (n.textContent.trim().length >= 40) { t = n; break }
        }
        if (t) break
      }
      if (!t) return { error: 'no long text node' }
      r.setStart(t, 2)
      r.setEnd(t, 32)
      return finish()
    }
    // cross / long：跨段选择
    const first = textNodes(ps[0])[0]
    let lastP = ps[Math.min(2, ps.length - 1)]
    if (m === 'long') {
      // 累计段落直到 >540 字
      let acc = 0
      for (let i = 0; i < ps.length; i++) {
        acc += ps[i].textContent.trim().length
        lastP = ps[i]
        if (acc > 540) break
      }
    }
    const lns = textNodes(lastP)
    const last = lns[lns.length - 1]
    r.setStart(first, 0)
    r.setEnd(last, last.textContent.length)
    return finish()
  }, mode)
}

const popVisible = page => page.locator('.sel-pop').isVisible().catch(() => false)
const popCount = page => page.locator('.sel-pop-btn').count()

async function popGeometry(page) {
  return page.evaluate(() => {
    const pop = document.querySelector('.sel-pop')
    if (!pop) return null
    const r = pop.getBoundingClientRect()
    const sel = window.getSelection()
    let s = null
    if (sel && sel.rangeCount > 0 && !sel.isCollapsed) {
      const rects = [...sel.getRangeAt(0).getClientRects()].filter(x => x.width > 0 && x.height > 0)
      if (rects.length) {
        const f = rects[0]
        s = { top: f.top, bottom: f.bottom, left: f.left, right: f.right }
      }
    }
    return {
      pop: { top: r.top, bottom: r.bottom, left: r.left, right: r.right, w: r.width, h: r.height },
      sel: s,
      vw: window.innerWidth,
      vh: window.innerHeight,
    }
  })
}

// ══ A. 核心：出现 / 位置 / 跨段 / 代码块 ═══════════════════════
console.log('== A. popover 出现与定位（匿名 paper-light 桌面）==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)

  // A1 普通段落选区 → popover 出现
  const s1 = await makeSelection(page, 'first-p')
  ok('选区样本文本', s1.len >= 5, JSON.stringify(s1))
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok('popover 出现', await popVisible(page))
  const btnCount = await popCount(page)
  ok('三按钮（问AI/引用/复制）', btnCount === 3, `got ${btnCount}`)

  // A2 位置：选区上方 + 视口内
  const g = await popGeometry(page)
  ok('popover 在视口内', g && g.pop.left >= 0 && g.pop.right <= g.vw && g.pop.top >= 0 && g.pop.bottom <= g.vh, JSON.stringify(g?.pop))
  const above = g.sel && g.pop.bottom <= g.sel.top + 2
  const below = g.sel && g.pop.top >= g.sel.bottom - 2
  ok('popover 在选区上方（或空间不足翻下）', !!(above || below), JSON.stringify({ pop: g?.pop, sel: g?.sel }))

  // A3 横向居中于选区附近（中心误差 < 200px）
  if (g.sel) {
    const pcx = (g.pop.left + g.pop.right) / 2
    const scx = (g.sel.left + g.sel.right) / 2
    ok('popover 横向跟随选区', Math.abs(pcx - scx) < 220, `pcx=${pcx.toFixed(0)} scx=${scx.toFixed(0)}`)
  }

  // A4 paper 主题方角 + 硬阴影
  const style = await page.locator('.sel-pop').evaluate(el => {
    const c = getComputedStyle(el)
    return { r: c.borderTopLeftRadius, shadow: c.boxShadow }
  })
  ok('paper 方角', style.r === '0px', style.r)
  ok('paper 硬阴影', !/0px 1px 3px/.test(style.shadow) || style.shadow.includes('6px'), style.shadow.slice(0, 60))

  // A5 跨段选择 → 出现
  await makeSelection(page, 'cross')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok('跨段选择出现', await popVisible(page))

  // A6 代码块内选择 → 出现（先滚进视口，否则选区在视口外 popover 正确隐藏）
  const sc = await page.evaluate(() => {
    const code = document.querySelector('.prose pre code')
    code?.scrollIntoView({ block: 'center', behavior: 'instant' })
    return !!code
  })
  if (!sc) {
    ok('代码块选择（无代码则跳过）', true, 'no code')
  } else {
    await sleep(300)
    const sc2 = await makeSelection(page, 'code')
    if (sc2.error) {
      ok('代码块选择（无代码文本跳过）', true, sc2.error)
    } else {
      await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
      ok('代码块内选择出现', await popVisible(page))
    }
  }

  // A7 <5 字 → 不出现
  await makeSelection(page, 'short')
  await sleep(500)
  ok('<5 字不出现', !(await popVisible(page)))

  // A8 >500 字 → 不出现
  const sl = await makeSelection(page, 'long')
  ok('long 选区 >500 字', sl.len > 500, `len=${sl.len}`)
  await sleep(500)
  ok('>500 字不出现', !(await popVisible(page)))

  // A9 .prose 外（标题）→ 不出现
  await makeSelection(page, 'title')
  await sleep(500)
  ok('.prose 外选区不出现', !(await popVisible(page)))

  // A10 Esc 关闭（选区清除 + popover 隐藏）
  await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok('Esc 前 popover 可见', await popVisible(page))
  await page.keyboard.press('Escape')
  await sleep(300)
  ok('Esc 后隐藏', !(await popVisible(page)))
  const selCleared = await page.evaluate(() => window.getSelection().isCollapsed)
  ok('Esc 后选区折叠', selCleared)

  // A11 选区滚出视口 → 隐藏；滚回 → 重现（instant 滚动避免 smooth 动画时序干扰；
  // 选区在屏幕中央，向下滚一屏将其推出上视口）
  await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok('A11 前置 popover 可见', await popVisible(page))
  await page.evaluate(() => {
    window.scrollBy({ top: window.innerHeight, behavior: 'instant' })
  })
  await sleep(600)
  const outRect = await page.evaluate(() => {
    const sel = window.getSelection()
    if (!sel.rangeCount) return null
    const r = [...sel.getRangeAt(0).getClientRects()].find(x => x.width > 0)
    return r ? { top: r.top, bottom: r.bottom, vh: window.innerHeight } : null
  })
  ok('选区确已滚出视口', !!outRect && (outRect.bottom <= 0 || outRect.top >= outRect.vh), JSON.stringify(outRect))
  ok('滚出视口隐藏', !(await popVisible(page)))
  await page.evaluate(() => {
    window.scrollBy({ top: -window.innerHeight, behavior: 'instant' })
  })
  await sleep(600)
  ok('滚回视口重现', await popVisible(page))

  // A12 点击 popover 外部 → 隐藏
  await page.locator('.post-title').click({ position: { x: 5, y: 5 } }).catch(() => {})
  await sleep(300)
  ok('外部点击隐藏', !(await popVisible(page)))

  // A13 真实鼠标拖选：拖动中不显示，松开后显示
  const para = page.locator('.prose p').nth(1)
  const box = await para.boundingBox()
  if (box) {
    await page.evaluate(() => window.getSelection()?.removeAllRanges())
    await page.mouse.move(box.x + 8, box.y + box.height / 2)
    await page.mouse.down()
    await page.mouse.move(box.x + Math.min(box.width - 8, 320), box.y + box.height / 2, { steps: 8 })
    await sleep(200)
    ok('拖动中不显示', !(await popVisible(page)))
    await page.mouse.up()
    await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
    ok('松开后出现', await popVisible(page))
  } else {
    ok('真实拖选（无 boundingBox 跳过）', true)
  }

  // A14 TOC 点击不受影响
  const tocBtn = page.locator('.post-toc-wrap .group\\/toc').first()
  if (await tocBtn.count()) {
    const y0 = await page.evaluate(() => window.scrollY)
    await tocBtn.click()
    await sleep(600)
    const y1 = await page.evaluate(() => window.scrollY)
    ok('TOC 点击滚动', y1 !== y0 || (await page.locator('.toc [class*=text-accent], .post-toc-wrap [class*=accent]').count()) > 0, `y ${y0}→${y1}`)
  } else {
    ok('TOC 点击（无目录跳过）', true)
  }

  // A15 横向溢出 = 0
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok('横向溢出=0', overflow <= 0, `overflow=${overflow}`)
  ok('无 pageerror', page.errors.length === 0, page.errors.slice(0, 2).join(' | '))
  await ctx.close()
}

// ══ B. 复制 ═══════════════════════════════════════════════════
console.log('== B. 复制（clipboard + toast）==')
{
  const { ctx, page } = await newPage('paper-light', DESKTOP, { clipboard: true })
  await gotoPost(page)
  const s = await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.locator('.sel-pop-btn', { hasText: '复制' }).click()
  await sleep(300)
  const clip = await page.evaluate(() => navigator.clipboard.readText().catch(() => ''))
  ok('剪贴板=选区文本', clip.replace(/\s+/g, ' ').includes(s.sample.slice(0, 20)), `clip="${clip.slice(0, 40)}"`)
  const toastTxt = await page.locator('[role="status"]').allInnerTexts().catch(() => [])
  ok('toast 已复制', toastTxt.some(t => t.includes('已复制')), JSON.stringify(toastTxt))
  ok('复制后 popover 隐藏', !(await popVisible(page)))
  await ctx.close()
}

// ══ C. 登录态：问 AI 这段 → 面板 + 引用条 + 聚焦 ════════════════
console.log('== C. 登录态问 AI 这段 ==')
{
  const { ctx, page } = await newPage('paper-light', DESKTOP, { tokens })
  await gotoPost(page)
  const s = await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.locator('.sel-pop-btn', { hasText: '问 AI' }).click()
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 }).catch(() => {})
  ok('聊天面板打开', await page.locator('.chat-panel').isVisible())
  const panelText = await page.locator('.chat-panel').innerText().catch(() => '')
  ok('引用条含选区', panelText.includes(s.sample.slice(0, 15)), s.sample.slice(0, 30))
  const focused = await page.evaluate(() => document.activeElement?.tagName === 'TEXTAREA')
  ok('输入框聚焦', focused)
  ok('popover 已隐藏', !(await popVisible(page)))
  await ctx.close()
}

// ══ D. 匿名：问 AI 这段 → 登录 + 暂存 + 回跳恢复 ════════════════
console.log('== D. 匿名问 AI 这段 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  const s = await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.locator('.sel-pop-btn', { hasText: '问 AI' }).click()
  await page.waitForURL('**/login**', { timeout: 8000 }).catch(() => {})
  ok('跳转登录页', page.url().includes('/login'), page.url())
  const stash = await page.evaluate(() => sessionStorage.getItem('mysite_pending_quote'))
  ok('quote 已暂存', !!stash && stash.includes(s.sample.slice(0, 10)), stash?.slice(0, 60))

  // 模拟登录成功后回跳：注入 token 重进文章页 → 面板自动打开带引用条
  await ctx.addInitScript(tk => {
    localStorage.setItem('mysite_access_token', JSON.stringify(tk.accessToken))
    localStorage.setItem('mysite_refresh_token', JSON.stringify(tk.refreshToken))
  }, tokens)
  await page.goto(postUrl, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 10000 }).catch(() => {})
  ok('回跳后面板自动打开', await page.locator('.chat-panel').isVisible())
  const panelText2 = await page.locator('.chat-panel').innerText().catch(() => '')
  ok('回跳引用条恢复', panelText2.includes(s.sample.slice(0, 15)), s.sample.slice(0, 30))
  await ctx.close()
}

// ══ E. 登录态：引用 → 滚动评论框 + > 预填 ═══════════════════════
console.log('== E. 登录态引用 ==')
{
  const { ctx, page } = await newPage('paper-light', DESKTOP, { tokens })
  await gotoPost(page)
  const s = await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const quoteBtn = page.locator('.sel-pop-btn', { hasText: '引用' })
  const hasQuote = await quoteBtn.count() > 0
  ok('引用按钮存在（评论开启）', hasQuote)
  if (hasQuote) {
    await quoteBtn.click()
    await sleep(900)
    const ta = page.locator('.scroll-mt-24 textarea').first()
    const val = await ta.inputValue().catch(() => '')
    ok('评论框 > 预填', val.startsWith('> ') && val.includes(s.sample.slice(0, 15)), val.slice(0, 60))
    const y = await page.evaluate(() => window.scrollY)
    ok('已滚动到评论区', y > 200, `scrollY=${y}`)
    ok('popover 已隐藏', !(await popVisible(page)))
  }
  await ctx.close()
}

// ══ F. 匿名：引用 → 登录 + 暂存 + 回跳预填 ══════════════════════
console.log('== F. 匿名引用 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  const s = await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const quoteBtn = page.locator('.sel-pop-btn', { hasText: '引用' })
  if (await quoteBtn.count() === 0) {
    ok('匿名引用（评论关闭跳过）', true)
    await ctx.close()
  } else {
    await quoteBtn.click()
    await page.waitForURL('**/login**', { timeout: 8000 }).catch(() => {})
    ok('跳转登录页', page.url().includes('/login'), page.url())
    const stash = await page.evaluate(() => sessionStorage.getItem('mysite_pending_comment'))
    ok('comment quote 已暂存', !!stash && stash.includes(target.id), stash?.slice(0, 60))

    await ctx.addInitScript(tk => {
      localStorage.setItem('mysite_access_token', JSON.stringify(tk.accessToken))
      localStorage.setItem('mysite_refresh_token', JSON.stringify(tk.refreshToken))
    }, tokens)
    await page.goto(postUrl, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.prose', { timeout: 20000 })
    await sleep(1600) // 等 500ms 延迟滚动 + 渲染
    const val = await page.locator('.scroll-mt-24 textarea').first().inputValue().catch(() => '')
    ok('回跳评论框预填', val.startsWith('> ') && val.includes(s.sample.slice(0, 15)), val.slice(0, 60))
    const y = await page.evaluate(() => window.scrollY)
    ok('回跳已滚动评论区', y > 100, `scrollY=${y}`)
    await ctx.close()
  }
}

// ══ G. 移动端（pointer: coarse）→ 无 popover ════════════════════
console.log('== G. 移动端触屏 ==')
{
  const { ctx, page } = await newPage('paper-light', { width: 390, height: 844 }, { touch: true })
  await gotoPost(page)
  await makeSelection(page, 'first-p')
  await sleep(600)
  ok('触屏不出现 popover', !(await popVisible(page)))
  const mql = await page.evaluate(() => matchMedia('(pointer: fine)').matches)
  ok('pointer:fine=false（环境确认）', mql === false, `matches=${mql}`)
  await ctx.close()
}

// ══ H. 主题：classic-light 也能用（圆角）+ paper-dark ════════════
console.log('== H. 主题兼容 ==')
for (const theme of ['classic-light', 'paper-dark']) {
  const { ctx, page } = await newPage(theme)
  await gotoPost(page)
  await makeSelection(page, 'first-p')
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok(`${theme} popover 出现`, await popVisible(page))
  const r = await page.locator('.sel-pop').evaluate(el => getComputedStyle(el).borderTopLeftRadius)
  const expectRound = theme === 'classic-light' ? '8px' : '0px'
  ok(`${theme} 圆角=${expectRound}`, r === expectRound, r)
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok(`${theme} 溢出=0`, overflow <= 0, `overflow=${overflow}`)
  await ctx.close()
}

// ══ I. About 页不启用（路由门控）═══════════════════════════════
console.log('== I. About 页门控 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose', { timeout: 15000 }).catch(() => {})
  await sleep(600)
  const hasProse = await page.locator('.prose').count()
  if (hasProse) {
    await makeSelection(page, 'first-p')
    await sleep(500)
    ok('About 页不出现 popover', !(await popVisible(page)))
  } else {
    ok('About 页（无 .prose 跳过）', true)
  }
  await ctx.close()
}

await browser.close()
console.log(`\n== T10 verify: ${pass} pass / ${fail} fail ==`)
process.exit(fail ? 1 : 0)
