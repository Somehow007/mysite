package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.entity.UserOperationLogDO;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dao.mapper.UserOperationLogMapper;
import io.github.somehow.mysite.dto.req.admin.UserRoleUpdateReqDTO;
import io.github.somehow.mysite.dto.req.admin.UserStatusUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.admin.AdminUserRespDTO;
import io.github.somehow.mysite.dto.resp.admin.UserOperationLogRespDTO;
import io.github.somehow.mysite.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final UserOperationLogMapper operationLogMapper;

    @Override
    public IPage<AdminUserRespDTO> listUsers(long current, long size, String keyword) {
        LambdaQueryWrapper<UserDO> wrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getDelFlag, 0)
                .orderByDesc(UserDO::getCreateTime);

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w
                    .like(UserDO::getUsername, keyword)
                    .or().like(UserDO::getRealName, keyword)
                    .or().like(UserDO::getEmail, keyword)
                    .or().like(UserDO::getPhoneNumber, keyword));
        }

        IPage<UserDO> page = userMapper.selectPage(new Page<>(current, size), wrapper);

        return page.convert(this::toAdminUserRespDTO);
    }

    @Override
    public AdminUserRespDTO getUserDetail(Long id) {
        UserDO userDO = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, id)
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null) {
            throw new ClientException(ErrorCode.ADMIN_USER_NOT_FOUND);
        }
        return toAdminUserRespDTO(userDO);
    }

    @Override
    @Transactional
    public void updateUserRole(Long id, UserRoleUpdateReqDTO requestParam, String operatorId) {
        UserDO userDO = getActiveUser(id);

        UserRole newRole;
        try {
            newRole = UserRole.valueOf(requestParam.getRole());
        } catch (IllegalArgumentException e) {
            throw new ClientException(ErrorCode.ADMIN_INVALID_ROLE_TYPE);
        }

        String oldRole = userDO.getRole() != null ? userDO.getRole().name() : "USER";
        userDO.setRole(newRole);
        userMapper.updateById(userDO);

        logOperation(operatorId, id, userDO.getUsername(), "ROLE_CHANGE",
                String.format("角色从 %s 变更为 %s", oldRole, newRole.name()));
    }

    @Override
    @Transactional
    public void updateUserStatus(Long id, UserStatusUpdateReqDTO requestParam, String operatorId) {
        UserDO userDO = getActiveUser(id);

        Integer newStatus = requestParam.getStatus();
        if (newStatus != 0 && newStatus != 1) {
            throw new ClientException(ErrorCode.ADMIN_INVALID_STATUS_VALUE);
        }

        Integer oldStatus = userDO.getStatus();
        userDO.setStatus(newStatus);
        userMapper.updateById(userDO);

        String statusText = newStatus == 1 ? "启用" : "禁用";
        logOperation(operatorId, id, userDO.getUsername(), "STATUS_CHANGE",
                String.format("状态从 %s 变更为 %s", oldStatus == 1 ? "启用" : "禁用", statusText));
    }

    @Override
    @Transactional
    public void deleteUser(Long id, String operatorId) {
        UserDO userDO = getActiveUser(id);

        userDO.setDelFlag(1);
        userMapper.updateById(userDO);

        logOperation(operatorId, id, userDO.getUsername(), "DELETE", "逻辑删除用户");
    }

    @Override
    public IPage<UserOperationLogRespDTO> listOperationLogs(long current, long size, Long targetUserId) {
        LambdaQueryWrapper<UserOperationLogDO> wrapper = Wrappers.lambdaQuery(UserOperationLogDO.class)
                .eq(targetUserId != null, UserOperationLogDO::getTargetUserId, targetUserId)
                .orderByDesc(UserOperationLogDO::getCreateTime);

        IPage<UserOperationLogDO> page = operationLogMapper.selectPage(new Page<>(current, size), wrapper);

        return page.convert(this::toOperationLogRespDTO);
    }

    private UserDO getActiveUser(Long id) {
        UserDO userDO = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, id)
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null) {
            throw new ClientException(ErrorCode.ADMIN_USER_NOT_FOUND);
        }
        return userDO;
    }

    private void logOperation(String operatorId, Long targetUserId, String targetUserName,
                              String operationType, String detail) {
        UserOperationLogDO logDO = UserOperationLogDO.builder()
                .operatorId(operatorId != null ? Long.parseLong(operatorId) : 0L)
                .operatorName(operatorId != null ? operatorId : "system")
                .targetUserId(targetUserId)
                .targetUserName(targetUserName)
                .operationType(operationType)
                .detail(detail)
                .createTime(new Date())
                .build();
        operationLogMapper.insert(logDO);
    }

    private AdminUserRespDTO toAdminUserRespDTO(UserDO userDO) {
        return AdminUserRespDTO.builder()
                .id(userDO.getId())
                .username(userDO.getUsername())
                .realName(userDO.getRealName())
                .email(userDO.getEmail())
                .phoneNumber(userDO.getPhoneNumber())
                .sex(userDO.getSex())
                .role(userDO.getRole() != null ? userDO.getRole().name() : "USER")
                .status(userDO.getStatus() != null ? userDO.getStatus() : 1)
                .followingCount(userDO.getFollowingCount())
                .followerCount(userDO.getFollowerCount())
                .createTime(userDO.getCreateTime())
                .updateTime(userDO.getUpdateTime())
                .build();
    }

    private UserOperationLogRespDTO toOperationLogRespDTO(UserOperationLogDO logDO) {
        return UserOperationLogRespDTO.builder()
                .id(logDO.getId())
                .operatorId(logDO.getOperatorId() != null ? logDO.getOperatorId().toString() : null)
                .operatorName(logDO.getOperatorName())
                .targetUserId(logDO.getTargetUserId() != null ? logDO.getTargetUserId().toString() : null)
                .targetUserName(logDO.getTargetUserName())
                .operationType(logDO.getOperationType())
                .detail(logDO.getDetail())
                .createTime(logDO.getCreateTime())
                .build();
    }
}
