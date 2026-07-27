package io.github.somehow.mysite.journal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 学习手帐自定义心情（sj_custom_mood）。
 * <p>主键为前端 nanoid（VARCHAR(21)），深色模式三档色存 JSON 列 dark_colors。</p>
 */
@Data
@TableName("sj_custom_mood")
public class SjCustomMoodDO {

    /** 前端 nanoid，直接作主键 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 所属用户 ID */
    private Long userId;

    /** 心情名称 */
    private String label;

    /** 表情（辅助表达/空状态） */
    private String emoji;

    /** 花瓣实色 #RRGGBB */
    private String solid;

    /** 深调色（tint 底上的文字）#RRGGBB */
    private String ink;

    /** 浅底色（大面积铺垫）#RRGGBB */
    private String tint;

    /** 深色模式三档色 JSON：{"solid":"#…","ink":"#…","tint":"#…"} */
    private String darkColors;

    /** 创建时间，Unix 毫秒 */
    private Long createdAt;
}
