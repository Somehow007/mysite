package io.github.somehow.mysite.journal.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.journal.dao.entity.SjDayRecordDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SjDayRecordMapper extends BaseMapper<SjDayRecordDO> {

    /**
     * 日记 / 学习 subject / note 任一 LIKE 命中，按 date 去重，updated_at 倒序。
     */
    List<SjDayRecordDO> searchByKeyword(@Param("userId") Long userId,
                                        @Param("keyword") String keyword,
                                        @Param("limit") int limit);

    /**
     * 今天及之前、算作「有记录」的日期（mood 或非空 diary 或至少一条 learning）。
     * date 为 CHAR(10) YYYY-MM-DD，字符串比较，不做时区换算。
     */
    List<String> listRecordedDatesOnOrBefore(@Param("userId") Long userId,
                                             @Param("today") String today);
}
