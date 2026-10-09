package io.github.somehow.mysite.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档导入图片处理结果")
public class ArticleImportImageDTO {

    /** 图片在原文中的引用（URL / data URI / 相对路径） */
    @Schema(description = "原文中的图片引用")
    private String originalRef;

    /**
     * UPLOADED — 已转存到站点图床并替换链接
     * KEPT     — 已是本站图片，保留原链接
     * FAILED   — 未能自动上传，正文已写入补传占位
     */
    @Schema(description = "处理状态: UPLOADED / KEPT / FAILED")
    private String status;

    @Schema(description = "转存后的访问 URL（仅 UPLOADED）")
    private String url;

    @Schema(description = "失败原因（仅 FAILED）")
    private String reason;
}
