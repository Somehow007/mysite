package io.github.somehow.mysite.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

/**
 * ElasticSearch 文档实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "blog_articles")
public class ArticleDocument {

    /**
     * 文档唯一标识
     */
    @Id
    private String id;

    /**
     * 文章标题
     */
    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String title;

    /**
     * 文章内容
     */
    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String content;

    /**
     * 作者id
     */
    @Field(type = FieldType.Keyword) // 不分词，用于精确匹配
    private String authorId;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private Date createTime;
}