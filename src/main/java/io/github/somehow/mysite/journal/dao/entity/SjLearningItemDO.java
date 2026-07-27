package io.github.somehow.mysite.journal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 学习手帐学习条目（sj_learning_item）。
 * <p>外键关联 sj_day_record.id，ON DELETE CASCADE 随整日记录删除。</p>
 */
@Data
@TableName("sj_learning_item")
public class SjLearningItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 sj_day_record.id */
    private Long recordId;

    /** 前端 nanoid，用于编辑时对齐条目 */
    private String clientId;

    /** 学科/事项名称 */
    private String subject;

    /** 时长（分钟） */
    private Integer durationMin;

    /** 备注 */
    private String note;

    /** 条目颜色 #RRGGBB */
    private String color;

    /** 当日清单内排序 */
    private Integer sortOrder;
}
