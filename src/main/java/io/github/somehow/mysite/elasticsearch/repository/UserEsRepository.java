package io.github.somehow.mysite.elasticsearch.repository;

import io.github.somehow.mysite.elasticsearch.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserEsRepository extends ElasticsearchRepository<UserDocument, String> {

    Page<UserDocument> findByUsernameContaining(String username, Pageable pageable);

}
