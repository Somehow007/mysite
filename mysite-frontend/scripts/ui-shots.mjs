// UI 截图脚本：切主题 × 双视口 × 页面列表，检查横向溢出。
// 用法：node scripts/ui-shots.mjs [baseUrl] [outDir] [theme1,theme2,...]
// 依赖 dev server 已启动（默认 http://localhost:5173）和 playwright（devDependency）。
// 主题通过 localStorage mysite_theme_v2 在页面脚本执行前注入。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = (process.argv[2] ?? 'http://localhost:5173').replace(/\/$/, '')
const outDir = process.argv[3] ?? 'scripts/output/ui-shots'
const themes = (process.argv[4] ?? 'paper-light,paper-dark,classic-light').split(',')
const routes = ['/', '/archive', '/about']
const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]

mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch()
let failures = 0

for (const theme of themes) {
  for (const vp of viewports) {
    const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } })
    await context.addInitScript(t => {
      try { localStorage.setItem('mysite_theme_v2', t) } catch {}
    }, theme)
    const page = await context.newPage()

    // 先访问首页，等文章链接渲染出来后取第一篇详情页
    await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' })
    const postHref = await page
      .waitForSelector('a[href^="/post/"]', { timeout: 10000 })
      .then(el => el.getAttribute('href'))
      .catch(() => null)
    const pageRoutes = postHref ? [...routes, postHref] : routes

    for (const route of pageRoutes) {
      await page.goto(`${base}${route}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(600)
      const name = `${theme}-${vp.name}-${route === '/' ? 'home' : route.replaceAll('/', '_').replace(/^_/, '')}`
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      if (overflow > 0) {
        failures++
        console.log(`OVERFLOW ${overflow}px  ${name}  ${route}`)
      }
      await page.screenshot({ path: join(outDir, `${name}.png`), fullPage: true })
      console.log(`shot ${name}  overflowX=${overflow}`)
    }
    await context.close()
  }
}

await browser.close()
console.log(failures === 0 ? 'OK: no horizontal overflow' : `FAIL: ${failures} page(s) overflow`)
process.exit(failures === 0 ? 0 : 1)
