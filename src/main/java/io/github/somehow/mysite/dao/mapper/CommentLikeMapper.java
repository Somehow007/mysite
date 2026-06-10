package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.CommentLikeDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface CommentLikeMapper extends BaseMapper<CommentLikeDO> {

    @Delete("DELETE FROM t_comment_like WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
