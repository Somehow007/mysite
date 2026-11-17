package io.github.somehow.mysite.ragent.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LlmHolidayDTO {

    private LocalDate holidayDate;
    private String name;
}
