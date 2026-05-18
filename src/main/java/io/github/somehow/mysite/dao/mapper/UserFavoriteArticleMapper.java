package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserFavoriteArticleMapper extends BaseMapper<UserFavoriteArticleDO> {

    @Select("SELECT * FROM t_user_article_favorites WHERE user_id = #{userId} AND article_id = #{articleId}")
    UserFavoriteArticleDO selectByUserAndArticle(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Update("UPDATE t_user_article_favorites SET del_flag = 1, update_time = NOW() WHERE id = #{id}")
    int softDeleteById(@Param("id") Long id);

    @Update("UPDATE t_user_article_favorites SET del_flag = 0, update_time = NOW() WHERE id = #{id}")
    int softRestoreById(@Param("id") Long id);
}
