package io.github.somehow.mysite.journal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自定义心情新增/更新请求体。id 由前端 nanoid 生成（新增时必填；更新时以路径参数为准）。
 */
@Data
public class CustomMoodReqDTO {

    @Size(max = 21, message = "心情 id 过长")
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "心情 id 只能包含字母、数字、下划线和连字符")
    private String id;

    @NotBlank(message = "心情名称不能为空")
    @Size(max = 16, message = "心情名称过长")
    private String label;

    @Size(max = 8, message = "表情过长")
    private String emoji = "";

    @NotBlank(message = "solid 颜色不能为空")
    @Pattern(regexp = DarkColorsDTO.COLOR_PATTERN, message = "solid 颜色格式应为 #RRGGBB")
    private String solid;

    @NotBlank(message = "ink 颜色不能为空")
    @Pattern(regexp = DarkColorsDTO.COLOR_PATTERN, message = "ink 颜色格式应为 #RRGGBB")
    private String ink;

    @NotBlank(message = "tint 颜色不能为空")
    @Pattern(regexp = DarkColorsDTO.COLOR_PATTERN, message = "tint 颜色格式应为 #RRGGBB")
    private String tint;

    @NotNull(message = "深色模式颜色不能为空")
    @Valid
    private DarkColorsDTO dark;
}
