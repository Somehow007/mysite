package io.github.somehow.mysite.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章文档（txt / md）解析工具：编码探测、标题提取、图片引用扫描与替换。
 * 支持三种图片写法：标准 Markdown、HTML &lt;img&gt;、Obsidian 嵌入（![[...]]）。
 * 纯函数，无 IO，便于单测。
 */
public final class ArticleDocumentParser {

    /** Markdown 图片：![alt](url) / ![alt](url "title") */
    private static final Pattern MD_IMAGE = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(\\s*<?([^\\s)>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");

    /** HTML 图片：&lt;img src="..."&gt; */
    private static final Pattern HTML_IMAGE = Pattern.compile(
            "<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>",
            Pattern.CASE_INSENSITIVE);

    /**
     * Obsidian 嵌入：![[target]] / ![[target|300]] / ![[target|alt]]
     * target 可含路径与空格；笔记嵌入（无图片扩展名）不在此匹配结果中处理。
     */
    private static final Pattern OBSIDIAN_EMBED = Pattern.compile(
            "!\\[\\[([^\\]|#]+?)(?:\\|([^\\]]+))?\\]\\]");

    private static final Pattern IMAGE_EXTENSION = Pattern.compile(
            "\\.(png|jpe?g|gif|webp|svg|bmp|avif)$",
            Pattern.CASE_INSENSITIVE);

    /** Obsidian 尺寸参数：300 / 300x200 */
    private static final Pattern OBSIDIAN_SIZE = Pattern.compile("^\\d+(?:x\\d+)?$");

    /** 文首一级标题 */
    private static final Pattern H1 = Pattern.compile("^#\\s+(.+?)\\s*$", Pattern.MULTILINE);

    /** YAML front matter 中的 title */
    private static final Pattern FRONT_MATTER_TITLE = Pattern.compile(
            "(?m)^title:\\s*[\"']?(.+?)[\"']?\\s*$");

    private ArticleDocumentParser() {
    }

    /** 图片引用形态 */
    public enum RefKind {
        /** ![alt](url) */
        MARKDOWN,
        /** &lt;img src&gt; */
        HTML,
        /** ![[file]] Obsidian */
        OBSIDIAN
    }

    /** 一次扫描得到的图片引用（含在原文中的完整匹配串，便于整体替换） */
    public record ImageRef(
            String url,
            /** 整段匹配 */
            String fullMatch,
            int start,
            int end,
            RefKind kind,
            /** alt 文本（Obsidian 的 |参数 若非尺寸则为 alt） */
            String alt
    ) {
    }

    /**
     * 解码文档字节：UTF-8 BOM / UTF-8 / GBK 回退。
     * 全部失败时抛 {@link IllegalArgumentException}。
     */
    public static String decodeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        }
        String utf8 = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (!utf8.contains("�")) {
            return utf8;
        }
        try {
            String gbk = new String(bytes, java.nio.charset.Charset.forName("GBK"));
            if (!gbk.contains("�")) {
                return gbk;
            }
        } catch (Exception ignored) {
            // GBK 不可用时忽略
        }
        throw new IllegalArgumentException("unsupported encoding");
    }

    /**
     * 提取建议标题：优先 front matter 的 title，其次文首 H1，最后回退文件名。
     * 若取自 H1 / front matter，会从 content 中去掉对应块，避免标题重复。
     */
    public static TitleResult extractTitle(String content, String fileName) {
        String body = content == null ? "" : content;
        String title = null;

        if (body.startsWith("---")) {
            int end = body.indexOf("\n---", 3);
            if (end > 0) {
                String fm = body.substring(3, end);
                Matcher m = FRONT_MATTER_TITLE.matcher(fm);
                if (m.find()) {
                    title = m.group(1).trim();
                }
                int after = end + 4;
                if (after < body.length() && body.charAt(after) == '\n') {
                    after++;
                }
                body = body.substring(after).stripLeading();
            }
        }

        if (title == null || title.isEmpty()) {
            Matcher m = H1.matcher(body);
            if (m.find() && m.start() < 200) {
                title = m.group(1).trim();
                body = (body.substring(0, m.start()) + body.substring(m.end())).stripLeading();
            }
        }

        if (title == null || title.isEmpty()) {
            title = titleFromFileName(fileName);
        }

        return new TitleResult(title, body);
    }

