package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.CollectionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CollectionMapper extends BaseMapper<CollectionDO> {

    List<CollectionDO> selectByAuthorId(@Param("authorId") Long authorId);
}
