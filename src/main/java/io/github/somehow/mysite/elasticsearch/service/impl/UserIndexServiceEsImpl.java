package io.github.somehow.mysite.elasticsearch.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.elasticsearch.UserDocument;
import io.github.somehow.mysite.elasticsearch.repository.UserEsRepository;
import io.github.somehow.mysite.elasticsearch.service.UserIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
public class UserIndexServiceEsImpl implements UserIndexService {

    private final UserEsRepository userEsRepository;
    private final UserMapper userMapper;

    @Override
    public void indexUser(UserDO user) {
        UserDocument userDocument = UserDocument.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .realName(user.getRealName())
                .sex(user.getSex())
                .followingCount(user.getFollowingCount())
                .followerCount(user.getFollowerCount())
                .createTime(user.getCreateTime())
                .build();
        userEsRepository.save(userDocument);
    }

    @Override
    public IPage<UserSearchRespDTO> searchUsers(UserPageQueryReqDTO requestParam) {
        org.springframework.data.domain.PageRequest pageRequest = PageRequest.of(
                (int) (requestParam.getCurrent() - 1),
                (int) requestParam.getSize());

        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "").toLowerCase();

        org.springframework.data.domain.Page<UserDocument> esPage = userEsRepository.findByUsernameContaining(keyword, pageRequest);

        if (esPage.hasContent()) {
            List<Long> userIds = esPage.getContent().stream()
                    .map(doc -> Long.valueOf(doc.getId()))
                    .collect(Collectors.toList());

            List<UserDO> users = userMapper.selectBatchIds(userIds);

            IPage<UserSearchRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize(), esPage.getTotalElements());
            List<UserSearchRespDTO> data = users.stream()
                    .map(each -> BeanUtil.toBean(each, UserSearchRespDTO.class))
                    .toList();
            result.setRecords(data);
            return result;
        } else {
            return new Page<>();
        }
    }
}
