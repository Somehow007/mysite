package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_image")
public class ImageDO extends BaseDO {

    private Long id;
    private String originalName;
    private String storedName;
    private String filePath;
    private String url;
    private Long fileSize;
    private String contentType;
    private Integer width;
    private Integer height;
    private Integer sourceType;
    private String sourceUrl;
    private Long uploaderId;
    private Integer articleCount;
}
