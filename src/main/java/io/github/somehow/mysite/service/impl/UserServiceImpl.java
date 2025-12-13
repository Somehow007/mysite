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
import io.github.somehow.mysite.dto.req.user.UserPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserRegistryReqDTO;
import io.github.somehow.mysite.dto.req.user.UserUpdateReqDTO;
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
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户业务逻辑实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserFollowMapper userFollowMapper;
    private final UserEsRepository userEsRepository;

    @Override
    @Transactional
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
            
            // 同步到 Elasticsearch
            UserDocument userDocument = UserDocument.builder()
                    .id(userDO.getId().toString())
                    .username(userDO.getUsername())
                    .realName(userDO.getRealName())
                    .sex(userDO.getSex())
                    .followingCount(userDO.getFollowingCount())
                    .followerCount(userDO.getFollowerCount())
                    .createTime(userDO.getCreateTime())
                    .build();
            userEsRepository.save(userDocument);
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
                .set(StrUtil.isNotBlank(requestParam.getEmail()), UserDO::getEmail, requestParam.getRealName())
                .set(StrUtil.isNotBlank(requestParam.getPhoneNumber()), UserDO::getPhoneNumber, requestParam.getPhoneNumber())
                .set(StrUtil.isNotBlank(String.valueOf(requestParam.getSex())), UserDO::getSex, requestParam.getSex());

        int infectRow = baseMapper.update(updateWrapper);
        if (!SqlHelper.retBool(infectRow)) {
            throw new ClientException("更新失败，用户不存在");
        }
        
        // 同步更新到 Elasticsearch
        UserDO updatedUser = baseMapper.selectById(Long.parseLong(requestParam.getUserId()));
        if (updatedUser != null) {
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
    
    @Override
    public IPage<UserSearchRespDTO> pageQueryUser(UserPageQueryReqDTO requestParam) {
        // 构建ES分页参数
        PageRequest pageRequest = PageRequest.of(
                (int) (requestParam.getCurrent() - 1), 
                (int) requestParam.getSize());
        
        // 获取搜索关键词
        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "");
        
        // 获取搜索类型，默认按用户名搜索
//        String searchType = StrUtil.blankToDefault(requestParam.getSearchType(), "username");
        
        org.springframework.data.domain.Page<UserDocument> esPage = userEsRepository.findByUsernameContaining(keyword, pageRequest);
//        switch (searchType) {
//            case "username":
//                // 按用户名搜索
//                esPage = userEsRepository.findByUsernameContaining(keyword, pageRequest);
//                break;
//            case "realName":
//                // 按真实姓名搜索
//                esPage = userEsRepository.findByRealNameContaining(keyword, pageRequest);
//                break;
//            default:
//                // 默认按用户名搜索
//                esPage = userEsRepository.findByUsernameContaining(keyword, pageRequest);
//                break;
//        }
        
        // 将ES查询结果转换为需要的DTO格式
        if (esPage.hasContent()) {
            // 从数据库获取完整用户信息
            List<Long> userIds = esPage.getContent().stream()
                    .map(doc -> Long.valueOf(doc.getId()))
                    .collect(Collectors.toList());
                    
            List<UserDO> users = baseMapper.selectBatchIds(userIds);
            
            // 构造MyBatis Plus分页对象
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