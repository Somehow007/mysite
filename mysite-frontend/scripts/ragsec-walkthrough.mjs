// RAG 会话安全阶段二 走查：归属切换（user_id 主/visitorId 兜底）+ rename/delete 端点 + ChatHistory UI 恢复
// 用法: node scripts/ragsec-walkthrough.mjs [baseUrl]
// 依赖 dev server + playwright；后端交互走 route mock（wire 格式 = Result 包装）。
// 覆盖：
//   A. 历史列表 Result 解包渲染 + visitorId 参数透传
//   B. hover 显示重命名/删除按钮
//   C. 重命名流程：pencil → inline input → Enter → PUT（URL 带 visitorId、body title）→ 列表重拉
//   D. Esc 取消编辑 / 空标题不发请求
//   E. 删除流程：trash → danger confirm → DELETE 带 visitorId → 列表移除
//   F. 删除当前会话 → 消息区清空 + 新建态
//   G. rename/delete 失败（A120100）→ error toast，列表不变
//   H. 非属主 getMessages 空 → 切会话渲染空不报错
//   I. 匿名登录门回归：不请求 conversations
//   J. 移动端视口回归（历史抽屉可开合）
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

// 注意：localhost → ::1 可能被其他项目 vite 占用，用 127.0.0.1 直连 MySite dev server
const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/ragsec-walk')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()

const CONVS = [
  { id: '1001', title: '第一条会话', messageCount: 2, createTime: '2026-11-16T10:00:00', updateTime: '2026-11-16T10:00:00' },
  { id: '1002', title: '第二条会话', messageCount: 4, createTime: '2026-11-16T11:00:00', updateTime: '2026-11-16T11:00:00' },
]

function jsonRes(data) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: 'ok', data }) }
}

// 建页 + 登录态 + RAG 会话路由 mock。opts.convs 可传动态引用（数组内容会变）。
async function newPage({ viewport = { width: 1440, height: 900 }, convs = CONVS, msgs = {}, theme = null } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(t => {
    try {
      localStorage.setItem('mysite_access_token', JSON.stringify('fake-token'))
      if (t) localStorage.setItem('mysite_theme_v2', t)
    } catch {}
  }, theme)
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  const calls = { list: [], msgs: [], rename: [], del: [] }

  await page.route('**/v1/**', route => route.fulfill({ status: 502, body: '{}' }))
  await page.route('**/v1/auth/me', route => route.fulfill(jsonRes({
    id: '42', username: 'testuser', realName: '测试用户', role: 'USER',
  })))
  await page.route('**/v1/rag/conversations?*', route => {
    calls.list.push(route.request().url())
    return route.fulfill(jsonRes(convs))
  })
  await page.route('**/v1/rag/conversations', route => {
    calls.list.push(route.request().url())
    return route.fulfill(jsonRes(convs))
  })
  await page.route(/\/v1\/rag\/conversations\/\d+\/messages/, route => {
    const url = route.request().url()
    calls.msgs.push(url)
    const id = url.match(/conversations\/(\d+)\/messages/)[1]
    return route.fulfill(jsonRes(msgs[id] ?? []))
  })
  await page.route(/\/v1\/rag\/conversations\/\d+\/rename/, route => {
    calls.rename.push({ url: route.request().url(), body: route.request().postDataJSON() })
    return route.fulfill(route.response ?? jsonRes(null))
  })
  await page.route(/\/v1\/rag\/conversations\/\d+(\?|$)/, route => {
    if (route.request().method() === 'DELETE') {
      calls.del.push(route.request().url())
      return route.fulfill(jsonRes(null))
    }
    return route.fallback()
  })
  return { ctx, page, errors, calls }
}

