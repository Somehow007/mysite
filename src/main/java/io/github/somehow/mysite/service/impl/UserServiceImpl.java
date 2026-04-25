package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.entity.UserFollowDO;
import io.github.somehow.mysite.dao.mapper.UserFollowMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.auth.ChangePasswordReqDTO;
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSearchRespDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;
import io.github.somehow.mysite.elasticsearch.UserDocument;
import io.github.somehow.mysite.dao.mapper.UserEsRepository;
import io.github.somehow.mysite.service.UserService;
import lombok.RequiredArgsConstructor;
import com.alibaba.fastjson2.JSON;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final UserFollowMapper userFollowMapper;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UserEsRepository userEsRepository;

    @Override
    public UserSelectRespDTO selectUserById(String id) {
        UserDO userDO = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, Long.parseLong(id))
                .eq(UserDO::getDelFlag, 0));
        if (Objects.isNull(userDO)) {
            throw new ClientException("查询失败，请传入正确的用户");
        }
        UserSelectRespDTO result = BeanUtil.toBean(userDO, UserSelectRespDTO.class);
        result.setFavorites(null);
        result.setHistories(null);
        return result;
    }

    @Override
    @Transactional
    public void updateUser(UserUpdateReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getUserId())) {
            throw new ClientException("更新失败，请传入正确的用户信息");
        }
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getId, Long.parseLong(requestParam.getUserId()))
                .eq(UserDO::getDelFlag, 0)
                .set(StrUtil.isNotBlank(requestParam.getUsername()), UserDO::getUsername, requestParam.getUsername())
                .set(StrUtil.isNotBlank(requestParam.getRealName()), UserDO::getRealName, requestParam.getRealName())
                .set(StrUtil.isNotBlank(requestParam.getEmail()), UserDO::getEmail, requestParam.getEmail())
                .set(StrUtil.isNotBlank(requestParam.getPhoneNumber()), UserDO::getPhoneNumber, requestParam.getPhoneNumber())
                .set(StrUtil.isNotBlank(String.valueOf(requestParam.getSex())), UserDO::getSex, requestParam.getSex());

        int infectRow = baseMapper.update(updateWrapper);
        if (!SqlHelper.retBool(infectRow)) {
            throw new ClientException("更新失败，用户不存在");
        }

        UserDO updatedUser = baseMapper.selectById(Long.parseLong(requestParam.getUserId()));
        if (updatedUser != null && userEsRepository != null) {
            UserDocument userDocument = UserDocument.builder()
                    .id(updatedUser.getId().toString())
                    .username(updatedUser.getUsername())
                    .realName(updatedUser.getRealName())
                    .sex(updatedUser.getSex())
                    .followingCount(updatedUser.getFollowingCount())
                    .followerCount(updatedUser.getFollowerCount())
                    .createTime(updatedUser.getCreateTime())
                    .build();
            userEsRepository.save(userDocument);
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordReqDTO requestParam) {
        UserDO userDO = baseMapper.selectById(userId);
        if (Objects.isNull(userDO) || userDO.getDelFlag() == 1) {
            throw new ClientException("用户不存在");
        }
        
        if (!passwordEncoder.matches(requestParam.getOldPassword(), userDO.getPassword())) {
            throw new ClientException("旧密码错误");
        }
        
        if (passwordEncoder.matches(requestParam.getNewPassword(), userDO.getPassword())) {
            throw new ClientException("新密码不能与旧密码相同");
        }
        
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getId, userId)
                .eq(UserDO::getDelFlag, 0)
                .set(UserDO::getPassword, passwordEncoder.encode(requestParam.getNewPassword()));
        
        int affectRow = baseMapper.update(updateWrapper);
        if (!SqlHelper.retBool(affectRow)) {
            throw new ClientException("修改密码失败");
        }
    }

    @Override
    public void followUser(UserFollowReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getFollowerId()) || StrUtil.isBlank(requestParam.getFolloweeId())) {
            throw new ClientException("关注失败，请正确传入参数: " + JSON.toJSONString(requestParam));
        }
        if (Objects.equals(requestParam.getFollowerId(), requestParam.getFolloweeId())) {
            throw new ClientException("不能自己关注自己！");
        }
        UserFollowDO isExist = userFollowMapper.selectOne(Wrappers.lambdaQuery(UserFollowDO.class)
                .eq(UserFollowDO::getFollowerId, requestParam.getFollowerId())
                .eq(UserFollowDO::getFolloweeId, requestParam.getFolloweeId()));

        if (Objects.isNull(isExist)) {
            try {
                UserFollowDO build = BeanUtil.toBean(requestParam, UserFollowDO.class);
                build.setId(IdUtil.getSnowflakeNextId());
                userFollowMapper.insert(build);
                userFollowMapper.incrementFollowCount(Long.parseLong(requestParam.getFollowerId()), Long.parseLong(requestParam.getFolloweeId()), 1);
                return;
            } catch (DuplicateKeyException ex) {
                throw new ClientException("重复关注！");
            }
        }

        if (isExist.getDelFlag().equals(1)) {
            userFollowMapper.update(Wrappers.lambdaUpdate(UserFollowDO.class)
                    .eq(UserFollowDO::getFollowerId, Long.parseLong(requestParam.getFollowerId()))
                    .eq(UserFollowDO::getFolloweeId, Long.parseLong(requestParam.getFolloweeId()))
                    .set(UserFollowDO::getDelFlag, 0));
            userFollowMapper.incrementFollowCount(Long.parseLong(requestParam.getFollowerId()), Long.parseLong(requestParam.getFolloweeId()), 1);
            return;
        }

        userFollowMapper.update(Wrappers.lambdaUpdate(UserFollowDO.class)
                .eq(UserFollowDO::getFollowerId, Long.parseLong(requestParam.getFollowerId()))
                .eq(UserFollowDO::getFolloweeId, Long.parseLong(requestParam.getFolloweeId()))
                .set(UserFollowDO::getDelFlag, 1));
        userFollowMapper.decrementFollowCount(Long.parseLong(requestParam.getFollowerId()), Long.parseLong(requestParam.getFolloweeId()), 1);
    }

    @Override
    public IPage<UserPageQueryFollowRespDTO> selectFollowers(String id, long current, long size) {
        IPage<UserPageQueryFollowRespDTO> page = new Page<>(current, size);
        return baseMapper.pageFollowersResult(page, id);
    }

    @Override
    public IPage<UserPageQueryFollowRespDTO> selectFollowings(String id, long current, long size) {
        IPage<UserPageQueryFollowRespDTO> page = new Page<>(current, size);
        return baseMapper.pageFollowingsResult(page, id);
    }

    @Override
    public IPage<UserSearchRespDTO> pageQueryUser(UserPageQueryReqDTO requestParam) {
        if (userEsRepository == null) {
            IPage<UserSearchRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize());
            String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "");
            List<UserDO> users = baseMapper.selectList(Wrappers.lambdaQuery(UserDO.class)
                    .like(StrUtil.isNotBlank(keyword), UserDO::getUsername, keyword)
                    .eq(UserDO::getDelFlag, 0));
            List<UserSearchRespDTO> data = users.stream()
                    .map(each -> BeanUtil.toBean(each, UserSearchRespDTO.class))
                    .toList();
            result.setRecords(data);
            return result;
        }

        PageRequest pageRequest = PageRequest.of(
                (int) (requestParam.getCurrent() - 1),
                (int) requestParam.getSize());

        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "").toLowerCase();

        org.springframework.data.domain.Page<UserDocument> esPage = userEsRepository.findByUsernameContaining(keyword, pageRequest);

        if (esPage.hasContent()) {
            List<Long> userIds = esPage.getContent().stream()
                    .map(doc -> Long.valueOf(doc.getId()))
                    .collect(Collectors.toList());

            List<UserDO> users = baseMapper.selectBatchIds(userIds);

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
