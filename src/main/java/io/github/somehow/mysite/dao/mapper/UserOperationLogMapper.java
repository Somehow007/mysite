package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.somehow.mysite.dao.entity.UserOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserOperationLogMapper extends BaseMapper<UserOperationLogDO> {
}
