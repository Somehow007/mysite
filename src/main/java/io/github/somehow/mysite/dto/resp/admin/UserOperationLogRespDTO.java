package io.github.somehow.mysite.dto.resp.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class UserOperationLogRespDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String operatorId;

    private String operatorName;

    private String targetUserId;

    private String targetUserName;

    private String operationType;

    private String detail;

    private Date createTime;
}
