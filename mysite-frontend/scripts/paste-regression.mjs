/**
 * 粘贴三场景回归测试：
 * A. IDE 复制（<pre> 包裹）→ 应按 Markdown 解析
 * B. VS Code 复制（等宽字体 div 包裹）→ 应按 Markdown 解析
 * C. 网页富文本复制（真实格式）→ 应走 HTML 路径保留格式
 */
import { chromium } from 'playwright'

const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  permissions: ['clipboard-read', 'clipboard-write'],
})
const page = await context.newPage()
page.on('pageerror', (err) => console.log('[pageerror]', err.message))
await page.goto('http://localhost:5199/editor-test.html', { waitUntil: 'networkidle' })
await page.waitForSelector('.ProseMirror', { timeout: 15000 })

async function pasteWith(html, plain) {
  await page.evaluate(() => window.__test.set(''))
  await page.waitForTimeout(400)
  await page.click('.ProseMirror')
  await page.evaluate(
    async ({ html, plain }) => {
      await navigator.clipboard.write([
        new ClipboardItem({
          'text/plain': new Blob([plain], { type: 'text/plain' }),
          'text/html': new Blob([html], { type: 'text/html' }),
        }),
      ])
    },
    { html, plain },
  )
  await page.keyboard.press('Meta+v')
  await page.waitForTimeout(1000)
  return page.evaluate(() => ({
    h1: document.querySelectorAll('.ProseMirror h1').length,
    h2: document.querySelectorAll('.ProseMirror h2').length,
    codeBlocks: document.querySelectorAll('.ProseMirror .milkdown-code-block').length,
    bold: document.querySelectorAll('.ProseMirror strong').length,
    markdown: window.__test.get(),
  }))
}

// A. IDE 复制：<pre> 包裹的整篇 Markdown
const fixture = await page.evaluate(() => window.__test.fixture)
const escaped = fixture.replace(/&/g, '&amp;').replace(/</g, '&lt;')
const a = await pasteWith(`<pre style="font-family: Menlo, monospace;">${escaped}</pre>`, fixture)
console.log('A. IDE <pre> 复制:', JSON.stringify({ h1: a.h1, codeBlocks: a.codeBlocks }))
console.log('   → 期望 h1>=1 且代码块为 fixtures 自带的 ~6 个:', a.h1 >= 1 && a.codeBlocks >= 4 ? 'PASS' : 'FAIL')

// B. VS Code 复制：等宽字体 div 分行包裹
const vsHtml = `<div style="font-family: Consolas, 'Courier New', monospace; white-space: pre;">${fixture
  .split('\n')
  .map((l) => `<div><span>${l.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</span></div>`)
  .join('')}</div>`
const b = await pasteWith(vsHtml, fixture)
console.log('B. VS Code 等宽 div 复制:', JSON.stringify({ h1: b.h1, codeBlocks: b.codeBlocks }))
console.log('   → 期望按 Markdown 解析:', b.h1 >= 1 ? 'PASS' : 'FAIL')

// C. 网页富文本复制：HTML 与纯文本内容不一致（真实格式）
const c = await pasteWith('<h2>网页标题</h2><p>一些 <b>加粗</b> 内容</p>', '网页标题\n一些 加粗 内容')
console.log('C. 网页富文本复制:', JSON.stringify({ h2: c.h2, bold: c.bold, h1: c.h1 }))
console.log('   → 期望走 HTML 路径保留格式（h2=1, bold=1）:', c.h2 === 1 && c.bold === 1 ? 'PASS' : 'FAIL')

await page.screenshot({ path: 'scripts/output/g-paste-regression.png' })
await browser.close()
