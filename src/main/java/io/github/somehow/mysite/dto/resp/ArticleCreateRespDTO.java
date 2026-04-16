package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ArticleCreateRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String content;

    private String summary;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
}
