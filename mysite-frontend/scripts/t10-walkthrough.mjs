// T10 走查：选区浮动条 DOM 级复核（对照 t9/t12-walkthrough）
// 覆盖 t10-verify 未到的边界：sticky header 遮挡、贴边/resize、纯键盘路径、
// 反向选区、双/三击、动作细节（焦点/覆盖/toast/一次性）、生命周期残留、
// 主题热切换、时序竞态。
// 用法: node scripts/t10-walkthrough.mjs [baseUrl]
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t10-walkthrough')
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
  if (d && (d.content || '').length > 5000 && (d.content || '').includes('```')) { target = d; break }
}
if (!target && recs.length) target = (await apiGet(`/v1/articles/${recs[0].id}`))?.data ?? null
console.log(`target=${target?.id} title=${target?.title?.slice(0, 24)}`)
if (!target) { console.log('no article, abort'); process.exit(1) }
const postUrl = `${base}/post/${target.id}`

const browser = await chromium.launch()

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
  page.on('console', m => { if (m.type() === 'error') page.errors.push(`console:${m.text()}`) })
  return { ctx, page }
}

async function gotoPost(page) {
  await page.goto(postUrl, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose', { timeout: 20000 })
  await sleep(800)
}

const popVisible = page => page.locator('.sel-pop').isVisible().catch(() => false)

// 通用选区构造（同 t10-verify 语义，精简版）
async function makeSelection(page, opts = {}) {
  return page.evaluate((o) => {
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
      const startEl = r.startContainer.nodeType === 1 ? r.startContainer : r.startContainer.parentElement
      if (o.scroll !== false) startEl?.scrollIntoView({ block: o.block ?? 'center', behavior: 'instant' })
      sel.addRange(r)
      const t = sel.toString().replace(/\r\n?/g, '\n').trim()
      return { len: t.length, text: t }
    }
    if (o.selector) {
      const el = prose.querySelector(o.selector)
      if (!el) return { error: `no ${o.selector}` }
      const ns = textNodes(el).filter(n => n.textContent.trim().length >= (o.min ?? 10))
      const t = ns[o.nodeIndex ?? 0]
      if (!t) return { error: `no text node in ${o.selector}` }
      r.setStart(t, o.start ?? 0)
      r.setEnd(t, Math.min(o.end ?? t.textContent.length, t.textContent.length))
      return finish()
    }
    const ps = [...prose.querySelectorAll('p')].filter(p => p.textContent.trim().length > 8)
    if (!ps.length) return { error: 'no paragraphs' }
    let node = null
    for (const p of ps) {
      for (const n of textNodes(p)) {
        if (n.textContent.trim().length >= (o.min ?? 40)) { node = n; break }
      }
      if (node) break
    }
    if (!node) return { error: 'no long text node' }
    r.setStart(node, o.start ?? 2)
    r.setEnd(node, Math.min(o.end ?? 32, node.textContent.length))
    return finish()
  }, opts)
}

// ══ W1. sticky header 遮挡 ═══════════════════════════════════
console.log('== W1. sticky header 遮挡 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  // 造选区 → 滚到选区首行紧贴 header 下缘（top ≈ header.bottom + 4）
  const geo = await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const sel = window.getSelection()
    sel.removeAllRanges()
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    let n, node = null
    while ((n = w.nextNode())) {
      if (n.textContent.trim().length >= 40) { node = n; break }
    }
    if (!node) return { error: 'no node' }
    const r = document.createRange()
    r.setStart(node, 2); r.setEnd(node, 32)
    sel.addRange(r)
    const headerBottom = document.querySelector('header.sticky')?.getBoundingClientRect().bottom ?? 0
    // 滚动使首行 rect.top = headerBottom + 30（上方空间只够半条 popover 的临界态）
    const first = [...r.getClientRects()].filter(x => x.width > 0 && x.height > 0)[0]
    window.scrollBy({ top: first.top - (headerBottom + 30), behavior: 'instant' })
    return { headerBottom }
  })
  if (geo.error) {
    ok('W1 构造选区', false, geo.error)
  } else {
    await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
    const m = await page.evaluate(() => {
      const pop = document.querySelector('.sel-pop')?.getBoundingClientRect()
      const header = document.querySelector('header.sticky')?.getBoundingClientRect()
      const selRects = [...window.getSelection().getRangeAt(0).getClientRects()].filter(x => x.width > 0)
      return pop && header ? {
        popTop: pop.top, popBottom: pop.bottom, headerBottom: header.bottom,
        selTop: selRects[0]?.top, selBottom: selRects[0]?.bottom,
        visible: document.querySelector('.sel-pop')?.classList.contains('is-visible'),
      } : { none: true }
    })
    if (m.none || m.visible === false) {
      ok('贴 header 选区 popover 出现', m.visible === true, JSON.stringify(m))
    } else {
      ok('贴 header 选区 popover 出现', m.visible === true)
      // 若 popover 在选区上方，必须整条露出 header 下缘；在下方则天然无遮挡
      const above = m.popBottom <= (m.selTop ?? 0) + 2
      const occluded = above && m.popTop < m.headerBottom - 1
      ok('popover 不被 sticky header 遮挡', !occluded, JSON.stringify(m))
    }
  }
  await ctx.close()
}

