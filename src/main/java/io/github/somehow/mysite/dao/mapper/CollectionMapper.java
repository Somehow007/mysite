package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.dao.entity.CollectionDO;
import io.github.somehow.mysite.dto.resp.collection.CollectionPageQueryRespDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CollectionMapper extends BaseMapper<CollectionDO> {

    List<CollectionDO> selectByAuthorId(@Param("authorId") Long authorId);

    @Update("UPDATE t_collection SET article_count = article_count + #{delta} WHERE id = #{id} AND del_flag = 0")
    int updateArticleCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 分页查询合集，含作者名和文章总浏览量，支持按浏览量排序
     */
    IPage<CollectionPageQueryRespDTO> selectCollectionsPage(
            Page<CollectionPageQueryRespDTO> page,
            @Param("keyword") String keyword,
            @Param("authorId") Long authorId,
            @Param("sortBy") String sortBy,
            @Param("sortField") String sortField,
            @Param("sortOrder") String sortOrder);
}
