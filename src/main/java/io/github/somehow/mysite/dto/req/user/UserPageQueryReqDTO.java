package io.github.somehow.mysite.dto.req.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询用户请求参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页查询用户请求参数")
public class UserPageQueryReqDTO extends Page {

    /**
     * 搜索关键词，用户名
     */
    @Schema(description = "搜索关键词，用户名", example = "Somehow007")
    private String keyword;

//    /**
//     * 搜索类型
//     */
//    @Schema(description = "搜索类型: username-按用户名搜索, realName-按真实姓名搜索", example = "username")
//    private String searchType;
}