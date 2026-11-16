// T12 走查：DOM 级复核（对照 t9/t11-walkthrough）
// 用法: node scripts/t12-walkthrough.mjs [baseUrl]
// 依赖 dev server（默认 http://127.0.0.1:5173）+ 后端（/v1 代理）+ playwright。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://127.0.0.1:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t12-walkthrough')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()
const DESK = { width: 1440, height: 900 }
const MOB = { width: 390, height: 844 }

// 取真实文章 id（路由回归 + notfound 往返用）
const listJson = await (await fetch(`${base}/v1/articles?current=1&size=1&published=1`)).json()
const realId = String(listJson?.data?.records?.[0]?.id ?? listJson?.data?.list?.[0]?.id ?? '')
console.log(`realId=${realId}`)

async function newPage(theme, viewport) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  page.on('console', m => { if (m.type() === 'error') errors.push(`console:${m.text()}`) })
  return { ctx, page, errors }
}

// ── W1. 合法路由不被 catch-all 吞掉 ─────────────────────────
console.log('== W1. 合法路由回归 ==')
{
  const { ctx, page, errors } = await newPage('paper-light', DESK)
  const routes = [
    ['/', 'home', () => page.locator('text=不想翻目录').or(page.locator('main')).first().isVisible()],
    ['/archive', 'archive', () => page.locator('h1:has-text("归档")').isVisible()],
    ['/about', 'about', () => page.locator('h1:has-text("关于")').isVisible()],
    [`/post/${realId}`, 'post', () => page.locator('h1').first().isVisible()],
    ['/collections', 'collections', () => page.locator('main').first().isVisible()],
    ['/login', 'login', () => page.locator('main').first().isVisible()],
  ]
  for (const [path, name, check] of routes) {
    await page.goto(`${base}${path}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(600)
    const is404 = await page.locator('text=这一页不在本站').count() > 0
    ok(`${name} 非 404 且渲染`, !is404 && (await check()))
  }
  ok('W1 无 pageerror/console.error', errors.length === 0, errors.join(';'))
  await ctx.close()
}

// ── W2. post 有效→无效→有效 状态往返 ────────────────────────
console.log('== W2. post 路由状态往返 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/post/${realId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('article', { timeout: 15000 })
  ok('有效文章渲染', await page.locator('article').first().isVisible())

  // 客户端内跳转到不存在文章（SPA 导航）
  await page.evaluate(() => window.history.pushState({}, '', '/post/999999999999999999'))
  await page.evaluate(() => window.dispatchEvent(new PopStateEvent('popstate')))
  await page.waitForSelector('text=这一页不在本站', { timeout: 10000 })
  ok('有效→无效 同页组件切换出 404 面板', true)
  ok('正文不渲染', (await page.locator('article .prose').count()) === 0)

  // 浏览器后退恢复文章
  await page.goBack()
  await page.waitForSelector('article .prose', { timeout: 15000 })
  ok('后退恢复文章正文', await page.locator('article .prose').first().isVisible())
  await ctx.close()
}

// ── W3. 边界路径 ────────────────────────────────────────────
console.log('== W3. 边界路径 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  const nfCases = [
    ['/post/', 'post 空 id'],
    ['/nope?x=1&y=2', '带 query 的未知路径'],
    ['/a/b/c/deep', '多级未知路径'],
    [`/post/${realId}/extra`, '文章路径多余段'],
    ['/about//', '关于双斜杠'],
  ]
  for (const [path, name] of nfCases) {
    await page.goto(`${base}${path}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(400)
    const real404 = await page.locator('text=这一页不在本站').count() > 0
    const isAbout = await page.locator('h1:has-text("关于")').count() > 0
    // /about// 双斜杠：vue-router 不合并 → dev 下 404 可接受（生产 nginx merge_slashes 归一）
    if (path === '/about//') ok(`${name} → About 或 404 均可`, isAbout || real404, `404=${real404} about=${isAbout}`)
    else ok(`${name} → 404`, real404, `404=${real404} about=${isAbout}`)
  }
  // /post/0 数字但不存在 → notfound（走 API，A030100）
  await page.goto(`${base}/post/0`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=这一页不在本站', { timeout: 15000 })
  ok('/post/0 → 404 面板', true)
  await ctx.close()
}

// ── W4. 搜索弹层焦点归还 ────────────────────────────────────
console.log('== W4. 404 搜索入口焦点链 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/missing`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('button:has-text("搜索一下")', { timeout: 10000 })
  await page.click('button:has-text("搜索一下")')
  await page.waitForSelector('input[placeholder="搜索文章..."]', { state: 'visible' })
  const focused = await page.evaluate(() => document.activeElement?.getAttribute('placeholder'))
  ok('打开后焦点进输入框', focused === '搜索文章...', focused)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  const back = await page.evaluate(() => document.activeElement?.textContent?.trim())
  ok('Esc 关闭焦点归还「搜索一下」', back === '搜索一下', back)

  // ⌘K 在 404 页也能开
  await page.keyboard.press('Meta+k')
  await page.waitForSelector('input[placeholder="搜索文章..."]', { state: 'visible', timeout: 5000 })
  ok('⌘K 可打开', true)
  await page.keyboard.press('Escape')
  await ctx.close()
}

// ── W5. 404 最受欢迎链接可导航 ───────────────────────────────
console.log('== W5. 404 最受欢迎点击 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/missing`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('section:has(h2:has-text("最受欢迎")) li a', { timeout: 10000 })
  const href = await page.locator('section:has(h2:has-text("最受欢迎")) li a').first().getAttribute('href')
  ok('最受欢迎项指向 /post/', href?.startsWith('/post/'), href)
  await page.locator('section:has(h2:has-text("最受欢迎")) li a').first().click()
  await page.waitForSelector('article .prose', { timeout: 15000 })
  ok('点击进入真实文章', true)
  await ctx.close()
}

// ── W6. FAB 规则：404 页保留 chat FAB ─────────────────────────
console.log('== W6. FAB 规则 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/missing`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const fab = page.locator('.chat-fab')
  ok('404 页 FAB 存在（其他前台页保留规则）', await fab.count() === 1)
  if (await fab.count()) {
    await fab.click()
    await page.waitForSelector('.chat-panel', { state: 'visible', timeout: 5000 })
    ok('404 页面板可打开', true)
  }
  await ctx.close()
}

// ── W7. About mock 全要素 + 降级 ─────────────────────────────
console.log('== W7. About 细节 ==')
{
  const real = await (await fetch(`${base}/v1/site/config`)).json()
  const ctx = await browser.newContext({ viewport: DESK })
  await ctx.addInitScript(() => localStorage.setItem('mysite_theme_v2', 'paper-light'))
  await ctx.route('**/v1/site/config', route => {
    const data = { ...real.data }
    data.author = '走查作者'
    data.avatar = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJREFUAw=='
    data.aboutContent = '## 章节一\n\n> [!NOTE] 标题\n> callout 正文\n\n**加粗** `code` [外链](https://example.com)'
    data.techStack = ''
    data.github = ''
    data.rss = ''
    data.authorBio = ''
    data.authorTagline = ''
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...real, data }) })
  })
  const page = await ctx.newPage()
  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose', { timeout: 10000 })
  await page.waitForTimeout(500)

  ok('avatar img 渲染', await page.locator('.paper-card img').count() === 1)
  const paperH2 = await page.locator('.prose h2').first()
  const before = await paperH2.evaluate(el => getComputedStyle(el, '::before').content)
  // headless Chrome 对 ::before counter() 报原始声明值；规则命中即视为生效（渲染路径同文章页已验）
  ok('paper § 序号规则命中', /§|counter/.test(before ?? ''), before)
  ok('callout 渲染', await page.locator('.prose .callout').count() === 1)
  const link = page.locator('.prose a')
  ok('prose 外链存在', await link.count() === 1)
  // markdown 链接由 marked 渲染，检查 target/rel（useMarkdown 无 target 处理 → 原样输出）
  ok('prose 内 code', await page.locator('.prose p code, .prose li code').count() >= 1)
  ok('仅 techStack/无链接 → footer 整行隐藏', (await page.locator('.paper-card >> text=技术栈').count()) === 0)
  // bio/tagline 已置空：卡内只应剩作者名 h2（页脚 © 行也含作者名，故检查范围限卡内）
  const cardPs = await page.locator('.paper-card h2, .paper-card p').allTextContents()
  ok('bio/tagline 空 → 卡内仅作者名', cardPs.length === 1 && cardPs[0] === '走查作者', JSON.stringify(cardPs))
  await page.screenshot({ path: join(outDir, 'about-walkthrough.png'), fullPage: true })
  await ctx.close()
}

