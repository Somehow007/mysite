package io.github.somehow.mysite.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveRespDTO {

    private String year;
    private List<ArchiveMonth> months;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArchiveMonth {
        private String month;
        private List<ArchiveArticle> articles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArchiveArticle {
        private Long id;
        private String title;
        private String summary;
        private String coverImage;
        private String authorName;
        private java.util.Date createTime;
    }
}
