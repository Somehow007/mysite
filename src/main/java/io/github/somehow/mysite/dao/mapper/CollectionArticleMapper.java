package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.CollectionArticleDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CollectionArticleMapper extends BaseMapper<CollectionArticleDO> {

    void batchInsert(@Param("list") List<CollectionArticleDO> list);

    @Delete("DELETE FROM t_collection_article WHERE collection_id = #{collectionId}")
    int physicalDeleteByCollectionId(@Param("collectionId") Long collectionId);

    @Delete("DELETE FROM t_collection_article WHERE article_id = #{articleId}")
    int physicalDeleteByArticleId(@Param("articleId") Long articleId);

    @Select("SELECT COALESCE(MAX(sort_order), -1) FROM t_collection_article WHERE collection_id = #{collectionId} AND del_flag = 0")
    Integer selectMaxSortOrder(@Param("collectionId") Long collectionId);

    @Update("UPDATE t_collection_article SET sort_order = #{sortOrder} WHERE id = #{id}")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);
}
