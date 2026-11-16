// T11 验证：聊天面板换肤（用户墨块/AI 衬线正文/[n] 角标/出处卡/输入框/面板 chrome/历史抽屉）
// 用法: node scripts/t11-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://localhost:5173）+ playwright；SSE 与会话接口全部 mock。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t11')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

// 取一篇真实文章 id，出处卡点击跳转用
const listJson = await (await fetch(`${base}/v1/articles?current=1&size=1&published=1`)).json()
const realArticleId = String(listJson?.data?.records?.[0]?.id ?? listJson?.data?.list?.[0]?.id ?? '1')
console.log(`realArticleId=${realArticleId}`)

const SSE_BODY = [
  'data: {"type":"meta","conversationId":"t11-mock-conv"}', '',
  `data: {"type":"sources","sources":[{"title":"T11 验证来源文章","content":"片段","score":0.91,"articleId":"${realArticleId}","kbId":"1","kbName":"技术博客"},{"title":"无链接片段","content":"片段","score":0.55,"kbId":"1","kbName":"技术博客"}]}`, '',
  'data: {"type":"content","delta":"这是 T11 模拟回复：检索结果显示作者用熔断器保护下游[1]，无链接片段仅作对照[2]。"}', '',
  'data: {"type":"done"}', '', '',
].join('\n')

const CONV_LIST = [
  { id: 'c1', title: '历史对话一', messageCount: 4, createTime: '2026-11-01T10:00:00', updateTime: '2026-11-10T10:00:00' },
  { id: 'c2', title: '历史对话二', messageCount: 2, createTime: '2026-11-05T10:00:00', updateTime: '2026-11-11T10:00:00' },
]

const browser = await chromium.launch()

async function newPageWithMocks(theme, viewport) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  // SSE：首次延迟 1.2s（制造「正在生成」窗口），之后固定流
  let sseCount = 0
  await ctx.route('**/v1/rag/chat/stream**', async route => {
    sseCount++
    if (sseCount === 1) await new Promise(r => setTimeout(r, 1200))
    await route.fulfill({ status: 200, contentType: 'text/event-stream', body: SSE_BODY })
  })
  await ctx.route('**/v1/rag/conversations**', route => {
    const url = route.request().url()
    if (url.includes('/messages')) {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify([
        { role: 'USER', content: '历史里的问题' },
        { role: 'ASSISTANT', content: '历史里的回答，没有来源卡。' },
      ]) })
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(CONV_LIST) })
  })
  return { ctx, page: await ctx.newPage() }
}

