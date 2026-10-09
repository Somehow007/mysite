package io.github.somehow.mysite.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArticleDocumentParserTest {

    // ── decodeText ──────────────────────────────────────────────

    @Test
    void decode_utf8() {
        assertEquals("你好 markdown", ArticleDocumentParser.decodeText("你好 markdown".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void decode_utf8Bom() {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "标题".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        assertEquals("标题", ArticleDocumentParser.decodeText(all));
    }

    @Test
    void decode_gbkFallback() {
        byte[] gbk = "中文测试".getBytes(java.nio.charset.Charset.forName("GBK"));
        assertEquals("中文测试", ArticleDocumentParser.decodeText(gbk));
    }

    // ── extractTitle ────────────────────────────────────────────

    @Test
    void title_fromH1_removesHeading() {
        ArticleDocumentParser.TitleResult r =
                ArticleDocumentParser.extractTitle("# 我的标题\n\n正文", "a.md");
        assertEquals("我的标题", r.title());
        assertEquals("正文", r.content());
    }

    @Test
    void title_fromFrontMatter() {
        String src = "---\ntitle: 配置里的标题\ntags: a\n---\n\n正文内容";
        ArticleDocumentParser.TitleResult r = ArticleDocumentParser.extractTitle(src, "a.md");
        assertEquals("配置里的标题", r.title());
        assertEquals("正文内容", r.content());
    }

    @Test
    void title_fallbackFileName() {
        ArticleDocumentParser.TitleResult r =
                ArticleDocumentParser.extractTitle("没有标题", "我的文章.md");
        assertEquals("我的文章", r.title());
        assertEquals("没有标题", r.content());
    }

    @Test
    void title_firstH1EvenIfNotAtStart() {
        ArticleDocumentParser.TitleResult r = ArticleDocumentParser.extractTitle(
                "开头一段\n\n# 后面的标题\n\n更多", "x.md");
        assertEquals("后面的标题", r.title());
    }

    // ── findImageRefs ───────────────────────────────────────────

    @Test
    void find_mdAndHtmlImages_dedup() {
        String src = """
                ![a](https://x.com/a.png)
                <img src="https://x.com/b.png" alt="b">
                ![a2](https://x.com/a.png)
                ![local](./img/c.png)
                """;
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(3, refs.size());
        assertEquals("https://x.com/a.png", refs.get(0).url());
        assertEquals("https://x.com/b.png", refs.get(1).url());
        assertEquals("./img/c.png", refs.get(2).url());
    }

    @Test
    void find_dataUri() {
        String src = "![d](data:image/png;base64,iVBORw0KGgo=)";
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(1, refs.size());
        assertTrue(refs.get(0).url().startsWith("data:image/png"));
    }

    @Test
    void find_mdWithTitle() {
        String src = "![alt](https://x.com/a.png \"标题\")";
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(1, refs.size());
        assertEquals("https://x.com/a.png", refs.get(0).url());
    }

    // ── replaceImages ───────────────────────────────────────────

    @Test
    void replace_uploadedKeepsAlt() {
        String src = "前文\n\n![我的图](https://x.com/a.png)\n\n后文";
        Map<String, String> map = new HashMap<>();
        map.put("https://x.com/a.png", "/uploads/images/2025/01/1_x.png");
        String out = ArticleDocumentParser.replaceImages(src, map);
        assertTrue(out.contains("![我的图](/uploads/images/2025/01/1_x.png)"));
        assertFalse(out.contains("https://x.com/a.png"));
    }

    @Test
    void replace_failedUsesPlaceholder() {
        String src = "前文\n\n![x](./a.png)\n\n后文";
        Map<String, String> map = new HashMap<>();
        map.put("./a.png", "");
        String out = ArticleDocumentParser.replaceImages(src, map);
        assertFalse(out.contains("![x](./a.png)"));
        assertTrue(out.contains("图片待补传"));
        assertTrue(out.contains("./a.png"));
    }

    @Test
    void replace_nullMeansKeep() {
        String src = "![x](https://x.com/a.png)";
        Map<String, String> map = new HashMap<>();
        map.put("https://x.com/a.png", "https://x.com/a.png");
        String out = ArticleDocumentParser.replaceImages(src, map);
        assertEquals(src, out);
    }

    @Test
    void replace_htmlImage() {
        String src = "<img src=\"https://x.com/b.png\" alt=\"b\">";
        Map<String, String> map = new HashMap<>();
        map.put("https://x.com/b.png", "/uploads/images/b.png");
        String out = ArticleDocumentParser.replaceImages(src, map);
        assertTrue(out.contains("/uploads/images/b.png"));
        assertTrue(out.contains("alt=\"b\""));
    }

    @Test
    void placeholder_containsRef() {
        String p = ArticleDocumentParser.buildPlaceholder("./img/foo.png");
        assertTrue(p.contains("[!warning]"));
        assertTrue(p.contains("./img/foo.png"));
        assertTrue(p.contains("图片待补传"));
    }

    // ── Obsidian 嵌入 ──────────────────────────────────────────

    @Test
    void obsidian_simpleEmbed_isRecognized() {
        String src = "前文\n\n![[Pasted image 20261009131244.png]]\n\n后文";
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(1, refs.size());
        assertEquals("Pasted image 20261009131244.png", refs.get(0).url());
        assertEquals(ArticleDocumentParser.RefKind.OBSIDIAN, refs.get(0).kind());
    }

    @Test
    void obsidian_widthParam_notTreatedAsAlt() {
        String src = "![[img.png|300]]";
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(1, refs.size());
        assertEquals("img.png", refs.get(0).url());
        assertEquals("", refs.get(0).alt());
    }

    @Test
    void obsidian_altParam_keptAsAlt() {
        String src = "![[img.png|示意图]]";
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(1, refs.size());
        assertEquals("示意图", refs.get(0).alt());
    }

    @Test
    void obsidian_noteEmbed_ignored() {
        String src = "引用笔记 ![[Some Note]] 和图 ![[a.png]]";
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(src);
        assertEquals(1, refs.size());
        assertEquals("a.png", refs.get(0).url());
    }

    @Test
    void obsidian_replaceFailed_writesPlaceholderWithRef() {
        String src = "![[Pasted image 20261009131244.png]]";
        Map<String, String> map = new HashMap<>();
        map.put("Pasted image 20261009131244.png", "");
        String out = ArticleDocumentParser.replaceImages(src, map);
        assertTrue(out.contains("图片待补传"));
        assertTrue(out.contains("Pasted image 20261009131244.png"));
        assertFalse(out.contains("![["));
    }

    @Test
    void obsidian_replaceSuccess_becomesMarkdownImage() {
        String src = "![[Pasted image 20261009131244.png|架构图]]";
        Map<String, String> map = new HashMap<>();
        map.put("Pasted image 20261009131244.png", "/uploads/images/x.png");
        String out = ArticleDocumentParser.replaceImages(src, map);
        assertEquals("![架构图](/uploads/images/x.png)", out.trim());
    }

    @Test
    void baseName_stripsPath() {
        assertEquals("a.png", ArticleDocumentParser.baseName("attachments/a.png"));
        assertEquals("Pasted image 1.png", ArticleDocumentParser.baseName("Pasted image 1.png"));
    }

    // ── extractUploadImageUrls（暂存图转正用） ──────────────────

    @Test
    void extractUploadUrls_fromMarkdownAndCover() {
        String content = "看图 ![a](/uploads/images/2026/10/1_a.png) 和 ![](http://host/uploads/images/2026/10/2_b.png?x=1)";
        List<String> urls = ArticleDocumentParser.extractUploadImageUrls(
                content, "/uploads/images/2026/10/3_c.png");
        assertEquals(3, urls.size());
        assertTrue(urls.contains("/uploads/images/2026/10/1_a.png"));
        assertTrue(urls.contains("/uploads/images/2026/10/2_b.png"));
        assertTrue(urls.contains("/uploads/images/2026/10/3_c.png"));
    }

    @Test
    void extractUploadUrls_ignoresExternal() {
        List<String> urls = ArticleDocumentParser.extractUploadImageUrls(
                "![x](https://cdn.nlark.com/a.png)", "https://example.com/b.png");
        assertTrue(urls.isEmpty());
    }

    @Test
    void extractUploadUrls_fromRawHtml() {
        String content = "<img src=\"/uploads/images/2026/10/1_a.png\" alt=\"x\">";
        List<String> urls = ArticleDocumentParser.extractUploadImageUrls(content);
        assertEquals(List.of("/uploads/images/2026/10/1_a.png"), urls);
    }
}
