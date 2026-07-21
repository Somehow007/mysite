package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分块。
 * 一篇文档会被切分成多个 chunk，每个 chunk 独立向量化。
 */
@Data
@TableName("t_knowledge_chunk")
public class KnowledgeChunkDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long docId;
    private Long kbId;
    private Integer chunkIndex;
    private String content;
    /** 向量化专用文本，为 null 时回退到 content（Ragent 模式：代码块去噪、表格转 KV） */
    private String embeddingText;
    private Integer charCount;
    private LocalDateTime createTime;
}
