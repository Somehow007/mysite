package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.CollectionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CollectionMapper extends BaseMapper<CollectionDO> {

    List<CollectionDO> selectByAuthorId(@Param("authorId") Long authorId);

    @Update("UPDATE t_collection SET article_count = article_count + #{delta} WHERE id = #{id} AND del_flag = 0")
    int updateArticleCount(@Param("id") Long id, @Param("delta") int delta);
}