    public static String titleFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String name = fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.trim();
    }

    /** 取路径的文件名（含扩展名），用于与附件文件名匹配。 */
    public static String baseName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String name = path.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.trim();
    }

    /** 是否图片扩展名（用于过滤 Obsidian 笔记嵌入）。 */
    public static boolean hasImageExtension(String path) {
        if (path == null) {
            return false;
        }
        return IMAGE_EXTENSION.matcher(baseName(path)).find();
    }

    /**
     * 解析 Obsidian 嵌入参数：尺寸（300 / 300x200）忽略，其余作 alt。
     */
    public static String parseObsidianAlt(String param) {
        if (param == null) {
            return "";
        }
        String p = param.trim();
        if (p.isEmpty() || OBSIDIAN_SIZE.matcher(p).matches()) {
            return "";
        }
        return p;
    }

    /** 扫描 Markdown / HTML / Obsidian 中的图片引用，按首次出现顺序去重。 */
    public static List<ImageRef> findImageRefs(String content) {
        List<ImageRef> all = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return all;
        }

        Matcher md = MD_IMAGE.matcher(content);
        while (md.find()) {
            all.add(new ImageRef(md.group(2), md.group(0), md.start(), md.end(),
                    RefKind.MARKDOWN, md.group(1)));
        }

        Matcher html = HTML_IMAGE.matcher(content);
        while (html.find()) {
            all.add(new ImageRef(html.group(1), html.group(0),
                    html.start(), html.end(), RefKind.HTML, ""));
        }

        Matcher obs = OBSIDIAN_EMBED.matcher(content);
        while (obs.find()) {
            String target = obs.group(1) == null ? "" : obs.group(1).trim();
            // 笔记嵌入（无图片扩展名）不当作图片处理
            if (!hasImageExtension(target)) {
                continue;
            }
            String alt = parseObsidianAlt(obs.group(2));
            all.add(new ImageRef(target, obs.group(0), obs.start(), obs.end(),
                    RefKind.OBSIDIAN, alt));
        }

        all.sort((a, b) -> Integer.compare(a.start(), b.start()));

        Map<String, ImageRef> unique = new LinkedHashMap<>();
        for (ImageRef ref : all) {
            unique.putIfAbsent(ref.url(), ref);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 按处理结果替换正文中的图片。
     * <ul>
     *   <li>{@code replacement} 为 null 或等于原 URL → 保留原引用</li>
     *   <li>{@code replacement} 为空串 → 整段图片语法替换为「图片待补传」占位（含原引用）</li>
     *   <li>否则 → 替换为标准 Markdown 图片（保留 alt）</li>
     * </ul>
     *
     * @param urlToReplacement key = 原图片引用，value = 新 URL；空串表示失败需占位
     */
    public static String replaceImages(String content,
                                       Map<String, String> urlToReplacement) {
        if (content == null || content.isEmpty() || urlToReplacement == null || urlToReplacement.isEmpty()) {
            return content;
        }
        String result = replacePattern(content, MD_IMAGE, urlToReplacement, RefKind.MARKDOWN);
        result = replacePattern(result, HTML_IMAGE, urlToReplacement, RefKind.HTML);
        return replacePattern(result, OBSIDIAN_EMBED, urlToReplacement, RefKind.OBSIDIAN);
    }

    private static String replacePattern(String content,
                                         Pattern pattern,
                                         Map<String, String> urlToReplacement,
                                         RefKind kind) {
        Matcher m = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String url;
            String alt = "";
            if (kind == RefKind.MARKDOWN) {
                url = m.group(2);
                alt = m.group(1) == null ? "" : m.group(1);
            } else if (kind == RefKind.HTML) {
                url = m.group(1);
            } else {
                // Obsidian：仅处理图片扩展名
                url = m.group(1) == null ? "" : m.group(1).trim();
                if (!hasImageExtension(url)) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    continue;
                }
                alt = parseObsidianAlt(m.group(2));
            }

            String replacement = urlToReplacement.get(url);
            if (replacement == null || replacement.equals(url)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else if (replacement.isEmpty()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(buildPlaceholder(url)));
            } else if (kind == RefKind.HTML) {
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        m.group(0).replace(url, replacement)));
            } else {
                // Markdown / Obsidian 成功后统一为标准 Markdown 图片
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        "![" + alt + "](" + replacement + ")"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 站内图片 URL：/uploads/images/... */
    private static final Pattern UPLOADED_URL = Pattern.compile("/uploads/images/[^\\s)\"'<>\\]]+");

    /**
     * 从文章内容/封面中抽取站内图片 URL（去重，保序）。
     * 用于保存文章时把暂存图片转正。
     */
    public static List<String> extractUploadImageUrls(String... parts) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        if (parts != null) {
            for (String part : parts) {
                if (part == null || part.isEmpty()) {
                    continue;
                }
                // 先取结构化图片引用
                for (ImageRef ref : findImageRefs(part)) {
                    String path = normalizeUploadPath(ref.url());
                    if (path != null) {
                        seen.putIfAbsent(path, Boolean.TRUE);
                    }
                }
                // 再扫正文里裸露的 /uploads/images/（HTML、手写 URL）
                Matcher m = UPLOADED_URL.matcher(part);
                while (m.find()) {
                    String path = normalizeUploadPath(m.group());
                    if (path != null) {
                        seen.putIfAbsent(path, Boolean.TRUE);
                    }
                }
            }
        }
        return new ArrayList<>(seen.keySet());
    }

    /** 归一为 /uploads/images/...；非本站上传路径返回 null。 */
    public static String normalizeUploadPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String u = raw.trim();
        int idx = u.indexOf("/uploads/images/");
        if (idx < 0) {
            return null;
        }
        String path = u.substring(idx);
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int h = path.indexOf('#');
        if (h >= 0) {
            path = path.substring(0, h);
        }
        return path;
    }

    /** 生成「图片待补传」callout 占位（与编辑器 callout 语法一致）。 */
    public static String buildPlaceholder(String originalRef) {
        String ref = originalRef == null ? "" : originalRef.trim();
        if (ref.length() > 120) {
            ref = ref.substring(0, 117) + "…";
        }
        return "\n> [!warning] 图片待补传\n"
                + "> 原引用：`" + ref + "`\n"
                + "> 未能自动上传。请在此处粘贴或拖入图片，完成后删除本提示。\n";
    }

    /** 标题 + 正文 */
    public record TitleResult(String title, String content) {
    }
}
