package io.github.somehow.mysite.ragent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.ragent.dao.entity.LlmUsageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LlmUsageMapper extends BaseMapper<LlmUsageDO> {

    @Select("""
            SELECT COALESCE(SUM(cost), 0) AS total_cost,
                   COALESCE(SUM(total_tokens), 0) AS total_tokens,
                   COUNT(*) AS total_calls,
                   COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END), 0) AS success_calls
            FROM t_llm_usage
            WHERE create_time >= #{from} AND create_time < #{to}
            """)
    Map<String, Object> selectTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT CAST(create_time AS DATE) AS day,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COUNT(*) AS calls
            FROM t_llm_usage
            WHERE create_time >= #{from} AND create_time < #{to}
            GROUP BY CAST(create_time AS DATE)
            ORDER BY day
            """)
    List<Map<String, Object>> selectDaily(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT model, provider,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COUNT(*) AS calls
            FROM t_llm_usage
            WHERE create_time >= #{from} AND create_time < #{to}
            GROUP BY model, provider
            ORDER BY cost DESC
            """)
    List<Map<String, Object>> selectByModel(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT call_type,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COUNT(*) AS calls
            FROM t_llm_usage
            WHERE create_time >= #{from} AND create_time < #{to}
            GROUP BY call_type
            ORDER BY cost DESC
            """)
    List<Map<String, Object>> selectByCallType(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT COALESCE(NULLIF(username, ''), CONCAT('访客 ', LEFT(COALESCE(visitor_id, ''), 8))) AS actor,
                   user_id,
                   visitor_id,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COUNT(*) AS calls
            FROM t_llm_usage
            WHERE create_time >= #{from} AND create_time < #{to}
            GROUP BY COALESCE(NULLIF(username, ''), CONCAT('访客 ', LEFT(COALESCE(visitor_id, ''), 8))), user_id, visitor_id
            ORDER BY cost DESC
            LIMIT 10
            """)
    List<Map<String, Object>> selectTopUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
