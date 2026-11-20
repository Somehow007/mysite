package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 条件翻转 READY：入库完成时若文档已被软隐藏（HIDDEN，文章入库期间转私有/草稿）
     * 则不覆盖状态，返回影响行数（0 = 保持 HIDDEN）。
     */
    @Update("UPDATE t_knowledge_document SET status = 'READY' WHERE id = #{id} AND status <> 'HIDDEN'")
    int markReadyUnlessHidden(@Param("id") Long id);

    /**
     * 文章转非公开 → 软隐藏跨 KB 文档：READY/PENDING/CHUNKING → HIDDEN。
     * 只改状态不删向量/分块，检索侧 status='READY' 过滤自动排除；
     * FAILED 不动（本就不可检索，且避免半截向量被误翻回）。
     */
    @Update("UPDATE t_knowledge_document SET status = 'HIDDEN' " +
            "WHERE source_type = 'ARTICLE' AND source_ref = #{sourceRef} " +
            "AND status IN ('READY','PENDING','CHUNKING')")
    int hideArticleDocs(@Param("sourceRef") String sourceRef);
}
