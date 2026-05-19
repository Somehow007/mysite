package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图片上传响应")
public class ImageUploadRespDTO {

    @Schema(description = "图片ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "访问URL")
    private String url;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "MIME类型")
    private String contentType;

    @Schema(description = "图片宽度")
    private Integer width;

    @Schema(description = "图片高度")
    private Integer height;
}
