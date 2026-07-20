package io.github.somehow.mysite.ragent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索来源片段 DTO —— 前端展示"引用来源"时使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceChunkDTO {
    /** 来源文章标题 */
    private String title;
    /** 检索到的文本片段 */
    private String content;
    /** 相似度分数（向量检索的 cosine 相似度或 Rerank 后的相关性分数） */
    private float score;
}
