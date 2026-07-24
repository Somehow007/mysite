package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 意图 Mapper —— t_rag_intent。
 * <p>
 * 博客场景意图数量极少（3-5 个），全量加载 + 内存过滤。
 */
@Mapper
public interface IntentMapper extends BaseMapper<IntentDO> {

    /** 加载所有已启用的意图，按优先级降序 */
    @Select("SELECT * FROM t_rag_intent WHERE enabled = true ORDER BY priority DESC")
    List<IntentDO> listEnabled();

    /** 按 ID 查询（含已禁用的） */
    @Select("SELECT * FROM t_rag_intent WHERE id = #{id}")
    IntentDO findById(Long id);
}
