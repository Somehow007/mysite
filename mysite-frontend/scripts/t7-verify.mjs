// T7 验证：提问区 + ChatWidget launcher
// 用法: node scripts/t7-verify.mjs [baseUrl]
// 依赖 dev server 已启动（默认 http://localhost:5173）和 playwright（devDependency）。
// B 段需要一个 USER 角色账号：脚本会先自注册（已存在则复用），凭据可用
// T7_USER / T7_PASS 环境变量覆盖；SSE 流为 mock，不消耗真实 LLM。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t7')
mkdirSync(outDir, { recursive: true })

const T7_USER = process.env.T7_USER ?? 't7check'
const T7_PASS = process.env.T7_PASS ?? 'T7check!2026'

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

// ── A. 匿名流程 ─────────────────────────────────────────────
{
  console.log('== A. 匿名：面板渲染 + 提问 → 登录回跳 ==')
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))

  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=不想翻目录', { timeout: 15000 })
  await page.waitForTimeout(800) // KB 列表加载

  ok('提问区标题', await page.locator('h2', { hasText: '不想翻目录' }).count() === 1)
  ok('副标题 accent', await page.locator('text=直接问。').count() >= 1)
  ok('范围标签', await page.locator('text=全部文章').count() >= 1)
  ok('发送按钮', await page.locator('button', { hasText: '提问' }).count() >= 1)
  const ask = page.locator('section', { hasText: '不想翻目录' }).first()
  const chips = ask.locator('button.border-dashed')
  ok('示例 chips=4（旧后端走前端兜底）', await chips.count() === 4, `got ${await chips.count()}`)
  ok('额度文案', await page.locator('text=登录后使用 · 每小时 10 次').count() === 1)
  ok('FAB 不渲染（首页）', await page.locator('button[aria-label="打开 AI 助手"]').count() === 0)
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  ok('横向溢出=0', overflow === 0, `overflow=${overflow}`)
  await page.screenshot({ path: join(outDir, 'a-home-ask.png'), fullPage: false })
  await page.screenshot({ path: join(outDir, 'a-home-full.png'), fullPage: true })

  // chip 点击填入输入框
  await chips.first().click()
  const inputVal = await page.locator('input[placeholder*="LLM 熔断"]').inputValue()
  ok('chip 填充输入框', inputVal === 'LLM 熔断怎么做的', `got "${inputVal}"`)

  // 提交 → 登录页 + stash
  await ask.locator('input').fill('T7 测试问题：熔断策略是什么')
  await ask.locator('button', { hasText: '提问' }).click()
  await page.waitForURL('**/login**', { timeout: 5000 })
  ok('跳转 /login?redirect=/', page.url().includes('/login') && page.url().includes('redirect='), page.url())
  const stash = await page.evaluate(() => sessionStorage.getItem('mysite_pending_ask'))
  ok('sessionStorage 暂存', stash?.includes('T7 测试问题'), stash ?? 'null')

  // 未登录回首页：stash 被消费但不自动打开（无 session）
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const stashAfter = await page.evaluate(() => sessionStorage.getItem('mysite_pending_ask'))
  ok('stash 已清理', stashAfter === null, stashAfter ?? 'null')
  ok('未登录不自动开面板', await page.locator('.chat-panel').count() === 0)

  console.log('console/pageerror:', errors.length ? errors : 'none')
  await ctx.close()
}

