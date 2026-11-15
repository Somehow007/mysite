// T12 验证：404 兜底页 + About 重做
// 用法: node scripts/t12-verify.mjs [baseUrl]
// 依赖 dev server（默认 http://localhost:5173）+ 后端（/v1 代理）+ playwright。
// /v1/site/config 在 D 段用 route mock 注入 aboutContent/techStack 验证 markdown 链路。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = join(new URL('.', import.meta.url).pathname, 'output/t12')
mkdirSync(outDir, { recursive: true })

let pass = 0, fail = 0
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${extra}`) }
}

const browser = await chromium.launch()
const DESK = { width: 1440, height: 900 }
const MOB = { width: 390, height: 844 }

async function newPage(theme, viewport, { mockSite = false } = {}) {
  const ctx = await browser.newContext({ viewport })
  await ctx.addInitScript(t => { try { localStorage.setItem('mysite_theme_v2', t) } catch {} }, theme)
  if (mockSite) {
    const real = await (await fetch(`${base}/v1/site/config`)).json()
    const data = real?.data ?? {}
    data.author = '测试作者'
    data.authorTagline = 'Java 后端 · 在杭州'
    data.authorBio = '写代码，也写字。'
    data.github = 'https://github.com/example'
    data.techStack = 'Vue 3 · Spring Boot · MySQL · pgvector'
    data.aboutContent = [
      '## 你好，这里是关于页',
      '',
      '这里是 **markdown** 长文，验证 `useMarkdown` 渲染进 `.prose`。',
      '',
      '- 支持列表',
      '- 支持 `inline code`',
      '',
      '> 一段引用',
    ].join('\n')
    await ctx.route('**/v1/site/config', route =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ ...real, data }),
      }))
  }
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(String(e)))
  return { ctx, page, errors }
}

const overflowX = page => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)

// ── A. 桌面 paper-light：404 页全要素 ────────────────────────
console.log('== A. 桌面 1440 paper-light：/不存在路径 ==')
{
  const { ctx, page, errors } = await newPage('paper-light', DESK)
  await page.goto(`${base}/this-page-does-not-exist`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=404', { timeout: 10000 })
  await page.waitForTimeout(800) // 等「最受欢迎」拉取

  ok('大号 404 可见', await page.locator('text=404').first().isVisible())
  ok('文案存在', await page.locator('text=这一页不在本站').isVisible())
  const homeHref = await page.locator('a:has-text("回到首页")').getAttribute('href')
  ok('回首页链接', homeHref === '/', homeHref)
  ok('标题=页面不存在', (await page.title()).includes('页面不存在'), await page.title())
  ok('robots noindex', await page.locator('meta[name="robots"][content="noindex"]').count() === 1)

  // 最受欢迎 ≤3 篇
  const popCount = await page.locator('section:has(h2:has-text("最受欢迎")) li').count()
  ok('最受欢迎 1-3 篇', popCount >= 1 && popCount <= 3, `count=${popCount}`)

  // 搜索入口打开 SearchDialog
  await page.click('button:has-text("搜索一下")')
  await page.waitForSelector('input[placeholder="搜索文章..."]', { state: 'visible', timeout: 5000 })
  ok('搜索弹层打开', true)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  ok('横向溢出=0', (await overflowX(page)) === 0, await overflowX(page))
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, '404-desktop-paper-light.png'), fullPage: true })
  await ctx.close()
}

// ── B. /post/<不存在数字 id>：同款面板、URL 不变 ──────────────
console.log('== B. /post/999999999999999999（不存在）==')
{
  const { ctx, page, errors } = await newPage('paper-light', DESK)
  await page.goto(`${base}/post/999999999999999999`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=404', { timeout: 15000 })
  await page.waitForTimeout(500)

  ok('404 面板显示', await page.locator('text=这一页不在本站').isVisible())
  ok('URL 未变', page.url().includes('/post/999999999999999999'), page.url())
  ok('标题=页面不存在', (await page.title()).includes('页面不存在'), await page.title())
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  await page.screenshot({ path: join(outDir, 'post-404-desktop.png'), fullPage: true })
  await ctx.close()
}

// ── C. /post/abc：非数字 id 直接 notfound（不发 API）──────────
console.log('== C. /post/abc（非数字）==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  let articleCalls = 0
  await ctx.route('**/v1/articles/abc*', route => { articleCalls++; route.continue() })
  await page.goto(`${base}/post/abc`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=这一页不在本站', { timeout: 10000 })
  ok('非数字 id → 404 面板', true)
  ok('未发文章详情请求', articleCalls === 0, `calls=${articleCalls}`)
  await ctx.close()
}

// ── D. About：默认配置 + mock 注入长文/技术栈 ────────────────
console.log('== D. /about 默认配置 ==')
{
  const { ctx, page } = await newPage('paper-light', DESK)
  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('h1:has-text("关于")', { timeout: 10000 })
  await page.waitForTimeout(600)

  ok('页头「关于」', await page.locator('h1:has-text("关于")').isVisible())
  ok('作者卡作者名', await page.locator('h2:has-text("Admin")').isVisible())
  ok('描述行', await page.locator('text=一个极简的个人博客').first().isVisible())
  ok('无 aboutContent → 无 .prose', (await page.locator('.prose').count()) === 0)
  ok('无 techStack → 无技术栈行', (await page.locator('text=技术栈').count()) === 0)
  ok('横向溢出=0', (await overflowX(page)) === 0, await overflowX(page))
  await page.screenshot({ path: join(outDir, 'about-default.png'), fullPage: true })
  await ctx.close()
}

console.log('== D2. /about mock 注入 aboutContent + techStack ==')
{
  const { ctx, page, errors } = await newPage('paper-light', DESK, { mockSite: true })
  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.prose h2', { timeout: 10000 })
  await page.waitForTimeout(400)

  ok('作者名=测试作者', await page.locator('h2:has-text("测试作者")').isVisible())
  ok('tagline', await page.locator('text=Java 后端 · 在杭州').isVisible())
  ok('bio', await page.locator('text=写代码，也写字').isVisible())
  ok('github 链接', await page.locator('main a:has-text("GitHub")').getAttribute('href') === 'https://github.com/example')
  ok('技术栈行', await page.locator('text=技术栈 · Vue 3').isVisible())
  ok('.prose h2 渲染', await page.locator('.prose h2:has-text("你好，这里是关于页")').isVisible())
  ok('markdown strong', await page.locator('.prose strong:has-text("markdown")').isVisible())
  ok('inline code', await page.locator('.prose code:has-text("inline code")').isVisible())
  ok('blockquote', await page.locator('.prose blockquote').count() === 1)
  ok('无 pageerror', errors.length === 0, errors.join(';'))
  ok('横向溢出=0', (await overflowX(page)) === 0, await overflowX(page))
  await page.screenshot({ path: join(outDir, 'about-mock-paper-light.png'), fullPage: true })
  await ctx.close()
}

// ── E. 三主题 × 双视口：404 + about 溢出与截图 ────────────────
console.log('== E. 主题矩阵 ==')
for (const theme of ['paper-dark', 'classic-light']) {
  for (const [vpName, vp] of [['desktop', DESK], ['mobile', MOB]]) {
    const { ctx, page, errors } = await newPage(theme, vp)
    await page.goto(`${base}/no-such-page-${theme}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('text=这一页不在本站', { timeout: 10000 })
    await page.waitForTimeout(600)
    const ov404 = await overflowX(page)
    ok(`404 ${theme}/${vpName} 溢出=0`, ov404 === 0, `overflow=${ov404}`)
    ok(`404 ${theme}/${vpName} 无 pageerror`, errors.length === 0, errors.join(';'))
    await page.screenshot({ path: join(outDir, `404-${theme}-${vpName}.png`), fullPage: true })

    await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('h1:has-text("关于")', { timeout: 10000 })
    await page.waitForTimeout(400)
    const ovAbout = await overflowX(page)
    ok(`about ${theme}/${vpName} 溢出=0`, ovAbout === 0, `overflow=${ovAbout}`)
    await page.screenshot({ path: join(outDir, `about-${theme}-${vpName}.png`), fullPage: true })
    await ctx.close()
  }
}
// paper-light 移动端补齐（A/B/D 都是桌面）
{
  const { ctx, page } = await newPage('paper-light', MOB)
  await page.goto(`${base}/missing-mobile`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=这一页不在本站', { timeout: 10000 })
  await page.waitForTimeout(600)
  ok('404 paper-light/mobile 溢出=0', (await overflowX(page)) === 0, await overflowX(page))
  await page.screenshot({ path: join(outDir, '404-paper-light-mobile.png'), fullPage: true })

  await page.goto(`${base}/about`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('h1:has-text("关于")', { timeout: 10000 })
  await page.waitForTimeout(400)
  ok('about paper-light/mobile 溢出=0', (await overflowX(page)) === 0, await overflowX(page))
  await page.screenshot({ path: join(outDir, 'about-paper-light-mobile.png'), fullPage: true })
  await ctx.close()
}

await browser.close()
console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
