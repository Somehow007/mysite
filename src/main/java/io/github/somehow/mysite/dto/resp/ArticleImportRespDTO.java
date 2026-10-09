package io.github.somehow.mysite.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档导入结果（可直接写入编辑器）")
public class ArticleImportRespDTO {

    @Schema(description = "建议标题（取自首个一级标题或文件名，可为空）")
    private String title;

    @Schema(description = "处理后的 Markdown 内容（图片链接已转存或写入补传占位）")
    private String content;

    @Schema(description = "源文件名")
    private String fileName;

    @Schema(description = "图片处理明细")
    private List<ArticleImportImageDTO> images;
}
