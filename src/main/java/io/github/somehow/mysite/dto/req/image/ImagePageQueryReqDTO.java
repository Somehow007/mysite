package io.github.somehow.mysite.dto.req.image;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "图片分页查询请求参数")
public class ImagePageQueryReqDTO extends Page {

    @Schema(description = "搜索关键词，匹配原始文件名")
    private String keyword;

    @Schema(description = "来源类型: 0-本地上传 1-URL拉取")
    private Integer sourceType;
}
