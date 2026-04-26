package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName(value = "t_user_operation_log")
public class UserOperationLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long operatorId;

    private String operatorName;

    private Long targetUserId;

    private String targetUserName;

    private String operationType;

    private String detail;

    private Date createTime;
}
