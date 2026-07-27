package io.github.somehow.mysite.journal.dto;

import lombok.Data;

/**
 * 自定义心情 DTO，与前端 CustomMoodConfig 类型逐字段对齐：
 * <pre>
 * interface CustomMoodConfig {
 *   id: string; label: string; emoji: string;
 *   solid: string; ink: string; tint: string;
 *   dark: MoodDarkColors; createdAt: number;
 * }
 * </pre>
 */
@Data
public class CustomMoodDTO {

    /** 前端 nanoid */
    private String id;

    private String label;

    private String emoji;

    private String solid;

    private String ink;

    private String tint;

    /** 深色模式三档色 */
    private DarkColorsDTO dark;

    /** Unix 毫秒 */
    private Long createdAt;
}
