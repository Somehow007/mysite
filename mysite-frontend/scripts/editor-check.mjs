/**
 * 编辑器无头验证脚本（dev 工具，不进生产构建）。
 * 用法：先启动 dev server（npm run dev -- --port 5199 --strictPort），
 * 再运行 PLAYWRIGHT_BROWSERS_PATH=0 node scripts/editor-check.mjs
 */
import { chromium } from 'playwright'
import { mkdirSync, writeFileSync } from 'node:fs'

const BASE = 'http://localhost:5199/editor-test.html'
const OUT = new URL('./output/', import.meta.url).pathname
mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  permissions: ['clipboard-read', 'clipboard-write'],
})
const page = await context.newPage()
page.on('console', (msg) => {
  if (msg.type() === 'error') console.log('[console.error]', msg.text())
})
page.on('pageerror', (err) => console.log('[pageerror]', err.message))

await page.goto(BASE, { waitUntil: 'networkidle' })
await page.waitForSelector('.ProseMirror', { timeout: 15000 })
console.log('✓ 编辑器已挂载')

// ── A. round-trip：外部赋值 fixtures 全文 ──
const fixture = await page.evaluate(() => window.__test.fixture)
await page.evaluate((md) => window.__test.set(md), fixture)
await page.waitForTimeout(800) // 等待解析 + 防抖 emit
const roundTrip = await page.evaluate(() => window.__test.get())
writeFileSync(OUT + 'roundtrip-input.md', fixture)
writeFileSync(OUT + 'roundtrip-output.md', roundTrip)
console.log(`✓ round-trip: 输入 ${fixture.length} 字符 → 输出 ${roundTrip.length} 字符，一致=${fixture.trim() === roundTrip.trim()}`)
await page.screenshot({ path: OUT + 'a-roundtrip.png', fullPage: false })

// ── B. 滚动能力 ──
const scroll = await page.evaluate(() => {
  const el = document.querySelector('.crepe-container')
  if (!el) return { found: false }
  const before = el.scrollTop
  el.scrollTop = el.scrollHeight
  return {
    found: true,
    scrollHeight: el.scrollHeight,
    clientHeight: el.clientHeight,
    canScroll: el.scrollTop > before,
    scrollTopAfter: el.scrollTop,
  }
})
console.log('✓ 滚动测试:', JSON.stringify(scroll))
await page.waitForTimeout(300)
await page.screenshot({ path: OUT + 'b-scrolled-bottom.png' })

// ── C. 粘贴纯文本 Markdown（复现「粘贴变代码块」） ──
await page.evaluate(() => window.__test.set(''))
await page.waitForTimeout(500)
await page.click('.ProseMirror')
await page.evaluate((md) => navigator.clipboard.writeText(md), fixture)
const mod = process.platform === 'darwin' ? 'Meta' : 'Control'
await page.keyboard.press(`${mod}+v`)
await page.waitForTimeout(1000)
const pasted = await page.evaluate(() => window.__test.get())
writeFileSync(OUT + 'paste-output.md', pasted)
const codeBlockCount = await page.evaluate(
  () => document.querySelectorAll('.ProseMirror pre, .ProseMirror .code-block, [data-node-type="code_block"], .milkdown-code-block').length,
)
console.log(`✓ 粘贴测试: 输出 ${pasted.length} 字符，页面内代码块节点数=${codeBlockCount}`)
await page.screenshot({ path: OUT + 'c-after-paste.png' })

// ── D. 图片渲染尺寸（data URI 大图，避免外网依赖） ──
await page.reload({ waitUntil: 'networkidle' })
await page.waitForSelector('.ProseMirror', { timeout: 15000 })
await page.evaluate(() => {
  const svg = encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900"><rect width="1600" height="900" fill="#4f7cff"/><text x="800" y="450" font-size="60" fill="#fff" text-anchor="middle">1600x900</text></svg>',
  )
  window.__test.set(`# 图片测试\n\n![测试图](data:image/svg+xml,${svg})\n\n图片后的段落。\n`)
})
await page.waitForTimeout(2500)
const imgInfo = await page.evaluate(() => {
  const imgs = [...document.querySelectorAll('.ProseMirror img')].map((img) => ({
    width: img.getBoundingClientRect().width,
    height: img.getBoundingClientRect().height,
  }))
  return imgs
})
console.log('✓ 图片尺寸:', JSON.stringify(imgInfo))
await page.screenshot({ path: OUT + 'd-images.png' })

// ── E. Callout 渲染与 round-trip ──
await page.reload({ waitUntil: 'networkidle' })
await page.waitForSelector('.ProseMirror', { timeout: 15000 })
await page.evaluate(() =>
  window.__test.set(
    '> [!NOTE] 备注标题\n> 备注正文\n>\n> - 列表项一\n> - 列表项二\n\n> [!WARNING]\n> 警告正文\n\n> [!MISSING] 别名类型\n\n普通引用：\n\n> 这是普通引用块\n\n普通段落。',
  ),
)
await page.waitForTimeout(800)
const calloutInfo = await page.evaluate(() =>
  [...document.querySelectorAll('.ProseMirror .callout')].map((el) => ({
    cls: el.className,
    color: el.style.getPropertyValue('--callout-color'),
    title: el.querySelector('.callout-title-input')?.value ?? null,
    listItems: el.querySelectorAll('.callout-body li').length,
  })),
)
const calloutMd = await page.evaluate(() => window.__test.get())
writeFileSync(OUT + 'e-callout.md', calloutMd)
const normalQuoteKept = await page.evaluate(
  () => document.querySelectorAll('.ProseMirror blockquote:not(.callout blockquote)').length,
)
console.log('✓ callout 渲染:', JSON.stringify(calloutInfo))
console.log(`✓ callout 序列化含 [!NOTE]=${calloutMd.includes('[!NOTE]')} 普通引用块保留=${normalQuoteKept >= 1}`)
await page.screenshot({ path: OUT + 'e-callout.png' })

await browser.close()
console.log('完成，截图与输出见 scripts/output/')