// W7b: 仅 techStack 时 footer 仍显示
{
  const real = await (await fetch(`${base}/v1/site/config`)).json()
  const ctx = await browser.newContext({ viewport: DESK })
  await ctx.addInitScript(() => localStorage.setItem('mysite_theme_v2', 'paper-light'))
  await ctx.route('**/v1/site/config', route => {
    const data = { ...real.data, techStack: 'Vue 3' }
    route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...real, data }) })
  })
  const page = await ctx.newPage()
  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('h1:has-text("关于")')
  ok('仅 techStack → footer 行渲染', await page.locator('text=技术栈 · Vue 3').isVisible())
  await ctx.close()
}

// ── W8. useMarkdown 修复回归（真实文章）──────────────────────
console.log('== W8. 文章正文回归 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  // 找既有 inline code 又有 fenced block 的文章
  const list = await (await fetch(`${base}/v1/articles?current=1&size=30&published=1`)).json()
  const recs = list?.data?.records ?? list?.data?.list ?? []
  let target = null
  for (const a of recs) {
    const d = await (await fetch(`${base}/v1/articles/${a.id}`)).json()
    const c = d?.data?.content || ''
    if (c.includes('```') && /`[^`\n]+`/.test(c.replace(/```[\s\S]*?```/g, ''))) {
      target = a.id; break
    }
  }
  if (target) {
    await page.goto(`${base}/post/${target}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.prose pre', { timeout: 15000 })
    await page.waitForTimeout(500)
    const stats = await page.evaluate(() => ({
      inlineCode: document.querySelectorAll('.prose p code, .prose li code, .prose h2 code, .prose h3 code, .prose blockquote code').length,
      preBlocks: document.querySelectorAll('.prose pre').length,
      fencedLang: document.querySelector('.prose pre code')?.getAttribute('data-language') ?? null,
    }))
    ok('inline <code> 渲染 >0', stats.inlineCode > 0, `inline=${stats.inlineCode}`)
    ok('fenced <pre> 渲染 >0', stats.preBlocks > 0, `pre=${stats.preBlocks} lang=${stats.fencedLang}`)
    // inline code 内 markdown 符号不再被二次解析（*x* 不该出 <em>）
    const emInsideCode = await page.evaluate(() => document.querySelectorAll('.prose code em, .prose code strong').length)
    ok('code 内无 <em>/<strong> 污染', emInsideCode === 0, `count=${emInsideCode}`)
  } else {
    ok('找到含代码文章', false, '无含代码文章可验')
  }
  await ctx.close()
}

