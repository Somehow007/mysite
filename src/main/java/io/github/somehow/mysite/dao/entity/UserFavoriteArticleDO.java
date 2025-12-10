package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.Builder;
import lombok.Data;

/**
 * 用户收藏文章数据库表实体
 */
@Data
@Builder
@TableName(value = "t_user_article_favorites")
public class UserFavoriteArticleDO extends BaseDO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户Id
     */
    private Long userId;

    /**
     * 文章Id
     */
    private Long articleId;
}
