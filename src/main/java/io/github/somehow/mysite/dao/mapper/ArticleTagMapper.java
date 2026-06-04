package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.ArticleTagDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ArticleTagMapper extends BaseMapper<ArticleTagDO> {

    @Delete("DELETE FROM t_article_tag WHERE article_id = #{articleId}")
    int physicalDeleteByArticleId(@Param("articleId") Long articleId);
}
