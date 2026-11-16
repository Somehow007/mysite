// D5 走查：封面破图兜底 / 评论头像 UserAvatar / 移动端操作收敛 / 空状态纸本化 / 知识库自动选中
// 三主题抽查（paper-light 主断言 + classic-light 作用域对照）+ paper-light 移动端 390px。
// 用法：DASH_USER=xxx DASH_PASS=xxx node scripts/d5-walkthrough.mjs
import { chromium } from 'playwright'

const base = process.env.DASH_BASE ?? 'http://127.0.0.1:5173'
const api = process.env.DASH_API ?? 'http://127.0.0.1:8081'
const { DASH_USER: username, DASH_PASS: password } = process.env
if (!username || !password) { console.error('需要 DASH_USER / DASH_PASS'); process.exit(2) }

const res = await fetch(`${api}/v1/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }),
}).then(r => r.json())
if (!res?.data?.accessToken) { console.error('登录失败', res?.message ?? res); process.exit(2) }
const { accessToken, refreshToken, role } = res.data

let pass = 0, fail = 0, skip = 0
const check = (name, cond, detail = '') => {
  if (cond) { pass++; console.log(`  PASS ${name}`) }
  else { fail++; console.log(`  FAIL ${name} ${detail}`) }
}
const skipped = (name, why) => { skip++; console.log(`  SKIP ${name} (${why})`) }

const browser = await chromium.launch()

async function newPage(theme, viewport = { width: 1440, height: 900 }) {
  const context = await browser.newContext({ viewport })
  await context.addInitScript(([t, a, r, role]) => {
    localStorage.setItem('mysite_theme_v2', t)
    localStorage.setItem('mysite_access_token', JSON.stringify(a))
    localStorage.setItem('mysite_refresh_token', JSON.stringify(r))
    localStorage.setItem('mysite_user_role', JSON.stringify(role))
  }, [theme, accessToken, refreshToken, role])
  const page = await context.newPage()
  page.on('pageerror', e => { fail++; console.log(`  FAIL PAGEERROR ${e.message}`) })
  return { context, page }
}

const gotoDash = async (page, route) => {
  await page.goto(`${base}/dashboard${route}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
}

// 文章列表第一条注入必失败的封面 URL（127.0.0.1:9 discard 端口，连接即拒）
async function injectBrokenCover(page) {
  await page.route(/\/v1\/articles(\?|$)/, async route => {
    const r = await route.fetch()
    const json = await r.json()
    const rec = json?.data?.records
    if (Array.isArray(rec) && rec.length > 0) rec[0].coverImage = 'http://127.0.0.1:9/cover.png'
    await route.fulfill({ status: r.status(), contentType: 'application/json', body: JSON.stringify(json) })
  })
}

// 统计「有 w-14 + h-[38px] + bg-bg-secondary 但内部无 img」的空封面块
const countEmptyCoverBlocks = page => page.evaluate(() => {
  let n = 0
  for (const el of document.querySelectorAll('div')) {
    const c = el.className?.toString() ?? ''
    if (c.includes('w-14') && c.includes('h-[38px]') && c.includes('bg-bg-secondary') && !el.querySelector('img')) n++
  }
  return n
})

