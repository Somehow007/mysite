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

    /** 更新计划总篇数；plannedCount 传 null 时置 NULL（updateById 的 NOT_NULL 策略写不了 null） */
    @Update("UPDATE t_collection SET planned_count = #{plannedCount} WHERE id = #{id} AND del_flag = 0")
    int updatePlannedCount(@Param("id") Long id, @Param("plannedCount") Integer plannedCount);

    /**
     * 分页查询合集（按访问者视角），含作者名、可见文章数和文章总浏览量，支持按浏览量排序。
     *
     * @param viewerId 当前访问者ID（游客为 null）
     * @param isAdmin  是否管理员（管理员跳过全部可见性过滤）
     */
    IPage<CollectionPageQueryRespDTO> selectCollectionsPage(
            Page<CollectionPageQueryRespDTO> page,
            @Param("keyword") String keyword,
            @Param("authorId") Long authorId,
            @Param("sortBy") String sortBy,
            @Param("sortField") String sortField,
            @Param("sortOrder") String sortOrder,
            @Param("viewerId") Long viewerId,
            @Param("isAdmin") boolean isAdmin);
}