async function openChatWithHistory(page, path = '/about') {
  await page.goto(base + path, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  await page.locator('.chat-fab').waitFor({ state: 'visible', timeout: 8000 })
  await page.locator('.chat-fab').click()
  await page.waitForTimeout(400)
  await page.locator('button[aria-label="历史对话"]').click()
  // 抽屉内列表是异步拉取 + Vue 重渲染，等首条渲染 + 一帧沉降，避免 hover 命中已分离节点
  await page.locator('.chat-history-item').first().waitFor({ state: 'visible', timeout: 8000 })
  await page.waitForTimeout(400)
}

// ── A. 历史列表渲染 + visitorId 透传 ─────────────────────────
console.log('== A. 历史列表 Result 解包 + visitorId 透传 ==')
{
  const { ctx, page, calls } = await newPage()
  await openChatWithHistory(page)
  const items = page.locator('.chat-history-drawer .chat-history-item')
  await items.first().waitFor({ state: 'visible', timeout: 5000 })
  ok('两条会话渲染', await items.count() === 2, String(await items.count()))
  ok('标题文本正确', (await items.first().textContent()).includes('第一条会话'))
  ok('list 请求带 visitorId', calls.list.every(u => u.includes('visitorId=')), calls.list.join('|'))
  await page.screenshot({ path: join(outDir, 'a-history.png') })
  await ctx.close()
}

// ── B. hover 显示操作按钮 ────────────────────────────────────
console.log('== B. hover 显示重命名/删除按钮 ==')
{
  const { ctx, page } = await newPage()
  await openChatWithHistory(page)
  const row = page.locator('.chat-history-drawer .group').first()
  const editBtn = row.locator('button[title="重命名"]')
  const delBtn = row.locator('button[title="删除"]')
  ok('未悬停时按钮隐藏', !(await editBtn.isVisible()) && !(await delBtn.isVisible()))
  await row.hover()
  await page.waitForTimeout(200)
  ok('悬停后按钮可见', await editBtn.isVisible() && await delBtn.isVisible())
  await ctx.close()
}

// ── C. 重命名全流程 ─────────────────────────────────────────
console.log('== C. 重命名流程 ==')
{
  const renamed = [...CONVS]
  const { ctx, page, calls } = await newPage({ convs: renamed })
  await openChatWithHistory(page)

  await page.locator('.chat-history-drawer .group').first().hover()
  await page.locator('.chat-history-drawer button[title="重命名"]').first().click()
  const input = page.locator('.chat-history-drawer input[type="text"]')
  await input.waitFor({ state: 'visible' })
  ok('输入框预填原标题', (await input.inputValue()) === '第一条会话')
  await input.fill('重命名后的标题')
  // Enter 提交
  renamed[0] = { ...renamed[0], title: '重命名后的标题' }
  await input.press('Enter')
  await page.waitForTimeout(400)
  ok('PUT /rename 调用 1 次', calls.rename.length === 1, String(calls.rename.length))
  ok('rename URL 带 visitorId', calls.rename[0]?.url.includes('visitorId='))
  ok('rename body 携带新标题', calls.rename[0]?.body?.title === '重命名后的标题', JSON.stringify(calls.rename[0]?.body))
  ok('列表重拉刷新标题', (await page.locator('.chat-history-item').first().textContent()).includes('重命名后的标题'))
  ok('toast 提示已重命名', await page.getByText('已重命名').isVisible())
  await page.screenshot({ path: join(outDir, 'c-renamed.png') })
  await ctx.close()
}

// ── D. Esc 取消 + 空标题守卫 ────────────────────────────────
console.log('== D. Esc 取消 + 空标题守卫 ==')
{
  const { ctx, page, calls } = await newPage()
  await openChatWithHistory(page)
  await page.locator('.chat-history-drawer .group').first().hover()
  await page.locator('.chat-history-drawer button[title="重命名"]').first().click()
  const input = page.locator('.chat-history-drawer input[type="text"]')
  await input.fill('被丢弃的标题')
  await input.press('Escape')
  await page.waitForTimeout(200)
  ok('Esc 后回到正常态', !(await input.isVisible()))
  ok('Esc 未发请求', calls.rename.length === 0)
  ok('Esc 不关闭抽屉', await page.locator('.chat-history-drawer').isVisible())
  // 空标题 Enter
  await page.locator('.chat-history-drawer .group').first().hover()
  await page.locator('.chat-history-drawer button[title="重命名"]').first().click()
  await page.locator('.chat-history-drawer input[type="text"]').fill('   ')
  await page.locator('.chat-history-drawer input[type="text"]').press('Enter')
  await page.waitForTimeout(300)
  ok('空标题不发请求', calls.rename.length === 0)
  await ctx.close()
}

// ── E. 删除流程（非当前会话）────────────────────────────────
console.log('== E. 删除流程 ==')
{
  const { ctx, page, calls } = await newPage()
  await openChatWithHistory(page)
  const row = page.locator('.chat-history-drawer .group').nth(1) // 第二条，非当前
  await row.hover()
  await row.locator('button[title="删除"]').click()
  // confirm modal
  const modal = page.locator('.modal-panel')
  await modal.waitFor({ state: 'visible' })
  ok('确认弹窗含标题', (await modal.textContent()).includes('第二条会话'))
  await modal.locator('.btn-danger').click()
  await page.waitForTimeout(400)
  ok('DELETE 调用 1 次', calls.del.length === 1)
  ok('DELETE URL 指向 id=1002', calls.del[0]?.includes('/conversations/1002'), calls.del[0])
  ok('DELETE URL 带 visitorId', calls.del[0]?.includes('visitorId='))
  ok('列表剩 1 条', await page.locator('.chat-history-item').count() === 1)
  ok('toast 提示已删除', await page.getByText('已删除').isVisible())
  await page.screenshot({ path: join(outDir, 'e-deleted.png') })
  await ctx.close()
}

// ── F. 删除当前会话 → 清空消息区 ────────────────────────────
console.log('== F. 删除当前会话 ==')
{
  const msgs = { '1001': [{ role: 'USER', content: '问题' }, { role: 'ASSISTANT', content: '回答' }] }
  const { ctx, page, calls } = await newPage({ msgs })
  await openChatWithHistory(page)
  // 选中第一条 → 消息回填
  await page.locator('.chat-history-item').first().click()
  await page.waitForTimeout(400)
  ok('消息回填可见', await page.getByText('回答').isVisible())
  // 重开抽屉删除当前
  await page.locator('button[aria-label="历史对话"]').click()
  await page.waitForTimeout(300)
  const row = page.locator('.chat-history-drawer .group').first()
  await row.hover()
  await row.locator('button[title="删除"]').click()
  await page.locator('.modal-panel .btn-danger').click()
  await page.waitForTimeout(400)
  ok('DELETE 指向当前 id=1001', calls.del[0]?.includes('/conversations/1001'))
  ok('当前会话从列表移除', await page.locator('.chat-history-item').count() === 1)
  // 抽屉是面板级覆盖层（盖住 header），只能用抽屉自己的 X 或遮罩关闭
  await page.locator('button[aria-label="关闭历史对话"]').click()
  await page.waitForTimeout(300)
  ok('消息区回到空态', await page.getByText('你好，我是博客 AI 助手').isVisible())
  await ctx.close()
}

// ── G. 失败路径：rename A120100 → error toast ───────────────
console.log('== G. 失败路径 ==')
{
  const { ctx, page, calls } = await newPage()
  await page.route(/\/v1\/rag\/conversations\/\d+\/rename/, route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ code: 'A120100', message: '会话不存在', data: null }),
  }))
  await openChatWithHistory(page)
  await page.locator('.chat-history-drawer .group').first().hover()
  await page.locator('.chat-history-drawer button[title="重命名"]').first().click()
  await page.locator('.chat-history-drawer input[type="text"]').fill('新标题')
  await page.locator('.chat-history-drawer input[type="text"]').press('Enter')
  await page.waitForTimeout(400)
  ok('A120100 → error toast 会话不存在', await page.getByText('会话不存在').isVisible())
  ok('列表仍 2 条', await page.locator('.chat-history-item').count() === 2)
  await ctx.close()
}

