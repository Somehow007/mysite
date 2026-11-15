package io.github.somehow.mysite.dto.resp.collection;

import lombok.Data;

import java.util.List;

@Data
public class ArticleNavInfoRespDTO {

    private NavArticle prev;
    private NavArticle next;
    private Boolean inCollection;
    private String collectionId;
    private String collectionTitle;
    /**
     * 合集内当前访问者可见的已发布文章（按 sort_order 有序）。
     * 供前端渲染「第 N / M 篇」徽标与进度点；不属于合集或全部不可见时为 null/空。
     */
    private List<NavArticle> collectionArticles;

    @Data
    public static class NavArticle {
        private String id;
        private String title;
    }
}
