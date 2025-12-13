package io.github.somehow.mysite.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

/**
 * 用户信息 ElasticSearch 文档实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "users")
public class UserDocument {

    /**
     * 文档唯一标识
     */
    @Id
    private String id;

    /**
     * 用户名
     */
    @Field(type = FieldType.Text, analyzer = "ik_smart", searchAnalyzer = "ik_max_word")
    private String username;

    /**
     * 真实姓名
     */
    @Field(type = FieldType.Text, analyzer = "ik_smart", searchAnalyzer = "ik_max_word")
    private String realName;

    /**
     * 性别
     */
    @Field(type = FieldType.Integer)
    private Integer sex;

    /**
     * 关注人数
     */
    @Field(type = FieldType.Integer)
    private Integer followingCount;

    /**
     * 粉丝人数
     */
    @Field(type = FieldType.Integer)
    private Integer followerCount;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date)
    private Date createTime;
}