// ── §1 封面破图兜底 ──
console.log('== §1 封面破图兜底（注入坏封面，桌面+移动）==')
{
  const { context, page } = await newPage('paper-light')
  await injectBrokenCover(page)
  await gotoDash(page, '')
  await page.waitForTimeout(1500) // 等 img error → brokenCovers → v-if 移除
  const st = await page.evaluate(() => ({
    badImgs: document.querySelectorAll('img[src*="127.0.0.1:9"]').length,
    lazyImgs: document.querySelectorAll('.ui-table img[loading="lazy"]').length,
    rows: document.querySelectorAll('.ui-table tbody tr').length,
  }))
  check('桌面：坏封面 img 已移除（无破图/无空块残留）', st.badImgs === 0, JSON.stringify(st))
  check('桌面：封面 img 带 loading=lazy', st.lazyImgs > 0, `lazy=${st.lazyImgs}`)
  check('桌面：空封面块=0 且行数未丢', (await countEmptyCoverBlocks(page)) === 0 && st.rows > 0, `rows=${st.rows}`)
  await context.close()
}
{
  const { context, page } = await newPage('paper-light', { width: 390, height: 844 })
  await injectBrokenCover(page)
  await gotoDash(page, '')
  await page.waitForTimeout(1500)
  const st = await page.evaluate(() => ({
    badImgs: document.querySelectorAll('img[src*="127.0.0.1:9"]').length,
    cards: document.querySelectorAll('.md\\:hidden .ui-table-card').length,
    overflow: document.documentElement.scrollWidth - window.innerWidth,
  }))
  check('移动：坏封面 img 已移除', st.badImgs === 0, JSON.stringify(st))
  check('移动：卡片仍在 + 无横向溢出', st.cards > 0 && st.overflow <= 0, JSON.stringify(st))
  check('移动：空封面块=0', (await countEmptyCoverBlocks(page)) === 0)
  await context.close()
}

// ── §2 评论头像 UserAvatar ──
console.log('== §2 评论头像 → UserAvatar 首字兜底 ==')
{
  const { context, page } = await newPage('paper-light')
  await gotoDash(page, '/comments')
  const st = await page.evaluate(() => ({
    avatars: document.querySelectorAll('[role="img"]').length,
    oldImgs: document.querySelectorAll('img.w-6.h-6.rounded-full').length,
  }))
  if (st.avatars === 0) {
    skipped('评论头像断言', '无评论数据')
  } else {
    check('评论行使用 UserAvatar（role=img）', st.avatars > 0, `avatars=${st.avatars}`)
    check('旧裸 img 头像已清零', st.oldImgs === 0, `old=${st.oldImgs}`)
    // 破图兜底：对第一个带 img 的头像派发 error，首字应保留
    const r = await page.evaluate(() => {
      const img = document.querySelector('[role="img"] img')
      if (!img) return { hasImg: false }
      const wrap = img.closest('[role="img"]')
      img.dispatchEvent(new Event('error'))
      return { hasImg: true, wrapText: wrap?.textContent?.trim() ?? '' }
    })
    await page.waitForTimeout(200)
    const after = await page.evaluate(() => {
      const wrap = document.querySelector('[role="img"]')
      return { imgCount: wrap?.querySelectorAll('img').length ?? -1, text: wrap?.textContent?.trim() ?? '' }
    })
    check('头像破图后回退首字', r.hasImg && after.imgCount === 0 && after.text.length > 0,
      JSON.stringify({ ...r, ...after }))
  }
  await context.close()
}