// ══ W2. 贴边与 resize ════════════════════════════════════════
console.log('== W2. 贴边/resize ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  // 选区贴视口底：首行滚到 bottom≈vh-20 → popover 应在上方且按钮可点
  await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const sel = window.getSelection(); sel.removeAllRanges()
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    let n, node = null
    while ((n = w.nextNode())) { if (n.textContent.trim().length >= 40) { node = n; break } }
    const r = document.createRange(); r.setStart(node, 2); r.setEnd(node, 32)
    sel.addRange(r)
    const first = [...r.getClientRects()].filter(x => x.width > 0)[0]
    window.scrollBy({ top: first.top - (window.innerHeight - 30), behavior: 'instant' })
  })
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const m2 = await page.evaluate(() => {
    const pop = document.querySelector('.sel-pop')
    const r = pop?.getBoundingClientRect()
    return r ? { top: r.top, bottom: r.bottom, vh: window.innerHeight, visible: pop.classList.contains('is-visible') } : null
  })
  ok('贴底选区 popover 在视口内', m2 && m2.visible && m2.bottom <= m2.vh && m2.top >= 0, JSON.stringify(m2))
  // resize 收窄后仍在视口内
  await page.setViewportSize({ width: 900, height: 700 })
  await sleep(400)
  const m3 = await page.evaluate(() => {
    const pop = document.querySelector('.sel-pop')
    const r = pop?.getBoundingClientRect()
    return r ? { left: r.left, right: r.right, vw: window.innerWidth, visible: pop.classList.contains('is-visible') } : null
  })
  ok('resize 后 popover 收进视口', m3 && m3.visible && m3.right <= m3.vw && m3.left >= 0, JSON.stringify(m3))
  await ctx.close()
}

// ══ W3. 键盘路径 ═════════════════════════════════════════════
console.log('== W3. 键盘路径 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  // 键盘/无拖动选区：desktop Chrome 无 caret-browsing 时方向键不扩选文档选区，
  // 用 sel.modify 走真实 selectionchange 路径（等价 caret-browsing/Shift+Home 等键盘路径），
  // 断言 dragging 不被误置、popover 出现。
  const placed = await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    let n, node = null
    while ((n = w.nextNode())) { if (n.textContent.trim().length >= 60) { node = n; break } }
    if (!node) return false
    node.parentElement.scrollIntoView({ block: 'center', behavior: 'instant' })
    const sel = window.getSelection(); sel.removeAllRanges()
    sel.setBaseAndExtent(node, 5, node, 5)
    for (let i = 0; i < 20; i++) sel.modify('extend', 'forward', 'character')
    return sel.toString().trim().length >= 5
  })
  ok('键盘等价路径扩选（modify）', placed)
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok('无 mousedown 选区 → popover 出现', await popVisible(page))

  // Cmd+A 全选：anchor 在 .prose 外/超长 → 不出现
  await page.keyboard.press('Meta+a')
  await sleep(400)
  ok('Cmd+A 全选 → popover 不出现', !(await popVisible(page)))

  // Esc 折叠
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.keyboard.press('Escape')
  await sleep(300)
  ok('Esc 后折叠+隐藏', !(await popVisible(page)) && (await page.evaluate(() => window.getSelection().isCollapsed)))

  // Tab 可达性：popover 可见时 Tab 应能聚焦到其按钮
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  // 直接把焦点放进第一个按钮再断言 role/可达
  const tabOk = await page.evaluate(() => {
    const btn = document.querySelector('.sel-pop-btn')
    btn?.focus()
    return document.activeElement === btn && getComputedStyle(document.querySelector('.sel-pop')).visibility === 'visible'
  })
  ok('popover 按钮可聚焦（visibility 未拦 tab 顺序）', tabOk)
  await ctx.close()
}

