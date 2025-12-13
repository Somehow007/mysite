package io.github.somehow.mysite.dao.mapper;

import io.github.somehow.mysite.elasticsearch.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 用户 Elasticsearch 持久层
 */
public interface UserEsRepository extends ElasticsearchRepository<UserDocument, String> {

    /**
     * 根据用户名搜索用户
     *
     * @param username 用户名关键词
     * @param pageable 分页参数
     * @return 用户分页结果
     */
    Page<UserDocument> findByUsernameContaining(String username, Pageable pageable);

}