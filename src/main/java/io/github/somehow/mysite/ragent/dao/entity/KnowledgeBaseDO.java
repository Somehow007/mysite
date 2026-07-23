package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库定义。
 * 每个知识库对应一个 collection，有自己的分块策略和嵌入模型配置。
 */
@Data
@TableName("t_knowledge_base")
public class KnowledgeBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String description;
    private String collectionName;
    private String embeddingModel;
    private Integer embeddingDimension;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