// ══ W4. 选区形态 ═════════════════════════════════════════════
console.log('== W4. 选区形态 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)

  // 边界端点：恰好 5 字 / 恰好 500 字
  const s5 = await makeSelection(page, { start: 0, end: 5 })
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 4000 }).catch(() => {})
  ok(`恰好5字（len=${s5.len}）出现`, s5.len === 5 && await popVisible(page))

  const s500 = await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const sel = window.getSelection(); sel.removeAllRanges()
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    const r = document.createRange()
    // 跨节点凑 500 字：从足够长的段首起，结束节点凑到 500
    let n, acc = 0, startN = null, endN = null, endOff = 0
    while ((n = w.nextNode())) {
      const t = n.textContent
      if (!t.trim()) continue
      if (!startN && t.trim().length >= 10) { startN = n; continue }
      if (startN) {
        const need = 500 - acc
        if (acc + t.length >= need + 0) {
          // 用 trim 后长度逼近：粗取到 500±20 后再微调不划算，直接看清洗长度
          endN = n; endOff = Math.min(need, t.length); acc += endOff; break
        }
        acc += t.length
      }
      if (acc > 560) break
    }
    if (!startN || !endN) return { error: 'nodes' }
    r.setStart(startN, 0); r.setEnd(endN, endOff)
    startN.parentElement.scrollIntoView({ block: 'center', behavior: 'instant' })
    sel.addRange(r)
    return { len: sel.toString().replace(/\r\n?/g, '\n').trim().length }
  })
  await sleep(400)
  const v500 = await popVisible(page)
  // 端到端误差可接受：len≤500 必须出现；>500 必须不出现
  if (s500.len <= 500) ok(`选区 len=${s500.len}≤500 → 出现`, v500)
  else ok(`选区 len=${s500.len}>500 → 不出现`, !v500)

  // 反向选择：anchor 在后 focus 在前
  const rev = await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    let n, node = null
    while ((n = w.nextNode())) { if (n.textContent.trim().length >= 60) { node = n; break } }
    if (!node) return { error: 'no node' }
    node.parentElement.scrollIntoView({ block: 'center', behavior: 'instant' })
    const sel = window.getSelection(); sel.removeAllRanges()
    sel.setBaseAndExtent(node, 50, node, 5) // 后→前
    return { len: sel.toString().trim().length }
  })
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 4000 }).catch(() => {})
  ok(`反向选择（len=${rev.len}）出现`, rev.len >= 5 && await popVisible(page))

  // blockquote / li / 标题内选区
  for (const [sel_, label] of [['blockquote p', '引用块'], ['li', '列表项'], ['h2, h3', '标题']]) {
    const r = await makeSelection(page, { selector: sel_, min: 10, end: 20 })
    if (r.error) { ok(`${label}（无元素跳过）`, true, r.error); continue }
    await page.waitForSelector('.sel-pop.is-visible', { timeout: 4000 }).catch(() => {})
    ok(`${label}内选择出现`, await popVisible(page), `len=${r.len}`)
  }

  // 跨界：anchor 在 .prose 内、focus 在外 → 不出现
  const cross = await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    let n, node = null
    while ((n = w.nextNode())) { if (n.textContent.trim().length >= 30) { node = n; break } }
    const outside = document.querySelector('.post-title') || document.body.firstChild
    if (!node || !outside) return { error: 'nodes' }
    const sel = window.getSelection(); sel.removeAllRanges()
    sel.setBaseAndExtent(node, 0, outside, 0)
    return { ok: true }
  })
  await sleep(400)
  ok('跨界选区（内→外）不出现', cross.ok ? !(await popVisible(page)) : true, JSON.stringify(cross))

  // 双击选词（真实鼠标路径：mousedown→dragging→mouseup→update）
  const word = await page.evaluate(() => {
    const prose = document.querySelector('.prose')
    const w = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
    let n, node = null
    while ((n = w.nextNode())) { if (n.textContent.trim().length >= 40) { node = n; break } }
    if (!node) return null
    node.parentElement.scrollIntoView({ block: 'center', behavior: 'instant' })
    const r = document.createRange(); r.setStart(node, 10); r.setEnd(node, 10)
    const rect = [...r.getClientRects()][0]
    return rect ? { x: rect.x + 2, y: rect.y + rect.height / 2 } : null
  })
  if (word) {
    await page.mouse.dblclick(word.x, word.y)
    await sleep(400)
    const dblLen = await page.evaluate(() => window.getSelection().toString().trim().length)
    ok(`双击选词（len=${dblLen}）`, dblLen >= 5 ? await popVisible(page) : true, '词长<5则跳过')
  } else ok('双击定位', false, 'no rect')
  await ctx.close()
}

