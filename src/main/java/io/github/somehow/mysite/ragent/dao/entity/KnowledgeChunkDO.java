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
    private Integer charCount;
    private LocalDateTime createTime;
}
