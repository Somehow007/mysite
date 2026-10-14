/**
 * 导入补传占位符的解析/替换。
 *
 * 占位由后端 ArticleDocumentParser.buildPlaceholder 写入，形如：
 *   > [!warning] 图片待补传
 *   > 原引用：`foo.png`
 *   > 未能自动上传。请在此处粘贴或拖入图片，完成后删除本提示。
 *
 * 进入 Milkdown 编辑器后会被 callout 插件序列化为另一种形态：
 *   - `[` `]` 可能被转义为 `\[` `\]`
 *   - 类型大写为 `[!WARNING]`
 *   - 段落之间出现单独的 `>` 空行
 * 因此匹配必须按「引用行 + 块内原引用」进行，不能死板比对字面量。
 */

/** 取路径/URL 的最后一段作为文件名（去掉 query/hash） */
export function fileBaseName(path: string): string {
  const cleaned = path.split(/[?#]/)[0] || path
  return cleaned.split(/[\\/]/).pop() || cleaned
}

/** 匹配用 key：小写 + 尽量 decode，便于 `image.png` 与 `my%20image.png` 对齐 */
export function matchKey(name: string): string {
  let t = name.trim().toLowerCase()
  try {
    t = decodeURIComponent(t)
  } catch {
    /* 非编码串原样 */
  }
  return t
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function isQuoteLine(line: string): boolean {
  return /^\s*>/.test(line)
}

function isPlaceholderStartLine(line: string): boolean {
  return isQuoteLine(line) && line.includes('图片待补传')
}

/**
 * 判断占位块是否对应 originalRef。
 * 优先比对 `原引用：\`...\`` 代码段（兼容长引用被截断到 120 字符），
 * 兜底用 basename 精确等值（避免 `img.png` 误命中 `myimg.png`）。
 */
function blockMatchesRef(block: string, originalRef: string): boolean {
  const targetRef = matchKey(originalRef)
  const targetBase = matchKey(fileBaseName(originalRef))

  const codes = block.match(/`([^`]+)`/g) || []
  for (const c of codes) {
    const inner = c.slice(1, -1)
    const innerKey = matchKey(inner)
    const innerBase = matchKey(fileBaseName(inner))
    if (innerKey === targetRef || innerBase === targetBase) return true
    // 后端超长引用截断为「前 117 字 + …」
    const trimmed = innerKey.replace(/…$/, '')
    if (trimmed.length >= 8 && (targetRef.startsWith(trimmed) || trimmed.startsWith(targetRef))) {
      return true
    }
  }

  // 占位未加代码段（手改后）时退回全文包含
  const b = matchKey(block)
  if (targetRef && b.includes(targetRef)) return true
  return false
}

/**
 * 把「图片待补传」占位（或残留的 Obsidian / Markdown 引用）替换为已上传图片。
 * 未发生任何替换时原样返回（调用方据此判断补传是否真正生效）。
 */
export function replaceImagePlaceholder(
  markdown: string,
  originalRef: string,
  newUrl: string,
): string {
  if (!markdown || !newUrl) return markdown

  const lines = markdown.split('\n')
  const out: string[] = []
  const asImage = `![](${newUrl})`
  let replaced = false
  let i = 0

  while (i < lines.length) {
    const line = lines[i]!
    if (isPlaceholderStartLine(line)) {
      let j = i + 1
      while (j < lines.length && isQuoteLine(lines[j]!)) j++
      const block = lines.slice(i, j).join('\n')
      if (blockMatchesRef(block, originalRef)) {
        // 与正文图片保持一致：前后各留一个空行，避免和上下文粘连
        if (out.length > 0 && out[out.length - 1]!.trim() !== '') out.push('')
        out.push(asImage, '')
        i = j
        // 吃掉占位块后紧跟的空行，避免连续空行
        while (i < lines.length && lines[i]!.trim() === '' && out[out.length - 1] === '') {
          i++
        }
        replaced = true
        continue
      }
    }
    out.push(line)
    i++
  }

  if (replaced) return out.join('\n')

  // 占位块未命中：替换残留的 Obsidian / Markdown 图片语法
  const esc = escapeRegex(originalRef)
  const escBase = escapeRegex(fileBaseName(originalRef))

  const obs = new RegExp(`!\\[\\[(?:${esc}|${escBase})(?:\\|[^\\]]*)?\\]\\]`, 'g')
  let out2 = markdown.replace(obs, asImage)
  if (out2 !== markdown) return out2

  const mdImg = new RegExp(`!\\[[^\\]]*\\]\\(\\s*<?(?:${esc}|${escBase})>?\\s*\\)`, 'g')
  out2 = markdown.replace(mdImg, asImage)
  if (out2 !== markdown) return out2

  const htmlImg = new RegExp(
    `(<img\\b[^>]*?\\bsrc\\s*=\\s*["'])(?:${esc}|${escBase})(["'][^>]*?>)`,
    'gi',
  )
  return markdown.replace(htmlImg, `$1${newUrl}$2`)
}
