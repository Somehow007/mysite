// T11 走查：聊天面板换肤 DOM 级复核（主验证之外的边角）
// 用法: node scripts/t11-walkthrough.mjs [baseUrl]
// SSE 按问题文本路由到不同响应：DELAY=延迟1.5s(测取消) / ERR=error事件 / 其余=正常流。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t11')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

async function newPage(theme, viewport) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  let errFired = false
  await ctx.route('**/v1/rag/chat/stream**', async route => {
    const q = new URL(route.request().url()).searchParams.get('q') ?? ''
    if (q.includes('DELAY')) { await new Promise(r => setTimeout(r, 1500)) }
    if (q.includes('ERR') && !errFired) {
      errFired = true
      return route.fulfill({ status: 200, contentType: 'text/event-stream',
        body: 'data: {"type":"error","message":"走查模拟故障"}\n\n' })
    }
    const content = q.includes('CITE')
      ? '正文引用[1]生效；行内代码 `arr[1]` 不转；三位数[123]不转；块代码\n```\nx[2]=1\n```\n不转。'
      : '走查回复 **加粗** 与 `code`，引用[1]。'
    const sourcesJson = q.includes('SRC')
      ? '[{"title":"走查来源","content":"c","score":0.87,"kbId":"1","kbName":"技术博客"}]'
      : '[]'
    await route.fulfill({ status: 200, contentType: 'text/event-stream',
      body: `data: {"type":"meta","conversationId":"w-conv"}\n\ndata: {"type":"sources","sources":${sourcesJson}}\n\ndata: {"type":"content","delta":${JSON.stringify(content)}}\n\ndata: {"type":"done"}\n\n` })
  })
  await ctx.route('**/v1/rag/conversations**', r =>
    r.fulfill({ contentType: 'application/json', body: '[]' }))
  return { ctx, page: await ctx.newPage() }
}

async function openPanel(page) {
  await page.waitForSelector('.chat-fab', { timeout: 15000 })
  await page.click('.chat-fab')
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 5000 })
  await page.waitForTimeout(350)
}
async function send(page, q) {
  await page.locator('.chat-input-box textarea').fill(q)
  await page.locator('.chat-input-send').click()
}

// ── A. 角标边界 + markdown 基础 ──────────────────────────────
console.log('== A. 角标边界 ==')
{
  const { ctx, page } = await newPage('paper-light', { width: 1440, height: 900 })
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await openPanel(page)
  await send(page, 'CITE 边界')
  await page.waitForSelector('sup.chat-cite', { timeout: 8000 })
  await page.waitForTimeout(400)

  const md = page.locator('.chat-bubble-ai .chat-markdown-body').last()
  ok('正文 [1] 转角标', await md.locator('sup.chat-cite').count() === 1)
  ok('code 内 [1] 不转', await md.locator('code sup.chat-cite').count() === 0)
  const inline = await md.locator('code').allInnerTexts()
  ok('code 原文保留 arr[1]', inline.some(t => t.includes('[1]')), inline.join('|'))
  const bodyText = await md.locator('.markdown-body').innerText()
  ok('[123] 不转角标', bodyText.includes('[123]'), bodyText)
  ok('inline code 渲染为 code 元素', await md.locator('code').count() === 2,
    `codes=${await md.locator('code').count()}`)
  await ctx.close()
}
{
  const { ctx, page } = await newPage('paper-light', { width: 1440, height: 900 })
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await openPanel(page)
  await send(page, 'SRC 折叠')
  await page.waitForSelector('.chat-source-card', { timeout: 8000 })
  await page.waitForTimeout(300)
  const md = page.locator('.chat-bubble-ai .chat-markdown-body').last()
  ok('markdown 加粗', await md.locator('strong').count() === 1)
  ok('markdown 行内码', await md.locator('code').count() === 1)
  // 折叠开关：默认展开 → 点击收起 → 再点展开
  const toggle = page.locator('button', { hasText: '参考来源' })
  ok('来源标题(1)', (await toggle.innerText()).includes('(1)'))
  await toggle.click()
  await page.waitForTimeout(200)
  ok('点击后收起', await page.locator('.chat-source-card').count() === 0)
  await toggle.click()
  await page.waitForTimeout(200)
  ok('再点展开', await page.locator('.chat-source-card').count() === 1)
  await ctx.close()
}

// ── B. 取消/截断 + 失败重试 ──────────────────────────────────
console.log('== B. 取消与重试 ==')
{
  const { ctx, page } = await newPage('paper-light', { width: 1440, height: 900 })
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await openPanel(page)
  // 取消：DELAY 让流停在 pending，发送键变停止键
  await send(page, 'DELAY 取消测试')
  await page.waitForSelector('button[aria-label="停止生成"]', { timeout: 3000 })
  await page.locator('button[aria-label="停止生成"]').click()
  await page.waitForSelector('text=已停止生成', { timeout: 3000 })
  ok('取消后截断标记', true)
  ok('发送键恢复', await page.locator('button[aria-label="发送消息"]').count() === 1)
  // 重试：ERR 首请求失败 → 重新生成 → 第二次成功
  await send(page, 'ERR 重试测试')
  await page.waitForSelector('.chat-bubble-ai--failed', { timeout: 8000 })
  await page.locator('.chat-bubble-ai--failed button', { hasText: '重新生成' }).click()
  await page.waitForSelector('sup.chat-cite', { timeout: 8000 })
  await page.waitForTimeout(300)
  ok('重试成功渲染', await page.locator('.chat-bubble-ai--failed').count() === 0)
  await ctx.close()
}