// ── W9. /dashboard/不存在 的行为（记录）──────────────────────
console.log('== W9. /dashboard 兜底 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/dashboard/nonexistent`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const url = page.url()
  const is404 = await page.locator('text=这一页不在本站').count() > 0
  const isLogin = url.includes('/login')
  ok('未登录 /dashboard/xyz → 登录重定向或前台 404', is404 || isLogin, `url=${url} 404=${is404}`)
  await ctx.close()
}

// ── W10. 移动端：notfound 无 dock、无 FAB 冲突 ───────────────
console.log('== W10. 移动端 notfound ==')
{
  const { ctx, page, errors } = await newPage('paper-light', MOB)
  await page.goto(`${base}/post/999999999999999999`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=这一页不在本站', { timeout: 15000 })
  await page.waitForTimeout(500)
  ok('无 post-dock', (await page.locator('.post-dock').count()) === 0)
  ok('body 无 has-post-dock', !(await page.evaluate(() => document.body.classList.contains('has-post-dock'))))
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, 'post-404-mobile.png'), fullPage: true })
  await ctx.close()
}

// ── W11. 深色主题 404 面板色值 ───────────────────────────────
console.log('== W11. paper-dark 色值 ==')
{
  const { ctx, page } = await newPage('paper-dark', DESK)
  await page.goto(`${base}/missing`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=这一页不在本站', { timeout: 10000 })
  const colors = await page.evaluate(() => {
    const big = [...document.querySelectorAll('p')].find(p => p.textContent.trim() === '404')
    return { color: getComputedStyle(big).color, bg: getComputedStyle(document.body).backgroundColor }
  })
  ok('404 墨色=paper-dark text-primary', colors.color === 'rgb(236, 228, 214)', colors.color)
  ok('底=paper-dark bg-primary', colors.bg === 'rgb(22, 19, 15)', colors.bg)
  await ctx.close()
}

// ── W12. 404 页滚动行为/键盘可达性 ───────────────────────────
console.log('== W12. 键盘可达 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/missing`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=这一页不在本站', { timeout: 10000 })
  // Tab 到 回到首页/搜索一下 应能 focus
  let seen = []
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab')
    seen.push(await page.evaluate(() => document.activeElement?.textContent?.trim() || document.activeElement?.tagName))
    if (seen.some(s => s.includes('回到首页')) && seen.some(s => s.includes('搜索一下'))) break
  }
  ok('Tab 可达回首页+搜索', seen.some(s => s.includes('回到首页')) && seen.some(s => s.includes('搜索一下')), seen.join(','))
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
