package io.github.somehow.mysite.journal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/journal/streak 响应：连续有记录天数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreakDTO {

    /** 以今天为终点的连续有记录天数；今天无记录则为 0 */
    private Integer days;
}