// ── B. 登录回跳全流程（真实登录 + mock SSE）────────────────
{
  console.log('== B. 登录 → 回首页自动打开并发送 ==')
  // 确保测试账号存在（已存在时 register 返回错误，忽略即可）
  await fetch(`${base}/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: T7_USER, password: T7_PASS,
      email: `${T7_USER}@example.com`, realName: 'T7 Check', phoneNumber: '13800000000',
    }),
  }).catch(() => {})

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  // SSE mock：固定 meta → sources → content → done，消除真实 LLM 时延/抖动
  await ctx.route('**/v1/rag/chat/stream**', route => route.fulfill({
    status: 200,
    contentType: 'text/event-stream',
    body: [
      'data: {"type":"meta","conversationId":"t7-mock-conv"}', '',
      'data: {"type":"sources","sources":[]}', '',
      'data: {"type":"content","delta":"这是 T7 验证的模拟回复：熔断策略在连续失败后打开，冷却结束后首次成功即恢复路由。该内容用于断言流式渲染。"}', '',
      'data: {"type":"done"}', '', '',
    ].join('\n'),
  }))
  const page = await ctx.newPage()

  // 先制造 stash：匿名提交一次
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=不想翻目录', { timeout: 15000 })
  await page.waitForTimeout(800)
  const askB = page.locator('section', { hasText: '不想翻目录' }).first()
  await askB.locator('input').fill('T7 登录回跳测试：这个博客讲了什么')
  await askB.locator('button', { hasText: '提问' }).click()
  await page.waitForURL('**/login**', { timeout: 5000 })

  // UI 登录
  await page.fill('#login-username', T7_USER)
  await page.fill('#login-password', T7_PASS)
  await page.click('button[type="submit"]')
  await page.waitForURL(`${base}/`, { timeout: 10000 })
  ok('登录后回到首页', page.url().replace(/\/$/, '') === base, page.url())

  // 面板应自动打开且发出问题（等 chat-panel + 用户消息出现）
  await page.waitForSelector('.chat-panel', { timeout: 10000 })
  ok('聊天面板自动打开', true)
  await page.waitForSelector('text=T7 登录回跳测试', { timeout: 10000 })
  ok('用户消息已发出', true)
  // 等 assistant 气泡出现实质内容（justify-start = AI 消息；空 pending 不计）
  const gotAnswer = await page.waitForFunction(() => {
    const msgs = document.querySelectorAll('.chat-panel .justify-start')
    return [...msgs].some(el => el.textContent.trim().length > 30)
  }, { timeout: 90000 }).then(() => true).catch(() => false)
  ok('AI 流式回复有内容', gotAnswer)
  // 等流式结束（「正在生成」消失，最长再等 60s）
  await page.waitForFunction(() =>
    !document.querySelector('.chat-panel')?.textContent.includes('正在生成'),
    { timeout: 60000 }).catch(() => {})
  const panelText = await page.locator('.chat-panel').innerText()
  ok('面板无生成中残留', !panelText.includes('正在生成'), '')
  await page.screenshot({ path: join(outDir, 'b-after-login-chat.png') })
  await ctx.close()
}

// ── C. launcher API：预填 / 引用 / FAB 规则回归 ─────────────
{
  console.log('== C. launcher.open + FAB 显隐 ==')
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  const page = await ctx.newPage()

  // /about：FAB 应可见，点击打开面板（回归）
  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const fab = page.locator('button[aria-label="打开 AI 助手"]')
  ok('FAB 在 /about 可见', await fab.count() === 1)
  await fab.click()
  await page.waitForSelector('.chat-panel', { timeout: 5000 })
  ok('FAB 打开面板（回归）', true)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  ok('Esc 关闭面板', await page.locator('.chat-panel').count() === 0)

  // /post/:id：FAB 隐藏
  const postHref = await page.evaluate(async () => {
    const r = await fetch('/v1/articles?page=1&size=1&sortField=createTime&sortOrder=desc')
    const j = await r.json()
    return j?.data?.records?.[0]?.id ?? j?.data?.list?.[0]?.id ?? null
  })
  if (postHref) {
    await page.goto(`${base}/post/${postHref}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(800)
    ok('FAB 在文章页隐藏', await page.locator('button[aria-label="打开 AI 助手"]').count() === 0)
  } else {
    ok('FAB 在文章页隐藏', false, 'no post id')
  }

  // /page/2：首页分页页 FAB 隐藏
  await page.goto(`${base}/page/2`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  ok('FAB 在 /page/2 隐藏', await page.locator('button[aria-label="打开 AI 助手"]').count() === 0)
  ok('/page/2 无提问区', await page.locator('text=不想翻目录').count() === 0)

  // launcher 模块直调：预填问题（不发送）
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  await page.evaluate(async () => {
    const m = await import('/src/composables/useChatLauncher.ts')
    m.useChatLauncher().open({ question: '预填测试问题ABC' })
  })
  await page.waitForSelector('.chat-panel', { timeout: 5000 })
  await page.waitForTimeout(400)
  const taVal = await page.locator('.chat-panel textarea').inputValue()
  ok('launcher 预填输入框', taVal === '预填测试问题ABC', `got "${taVal}"`)

  // 引用条
  await page.evaluate(async () => {
    const m = await import('/src/composables/useChatLauncher.ts')
    m.useChatLauncher().open({ quote: '这是一段引用文本XYZ' })
  })
  await page.waitForTimeout(400)
  ok('引用条渲染', await page.locator('.chat-panel', { hasText: '这是一段引用文本XYZ' }).count() === 1)
  // 删除引用
  await page.locator('.chat-panel button[aria-label="删除引用"]').click()
  await page.waitForTimeout(200)
  ok('引用可删除', await page.locator('.chat-panel', { hasText: '这是一段引用文本XYZ' }).count() === 0)
  await page.keyboard.press('Escape')
  await ctx.close()
}

// ── D. 配置驱动状态（mock）─────────────────────────────────
{
  console.log('== D. askDemo 渲染 + KB 空降级 ==')
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })

  // mock site config 注入 askDemo
  await ctx.route('**/v1/site/config', async route => {
    const res = await route.fetch()
    const json = await res.json()
    json.data.askDemo = {
      question: '你是怎么处理 LLM 熔断的？',
      answer: '连续 2 次失败后打开熔断，首次成功即重置冷却[1]。路由只在首个 token 之前切换 provider[2]。',
      sources: [
        { title: '示例文章一：pgvector 实践', section: '§4 多模型路由' },
        { title: '示例文章二：取消信号', section: '§2 首 token 边界', articleId: '1' },
      ],
    }
    json.data.askExamples = ['自定义问题一', '自定义问题二']
    await route.fulfill({ response: res, json })
  })
  const page = await ctx.newPage()
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=不想翻目录', { timeout: 15000 })
  await page.waitForTimeout(800)
  ok('askDemo 示例块渲染', await page.locator('text=示例 · 你是怎么处理 LLM 熔断的？').count() === 1)
  const askSection = page.locator('section', { hasText: '不想翻目录' }).first()
  const supCount = await askSection.locator('sup').count()
  ok('answer 内 sup 角标=2', supCount === 2, `got ${supCount}`)
  ok('出处卡标题1', await askSection.locator('text=[1] 示例文章一：pgvector 实践').count() === 1)
  ok('出处卡 section', await askSection.locator('text=§4 多模型路由').count() === 1)
  const refLinks = await askSection.locator('a[href^="/post/"]').count()
  ok('articleId 卡渲染为链接(1个)', refLinks === 1, `got ${refLinks}`)
  const customChips = await askSection.locator('button.border-dashed').allInnerTexts()
  ok('askExamples 覆盖 chips=2', customChips.length === 2 && customChips.includes('自定义问题一'), JSON.stringify(customChips))
  await page.screenshot({ path: join(outDir, 'd-askdemo.png'), fullPage: false })
  await ctx.close()

  // mock KB 空 → 降级
  const ctx2 = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx2.route('**/v1/rag/knowledge-bases', route =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: '0', message: null, data: [] }) }))
  const page2 = await ctx2.newPage()
  await page2.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
  await page2.waitForSelector('text=不想翻目录', { timeout: 15000 })
  await page2.waitForTimeout(800)
  ok('无 KB 降级提示', await page2.locator('text=AI 助手暂未开放').count() === 1)
  ok('降级后无输入框', await page2.locator('input[placeholder*="LLM 熔断"]').count() === 0)
  await page2.screenshot({ path: join(outDir, 'd-degraded.png'), fullPage: false })
  await ctx2.close()
}

// ── E. 三主题截图 ─────────────────────────────────────────
{
  console.log('== E. 三主题截图 ==')
  for (const theme of ['paper-light', 'paper-dark', 'classic-light']) {
    for (const vp of [{ w: 1440, h: 900, n: 'd' }, { w: 390, h: 844, n: 'm' }]) {
      const ctx = await browser.newContext({ viewport: { width: vp.w, height: vp.h } })
      await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
      const page = await ctx.newPage()
      await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('text=不想翻目录', { timeout: 15000 })
      await page.waitForTimeout(800)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      ok(`溢出=0 ${theme} ${vp.n}`, overflow === 0, `overflow=${overflow}`)
      // ask 区块单独截图
      const sec = page.locator('section', { hasText: '不想翻目录' }).first()
      await sec.screenshot({ path: join(outDir, `e-ask-${theme}-${vp.n}.png`) })
      await ctx.close()
    }
  }
}

await browser.close()
console.log(`\n== RESULT: ${pass} pass, ${fail} fail ==`)
process.exit(fail ? 1 : 0)