// ── §3 移动端文章卡操作收敛 ──
console.log('== §3 移动端操作收敛（390px）==')
{
  const { context, page } = await newPage('paper-light', { width: 390, height: 844 })
  await gotoDash(page, '')
  const card = page.locator('.md\\:hidden .ui-table-card').first()
  if ((await card.count()) === 0) {
    skipped('移动端操作', '无文章数据')
  } else {
    const texts = await card.locator('button').allTextContents()
    const hasMore = await card.locator('button[aria-label="更多操作"]').count()
    check('卡片含 查看/编辑 文字钮 + ⋯更多',
      texts.some(t => t.includes('查看')) && texts.some(t => t.includes('编辑')) && hasMore === 1,
      JSON.stringify({ texts, hasMore }))
    check('卡片内无平铺 置顶/删除 文字钮',
      !texts.some(t => t.includes('置顶')) && !texts.some(t => t.trim() === '删除'),
      JSON.stringify(texts))

    // 展开 ⋯ 菜单
    await card.locator('button[aria-label="更多操作"]').click()
    const panel = page.locator('.dropdown-panel:visible').last()
    await panel.waitFor({ state: 'visible', timeout: 3000 })
    const menuText = await panel.textContent()
    const hasSep = await panel.locator('.border-t').count()
    const delItem = panel.locator('button', { hasText: '删除' })
    const delCls = await delItem.getAttribute('class')
    check('菜单含 置顶/取消置顶 + 分隔线 + 删除红字',
      /置顶/.test(menuText ?? '') && hasSep > 0 && /text-danger/.test(delCls ?? ''),
      JSON.stringify({ menuText, hasSep, delCls }))

    // 删除 → 仍走确认框（不真删，点取消）
    await delItem.click()
    await page.locator('.modal-panel:visible').first().waitFor({ state: 'visible', timeout: 3000 })
    const modalText = await page.locator('.modal-panel:visible').first().textContent()
    check('点删除仍弹确认框', /删除/.test(modalText ?? '') && /确定要删除/.test(modalText ?? ''), (modalText ?? '').slice(0, 80))
    await page.locator('.modal-panel:visible button', { hasText: '取消' }).first().click()
    await page.waitForTimeout(300)

    // 置顶切换：菜单关闭 + 文案翻转；再切回还原
    await card.locator('button[aria-label="更多操作"]').click()
    await panel.waitFor({ state: 'visible', timeout: 3000 })
    const topBtn = panel.locator('button', { hasText: /置顶/ }).first()
    const before = (await topBtn.textContent())?.trim()
    await topBtn.click()
    await page.waitForTimeout(400)
    const panelGone = await page.locator('.dropdown-panel:visible').count()
    check('点置顶后菜单关闭', panelGone === 0, `visible panels=${panelGone}`)
    await card.locator('button[aria-label="更多操作"]').click()
    await panel.waitFor({ state: 'visible', timeout: 3000 })
    const afterLabel = (await panel.locator('button', { hasText: /置顶/ }).first().textContent())?.trim()
    check('置顶状态翻转', before !== afterLabel && /置顶/.test(afterLabel ?? ''), `${before} → ${afterLabel}`)
    // 还原
    await panel.locator('button', { hasText: /置顶/ }).first().click()
    await page.waitForTimeout(400)
  }
  await context.close()
}

