package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 中国法定节假日（DeepSeek 峰谷价排除依据）。 */
@Data
@TableName("t_llm_holiday")
public class LlmHolidayDO {

    @TableId(type = IdType.INPUT)
    private LocalDate holidayDate;
    private String name;
}
