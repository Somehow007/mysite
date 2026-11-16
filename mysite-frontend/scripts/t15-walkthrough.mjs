// T15 走查（DOM 级二次复核）：匿名聊天清理 —— 登录门 + 管道保留验证
// 用法: node scripts/t15-walkthrough.mjs [baseUrl]
// 依赖 dev server + playwright；全部后端交互走 route mock。
// 覆盖（首轮 verify 之外）：
//   A. 登录回跳自动重开 e2e（真实表单提交 → redirect → 面板自开，flag 单次消费）
//   B. 历史抽屉有数据态（列表渲染 → 选中 → 消息回填；无重命名/删除）
//   C. 角色额度文案（USER/CREATOR/ADMIN）
//   D. SSE 请求 visitorId 仍透传（管道保留证据）+ 流式渲染
//   E. 关闭路径：Esc / 遮罩 / 重复开合
//   F. 移动端文章页 dock「问这篇」→ 登录门 + 「✦ 问这篇文章」标题
//   G. paper-dark 主题下登录门
//   H. 首页 FAB 隐藏回归
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t15-walk')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()
const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)

function jsonRes(data, wrap = true) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify(wrap ? { code: '0', message: 'ok', data } : data) }
}

// user: null=匿名（不注入 token，也不 mock /me）；对象=登录态
async function newPage({ viewport = { width: 1440, height: 900 }, user = null, theme = null } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(({ u, t }) => {
    try {
      if (u) localStorage.setItem('mysite_access_token', JSON.stringify('fake-token'))
      if (t) localStorage.setItem('mysite_theme_v2', t)
    } catch {}
  }, { u: !!user, t: theme })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))

  // 兜底：未 mock 的 /v1/* 一律 502，避免打到死掉的 8081 拖慢测试
  await page.route('**/v1/**', route => route.fulfill({ status: 502, body: '{}' }))
  if (user) {
    await page.route('**/v1/auth/me', route => route.fulfill(jsonRes({
      id: '1', username: 'testuser', realName: '测试用户', role: user,
    })))
  }
  return { ctx, page, errors }
}

