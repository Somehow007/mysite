package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

import java.util.List;

/** 节假日全量替换：PUT 语义，提交的列表即新的完整清单。 */
@Data
public class LlmHolidaysUpdateRequest {

    private List<LlmHolidayDTO> items;
}
