package io.github.somehow.mysite.dao.mapper;

import io.github.somehow.mysite.elasticsearch.ArticleDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Elasticsearch 持久层
 */
public interface ArticleEsRepository extends ElasticsearchRepository<ArticleDocument, String> {

    /**
     * 根据标题搜索文章
     *
     * @param title 标题关键词
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    Page<ArticleDocument> findByTitleContaining(String title, Pageable pageable);
    
    /**
     * 根据内容搜索文章
     *
     * @param content 内容关键词
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    Page<ArticleDocument> findByContentContaining(String content, Pageable pageable);
    
    /**
     * 根据作者ID搜索文章
     *
     * @param authorId 作者ID
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    Page<ArticleDocument> findByAuthorId(String authorId, Pageable pageable);
    
    /**
     * 根据作者ID列表搜索文章
     *
     * @param authorIds 作者ID列表
     * @param pageable 分页参数
     * @return 文章分页结果
     */
    Page<ArticleDocument> findByAuthorIdIn(List<String> authorIds, Pageable pageable);
}