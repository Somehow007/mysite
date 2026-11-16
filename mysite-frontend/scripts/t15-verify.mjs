// T15 验证：匿名聊天死代码清理 —— 登录门 + visitorId 语义 + 死按钮移除
// 用法: node scripts/t15-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://127.0.0.1:5173）+ playwright。
// 后端不在时用 route mock 模拟登录态（/v1/auth/me + /v1/rag/*）。
// 覆盖：匿名登录门（桌面/移动）、零 RAG 请求、登录跳转+标记、
//       登录态面板正常（历史钮/KB栏/输入框/额度文案）、横向溢出。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t15')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

async function newPage({ viewport = { width: 1440, height: 900 }, loggedIn = false } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript((li) => {
    try {
      if (li) localStorage.setItem('mysite_access_token', JSON.stringify('fake-token'))
    } catch {}
  }, loggedIn)
  const page = await ctx.newPage()
  const errors = []
  const ragRequests = []
  page.on('pageerror', e => errors.push(String(e)))
  page.on('request', r => { if (r.url().includes('/v1/rag/')) ragRequests.push(r.url()) })
  if (loggedIn) {
    // 模拟登录态：/v1/auth/me 返回 USER；KB 列表 / 会话历史返回空/样例
    await page.route('**/v1/auth/me', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'ok', data: {
        id: '1', username: 'testuser', realName: '测试用户', role: 'USER',
      } }),
    }))
    await page.route('**/v1/rag/knowledge-bases**', route => route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'ok', data: [
        { id: '1001', name: '博客文章', description: '', enabled: true },
      ] }),
    }))
    await page.route('**/v1/rag/conversations**', route => route.fulfill({
      status: 200, contentType: 'application/json', body: '[]',
    }))
  }
  return { ctx, page, errors, ragRequests }
}

const overflowOf = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)

// FAB 只在非首页/文章页出现；用 /about（前台页，后端挂了也能渲染）
async function openChat(page) {
  await page.goto(base + '/about', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  const fab = page.locator('.chat-fab')
  await fab.waitFor({ state: 'visible', timeout: 8000 })
  await fab.click()
  await page.waitForTimeout(400)
}

// ── A. 匿名桌面：登录门 ─────────────────────────────────────
console.log('== A. 匿名 1440×900：登录门 ==')
{
  const { ctx, page, errors, ragRequests } = await newPage()
  await openChat(page)
  const panel = page.locator('.chat-panel')
  ok('面板已打开', await panel.isVisible())
  ok('登录门标题可见', await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).isVisible())
  ok('游客额度文案不存在', !(await page.locator('.chat-panel').getByText('游客额度').count()))
  ok('匿名提示条不存在', !(await page.locator('.chat-panel').getByText('享受更多额度').count()))
  ok('输入框不存在', !(await page.locator('.chat-panel textarea').count()))
  ok('历史按钮不存在', !(await page.locator('.chat-panel [aria-label="历史对话"]').count()))
  ok('新会话按钮不存在', !(await page.locator('.chat-panel [aria-label="新会话"]').count()))
  ok('面板期间零 RAG 请求', ragRequests.length === 0, ragRequests.join(','))
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'a-anon-gate-1440.png') })

  // 点击登录 → 跳 /login?redirect + sessionStorage 标记
  await page.locator('.chat-panel').getByRole('button', { name: '登录 / 注册' }).click()
  await page.waitForTimeout(500)
  ok('跳转登录页', page.url().includes('/login'), page.url())
  ok('redirect 参数带回 /about', page.url().includes('redirect='), page.url())
  const flag = await page.evaluate(() => sessionStorage.getItem('mysite_reopen_chat'))
  ok('reopen 标记已写入', flag === '1', String(flag))
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── B. 匿名移动：sheet 登录门 ────────────────────────────────
console.log('== B. 匿名 390×844：sheet 登录门 ==')
{
  const { ctx, page, errors } = await newPage({ viewport: { width: 390, height: 844 } })
  await openChat(page)
  ok('登录门标题可见', await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).isVisible())
  ok('拖拽把手存在（sheet 形态）', await page.locator('.chat-panel .w-9.h-1').count() > 0)
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'b-anon-gate-390.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── C. 登录态桌面：面板正常 ─────────────────────────────────
console.log('== C. 登录态 1440×900：面板正常 ==')
{
  const { ctx, page, errors } = await newPage({ loggedIn: true })
  await openChat(page)
  // 等 userStore.init → /v1/auth/me mock 生效
  await page.waitForTimeout(800)
  ok('登录门不存在', !(await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).count()))
  ok('输入框存在', await page.locator('.chat-panel textarea').count() === 1)
  ok('额度文案=USER 10次/小时', await page.locator('.chat-panel').getByText('剩余额度 10 次/小时').isVisible())
  ok('历史按钮存在', await page.locator('.chat-panel [aria-label="历史对话"]').count() === 1)
  ok('新会话按钮存在', await page.locator('.chat-panel [aria-label="新会话"]').count() === 1)
  ok('KB 标签渲染（mock 1 个）', await page.locator('.chat-panel .chat-kb-pill').count() === 1)
  ok('空态建议问题存在', await page.locator('.chat-panel').getByText('介绍一下这个博客').count() === 1)
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'c-login-panel-1440.png') })

  // 历史抽屉：打开 → 无重命名/删除按钮
  await page.locator('.chat-panel [aria-label="历史对话"]').click()
  await page.waitForTimeout(400)
  ok('历史抽屉打开', await page.locator('.chat-history-drawer').isVisible())
  ok('历史区无重命名按钮', !(await page.locator('.chat-history-drawer [title="重命名"]').count()))
  ok('历史区无删除按钮', !(await page.locator('.chat-history-drawer [title="删除"]').count()))
  ok('新建对话按钮存在', await page.locator('.chat-history-drawer').getByText('新建对话').isVisible())
  await page.screenshot({ path: join(outDir, 'c-login-history-1440.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── D. 登录态移动：sheet 正常 ────────────────────────────────
console.log('== D. 登录态 390×844：sheet 正常 ==')
{
  const { ctx, page, errors } = await newPage({ loggedIn: true, viewport: { width: 390, height: 844 } })
  await openChat(page)
  await page.waitForTimeout(800)
  ok('输入框存在', await page.locator('.chat-panel textarea').count() === 1)
  ok('登录门不存在', !(await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).count()))
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'd-login-sheet-390.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── E. paper 主题下登录门 ────────────────────────────────────
console.log('== E. paper-light 主题：登录门 ==')
{
  const { ctx, page, errors } = await newPage()
  // mysite_theme_v2 存原始主题 id（useStorage any 序列化器不加引号），不是 JSON
  await ctx.addInitScript(() => { try { localStorage.setItem('mysite_theme_v2', 'paper-light') } catch {} })
  await openChat(page)
  ok('登录门标题可见', await page.locator('.chat-panel').locator('p.font-serif', { hasText: '登录后使用 AI 助手' }).isVisible())
  ok('data-theme=paper-light', await page.evaluate(() => document.documentElement.dataset.theme) === 'paper-light')
  ok('横向溢出=0', (await overflowOf(page)) === 0)
  await page.screenshot({ path: join(outDir, 'e-anon-gate-paper-1440.png') })
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} pass / ${fail} fail`)
process.exit(fail ? 1 : 0)
