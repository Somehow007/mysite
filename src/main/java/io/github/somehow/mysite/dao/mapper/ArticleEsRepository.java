package io.github.somehow.mysite.dao.mapper;

import io.github.somehow.mysite.elasticsearch.ArticleDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ArticleEsRepository extends ElasticsearchRepository<ArticleDocument, String> {

    Page<ArticleDocument> findByTitleContaining(String title, Pageable pageable);

    Page<ArticleDocument> findByContentContaining(String content, Pageable pageable);

    Page<ArticleDocument> findByAuthorId(String authorId, Pageable pageable);

    Page<ArticleDocument> findByAuthorIdIn(List<String> authorIds, Pageable pageable);

    Page<ArticleDocument> findByCategoryId(String categoryId, Pageable pageable);

    Page<ArticleDocument> findByCategoryIdIn(List<String> categoryIds, Pageable pageable);

    Page<ArticleDocument> findByIdIn(List<String> ids, Pageable pageable);

    Page<ArticleDocument> findByCategoryIdAndTitleContaining(String categoryId, String title, Pageable pageable);

    Page<ArticleDocument> findByCategoryIdAndContentContaining(String categoryId, String content, Pageable pageable);

    Page<ArticleDocument> findByCategoryIdAndAuthorIdIn(String categoryId, List<String> authorIds, Pageable pageable);

    Page<ArticleDocument> findByIdInAndTitleContaining(List<String> ids, String title, Pageable pageable);

    Page<ArticleDocument> findByIdInAndContentContaining(List<String> ids, String content, Pageable pageable);

    Page<ArticleDocument> findByIdInAndAuthorIdIn(List<String> ids, List<String> authorIds, Pageable pageable);
}
