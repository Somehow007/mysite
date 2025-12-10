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
import io.github.somehow.mysite.dto.req.user.UserFollowReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserPageQueryFollowRespDTO;
import io.github.somehow.mysite.dto.req.user.UserRegistryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.user.UserSelectRespDTO;
import io.github.somehow.mysite.service.UserService;
import lombok.RequiredArgsConstructor;
import com.alibaba.fastjson2.JSON;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 用户业务逻辑实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserFollowMapper userFollowMapper;

    @Override
    public void registry(UserRegistryReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getUsername()) || StrUtil.isBlank(requestParam.getPassword())) {
            throw new ClientException("注册失败，请检查您是否成功填写用户名或密码");
        }

        if (StrUtil.isBlank(requestParam.getPhoneNumber())) {
            throw new ClientException("请填写您的手机号");
        }

        if (StrUtil.isBlank(requestParam.getRealName())) {
            throw new ClientException("请填写您的真实姓名");
        }

        UserDO userDO = BeanUtil.toBean(requestParam, UserDO.class);
        userDO.setId(IdUtil.getSnowflakeNextId());
        userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
        try {
            baseMapper.insert(userDO);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("注册失败，该用户名已创建");
        }
    }

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
    public void updateUser(UserUpdateReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getUserId())) {
            throw new ClientException("更新失败，请传入正确的用户信息");
        }
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getId, Long.parseLong(requestParam.getUserId()))
                .eq(UserDO::getDelFlag, 0)
                .set(StrUtil.isNotBlank(requestParam.getUsername()), UserDO::getUsername, requestParam.getUsername())
                .set(StrUtil.isNotBlank(requestParam.getRealName()), UserDO::getRealName, requestParam.getRealName())
                .set(StrUtil.isNotBlank(requestParam.getEmail()), UserDO::getEmail, requestParam.getRealName())
                .set(StrUtil.isNotBlank(requestParam.getPhoneNumber()), UserDO::getPhoneNumber, requestParam.getPhoneNumber())
                .set(StrUtil.isNotBlank(String.valueOf(requestParam.getSex())), UserDO::getSex, requestParam.getSex());

        int infectRow = baseMapper.update(updateWrapper);
        if (!SqlHelper.retBool(infectRow)) {
            throw new ClientException("更新失败，用户不存在");
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

        // 如果不存在说明第一次关注，添加数据
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

        // 如果已删除，那么就是要重新关注
        if (isExist.getDelFlag().equals(1)) {
            userFollowMapper.update(Wrappers.lambdaUpdate(UserFollowDO.class)
                    .eq(UserFollowDO::getFollowerId, Long.parseLong(requestParam.getFollowerId()))
                    .eq(UserFollowDO::getFolloweeId, Long.parseLong(requestParam.getFolloweeId()))
                    .set(UserFollowDO::getDelFlag, 0));
            userFollowMapper.incrementFollowCount(Long.parseLong(requestParam.getFollowerId()), Long.parseLong(requestParam.getFolloweeId()), 1);
            return;
        }

        // 否则就是要删除
        userFollowMapper.update(Wrappers.lambdaUpdate(UserFollowDO.class)
                .eq(UserFollowDO::getFollowerId, Long.parseLong(requestParam.getFollowerId()))
                .eq(UserFollowDO::getFolloweeId, Long.parseLong(requestParam.getFolloweeId()))
                .set(UserFollowDO::getDelFlag, 1));
        userFollowMapper.decrementFollowCount(Long.parseLong(requestParam.getFollowerId()), Long.parseLong(requestParam.getFolloweeId()), 1);

    }

    @Override
    public IPage<UserPageQueryFollowRespDTO> selectFollowers(String id) {
        IPage<UserPageQueryFollowRespDTO> page = new Page<>(1, 10);
        return baseMapper.pageFollowersResult(page, id);
    }

    @Override
    public IPage<UserPageQueryFollowRespDTO> selectFollowings(String id) {
        IPage<UserPageQueryFollowRespDTO> page = new Page<>(1, 10);
        return baseMapper.pageFollowingsResult(page, id);
    }
}
