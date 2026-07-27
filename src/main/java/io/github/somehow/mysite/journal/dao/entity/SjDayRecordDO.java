package io.github.somehow.mysite.journal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 学习手帐单日记录（sj_day_record）。
 * <p>
 * 不继承 BaseDO：手帐子系统是硬删除语义（删除整日即真删除，条目经外键级联清理），
 * 无 del_flag；时间字段为 BIGINT Unix 毫秒（与前端 createdAt/updatedAt: number 一致，
 * 避免时区换算）；date 为 CHAR(10) 字符串 'YYYY-MM-DD'，全程禁止 UTC 转换。
 * </p>
 */
@Data
@TableName("sj_day_record")
public class SjDayRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID（服务端从登录态解析，前端不传） */
    private Long userId;

    /** YYYY-MM-DD，用户本地日历日 */
    private String date;

    /** 预设 6 种心情枚举或自定义心情 nanoid，null=未记录 */
    private String mood;

    /** Markdown 原文 */
    private String diary;

    /** 创建时间，Unix 毫秒 */
    private Long createdAt;

    /** 更新时间，Unix 毫秒 */
    private Long updatedAt;
}
