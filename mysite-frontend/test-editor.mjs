/**
 * MarkdownEditor 稳定性与性能测试套件。
 *
 * 运行方式:  node test-editor.mjs
 *
 * 覆盖范围:
 *   1. computeBlocks —— 代码块/数学块/标注块识别（含嵌套、特殊字符、未闭合）
 *   2. 安全不变量回归测试 —— 禁止 Decoration.line 与整行 Decoration.replace 共存
 *      这是历次编辑器崩溃的根因，本测试覆盖所有装饰函数：
 *        - codeBlockDecos（已修复）
 *        - headingDecos（本次修复）
 *        - blockquoteDecos（本次修复）
 *        - calloutDecos（本次修复）
 *   3. 性能 —— 大文档（1000/5000 行）块计算耗时
 */

import { createJiti } from 'jiti'
import { Text } from '@codemirror/state'

const jiti = createJiti(import.meta.url)
const {
  computeBlocks,
  codeBlockDecos,
  headingDecos,
  blockquoteDecos,
  calloutDecos,
} = await jiti.import('./src/editor/livePreview.ts')

// ── 测试框架 ──
let passed = 0
let failed = 0
const failures = []

function assert(cond, msg) {
  if (cond) { passed++ }
  else { failed++; failures.push(msg); console.error('  ✗ FAIL:', msg) }
}

function assertEq(actual, expected, msg) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected)
  if (ok) { passed++ }
  else { failed++; failures.push(`${msg}\n    expected: ${JSON.stringify(expected)}\n    actual:   ${JSON.stringify(actual)}`); console.error('  ✗ FAIL:', msg) }
}

function section(name) { console.log(`\n── ${name} ──`) }

function makeDoc(lines) {
  return Text.of(lines)
}

// 区分 Decoration 类型：通过原型构造器名判定
// LineDecoration / MarkDecoration / PointDecoration(replace)
function decoType(deco) { return Object.getPrototypeOf(deco)?.constructor?.name }
function isReplaceDeco(deco) { return decoType(deco) === 'PointDecoration' }
function isLineDeco(deco) { return decoType(deco) === 'LineDecoration' }
function isMarkDeco(deco) { return decoType(deco) === 'MarkDecoration' }

// 取代码块列表（仅 code 类型）的简化形式便于断言
function codeBlocksOf(doc) {
  return computeBlocks(doc)
    .filter(b => b.type === 'code')
    .map(b => ({ start: b.startLine, end: b.endLine, lang: b.lang }))
}

/**
 * 核心安全不变量检查函数。
 *
 * 验证：如果装饰列表中存在 LineDecoration，则不存在任何 ReplaceDecoration
 * 覆盖整行文本（from === lineFrom && to - from >= text.length）。
 *
 * 违反此不变量是历次编辑器崩溃的根因。
 */
function assertNoFullLineReplaceWithLineDeco(decos, lineFrom, lineText, desc) {
  const hasLineDeco = decos.some(d => isLineDeco(d.deco))
  if (!hasLineDeco) return // 无 line 装饰时整行 replace 是安全的

  for (const d of decos) {
    if (!isReplaceDeco(d.deco)) continue
    if (d.from < d.to) {
      const replaceLen = d.to - d.from
      const isFullLine = d.from <= lineFrom && replaceLen >= lineText.length
      assert(!isFullLine, `${desc}: 禁止 line 装饰 + 整行 replace 共存 (text="${lineText}", replaceLen=${replaceLen})`)
    }
  }
}

// ═══════════════════════════════════════════════════════════════
// 1. computeBlocks: 代码块识别
// ═══════════════════════════════════════════════════════════════
section('computeBlocks: 基础代码块')
{
  const doc = makeDoc(['```', 'console.log(1)', '```'])
  assertEq(codeBlocksOf(doc), [{ start: 1, end: 3, lang: '' }], '无语言代码块')
}

section('computeBlocks: 带语言标识')
{
  const doc = makeDoc(['```js', 'const x = 1', '```'])
  assertEq(codeBlocksOf(doc), [{ start: 1, end: 3, lang: 'js' }], 'js 代码块')
}

