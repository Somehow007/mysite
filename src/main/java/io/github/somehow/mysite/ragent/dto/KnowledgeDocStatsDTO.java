package io.github.somehow.mysite.ragent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocStatsDTO {
    /** 知识库内文档总数（不受列表筛选影响） */
    private int total;
    private int ready;
    private int processing;
    private int failed;
}
