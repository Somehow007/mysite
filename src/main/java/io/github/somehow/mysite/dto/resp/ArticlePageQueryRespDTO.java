package io.github.somehow.mysite.dto.resp;

import lombok.Data;

/**
 * 分页查询文章请求返回体
 */
@Data
public class ArticlePageQueryRespDTO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 摘要/简介（可选，用于列表页展示）
     */
    private String summary;

    /**
     * 阅读量
     */
    private Integer viewCount;

    /**
     * 收藏量
     */
    private Integer favoriteCount;

    /**
     * 作者名称
     */
    private String authorName;
}