// ── §4 空状态 ──
console.log('== §4 空状态（EmptyState + paper 虚线框作用域）==')
{
  // 分类空态：拦截 tree 接口返回 []
  const { context, page } = await newPage('paper-light')
  await page.route('**/v1/categories/tree', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: null, data: [] }) }))
  await gotoDash(page, '/categories')
  const icon = page.locator('.ui-empty-icon').first()
  if ((await icon.count()) === 0) {
    fail++; console.log('  FAIL 分类空态未渲染 EmptyState')
  } else {
    const cs = await icon.evaluate(el => {
      const s = getComputedStyle(el)
      return { borderTopStyle: s.borderTopStyle, borderTopWidth: s.borderTopWidth, bg: s.backgroundColor }
    })
    const texts = await page.evaluate(() => document.body.innerText)
    check('paper：空状态图标=虚线框+透明底',
      cs.borderTopStyle === 'dashed' && cs.borderTopWidth === '1px' && cs.bg === 'rgba(0, 0, 0, 0)',
      JSON.stringify(cs))
    check('分类空态文案+新建分类按钮', /还没有分类/.test(texts) && /分类用于组织文章/.test(texts) && /新建分类/.test(texts))
  }
  await context.close()
}
{
  // classic-light 对照：同一拦截，虚线框不应生效
  const { context, page } = await newPage('classic-light')
  await page.route('**/v1/categories/tree', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: null, data: [] }) }))
  await gotoDash(page, '/categories')
  const icon = page.locator('.ui-empty-icon').first()
  if ((await icon.count()) === 0) {
    fail++; console.log('  FAIL classic 分类空态未渲染 EmptyState')
  } else {
    const cs = await icon.evaluate(el => {
      const s = getComputedStyle(el)
      return { borderTopStyle: s.borderTopStyle, bg: s.backgroundColor }
    })
    check('classic：空状态图标无虚线框（作用域隔离）',
      cs.borderTopStyle !== 'dashed', JSON.stringify(cs))
  }
  await context.close()
}
{
  // 合集搜索无结果 → 「没有匹配的合集」无按钮
  const { context, page } = await newPage('paper-light')
  await gotoDash(page, '/collections')
  const input = page.locator('input[placeholder*="搜索"]').first()
  await input.fill('zzz不存在的合集999')
  await input.press('Enter')
  await page.waitForTimeout(1200)
  const texts = await page.evaluate(() => document.body.innerText)
  const hasEmptyIcon = await page.locator('.ui-empty-icon').count()
  const btnCount = await page.evaluate(() => {
    const icon = document.querySelector('.ui-empty-icon')
    const root = icon?.closest('div')?.parentElement
    return root ? root.querySelectorAll('button').length : -1
  })
  check('合集无匹配空态文案', /没有匹配的合集/.test(texts) && /换个关键词试试/.test(texts))
  check('无匹配空态无操作按钮', hasEmptyIcon > 0 && btnCount === 0, `btnCount=${btnCount}`)
  await context.close()
}
{
  // 知识库零库空态：拦截 knowledge-bases 返回 []
  const { context, page } = await newPage('paper-light')
  await page.route('**/v1/rag/knowledge-bases', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: '0', message: null, data: [] }) }))
  await gotoDash(page, '/knowledge')
  const texts = await page.evaluate(() => document.body.innerText)
  check('知识库零库空态文案+按钮',
    /还没有知识库/.test(texts) && /创建知识库后/.test(texts) && /新建知识库/.test(texts))
  await context.close()
}

// ── §5 知识库自动选中 ──
console.log('== §5 知识库首次加载自动选中 ==')
{
  const { context, page } = await newPage('paper-light')
  await gotoDash(page, '/knowledge')
  const texts = await page.evaluate(() => document.body.innerText)
  if (/还没有知识库/.test(texts)) {
    skipped('知识库自动选中', '库中无知识库')
  } else {
    // 未点击任何卡片，详情面板应已出现
    const panelVisible = await page.evaluate(() => /文档总数/.test(document.body.innerText))
    check('进入即展示文档面板（自动选中第一个 KB）', panelVisible)
    const selectedCount = await page.evaluate(() =>
      [...document.querySelectorAll('button')].filter(b => b.className.includes('border-accent')).length)
    check('有 KB 卡片处于选中态', selectedCount >= 1, `selected=${selectedCount}`)

    // 点击已选中卡片 → 取消选中 → 占位文案，且不被自动选回
    const kbCard = page.locator('button').filter({ hasText: /篇文档/ }).first()
    const kbName = (await kbCard.textContent())?.slice(0, 30)
    await kbCard.click()
    await page.waitForTimeout(800)
    const after = await page.evaluate(() => ({
      placeholder: /点击上方知识库查看文档/.test(document.body.innerText),
      panel: /文档总数/.test(document.body.innerText),
    }))
    check('取消选中后显示新占位文案且面板收起', after.placeholder && !after.panel, JSON.stringify(after))
    await page.waitForTimeout(1200)
    const still = await page.evaluate(() => /点击上方知识库查看文档/.test(document.body.innerText))
    check('取消选中后未被自动选回', still)

    // 再点同一卡片 → 重新选中，面板回来
    await kbCard.click()
    await page.waitForTimeout(1200)
    const reselected = await page.evaluate(() => /文档总数/.test(document.body.innerText))
    check('再次点击恢复选中与面板', reselected, `kb=${kbName}`)
  }
  await context.close()
}

await browser.close()
console.log(`D5 WALKTHROUGH DONE: ${pass} pass / ${fail} fail / ${skip} skip`)
process.exit(fail === 0 ? 0 : 1)