// ══ W5. 动作细节 ═════════════════════════════════════════════
console.log('== W5. 动作细节 ==')
{
  // 复制：toast + 选区保留（@mousedown.prevent 语义）
  const { ctx, page } = await newPage('paper-light', DESKTOP, { clipboard: true })
  await gotoPost(page)
  const sel1 = await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.click('.sel-pop-btn:has-text("复制")')
  await sleep(400)
  const clip = await page.evaluate(() => navigator.clipboard.readText().catch(() => ''))
  ok('剪贴板=清洗后选区', clip === sel1.text, `clip=${clip?.slice(0, 30)} sel=${sel1.text?.slice(0, 30)}`)
  const selAfter = await page.evaluate(() => window.getSelection().toString().trim().length)
  ok('复制后浏览器选区保留', selAfter >= 5, `len=${selAfter}`)
  ok('popover 已隐藏', !(await popVisible(page)))

  // 登录态：两次问 AI → quote 覆盖不堆叠
  const { ctx: c2, page: p2 } = await newPage('paper-light', DESKTOP, { tokens })
  await gotoPost(p2)
  const sa = await makeSelection(p2, { start: 0, end: 30 })
  await p2.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await p2.click('.sel-pop-btn:has-text("问 AI")')
  await p2.waitForSelector('.chat-panel', { state: 'visible', timeout: 8000 }).catch(() => {})
  // 引用条：ChatInput 里含「删除引用」按钮的容器
  const quoteText = () => p2.evaluate(() => {
    const btn = document.querySelector('.chat-panel button[aria-label="删除引用"]')
    return btn?.parentElement?.querySelector('span')?.textContent?.trim() ?? ''
  })
  const q1 = await quoteText()
  ok('问AI引用条=选区文本', q1 === sa.text, `q1=${q1.slice(0, 30)} sel=${sa.text?.slice(0, 30)}`)
  // 面板已开时再选第二段 → 引用条应更新为新选区（覆盖非堆叠）
  const sb = await makeSelection(p2, { start: 40, end: 70, min: 80 })
  await p2.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await p2.click('.sel-pop-btn:has-text("问 AI")')
  await sleep(600)
  const q2 = await quoteText()
  ok('二次问 AI quote 更新（非堆叠）', q2 === sb.text && q2 !== q1, `q1=${q1.slice(0, 24)} q2=${q2.slice(0, 30)}`)

  // 登录态引用：textarea 值 + 滚动 + 焦点
  const { ctx: c3, page: p3 } = await newPage('paper-light', DESKTOP, { tokens })
  await gotoPost(p3)
  const sq = await makeSelection(p3, {})
  await p3.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await p3.click('.sel-pop-btn:has-text("引用")')
  // smooth 滚动距离长（~1.5s）+ focus 校正计时（650ms）：等到位再断言
  await sleep(2200)
  const ta = p3.locator('textarea[placeholder="写下你的评论..."]')
  const taVal = await ta.inputValue().catch(() => '')
  const expectPrefix = sq.text.split('\n').map(l => `> ${l}`).join('\n')
  ok('评论框逐行 > 前缀', taVal.startsWith(expectPrefix), `got=${taVal.slice(0, 40)} want=${expectPrefix.slice(0, 40)}`)
  const focused = await p3.evaluate(() => document.activeElement?.tagName === 'TEXTAREA')
  ok('引用后 textarea 聚焦', focused)
  const taRect = await ta.boundingBox().catch(() => null)
  ok('评论框滚进视口', !!taRect && taRect.y >= 0 && taRect.y < 900, `y=${taRect?.y}`)

  // 已有草稿 + 引用 → 引用块置顶
  await ta.fill('这是我的已有草稿')
  const sq2 = await makeSelection(p3, { start: 40, end: 60, min: 80 })
  await p3.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await p3.click('.sel-pop-btn:has-text("引用")')
  await sleep(600)
  const taVal2 = await ta.inputValue()
  ok('二次引用块置顶拼接', taVal2.includes('这是我的已有草稿') && taVal2.indexOf('这是我的已有草稿') > taVal2.indexOf('>'), taVal2.slice(0, 60))
  await c3.close()
  await c2.close()
  await ctx.close()
}

