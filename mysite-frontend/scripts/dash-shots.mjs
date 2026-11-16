// 后台截图脚本：主题 × 视口 × 后台路由，检查横向溢出（dashboard 纸本化 D1–D5 共用）。
// 用法：DASH_USER=xxx DASH_PASS=xxx node scripts/dash-shots.mjs [outDir] [theme1,theme2,...] [route1,route2,...]
// 依赖：dev server 5173 + 后端 8081 已启动；playwright（devDependency）。
// 注意：本机 5173 上可能还有别的项目绑在 [::1]，所以默认用 127.0.0.1，不要用 localhost。
// 登录直连 8081 拿 token（node fetch 走 vite 代理会拿到空 body），再注入 localStorage。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const base = process.env.DASH_BASE ?? 'http://127.0.0.1:5173'
const api = process.env.DASH_API ?? 'http://127.0.0.1:8081'
const { DASH_USER: username, DASH_PASS: password } = process.env
if (!username || !password) {
  console.error('需要环境变量 DASH_USER / DASH_PASS（ADMIN 账号）')
  process.exit(2)
}

const outDir = process.argv[2] ?? 'scripts/output/dash-shots'
const themes = (process.argv[3] ?? 'paper-light,paper-dark,classic-light').split(',')
const routes = (process.argv[4] ??
  ',posts/new,collections,users,comments,categories,tags,images,knowledge,ai,settings').split(',')
const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]

const res = await fetch(`${api}/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }),
}).then(r => r.json())
if (!res?.data?.accessToken) {
  console.error('登录失败', res?.message ?? res)
  process.exit(2)
}
const { accessToken, refreshToken, role } = res.data

mkdirSync(outDir, { recursive: true })
const browser = await chromium.launch()
let failures = 0

for (const theme of themes) {
  for (const vp of viewports) {
    const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } })
    await context.addInitScript(([t, a, r, role]) => {
      localStorage.setItem('mysite_theme_v2', t)
      localStorage.setItem('mysite_access_token', JSON.stringify(a))
      localStorage.setItem('mysite_refresh_token', JSON.stringify(r))
      // 路由守卫 requiresAdmin 读 localStorage user_role，先于 fetchCurrentUser 回填，需预置
      localStorage.setItem('mysite_user_role', JSON.stringify(role))
    }, [theme, accessToken, refreshToken, role])
    const page = await context.newPage()
    page.on('pageerror', e => { failures++; console.log(`PAGEERROR ${theme}/${vp.name}: ${e.message}`) })

    for (const route of routes) {
      await page.goto(`${base}/dashboard${route ? `/${route}` : ''}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(1200)
      const name = `${theme}-${vp.name}-${route ? route.replaceAll('/', '_') : 'posts'}`
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      if (overflow > 0) {
        failures++
        console.log(`OVERFLOW ${overflow}px  ${name}`)
      }
      await page.screenshot({ path: join(outDir, `${name}.png`) })
      console.log(`shot ${name}  overflowX=${overflow}`)
    }
    await context.close()
  }
}

await browser.close()
console.log(failures === 0 ? 'OK: no overflow, no page error' : `FAIL: ${failures} problem(s)`)
process.exit(failures === 0 ? 0 : 1)
