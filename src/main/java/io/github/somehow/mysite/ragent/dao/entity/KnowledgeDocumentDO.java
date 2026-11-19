package io.github.somehow.mysite.ragent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档记录。
 * 每篇博客文章对应一条文档记录，状态流转：PENDING → CHUNKING → READY / FAILED。
 */
@Data
@TableName("t_knowledge_document")
public class KnowledgeDocumentDO {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long kbId;
    private String title;
    /** 来源类型：ARTICLE / UPLOAD */
    private String sourceType;
    /** 来源引用：文章 ID 或上传文件名 */
    private String sourceRef;
    private String fileType;
    /** 状态：PENDING / CHUNKING / READY / FAILED */
    private String status;
    /** 失败原因（仅在 FAILED 状态时有值） */
    private String failReason;
    /** 内容 hash（正文+分块参数+embedding 模型/维度）；READY 且匹配时事件路径跳过重建。内部字段，不进 API 响应 */
    @JsonIgnore
    private String contentHash;
    private Integer chunkCount;
    private Integer charCount;
    private LocalDateTime createTime;
}