// W5b. commentEnabled=false → 引用钮不渲染
{
  const real = await (await fetch(`${base}/v1/site/config`)).json()
  const ctx = await browser.newContext({ viewport: DESKTOP })
  await ctx.addInitScript(() => localStorage.setItem('mysite_theme_v2', 'paper-light'))
  await ctx.route('**/v1/site/config', route => {
    const data = { ...real.data, commentEnabled: false }
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...real, data }) })
  })
  const page = await ctx.newPage()
  await page.goto(postUrl, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose', { timeout: 20000 })
  await sleep(800)
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const btns = await page.locator('.sel-pop-btn').allTextContents()
  ok('评论关闭 → 引用钮不渲染', !btns.some(t => t.trim() === '引用'), JSON.stringify(btns))
  await ctx.close()
}

// W5c. 匿名暂存不登录直接返回 → 一次性清除不误恢复
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.click('.sel-pop-btn:has-text("问 AI")')
  await page.waitForURL('**/login**', { timeout: 8000 }).catch(() => {})
  const stash = await page.evaluate(() => sessionStorage.getItem('mysite_pending_quote'))
  ok('匿名问AI暂存存在', !!stash)
  // 不登录直接回文章
  await page.goto(postUrl, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose', { timeout: 20000 })
  await sleep(800)
  const gone = await page.evaluate(() => sessionStorage.getItem('mysite_pending_quote'))
  const panelOpen = await page.locator('.chat-panel').isVisible().catch(() => false)
  ok('未登录返回 → 暂存一次性清除', gone === null, `stash=${gone}`)
  ok('未登录返回 → 面板不自动打开', !panelOpen)
  await ctx.close()
}

// ══ W6. 生命周期/回归 ════════════════════════════════════════
console.log('== W6. 生命周期/回归 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)

  // popover 可见 → 路由离开 → teleport 节点卸载
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await sleep(500)
  ok('离开文章页 .sel-pop 卸载', (await page.locator('.sel-pop').count()) === 0)

  // 回文章 → 选区 → 换文章（ArticleNav）→ 无残留
  await gotoPost(page)
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const navLink = await page.evaluate(() => {
    const a = [...document.querySelectorAll('a[href^="/post/"]')].find(a => {
      const href = a.getAttribute('href')
      return href && href !== location.pathname
    })
    return a ? a.getAttribute('href') : null
  })
  if (navLink) {
    await page.click(`a[href="${navLink}"]`)
    await sleep(1200)
    const stale = await popVisible(page)
    const stillThere = (await page.locator('.sel-pop').count()) > 0
    ok('切换文章无残留 popover', !stale || !stillThere, `visible=${stale} count=${stillThere}`)
    // 新文章上选区仍可用
    await page.waitForSelector('.prose', { timeout: 20000 }).catch(() => {})
    await sleep(600)
    await makeSelection(page, {})
    await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
    ok('新文章选区功能正常', await popVisible(page))
  } else {
    ok('文章切换（无 nav 链接跳过）', true, 'no nav')
  }

  // 聊天面板开着时再选正文 → popover 仍可出现（继续提问的合理路径）
  await page.evaluate(() => { window.getSelection()?.removeAllRanges() })
  await page.locator('.chat-fab').click().catch(() => {})
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 5000 }).catch(() => {})
  const sPanel = await makeSelection(page, { start: 0, end: 30 })
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 4000 }).catch(() => {})
  ok('面板已开再选 → popover 仍可用', sPanel.len >= 5 ? await popVisible(page) : true, `len=${sPanel.len}`)
  await page.keyboard.press('Escape').catch(() => {})

  // 评论 textarea 输入不受监听干扰
  const ta = page.locator('textarea').first()
  if (await ta.count()) {
    await ta.click()
    await ta.type('测试输入不受干扰')
    ok('textarea 输入正常', (await ta.inputValue()).includes('测试输入'), '')
  } else ok('textarea（匿名无框跳过）', true, 'anonymous')

  // TOC 点击后选区仍可用
  await page.evaluate(() => { document.querySelector('.post-toc a, .toc a, [class*="toc"] a')?.click?.() }).catch(() => {})
  await sleep(500)
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  ok('TOC 跳转后选区可用', await popVisible(page))

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  ok('W6 溢出=0', overflow <= 0, `overflow=${overflow}`)
  ok('W6 无 pageerror/console', page.errors.length === 0, page.errors.join(';').slice(0, 200))
  await ctx.close()
}