// ── H. 非属主 messages 空 → 不报错 ──────────────────────────
console.log('== H. 非属主 getMessages 返回空 ==')
{
  const { ctx, page, errors } = await newPage({ msgs: { '1001': [] } })
  await openChatWithHistory(page)
  await page.locator('.chat-history-item').first().click()
  await page.waitForTimeout(400)
  ok('切会话无异常', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── I. 匿名登录门回归 ────────────────────────────────────────
console.log('== I. 匿名登录门 ==')
{
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const convCalls = []
  await page.route('**/v1/**', route => route.fulfill({ status: 502, body: '{}' }))
  await page.route('**/v1/rag/conversations*', route => { convCalls.push(1); return route.fulfill(jsonRes([])) })
  await page.goto(base + '/about', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  await page.locator('.chat-fab').click()
  await page.waitForTimeout(400)
  ok('匿名显示登录门', await page.getByText('登录后使用 AI 助手').nth(1).isVisible())
  ok('匿名不请求 conversations', convCalls.length === 0)
  ok('匿名无历史按钮', !(await page.locator('button[aria-label="历史对话"]').isVisible()))
  await ctx.close()
}

// ── J. 移动端历史抽屉开合 ────────────────────────────────────
console.log('== J. 移动端抽屉 ==')
{
  const { ctx, page } = await newPage({ viewport: { width: 390, height: 844 } })
  await page.goto(base + '/about', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(500)
  await page.locator('.chat-fab').click()
  await page.waitForTimeout(400)
  await page.locator('button[aria-label="历史对话"]').click()
  await page.waitForTimeout(300)
  ok('移动端抽屉打开', await page.locator('.chat-history-drawer').isVisible())
  ok('移动端列表渲染', await page.locator('.chat-history-item').count() === 2)
  await page.screenshot({ path: join(outDir, 'j-mobile.png') })
  await ctx.close()
}

console.log(`\n== 结果: ${pass} pass / ${fail} fail ==`)
await browser.close()
process.exit(fail ? 1 : 0)