section('computeBlocks: 多个代码块')
{
  const doc = makeDoc(['```py', 'a=1', '```', '正文', '```ts', 'let b: number', '```'])
  assertEq(codeBlocksOf(doc), [
    { start: 1, end: 3, lang: 'py' },
    { start: 5, end: 7, lang: 'ts' },
  ], '两个代码块')
}

section('computeBlocks: 未闭合代码块')
{
  const doc = makeDoc(['```js', 'code', 'more code'])
  assertEq(codeBlocksOf(doc), [{ start: 1, end: 3, lang: 'js' }], '未闭合块延伸到文档末尾')
}

section('computeBlocks: 代码块内含 ``` 字符串（非围栏）')
{
  // 行内有 ``` 但不以 ``` 开头，不应误判为围栏
  const doc = makeDoc(['```js', 'const s = "```"', 'console.log(s)', '```'])
  const blocks = codeBlocksOf(doc)
  assertEq(blocks, [{ start: 1, end: 4, lang: 'js' }], '含 ``` 字符串的代码块')
}

section('computeBlocks: 空代码块')
{
  const doc = makeDoc(['```', '```'])
  assertEq(codeBlocksOf(doc), [{ start: 1, end: 2, lang: '' }], '空代码块')
}

section('computeBlocks: 波浪号围栏 ~~~')
{
  const doc = makeDoc(['~~~ruby', 'puts "hi"', '~~~'])
  const blocks = computeBlocks(doc).filter(b => b.type === 'code')
  assertEq(blocks.length, 1, '波浪号围栏被识别')
  assertEq(blocks[0].lang, 'ruby', '波浪号围栏语言')
}

section('computeBlocks: 特殊字符与 Unicode')
{
  const doc = makeDoc(['```', '中文内容 🎉 <script>alert(1)</script>', 'const emoji = "🚀"', '```'])
  const blocks = codeBlocksOf(doc)
  assertEq(blocks, [{ start: 1, end: 4, lang: '' }], '含特殊字符/Unicode 的代码块')
}

section('computeBlocks: 嵌套标注内的代码块')
{
  // 标注块内含代码块
  const doc = makeDoc(['> [!NOTE]', '> 标题', '> ```js', '> code', '> ```'])
  const blocks = computeBlocks(doc)
  const callouts = blocks.filter(b => b.type === 'callout')
  assert(callouts.length >= 1, '标注块被识别')
}

// ═══════════════════════════════════════════════════════════════
// 2. 安全不变量回归测试 —— 所有装饰函数
// ═══════════════════════════════════════════════════════════════

// ── 2.1 codeBlockDecos（已修复，回归保护）──
section('codeBlockDecos: 安全不变量 —— 禁止整行 replace + line 共存')
{
  const block = { type: 'code', startLine: 1, endLine: 3, lang: 'js' }
  const cases = [
    { text: '```js', ln: 1, desc: '开头围栏(带语言)' },
    { text: '```', ln: 1, desc: '开头围栏(无语言)' },
    { text: 'code line', ln: 2, desc: '内容行' },
    { text: '```', ln: 3, desc: '结尾围栏' },
    { text: '```  ', ln: 3, desc: '结尾围栏(带尾空格)' },
    { text: '  ```js', ln: 1, desc: '缩进开头围栏' },
    { text: '```js extra', ln: 1, desc: '围栏后带额外内容' },
  ]

  for (const c of cases) {
    const decos = codeBlockDecos(c.text, 0, block, c.ln)
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `codeBlockDecos/${c.desc}`)
  }
}

section('codeBlockDecos: 开头围栏带语言 —— 仅隐藏反引号，保留语言标记')
{
  const block = { type: 'code', startLine: 1, endLine: 3, lang: 'js' }
  const decos = codeBlockDecos('```js', 0, block, 1)
  // 应存在一个 replace 范围恰好覆盖 ``` (3 字符)，不覆盖 js
  const backtickReplace = decos.find(d => isReplaceDeco(d.deco) && d.to - d.from === 3)
  assert(backtickReplace !== undefined, '开头围栏: 存在仅覆盖 ``` 的 replace')
  // 不应存在覆盖整行 (5 字符) 的 replace
  const fullLine = decos.some(d => isReplaceDeco(d.deco) && d.to - d.from >= 5)
  assert(!fullLine, '开头围栏: 不存在覆盖整行的 replace')
}