async function openChat(page, path = '/about') {
  await page.goto(base + path, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  await page.locator('.chat-fab').waitFor({ state: 'visible', timeout: 8000 })
  await page.locator('.chat-fab').click()
  await page.waitForTimeout(400)
}

// ── A. 登录回跳自动重开 e2e ─────────────────────────────────
console.log('== A. 登录回跳自动重开 e2e ==')
{
  const { ctx, page, errors } = await newPage()
  await page.route('**/v1/auth/login', route => route.fulfill(jsonRes({
    accessToken: 'new-a', refreshToken: 'new-r',
  })))
  await page.route('**/v1/auth/me', route => route.fulfill(jsonRes({
    id: '1', username: 'testuser', realName: '测试用户', role: 'USER',
  })))

  await openChat(page, '/about')
  await page.locator('.chat-panel').getByRole('button', { name: '登录 / 注册' }).click()
  await page.waitForURL(/\/login/)
  ok('落在登录页', page.url().includes('/login'), page.url())

  // 真实表单提交 → LoginView.onLoginSuccess → fetchCurrentUser → redirect → watcher 消费 flag
  await page.locator('#login-username').fill('testuser')
  await page.locator('#login-password').fill('whatever')
  await page.locator('button[type="submit"]').click()
  await page.waitForURL(/\/about/, { timeout: 8000 })
  ok('登录后回跳 /about', page.url().includes('/about'), page.url())

  await page.waitForTimeout(600)
  ok('面板自动重开', await page.locator('.chat-panel').isVisible())
  ok('门已消失（正常聊天面）', !(await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).count()))
  ok('输入框回归', await page.locator('.chat-panel textarea').count() === 1)
  const flag = await page.evaluate(() => sessionStorage.getItem('mysite_reopen_chat'))
  ok('flag 已单次消费', flag === null, String(flag))
  await page.screenshot({ path: join(outDir, 'a-reopen-1440.png') })

  // flag 已消费 → 关闭面板再导航，不应重开
  await page.locator('.chat-panel [aria-label="关闭"]').click()
  await page.goto(base + '/collections', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  ok('换页后不再自开', !(await page.locator('.chat-panel').count()))
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── B. 历史抽屉有数据态 ──────────────────────────────────────
console.log('== B. 历史列表 → 选中 → 消息回填 ==')
{
  const { ctx, page, errors } = await newPage({ user: 'USER' })
  await page.route('**/v1/rag/knowledge-bases**', route => route.fulfill(jsonRes([
    { id: '1', name: '博客文章', enabled: true },
  ])))
  // 会话接口按 URL 分流（glob 不跨 query string；列表/消息均为裸数组，后端不包 Result）
  await page.route('**/v1/rag/conversations**', route => {
    const url = route.request().url()
    route.fulfill(jsonRes(url.includes('/messages')
      ? [
        { role: 'USER', content: '历史上文问题' },
        { role: 'ASSISTANT', content: '历史上文回答' },
      ]
      : [
        { id: '101', title: 'JWT 怎么配', messageCount: 4, createTime: '2026-11-15 10:00', updateTime: '2026-11-15 12:00' },
        { id: '102', title: 'pgvector 索引', messageCount: 2, createTime: '2026-11-14 10:00', updateTime: '2026-11-14 11:00' },
      ], false))
  })

  await openChat(page)
  await page.waitForTimeout(600)
  await page.locator('.chat-panel [aria-label="历史对话"]').click()
  await page.waitForTimeout(400)
  ok('抽屉渲染 2 条历史', await page.locator('.chat-history-item').count() === 2)
  ok('含会话标题', await page.locator('.chat-history-item').first().getByText('JWT 怎么配').isVisible())

  await page.locator('.chat-history-item').first().click()
  await page.waitForTimeout(500)
  ok('抽屉关闭', !(await page.locator('.chat-history-drawer').isVisible()))
  ok('历史消息已回填', await page.locator('.chat-panel').getByText('历史上文问题').isVisible()
    && await page.locator('.chat-panel').getByText('历史上文回答').isVisible())
  ok('无重命名入口', !(await page.locator('.chat-history-item [title="重命名"]').count())
    && !(await page.locator('.chat-history-item [title="删除"]').count()))
  await page.screenshot({ path: join(outDir, 'b-history-1440.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── C. 角色额度文案 ─────────────────────────────────────────
console.log('== C. 角色额度文案 ==')
for (const [role, expect] of [['USER', '剩余额度 10 次/小时'], ['CREATOR', '剩余额度 20 次/小时'], ['ADMIN', '无限额度']]) {
  const { ctx, page } = await newPage({ user: role })
  await openChat(page)
  await page.waitForTimeout(700)
  ok(`${role} → ${expect}`, await page.locator('.chat-panel').getByText(expect).isVisible())
  await ctx.close()
}

// ── D. SSE：visitorId 仍透传 + 流式渲染 ─────────────────────
console.log('== D. SSE visitorId 透传 + 流式 ==')
{
  const { ctx, page, errors } = await newPage({ user: 'USER' })
  await page.route('**/v1/rag/conversations**', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  let streamUrl = null
  await page.route('**/v1/rag/chat/stream**', route => {
    streamUrl = route.request().url()
    route.fulfill({
      status: 200, contentType: 'text/event-stream',
      body: 'data: {"type":"meta","conversationId":"999"}\n\n'
        + 'data: {"type":"sources","sources":[]}\n\n'
        + 'data: {"type":"content","delta":"走查"}\n\n'
        + 'data: {"type":"content","delta":"回答"}\n\n'
        + 'data: {"type":"done"}\n\n',
    })
  })

  await openChat(page)
  await page.waitForTimeout(700)
  const ta = page.locator('.chat-panel textarea')
  await ta.fill('验证一下')
  await page.locator('.chat-panel textarea').press('Enter')
  await page.waitForTimeout(800)
  ok('SSE 请求已发出', !!streamUrl)
  ok('URL 带 visitorId', !!streamUrl && streamUrl.includes('visitorId='), streamUrl ?? 'none')
  ok('非文章页不带 articleId', !!streamUrl && !streamUrl.includes('articleId='))
  ok('流式内容渲染', await page.locator('.chat-panel').getByText('走查回答').isVisible())
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── E. 关闭路径与重复开合 ────────────────────────────────────
console.log('== E. Esc / 遮罩 / 重复开合 ==')
{
  const { ctx, page, errors } = await newPage({ viewport: { width: 390, height: 844 } })
  await openChat(page)
  ok('sheet 已开', await page.locator('.chat-panel').isVisible())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  ok('Esc 关闭', !(await page.locator('.chat-panel').count()))
  // 再次打开（FAB 回到非 post 页 /about → 移动也显示 FAB）
  await page.locator('.chat-fab').click()
  await page.waitForTimeout(400)
  ok('重开仍见门', await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).isVisible())
  // 点遮罩关闭：sheet 顶留 ~24px 缝；遮罩 z-50 后该缝（0..24px）顶层元素=遮罩
  const hitEl = await page.evaluate(() => {
    const el = document.elementFromPoint(195, 12)
    return el?.className?.toString().includes('bg-black/40') ? 'mask' : (el?.className?.toString().slice(0, 60) ?? 'null')
  })
  ok('缝隙处顶层=遮罩', hitEl === 'mask', hitEl)
  await page.mouse.click(195, 12)
  await page.waitForTimeout(400)
  ok('遮罩点击关闭', !(await page.locator('.chat-panel').count()))
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── F. 移动文章页 dock「问这篇」→ 登录门 + 文章标题 ───────────
console.log('== F. dock 问这篇（匿名）==')
{
  const { ctx, page, errors } = await newPage({ viewport: { width: 390, height: 844 } })
  await page.route('**/v1/articles/123', route => route.fulfill(jsonRes({
    id: '123', title: '走查测试文章', summary: '', content: '第一段。\n\n第二段。',
    coverImage: null, categoryId: null, categoryName: '测试', categorySlug: 'test',
    authorId: '1', authorName: '作者', viewCount: 1, favoriteCount: 0,
    tags: null, createTime: '2026-01-01 10:00:00', updateTime: '2026-01-01 10:00:00',
  })))
  await page.goto(base + '/post/123', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const askBtn = page.locator('.post-dock-btn.post-dock-ask')
  ok('dock 问这篇可见', await askBtn.isVisible())
  await askBtn.click()
  await page.waitForTimeout(500)
  ok('面板打开', await page.locator('.chat-panel').isVisible())
  ok('标题=✦ 问这篇文章', await page.locator('.chat-panel').getByText('✦ 问这篇文章').isVisible())
  ok('副标题带文章标题', await page.locator('.chat-panel').getByText('走查测试文章').isVisible())
  ok('登录门在', await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).isVisible())
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'f-dock-gate-390.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── G. paper-dark 登录门 ────────────────────────────────────
console.log('== G. paper-dark 登录门 ==')
{
  const { ctx, page, errors } = await newPage({ theme: 'paper-dark' })
  await openChat(page)
  ok('门标题可见', await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).isVisible())
  const gateBtn = page.locator('.chat-panel').getByRole('button', { name: '登录 / 注册' })
  ok('登录按钮可交互', await gateBtn.isEnabled())
  await page.screenshot({ path: join(outDir, 'g-gate-dark-1440.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── H. FAB 显隐回归 ──────────────────────────────────────────
console.log('== H. FAB 显隐回归 ==')
{
  const { ctx, page } = await newPage()
  await page.goto(base + '/', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  ok('首页无 FAB', !(await page.locator('.chat-fab').count()))
  await page.goto(base + '/about', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  ok('/about 有 FAB', await page.locator('.chat-fab').count() === 1)
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} pass / ${fail} fail`)
process.exit(fail ? 1 : 0)
