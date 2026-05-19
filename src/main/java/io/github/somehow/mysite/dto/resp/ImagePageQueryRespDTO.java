package io.github.somehow.mysite.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图片分页查询响应")
public class ImagePageQueryRespDTO {

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

    @Schema(description = "来源类型 0:本地上传 1:URL拉取")
    private Integer sourceType;

    @Schema(description = "原始URL")
    private String sourceUrl;

    @Schema(description = "上传者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    @Schema(description = "创建时间")
    private Date createTime;
}
