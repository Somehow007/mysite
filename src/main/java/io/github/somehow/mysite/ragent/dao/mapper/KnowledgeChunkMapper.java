package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkDO> {

    @Select("SELECT * FROM t_knowledge_chunk WHERE doc_id = #{docId} ORDER BY chunk_index")
    List<KnowledgeChunkDO> selectByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM t_knowledge_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);
}
