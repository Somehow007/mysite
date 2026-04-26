package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dto.req.admin.UserRoleUpdateReqDTO;
import io.github.somehow.mysite.dto.req.admin.UserStatusUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.admin.AdminUserRespDTO;
import io.github.somehow.mysite.dto.resp.admin.UserOperationLogRespDTO;

public interface AdminUserService {

    IPage<AdminUserRespDTO> listUsers(long current, long size, String keyword);

    AdminUserRespDTO getUserDetail(Long id);

    void updateUserRole(Long id, UserRoleUpdateReqDTO requestParam, String operatorId);

    void updateUserStatus(Long id, UserStatusUpdateReqDTO requestParam, String operatorId);

    void deleteUser(Long id, String operatorId);

    IPage<UserOperationLogRespDTO> listOperationLogs(long current, long size, Long targetUserId);
}
