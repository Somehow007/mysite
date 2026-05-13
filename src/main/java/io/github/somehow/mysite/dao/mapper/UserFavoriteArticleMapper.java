package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserFavoriteArticleMapper extends BaseMapper<UserFavoriteArticleDO> {

    @Select("SELECT * FROM t_user_article_favorites WHERE user_id = #{userId} AND article_id = #{articleId}")
    UserFavoriteArticleDO selectByUserAndArticle(@Param("userId") Long userId, @Param("articleId") Long articleId);
}
