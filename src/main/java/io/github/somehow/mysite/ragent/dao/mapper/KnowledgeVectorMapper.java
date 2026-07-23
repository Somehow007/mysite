package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeVectorDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 向量数据 Mapper。
 * 注意：embedding 列的写入/检索不走 MyBatis-Plus，
 * 由 {@link io.github.somehow.mysite.ragent.vector.PgvectorVectorStore} 原生 JDBC 操作。
 * 此 Mapper 仅用于按 chunk_id / kb_id 查询元数据。
 */
@Mapper
public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVectorDO> {
}
