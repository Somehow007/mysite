package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {

    /** 按来源查找文档（用于 upsert 去重） */
    @Select("SELECT * FROM t_knowledge_document WHERE kb_id = #{kbId} AND source_type = #{sourceType} AND source_ref = #{sourceRef}")
    KnowledgeDocumentDO findBySourceRef(@Param("kbId") Long kbId,
                                        @Param("sourceType") String sourceType,
                                        @Param("sourceRef") String sourceRef);
}
