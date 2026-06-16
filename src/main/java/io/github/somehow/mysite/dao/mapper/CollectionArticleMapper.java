package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.CollectionArticleDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CollectionArticleMapper extends BaseMapper<CollectionArticleDO> {

    void batchInsert(@Param("list") List<CollectionArticleDO> list);

    @Delete("DELETE FROM t_collection_article WHERE collection_id = #{collectionId}")
    int physicalDeleteByCollectionId(@Param("collectionId") Long collectionId);

    @Delete("DELETE FROM t_collection_article WHERE article_id = #{articleId}")
    int physicalDeleteByArticleId(@Param("articleId") Long articleId);
}
