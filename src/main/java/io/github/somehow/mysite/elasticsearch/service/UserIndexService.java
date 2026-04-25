package io.github.somehow.mysite.elasticsearch.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;

public interface UserIndexService {

    void indexUser(UserDO user);

    IPage<UserSearchRespDTO> searchUsers(UserPageQueryReqDTO requestParam);

}