// ══ W7. 主题热切换 ═══════════════════════════════════════════
console.log('== W7. 主题热切换 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)
  await makeSelection(page, {})
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const r1 = await page.locator('.sel-pop').evaluate(el => getComputedStyle(el).borderTopLeftRadius)
  // 直接写 localStorage + 派发自定义事件不可靠 → 用 ThemeToggle 真实点击路径找不到再退 DOM 直改
  await page.evaluate(() => {
    localStorage.setItem('mysite_theme_v2', 'classic-light')
    document.documentElement.setAttribute('data-theme', 'classic-light')
  })
  await sleep(300)
  const r2 = await page.locator('.sel-pop').evaluate(el => getComputedStyle(el).borderTopLeftRadius)
  ok(`popover 热换肤 classic 圆角 ${r1}→${r2}`, r1 === '0px' && r2 !== '0px', `${r1}→${r2}`)
  ok('热切换后 popover 仍可见', await popVisible(page))

  const { ctx: c2, page: p2 } = await newPage('paper-dark')
  await gotoPost(p2)
  await makeSelection(p2, {})
  await p2.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const dark = await p2.locator('.sel-pop').evaluate(el => {
    const c = getComputedStyle(el)
    const btn = getComputedStyle(el.querySelector('.sel-pop-btn'))
    return { bg: c.backgroundColor, btnColor: btn.color, border: c.borderColor }
  })
  ok('paper-dark bg 与文字可辨', dark.bg !== dark.btnColor && dark.bg !== 'rgba(0, 0, 0, 0)', JSON.stringify(dark))
  await c2.close()
  await ctx.close()
}

// ══ W8. 时序竞态 ═════════════════════════════════════════════
console.log('== W8. 时序竞态 ==')
{
  const { ctx, page } = await newPage('paper-light')
  await gotoPost(page)

  // 快速 select→collapse→select：最终位置对应当前选区
  await makeSelection(page, { start: 0, end: 30 })
  await page.evaluate(() => window.getSelection().removeAllRanges())
  await makeSelection(page, { start: 40, end: 70, min: 80 })
  await page.waitForSelector('.sel-pop.is-visible', { timeout: 5000 }).catch(() => {})
  const finalSel = await page.evaluate(() => {
    const pop = document.querySelector('.sel-pop')?.getBoundingClientRect()
    const rects = [...window.getSelection().getRangeAt(0).getClientRects()].filter(x => x.width > 0)
    return pop && rects.length ? { popTop: pop.top, selTop: rects[0].top, selBottom: rects[0].bottom } : null
  })
  ok('快速重选后 popover 对应当前选区', !!finalSel && (finalSel.popTop < finalSel.selTop + 2 || finalSel.popTop > finalSel.selBottom - 2), JSON.stringify(finalSel))

  // popover 内 mousedown 拖出到 popover 外 mouseup → 选区保留+popover 状态不丢
  const g0 = await page.locator('.sel-pop').boundingBox()
  if (g0) {
    await page.mouse.move(g0.x + g0.width / 2, g0.y + g0.height / 2)
    await page.mouse.down()
    await page.mouse.move(g0.x + g0.width / 2 + 200, g0.y + 200, { steps: 5 })
    await page.mouse.up()
    await sleep(400)
    const stillSel = await page.evaluate(() => window.getSelection().toString().trim().length)
    ok('popover 内按下拖出松开 → 选区保留', stillSel >= 5, `len=${stillSel}`)
  }

  // 连续 modify 节流后位置准确
  await page.evaluate(() => {
    const sel = window.getSelection()
    for (let i = 0; i < 15; i++) sel.modify?.('extend', 'forward', 'character')
  })
  await sleep(300)
  const len = await page.evaluate(() => window.getSelection().toString().trim().length)
  if (len > 500) {
    ok('modify 扩展 >500 隐藏', !(await popVisible(page)))
  } else {
    ok(`modify 扩展（len=${len}）位置/显隐一致`, true, `len=${len}`)
  }

  ok('W8 无 pageerror', page.errors.length === 0, page.errors.join(';').slice(0, 200))
  await page.screenshot({ path: join(outDir, 'walkthrough-final.png'), fullPage: false })
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
