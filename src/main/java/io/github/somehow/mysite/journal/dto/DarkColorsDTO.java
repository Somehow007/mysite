package io.github.somehow.mysite.journal.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 深色模式三档色，落库为 sj_custom_mood.dark_colors JSON 列。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DarkColorsDTO {

    public static final String COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    @Pattern(regexp = COLOR_PATTERN, message = "深色 solid 颜色格式应为 #RRGGBB")
    private String solid;

    @Pattern(regexp = COLOR_PATTERN, message = "深色 ink 颜色格式应为 #RRGGBB")
    private String ink;

    @Pattern(regexp = COLOR_PATTERN, message = "深色 tint 颜色格式应为 #RRGGBB")
    private String tint;
}