section('codeBlockDecos: 无语言围栏 —— 使用 mark 而非 replace')
{
  const block = { type: 'code', startLine: 1, endLine: 2, lang: '' }
  const decos = codeBlockDecos('```', 0, block, 1)
  // 整行就是 ```，不应有任何 replace 覆盖它（应使用 mark）
  const fullLineReplace = decos.some(d => isReplaceDeco(d.deco) && d.to - d.from >= 3)
  assert(!fullLineReplace, '无语言围栏: 不存在覆盖整行的 replace')
  // 应存在 mark 装饰
  const hasMark = decos.some(d => isMarkDeco(d.deco))
  assert(hasMark, '无语言围栏: 使用 mark 装饰（非 replace）')
}

// ── 2.2 headingDecos（本次修复重点）──
section('headingDecos: 安全不变量 —— 空标题不崩溃')
{
  // 这些是之前导致崩溃的场景：行只有标记，没有内容
  const cases = [
    { text: '# ', desc: '一级空标题' },
    { text: '## ', desc: '二级空标题' },
    { text: '###### ', desc: '六级空标题' },
    { text: '#', desc: '只有井号无空格（不匹配，应返回空）' },
  ]

  for (const c of cases) {
    const decos = headingDecos(c.text, 0)
    // 如果不匹配（如 "#"），decos 为空，跳过检查
    if (decos.length === 0) continue
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `headingDecos/${c.desc}`)
  }
}

section('headingDecos: 有内容的标题 —— 隐藏标记 + 样式化内容')
{
  const decos = headingDecos('# Hello', 0)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '# Hello', 'headingDecos/有内容标题')

  // 应存在 replace 隐藏 "# " (2 字符)
  const markerReplace = decos.find(d => isReplaceDeco(d.deco) && d.to - d.from === 2)
  assert(markerReplace !== undefined, '有内容标题: 存在隐藏标记的 replace')

  // 应存在 mark 样式化内容
  const hasMark = decos.some(d => isMarkDeco(d.deco))
  assert(hasMark, '有内容标题: 存在样式化内容的 mark')
}

section('headingDecos: 空标题 —— 使用 mark 弱化而非 replace 隐藏')
{
  const decos = headingDecos('# ', 0)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '# ', 'headingDecos/空标题')

  // 不应存在 replace（因为整行只有标记）
  const hasReplace = decos.some(d => isReplaceDeco(d.deco) && d.from < d.to)
  assert(!hasReplace, '空标题: 不存在 replace 装饰')

  // 应存在 mark 弱化整行
  const hasMark = decos.some(d => isMarkDeco(d.deco))
  assert(hasMark, '空标题: 使用 mark 弱化标记')
}

// ── 2.3 blockquoteDecos（本次修复重点）──
section('blockquoteDecos: 安全不变量 —— 空引用不崩溃')
{
  const cases = [
    { text: '> ', desc: '空引用(带空格)' },
    { text: '>', desc: '只有大于号' },
    { text: '> content', desc: '有内容的引用' },
    { text: '  > ', desc: '缩进空引用' },
  ]

  for (const c of cases) {
    const decos = blockquoteDecos(c.text, 0)
    if (decos.length === 0) continue
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `blockquoteDecos/${c.desc}`)
  }
}

section('blockquoteDecos: 空引用 —— 使用 mark 弱化而非 replace')
{
  const decos = blockquoteDecos('> ', 0)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '> ', 'blockquoteDecos/空引用')

  const hasReplace = decos.some(d => isReplaceDeco(d.deco) && d.from < d.to)
  assert(!hasReplace, '空引用: 不存在 replace 装饰')

  const hasMark = decos.some(d => isMarkDeco(d.deco))
  assert(hasMark, '空引用: 使用 mark 弱化标记')
}

