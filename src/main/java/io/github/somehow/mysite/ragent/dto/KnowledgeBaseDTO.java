package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseDTO {
    private String id;
    private String name;
    private String description;
    private String collectionName;
    private String embeddingModel;
    private Integer embeddingDimension;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer docCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
