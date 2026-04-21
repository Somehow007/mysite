package io.github.somehow.mysite.dto.req.article;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "更新文章请求实体")
public class ArticleUpdateReqDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private Long categoryId;
    private Integer published;
    private List<Long> tagIds;
}