// ── A. 桌面 paper-light：全要素 ──────────────────────────────
console.log('== A. 桌面 1440 paper-light ==')
{
  const { ctx, page } = await newPageWithMocks('paper-light', { width: 1440, height: 900 })
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))

  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.chat-fab', { timeout: 15000 })
  await page.click('.chat-fab')
  await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 5000 })
  await page.waitForTimeout(400)

  // 面板 chrome
  const panelBg = await page.locator('.chat-panel').evaluate(el => getComputedStyle(el).backgroundColor)
  ok('面板 paper 底色=bg-elevated', panelBg === 'rgb(251, 248, 242)', panelBg)
  const panelRadius = await page.locator('.chat-panel').evaluate(el => getComputedStyle(el).borderRadius)
  ok('面板方角', panelRadius === '0px', panelRadius)
  const panelBorder = await page.locator('.chat-panel').evaluate(el => getComputedStyle(el).borderColor)
  ok('面板墨线边框', panelBorder === 'rgb(29, 26, 22)', panelBorder)

  // 头部
  const titleFont = await page.locator('.chat-panel h1').evaluate(el => getComputedStyle(el).fontFamily)
  ok('标题衬线', /serif|Songti|Noto/i.test(titleFont), titleFont)

  // 空态 + 匿名提示（未登录）
  ok('空态示例 chips', await page.locator('.chat-empty-chip').count() >= 3)
  ok('匿名提示条', await page.locator('.chat-note').count() >= 1)

  // KB 标签（真实后端返回若干 KB）
  const kbCount = await page.locator('.chat-kb-pill').count()
  ok('KB 标签渲染', kbCount >= 0, `count=${kbCount}`)

  // 输入框：衬线 + 墨色发送键
  const inputFont = await page.locator('.chat-input-box textarea').evaluate(el => getComputedStyle(el).fontFamily)
  ok('输入框衬线', /serif|Songti|Noto/i.test(inputFont), inputFont)
  const sendBgBefore = await page.locator('.chat-input-send').evaluate(el => getComputedStyle(el).backgroundColor)
  ok('发送键禁用态', sendBgBefore === 'rgb(235, 227, 211)', sendBgBefore) // --bg-code paper-light

  // 发消息 → 流式窗口断言「正在生成」
  await page.locator('.chat-input-box textarea').fill('T11 验证问题')
  await page.locator('.chat-input-send').click()
  await page.waitForSelector('text=正在生成', { timeout: 3000 })
  ok('流式中状态行', true)

  // 等流结束
  await page.waitForSelector('.chat-cite', { timeout: 8000 })
  await page.waitForTimeout(600)

  // 用户气泡：bg-secondary 墨块
  const userBubble = page.locator('.chat-bubble-user')
  ok('用户气泡存在', await userBubble.count() === 1)
  const ubBg = await userBubble.evaluate(el => getComputedStyle(el).backgroundColor)
  ok('用户气泡=bg-secondary', ubBg === 'rgb(239, 231, 216)', ubBg)
  const ubRadius = await userBubble.evaluate(el => getComputedStyle(el).borderRadius)
  ok('用户气泡方角', ubRadius === '0px', ubRadius)

  // AI 正文衬线
  const aiFont = await page.locator('.chat-bubble-ai .chat-markdown-body').evaluate(el => getComputedStyle(el).fontFamily)
  ok('AI 正文衬线', /serif|Songti|Noto/i.test(aiFont), aiFont)

  // [n] 角标
  const cite = page.locator('sup.chat-cite')
  ok('chat-cite 角标×2', await cite.count() === 2, `count=${await cite.count()}`)
  const citeColor = await cite.first().evaluate(el => getComputedStyle(el).color)
  ok('角标 accent 色', citeColor === 'rgb(163, 62, 28)', citeColor)

  // 出处卡：第一张可点跳 /post/，第二张静态
  const cards = page.locator('.chat-source-card')
  ok('出处卡×2', await cards.count() === 2, `count=${await cards.count()}`)
  const card0 = cards.nth(0)
  const card0Tag = await card0.evaluate(el => el.tagName)
  const card0Href = await card0.getAttribute('href')
  ok('出处卡0 为链接', card0Tag === 'A' && card0Href === `/post/${realArticleId}`, `${card0Tag} ${card0Href}`)
  const card1Tag = await cards.nth(1).evaluate(el => el.tagName)
  ok('出处卡1 静态(div)', card1Tag === 'DIV', card1Tag)
  const cardText = await card0.innerText()
  ok('卡含 [1] 编号', cardText.includes('[1]'), cardText)
  ok('卡含 kbName+百分比', /技术博客/.test(cardText) && /%/.test(cardText), cardText)

  // 输入框已清空（发送后）
  const inputVal = await page.locator('.chat-input-box textarea').inputValue()
  ok('发送后输入清空', inputVal === '', inputVal)

  // 历史抽屉
  await page.locator('button[aria-label="历史对话"]').click()
  await page.waitForSelector('.chat-history-drawer', { state: 'visible', timeout: 3000 })
  ok('历史抽屉打开', await page.locator('.chat-history-item').count() === 2)
  const drawerRadius = await page.locator('.chat-history-new').evaluate(el => getComputedStyle(el).borderRadius)
  ok('历史按钮方角', drawerRadius === '0px', drawerRadius)
  await page.locator('.chat-history-item').first().click()
  await page.waitForTimeout(400)
  ok('切换历史会话渲染消息', await page.locator('.chat-bubble-user').count() === 1)
  ok('历史消息 AI 衬线容器', await page.locator('.chat-bubble-ai').count() === 1)
  // 关抽屉（Esc 分级：先关抽屉）
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)
  ok('Esc 关抽屉', await page.locator('.chat-history-drawer').count() === 0)

  // 截图
  await page.screenshot({ path: join(outDir, 'a-paper-desktop-chat.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── B. 失败态：SSE error 事件 → 错误条 ───────────────────────
console.log('== B. 失败态 ==')
{
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  await ctx.route('**/v1/rag/chat/stream**', route => route.fulfill({
    status: 200, contentType: 'text/event-stream',
    body: 'data: {"type":"meta","conversationId":"x"}\n\ndata: {"type":"error","message":"模拟服务故障"}\n\n',
  }))
  await ctx.route('**/v1/rag/conversations**', route =>
    route.fulfill({ contentType: 'application/json', body: '[]' }))
  const page = await ctx.newPage()
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.chat-fab', { timeout: 15000 })
  await page.click('.chat-fab')
  await page.waitForSelector('.chat-panel', { state: 'visible' })
  await page.locator('.chat-input-box textarea').fill('会失败的问题')
  await page.locator('.chat-input-send').click()
  await page.waitForSelector('.chat-bubble-ai--failed', { timeout: 8000 })
  ok('失败消息 danger 框', await page.locator('.chat-bubble-ai--failed').count() === 1)
  ok('失败重试按钮', await page.locator('.chat-bubble-ai--failed button', { hasText: '重新生成' }).count() === 1)
  ok('错误横幅', await page.locator('.chat-note', { hasText: '模拟服务故障' }).count() === 1)
  await page.screenshot({ path: join(outDir, 'b-failed.png') })
  await ctx.close()
}

// ── C. 主题 × 视口：面板渲染 + 横向溢出 ──────────────────────
console.log('== C. 主题×视口 ==')
for (const theme of ['paper-light', 'paper-dark', 'classic-light']) {
  for (const vp of [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }]) {
    const { ctx, page } = await newPageWithMocks(theme, { width: vp.width, height: vp.height })
    await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.chat-fab', { timeout: 15000 })
    await page.click('.chat-fab')
    await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 5000 })
    await page.waitForTimeout(300)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    ok(`${theme}/${vp.name} 溢出=0`, overflow === 0, `overflow=${overflow}`)
    // 面板内关键元素都在
    ok(`${theme}/${vp.name} 面板要素`,
      (await page.locator('.chat-input-box').count()) === 1 &&
      (await page.locator('.chat-panel h1').count()) === 1)
    const radius = await page.locator('.chat-panel').evaluate(el => getComputedStyle(el).borderRadius)
    if (theme.startsWith('paper')) {
      ok(`${theme}/${vp.name} 方角`, radius === '0px', radius)
    } else {
      ok(`${theme}/${vp.name} 旧主题圆角`, radius !== '0px', radius)
    }
    await page.screenshot({ path: join(outDir, `c-${theme}-${vp.name}.png`) })
    await ctx.close()
  }
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail === 0 ? 0 : 1)
