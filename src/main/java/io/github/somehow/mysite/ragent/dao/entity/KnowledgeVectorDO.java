package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量数据。
 * 与 chunk 一一对应，embedding 列通过 {@link PgvectorVectorStore} 的原生 JDBC 操作。
 */
@Data
@TableName("t_knowledge_vector")
public class KnowledgeVectorDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long chunkId;
    private Long kbId;
    /** embedding 列类型为 pgvector vector(1024)，不在此 entity 中映射，由 VectorStore 原生 SQL 操作 */
    private String model;
    private LocalDateTime createTime;
}
