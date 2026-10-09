package io.github.somehow.mysite.service;

import io.github.somehow.mysite.dto.resp.ArticleImportRespDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 写文章页文档导入：解析 txt / md，能转存的图片自动上传，失败写入补传占位。
 */
public interface ArticleImportService {

    /**
     * 导入文档（无附件图片）。
     */
    default ArticleImportRespDTO importDocument(MultipartFile file) {
        return importDocument(file, null);
    }

    /**
     * 导入文档并返回可直接写入编辑器的 Markdown。
     *
     * @param file        txt / md 文档
     * @param attachments 可选图片附件（与文中 ![[file]] / 相对路径按文件名匹配）
     *                    图片处理失败不会导致整体失败，只会写入占位并在 images 中说明原因。
     */
    ArticleImportRespDTO importDocument(MultipartFile file, List<MultipartFile> attachments);
}
