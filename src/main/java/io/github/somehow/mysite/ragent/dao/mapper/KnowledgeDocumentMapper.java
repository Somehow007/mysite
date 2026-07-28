package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {

    /** 按来源查找文档（用于 upsert 去重） */
    @Select("SELECT * FROM t_knowledge_document WHERE kb_id = #{kbId} AND source_type = #{sourceType} AND source_ref = #{sourceRef}")
    KnowledgeDocumentDO findBySourceRef(@Param("kbId") Long kbId,
                                        @Param("sourceType") String sourceType,
                                        @Param("sourceRef") String sourceRef);

    /** 按来源查找所有知识库中的文档（用于文章删除后跨 KB 清理） */
    @Select("SELECT * FROM t_knowledge_document WHERE source_type = #{sourceType} AND source_ref = #{sourceRef}")
    List<KnowledgeDocumentDO> findBySource(@Param("sourceType") String sourceType,
                                           @Param("sourceRef") String sourceRef);
}
