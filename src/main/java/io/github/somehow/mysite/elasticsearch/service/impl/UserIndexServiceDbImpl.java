package io.github.somehow.mysite.elasticsearch.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.elasticsearch.service.UserIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class UserIndexServiceDbImpl implements UserIndexService {

    private final UserMapper userMapper;

    @Override
    public void indexUser(UserDO user) {
    }

    @Override
    public IPage<UserSearchRespDTO> searchUsers(UserPageQueryReqDTO requestParam) {
        IPage<UserSearchRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "");
        List<UserDO> users = userMapper.selectList(Wrappers.lambdaQuery(UserDO.class)
                .like(StrUtil.isNotBlank(keyword), UserDO::getUsername, keyword)
                .eq(UserDO::getDelFlag, 0));
        List<UserSearchRespDTO> data = users.stream()
                .map(each -> BeanUtil.toBean(each, UserSearchRespDTO.class))
                .toList();
        result.setRecords(data);
        return result;
    }
}
