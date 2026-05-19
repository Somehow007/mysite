package io.github.somehow.mysite.dto.req.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图片URL上传请求")
public class ImageUrlUploadReqDTO {

    @Schema(description = "图片URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;
}
