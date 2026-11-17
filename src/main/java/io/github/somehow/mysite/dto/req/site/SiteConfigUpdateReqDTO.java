package io.github.somehow.mysite.dto.req.site;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 站点配置更新请求（PUT /v1/site/config，ADMIN）。
 * 字符串字段：空串/空白 = 清除覆盖值（回退 env/yaml 默认）；null 同样按清除处理。
 * 不含 navigation / askExamples / askDemo——结构性配置仍走 yaml/env，不开放后台编辑。
 */
@Data
@Schema(description = "站点配置更新请求")
public class SiteConfigUpdateReqDTO {

    @Schema(description = "站点标题", example = "Somehow's Blog")
    private String title;

    @Schema(description = "站点副标题")
    private String subtitle;

    @Schema(description = "站点简介（meta description / og:description）", example = "记录学习与思考的个人博客")
    private String description;

    @Schema(description = "作者署名", example = "somehow")
    private String author;

    @Schema(description = "作者简介")
    private String authorBio;

    @Schema(description = "作者一句话签名")
    private String authorTagline;

    @Schema(description = "关于页长文（markdown，支持多行）")
    private String aboutContent;

    @Schema(description = "技术栈一句话", example = "Java · Spring Boot · Vue 3")
    private String techStack;

    @Schema(description = "站点/作者头像 URL")
    private String avatar;

    @Schema(description = "站点绝对地址（canonical / og:url / JSON-LD）", example = "https://somehow007.top")
    private String url;

    @Schema(description = "GitHub 主页")
    private String github;

    @Schema(description = "RSS 地址")
    private String rss;

    @Schema(description = "评论开关")
    private Boolean commentEnabled;
}