section('blockquoteDecos: 有内容的引用 —— 隐藏标记 + 样式化内容')
{
  const decos = blockquoteDecos('> This is a quote', 0)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '> This is a quote', 'blockquoteDecos/有内容引用')

  const markerReplace = decos.find(d => isReplaceDeco(d.deco) && d.to - d.from === 2)
  assert(markerReplace !== undefined, '有内容引用: 存在隐藏 "> " 的 replace')
}

// ── 2.4 calloutDecos（本次修复重点）──
section('calloutDecos: 安全不变量 —— 无标题标注不崩溃')
{
  const block = { type: 'callout', startLine: 1, endLine: 3, calloutType: 'NOTE' }
  const cases = [
    { text: '> [!NOTE]', ln: 1, desc: '无标题标注头' },
    { text: '> [!NOTE] Title', ln: 1, desc: '有标题标注头' },
    { text: '>', ln: 2, desc: '空续行' },
    { text: '> content', ln: 2, desc: '有内容续行' },
    { text: '> [!TIP]', ln: 1, desc: 'TIP 类型无标题' },
    { text: '> [!WARNING] Warn Title', ln: 1, desc: 'WARNING 类型有标题' },
  ]

  for (const c of cases) {
    const decos = calloutDecos(c.text, 0, block, c.ln)
    if (decos.length === 0) continue
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `calloutDecos/${c.desc}`)
  }
}

section('calloutDecos: 无标题标注头 —— 使用 mark 弱化而非 widget replace')
{
  const block = { type: 'callout', startLine: 1, endLine: 3, calloutType: 'NOTE' }
  const decos = calloutDecos('> [!NOTE]', 0, block, 1)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '> [!NOTE]', 'calloutDecos/无标题标注头')

  // 不应存在覆盖整行的 replace（widget replace 也不行）
  const fullLineReplace = decos.some(d => isReplaceDeco(d.deco) && d.to - d.from >= 10)
  assert(!fullLineReplace, '无标题标注头: 不存在覆盖整行的 replace')

  // 应存在 mark 弱化
  const hasMark = decos.some(d => isMarkDeco(d.deco))
  assert(hasMark, '无标题标注头: 使用 mark 弱化标记')
}

section('calloutDecos: 有标题标注头 —— 使用 widget 替换标记')
{
  const block = { type: 'callout', startLine: 1, endLine: 3, calloutType: 'NOTE' }
  const decos = calloutDecos('> [!NOTE] Title', 0, block, 1)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '> [!NOTE] Title', 'calloutDecos/有标题标注头')

  // 应存在 widget replace（覆盖 "> [!NOTE] " = 10 字符，但行总长 16，不覆盖整行）
  const widgetReplace = decos.find(d => isReplaceDeco(d.deco) && d.to - d.from === 10)
  assert(widgetReplace !== undefined, '有标题标注头: 存在 widget replace 覆盖标记')
}

section('calloutDecos: 空续行 —— 使用 mark 弱化而非 replace')
{
  const block = { type: 'callout', startLine: 1, endLine: 3, calloutType: 'NOTE' }
  const decos = calloutDecos('>', 0, block, 2)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '>', 'calloutDecos/空续行')

  const hasReplace = decos.some(d => isReplaceDeco(d.deco) && d.from < d.to)
  assert(!hasReplace, '空续行: 不存在 replace 装饰')

  const hasMark = decos.some(d => isMarkDeco(d.deco))
  assert(hasMark, '空续行: 使用 mark 弱化标记')
}

section('calloutDecos: 有内容续行 —— 隐藏标记 + 样式化内容')
{
  const block = { type: 'callout', startLine: 1, endLine: 3, calloutType: 'NOTE' }
  const decos = calloutDecos('> Some content here', 0, block, 2)
  assertNoFullLineReplaceWithLineDeco(decos, 0, '> Some content here', 'calloutDecos/有内容续行')

  // 应存在 replace 隐藏 "> " (2 字符)
  const markerReplace = decos.find(d => isReplaceDeco(d.deco) && d.to - d.from === 2)
  assert(markerReplace !== undefined, '有内容续行: 存在隐藏 "> " 的 replace')
}