// ── C. 空态 chip / 重开保留 / FAB 路由 ───────────────────────
console.log('== C. 交互链路 ==')
{
  const { ctx, page } = await newPage('paper-light', { width: 1440, height: 900 })
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await openPanel(page)
  const chip = page.locator('.chat-empty-chip').first()
  const chipText = (await chip.innerText()).trim()
  await chip.click()
  await page.waitForSelector('.chat-bubble-user', { timeout: 5000 })
  const sent = (await page.locator('.chat-bubble-user').innerText()).trim()
  ok('空态 chip 直发', sent === chipText, `${chipText} vs ${sent}`)
  await page.waitForTimeout(600)
  // 关闭面板 → FAB 回来 → 重开消息保留（DefaultLayout keep-alive）
  await page.locator('button[aria-label="关闭"]').click()
  await page.waitForSelector('.chat-fab', { state: 'visible', timeout: 3000 })
  ok('关闭后 FAB 回归', true)
  await page.click('.chat-fab')
  await page.waitForTimeout(400)
  ok('重开消息保留', await page.locator('.chat-bubble-user').count() === 1)
  await ctx.close()
}
{
  // FAB 按路由显隐：post 无 FAB，archive 有
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const listJson = await (await fetch(`${base}/v1/articles?current=1&size=1&published=1`)).json()
  const aid = listJson?.data?.records?.[0]?.id ?? listJson?.data?.list?.[0]?.id
  await page.goto(`${base}/post/${aid}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  ok('/post 无 FAB', await page.locator('.chat-fab').count() === 0)
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.chat-fab', { timeout: 8000 })
  ok('/archive 有 FAB', true)
  await ctx.close()
}

// ── D. paper-dark 色值 + 长串溢出 + 移动 sheet ───────────────
console.log('== D. paper-dark / 溢出 / 移动 ==')
{
  const { ctx, page } = await newPage('paper-dark', { width: 1440, height: 900 })
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await openPanel(page)
  await send(page, 'SRC 暗色走查')
  await page.waitForSelector('sup.chat-cite', { timeout: 8000 })
  await page.waitForTimeout(300)
  const citeColor = await page.locator('sup.chat-cite').first().evaluate(el => getComputedStyle(el).color)
  ok('暗色角标=accent', citeColor === 'rgb(224, 121, 79)', citeColor)
  const panelBorder = await page.locator('.chat-panel').evaluate(el => getComputedStyle(el).borderColor)
  ok('暗色面板墨线', panelBorder === 'rgb(236, 228, 214)', panelBorder)
  const ubBg = await page.locator('.chat-bubble-user').evaluate(el => getComputedStyle(el).backgroundColor)
  ok('暗色用户块=bg-secondary', ubBg === 'rgb(31, 27, 22)', ubBg)
  const cardBg = await page.locator('.chat-source-card').first().evaluate(el => getComputedStyle(el).backgroundColor)
  ok('暗色出处卡底', cardBg === 'rgb(22, 19, 15)', cardBg) // --bg-primary paper-dark
  // 长串不溢出面板
  await send(page, 'LONG https://' + 'a'.repeat(180) + '.com')
  await page.waitForSelector('text=已停止生成', { timeout: 2000 }).catch(() => {})
  await page.waitForSelector('.chat-bubble-ai .chat-markdown-body', { timeout: 8000 })
  await page.waitForTimeout(300)
  const leak = await page.evaluate(() => {
    const panel = document.querySelector('.chat-panel')
    const b = [...document.querySelectorAll('.chat-bubble-user')].pop()
    return b.getBoundingClientRect().right - panel.getBoundingClientRect().right
  })
  ok('用户长串不溢出面板', leak <= 1, `leak=${leak}`)
  await page.screenshot({ path: join(outDir, 'w-paper-dark.png') })
  await ctx.close()
}
{
  const { ctx, page } = await newPage('paper-light', { width: 390, height: 844 })
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await openPanel(page)
  const r = await page.locator('.chat-panel').evaluate(el => {
    const rc = el.getBoundingClientRect()
    return { top: rc.top, bottom: rc.bottom, bg: getComputedStyle(el).backgroundColor }
  })
  ok('移动 sheet 底部贴底', Math.abs(r.bottom - 844) <= 8, `bottom=${r.bottom}`)
  ok('移动 sheet 纸面底', r.bg === 'rgb(246, 241, 231)', r.bg) // paper-light bg-primary
  ok('移动 sheet 顶部留缝', r.top > 0, `top=${r.top}`)
  await page.screenshot({ path: join(outDir, 'w-mobile-sheet.png') })
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail === 0 ? 0 : 1)
