package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dto.resp.ArticleImportImageDTO;
import io.github.somehow.mysite.dto.resp.ArticleImportRespDTO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import io.github.somehow.mysite.service.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleImportServiceImplTest {

    @Mock
    ImageService imageService;

    @InjectMocks
    ArticleImportServiceImpl service;

    private static MockMultipartFile md(String name, String content) {
        return new MockMultipartFile("file", name, "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void import_txt_fillsTitleFromFileName() {
        ArticleImportRespDTO result = service.importDocument(
                md("我的笔记.txt", "第一段。\n\n第二段。"));
        assertEquals("我的笔记", result.getTitle());
        assertTrue(result.getContent().contains("第一段"));
        assertTrue(result.getImages().isEmpty());
    }

    @Test
    void import_md_extractsH1Title() {
        ArticleImportRespDTO result = service.importDocument(
                md("a.md", "# 文章标题\n\n正文内容"));
        assertEquals("文章标题", result.getTitle());
        assertFalse(result.getContent().contains("# 文章标题"));
        assertTrue(result.getContent().contains("正文内容"));
    }

    @Test
    void import_relativeImage_writesPlaceholder_notFail() {
        String src = "# t\n\n![本地](./images/foo.png)\n\n结束";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src));

        // 图片语法已去掉，但占位中保留原引用便于补传
        assertFalse(result.getContent().contains("![本地](./images/foo.png)"));
        assertTrue(result.getContent().contains("图片待补传"));
        assertTrue(result.getContent().contains("./images/foo.png"));
        assertTrue(result.getContent().contains("结束"));

        assertEquals(1, result.getImages().size());
        ArticleImportImageDTO img = result.getImages().get(0);
        assertEquals("FAILED", img.getStatus());
        assertEquals("./images/foo.png", img.getOriginalRef());
        verifyNoInteractions(imageService);
    }

    @Test
    void import_remoteImage_uploadFails_stillReturnsContent() {
        when(imageService.uploadImageByUrl(anyString()))
                .thenThrow(new ClientException("远程图片拉取失败"));

        String src = "# t\n\n![远程](https://example.com/a.png)\n\n正文";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src));

        assertTrue(result.getContent().contains("正文"));
        assertTrue(result.getContent().contains("图片待补传"));
        assertEquals(1, result.getImages().size());
        assertEquals("FAILED", result.getImages().get(0).getStatus());
    }

    @Test
    void import_remoteImage_success_replacesUrl() {
        ImageUploadRespDTO uploaded = ImageUploadRespDTO.builder()
                .url("/uploads/images/2025/01/1_ab.png")
                .build();
        when(imageService.uploadImageByUrl("https://example.com/a.png")).thenReturn(uploaded);

        String src = "# t\n\n![远程](https://example.com/a.png)";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src));

        assertTrue(result.getContent().contains("/uploads/images/2025/01/1_ab.png"));
        assertFalse(result.getContent().contains("https://example.com/a.png"));
        assertEquals(1, result.getImages().size());
        assertEquals("UPLOADED", result.getImages().get(0).getStatus());
    }

    @Test
    void import_localUploadUrl_keptAsIs() {
        String src = "# t\n\n![已有](/uploads/images/2025/01/1_x.png)";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src));

        assertTrue(result.getContent().contains("/uploads/images/2025/01/1_x.png"));
        assertEquals(1, result.getImages().size());
        assertEquals("KEPT", result.getImages().get(0).getStatus());
        verifyNoInteractions(imageService);
    }

    @Test
    void import_rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));
        assertThrows(ClientException.class, () -> service.importDocument(file));
    }

    @Test
    void import_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", new byte[0]);
        assertThrows(ClientException.class, () -> service.importDocument(file));
    }

    @Test
    void import_obsidianEmbed_withoutAttachment_writesPlaceholder() {
        String src = "# t\n\n![[Pasted image 20261009131244.png]]\n\n正文";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src));

        assertFalse(result.getContent().contains("![[Pasted image 20261009131244.png]]"));
        assertTrue(result.getContent().contains("图片待补传"));
        assertTrue(result.getContent().contains("Pasted image 20261009131244.png"));
        assertEquals(1, result.getImages().size());
        assertEquals("FAILED", result.getImages().get(0).getStatus());
        verifyNoInteractions(imageService);
    }

    @Test
    void import_obsidianEmbed_withCompanion_uploadsAndReplaces() {
        ImageUploadRespDTO uploaded = ImageUploadRespDTO.builder()
                .url("/uploads/images/2025/01/1_ab.png")
                .build();
        when(imageService.uploadImageForImport(any())).thenReturn(uploaded);

        MockMultipartFile companion = new MockMultipartFile(
                "attachments", "Pasted image 20261009131244.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0});

        String src = "# t\n\n![[Pasted image 20261009131244.png]]";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src), List.of(companion));

        assertTrue(result.getContent().contains("/uploads/images/2025/01/1_ab.png"));
        assertFalse(result.getContent().contains("![["));
        assertEquals(1, result.getImages().size());
        assertEquals("UPLOADED", result.getImages().get(0).getStatus());
    }

    @Test
    void import_relativePath_matchesCompanionByBaseName() {
        ImageUploadRespDTO uploaded = ImageUploadRespDTO.builder()
                .url("/uploads/images/2025/01/1_cd.png")
                .build();
        when(imageService.uploadImageForImport(any())).thenReturn(uploaded);

        MockMultipartFile companion = new MockMultipartFile(
                "attachments", "foo.png", "image/png", new byte[]{1, 2, 3});

        String src = "# t\n\n![x](./images/foo.png)";
        ArticleImportRespDTO result = service.importDocument(md("a.md", src), List.of(companion));

        assertTrue(result.getContent().contains("/uploads/images/2025/01/1_cd.png"));
        assertEquals(1, result.getImages().size());
        assertEquals("UPLOADED", result.getImages().get(0).getStatus());
    }
}