// ═══════════════════════════════════════════════════════════════
// 3. 性能测试：大文档块计算
// ═══════════════════════════════════════════════════════════════
section('性能: 1000 行文档块计算')
{
  const lines = []
  for (let i = 0; i < 200; i++) {
    lines.push('```js')
    lines.push(`const x${i} = ${i};`)
    lines.push('console.log(x' + i + ');')
    lines.push('```')
    lines.push('正文段落 ' + i + '，包含 **加粗** 和 `行内代码`。')
  }
  const doc = makeDoc(lines)
  assertEq(doc.lines, 1000, '文档行数 = 1000')

  const t0 = performance.now()
  const blocks = computeBlocks(doc)
  const t1 = performance.now()
  const codeBlocks = blocks.filter(b => b.type === 'code')
  assertEq(codeBlocks.length, 200, '1000 行文档识别 200 个代码块')
  console.log(`  ℹ computeBlocks(1000 行): ${(t1 - t0).toFixed(2)}ms`)
  // 单次块计算应远低于 16ms (60fps 帧预算)
  assert(t1 - t0 < 16, '1000 行块计算 < 16ms (单帧预算)')
}

section('性能: 5000 行文档块计算')
{
  const lines = []
  for (let i = 0; i < 1000; i++) {
    lines.push('```py')
    lines.push(`def f${i}(): pass`)
    lines.push('```')
    lines.push('段落 ' + i)
    lines.push('另一段 ' + i)
  }
  const doc = makeDoc(lines)
  assertEq(doc.lines, 5000, '文档行数 = 5000')

  const t0 = performance.now()
  const blocks = computeBlocks(doc)
  const t1 = performance.now()
  console.log(`  ℹ computeBlocks(5000 行): ${(t1 - t0).toFixed(2)}ms`)
  assert(blocks.filter(b => b.type === 'code').length === 1000, '5000 行文档识别 1000 个代码块')
  // 5000 行块计算应在合理范围内
  assert(t1 - t0 < 50, '5000 行块计算 < 50ms')
}

// ═══════════════════════════════════════════════════════════════
// 4. 边界场景测试
// ═══════════════════════════════════════════════════════════════
section('边界: 标注块内含空续行 —— 不崩溃')
{
  // 模拟用户在标注块中按 Enter 后产生的空续行
  const block = { type: 'callout', startLine: 1, endLine: 4, calloutType: 'NOTE' }
  const lines = [
    { text: '> [!NOTE]', ln: 1, desc: '标注头(无标题)' },
    { text: '>', ln: 2, desc: '空续行1' },
    { text: '>', ln: 3, desc: '空续行2' },
    { text: '> [!TIP] Tip', ln: 4, desc: '标注尾(有标题)' },
  ]
  for (const c of lines) {
    const decos = calloutDecos(c.text, 0, block, c.ln)
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `边界/标注块/${c.desc}`)
  }
}

section('边界: 连续空标题 —— 不崩溃')
{
  const cases = [
    { text: '# ', desc: '空标题1' },
    { text: '## ', desc: '空标题2' },
    { text: '### ', desc: '空标题3' },
  ]
  for (const c of cases) {
    const decos = headingDecos(c.text, 0)
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `边界/连续空标题/${c.desc}`)
  }
}

section('边界: 连续空引用 —— 不崩溃')
{
  const cases = [
    { text: '> ', desc: '空引用1' },
    { text: '>', desc: '空引用2' },
    { text: '> ', desc: '空引用3' },
  ]
  for (const c of cases) {
    const decos = blockquoteDecos(c.text, 0)
    assertNoFullLineReplaceWithLineDeco(decos, 0, c.text, `边界/连续空引用/${c.desc}`)
  }
}

// ═══════════════════════════════════════════════════════════════
// 结果汇总
// ═══════════════════════════════════════════════════════════════
console.log(`\n══════════════════════════════════════`)
console.log(`  通过: ${passed}  失败: ${failed}`)
if (failed > 0) {
  console.log('  失败项:')
  failures.forEach(f => console.log('    - ' + f.split('\n')[0]))
  process.exit(1)
} else {
  console.log('  全部通过 ✓')
}
