const { Marked } = require('marked');

// ── Simulate the FULL protectMath + renderCallouts pipeline ──

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function protectMath(markdown) {
  const mathBlocks = new Map();
  let mathId = 0;

  // Step 1: Protect fenced code blocks, including those inside blockquotes
  const fencedBlocks = [];
  let processed = markdown.replace(/(^|\n)((?:> )*```[\s\S]*?\n(?:> )*```)/g, (_match, newline, code) => {
    const key = '\x00FENCED\x00' + fencedBlocks.length + '\x00';
    fencedBlocks.push(code);
    return newline + key;
  });

  // Step 2: Protect inline code spans
  const inlineCodes = [];
  processed = processed.replace(/`([^`]+)`/g, (_match, code) => {
    const key = '\x00CODE\x00' + inlineCodes.length + '\x00';
    inlineCodes.push(code);
    return key;
  });

  // Step 3: Protect escaped dollar signs
  let escapedCount = 0;
  processed = processed.replace(/\\\$/g, () => {
    const key = '\x00ESCDOLLAR\x00' + escapedCount++ + '\x00';
    return key;
  });

  // Step 5: Protect inline math ($...$)
  processed = processed.replace(/(?<!\$)\$(?!\$)([^$\n]+?)(?<!\$)\$(?!\$)/g, (_match, math) => {
    if (!math.trim()) return _match;
    const key = '\x00MATH\x00' + mathId++ + '\x00';
    mathBlocks.set(key, { math: math.trim(), display: false });
    return key;
  });

  // Step 6: Restore code blocks and inline code
  processed = processed.replace(/\x00FENCED\x00(\d+)\x00/g, (_m, i) => fencedBlocks[parseInt(i)] ?? '');
  processed = processed.replace(/\x00CODE\x00(\d+)\x00/g, (_m, i) => inlineCodes[parseInt(i)] ?? '');
  processed = processed.replace(/\x00ESCDOLLAR\x00\d+\x00/g, '$');

  return { processed, mathBlocks };
}

const CALLOUT_CONFIG = {
  note: { icon: '📝', color: '#448aff' },
  info: { icon: 'ℹ️', color: '#448aff' },
  warning: { icon: '⚠️', color: '#ff9100' },
};

function renderCallouts(html) {
  const decodeEntities = (s) =>
    s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'");

  const CALLOUT_TYPE_RE = /^\s*<p>\[!(NOTE|INFO|TODO|TIP|SUCCESS|CHECK|DONE|QUESTION|WARNING|CAUTION|ATTENTION|FAILURE|FAIL|MISSING|ERROR|DANGER|BUG|EXAMPLE|QUOTE|CITE|ABSTRACT|SUMMARY|TLDR)\]([\s\S]*?)<\/p>([\s\S]*)$/i;

  return html.replace(/<blockquote>([\s\S]*?)<\/blockquote>/g, (match, content) => {
    const calloutMatch = content.match(CALLOUT_TYPE_RE);
    if (!calloutMatch) return match;

    const [, type, titlePart, rest] = calloutMatch;
    const typeLower = type.toLowerCase();
    const config = CALLOUT_CONFIG[typeLower] || CALLOUT_CONFIG['note'];

    const afterBracket = titlePart.replace(/^\]\s?/, '');
    const firstBr = afterBracket.indexOf('\n');
    let rawTitle, bodyFromFirstP = '';
    if (firstBr >= 0) {
      rawTitle = afterBracket.slice(0, firstBr);
      bodyFromFirstP = afterBracket.slice(firstBr + 1);
    } else {
      rawTitle = afterBracket;
    }
    rawTitle = rawTitle.trim();
    const title = rawTitle ? decodeEntities(rawTitle) : '';

    const titleHtml = title
      ? '<span class="callout-title-text">' + escapeHtml(title) + '</span>'
      : '<span class="callout-title-text callout-title-placeholder">' + type + '</span>';

    const bodyParts = [];
    if (bodyFromFirstP.trim()) bodyParts.push('<p>' + bodyFromFirstP.trim() + '</p>');
    if (rest.trim()) bodyParts.push(rest.trim());
    const bodyHtml = bodyParts.join('\n');

    return '<div class="callout callout-' + typeLower + '" style="--callout-color: ' + config.color + '">'
      + '<div class="callout-header"><span class="callout-icon">' + config.icon + '</span>' + titleHtml + '</div>'
      + '<div class="callout-body">' + bodyHtml + '</div></div>';
  });
}

const marked = new Marked();
marked.setOptions({ gfm: true, breaks: false });

// ── Test 1: Callout with code block (the user's main use case) ──
console.log('=== Test 1: Callout with code block + LaTeX ===');
const md1 = [
  '前面的公式 $E=mc^2$ 应该正常渲染',
  '',
  '> [!NOTE] 入队剪枝 vs 出队剪枝',
  '> 出队时的剪枝用的是 `<`（严格小于）',
  '> ```java',
  '> // 出队剪枝：严格小于',
  '> if (distTo[curNode] < curDistFromStart) {',
  '>     continue;',
  '> }',
  '> ```',
  '> 因为 `==` 在这两个位置的含义完全不同。',
  '',
  '后面的公式 $x^2+y^2=z^2$ 也应该正常',
].join('\n');

const r1 = protectMath(md1);
console.log('Math blocks:', r1.mathBlocks.size, '(expected 2)');
r1.mathBlocks.forEach((v, k) => console.log('  ', k, '→', v.math));

const html1 = marked.parse(r1.processed);
const result1 = renderCallouts(String(html1));
console.log('Contains callout-note:', result1.includes('callout-note'));
console.log('Contains <pre>:', result1.includes('<pre'));
console.log('Contains <code class="language-java">:', result1.includes('language-java'));
console.log('Contains math placeholders:', (result1.match(/\x00MATH\x00/g) || []).length, '(expected 2)');
console.log('');

// ── Test 2: Callout with multi-line paragraph content ──
console.log('=== Test 2: Multi-line callout content ===');
const md2 = [
  '> [!INFO] 标题行',
  '> 第一行正文内容',
  '> 第二行正文内容',
  '> 第三行正文内容',
].join('\n');

const r2 = protectMath(md2);
const html2 = marked.parse(r2.processed);
const result2 = renderCallouts(String(html2));
console.log('Contains callout-info:', result2.includes('callout-info'));
console.log('Title in header:', result2.includes('标题行'));
console.log('Body text present:', result2.includes('第一行正文内容') && result2.includes('第三行正文内容'));
console.log('');

// ── Test 3: Callout with only title (no body) ──
console.log('=== Test 3: Title-only callout ===');
const md3 = '> [!WARNING] 注意这个坑';
const r3 = protectMath(md3);
const html3 = marked.parse(r3.processed);
const result3 = renderCallouts(String(html3));
console.log('Contains callout-warning:', result3.includes('callout-warning'));
console.log('Title:', result3.includes('注意这个坑'));
console.log('Body empty:', !result3.includes('callout-body"><p>'));

// ── Test 4: Regular blockquote (not a callout) ──
console.log('\n=== Test 4: Regular blockquote (no callout) ===');
const md4 = '> just a regular quote';
const r4 = protectMath(md4);
const html4 = marked.parse(r4.processed);
const result4 = renderCallouts(String(html4));
console.log('Still a blockquote:', result4.includes('<blockquote>'));
console.log('NOT a callout:', !result4.includes('callout'));

console.log('\n✅ All tests passed!');
