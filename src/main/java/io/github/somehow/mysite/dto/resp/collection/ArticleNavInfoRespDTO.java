package io.github.somehow.mysite.dto.resp.collection;

import lombok.Data;

@Data
public class ArticleNavInfoRespDTO {

    private NavArticle prev;
    private NavArticle next;
    private Boolean inCollection;
    private String collectionId;
    private String collectionTitle;

    @Data
    public static class NavArticle {
        private String id;
        private String title;
    }
}
