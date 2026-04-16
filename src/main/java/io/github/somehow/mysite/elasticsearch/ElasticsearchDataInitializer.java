package io.github.somehow.mysite.elasticsearch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.ArticleEsRepository;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserEsRepository;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchDataInitializer implements CommandLineRunner {

    private final ArticleEsRepository articleEsRepository;
    private final UserEsRepository userEsRepository;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始Elasticsearch数据初始化检查...");
        
        checkAndSyncArticles();
        checkAndSyncUsers();
        
        log.info("Elasticsearch数据初始化完成");
    }

    private void checkAndSyncArticles() {
        try {
            long esCount = articleEsRepository.count();
            Long dbCount = articleMapper.selectCount(
                new LambdaQueryWrapper<ArticleDO>()
                    .eq(ArticleDO::getDelFlag, 0)
            );
            
            log.info("文章数据检查 - 数据库: {}, ES: {}", dbCount, esCount);
            
            if (esCount == 0 && dbCount > 0) {
                log.warn("ES文章索引为空，开始从数据库同步数据...");
                syncAllArticlesToEs();
            }
        } catch (Exception e) {
            log.error("文章数据同步失败: {}", e.getMessage());
        }
    }

    private void checkAndSyncUsers() {
        try {
            long esCount = userEsRepository.count();
            Long dbCount = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>()
                    .eq(UserDO::getDelFlag, 0)
            );
            
            log.info("用户数据检查 - 数据库: {}, ES: {}", dbCount, esCount);
            
            if (esCount == 0 && dbCount > 0) {
                log.warn("ES用户索引为空，开始从数据库同步数据...");
                syncAllUsersToEs();
            }
        } catch (Exception e) {
            log.error("用户数据同步失败: {}", e.getMessage());
        }
    }

    private void syncAllArticlesToEs() {
        List<ArticleDO> articles = articleMapper.selectList(
            new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getDelFlag, 0)
                .orderByDesc(ArticleDO::getCreateTime)
        );
        
        if (articles.isEmpty()) {
            log.warn("数据库中没有文章数据");
            return;
        }
        
        List<ArticleDocument> documents = articles.stream()
            .map(this::convertToArticleDocument)
            .collect(Collectors.toList());
        
        articleEsRepository.saveAll(documents);
        log.info("成功同步 {} 篇文章到Elasticsearch", documents.size());
    }

    private void syncAllUsersToEs() {
        List<UserDO> users = userMapper.selectList(
            new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getDelFlag, 0)
                .orderByDesc(UserDO::getCreateTime)
        );
        
        if (users.isEmpty()) {
            log.warn("数据库中没有用户数据");
            return;
        }
        
        List<UserDocument> documents = users.stream()
            .map(this::convertToUserDocument)
            .collect(Collectors.toList());
        
        userEsRepository.saveAll(documents);
        log.info("成功同步 {} 个用户到Elasticsearch", documents.size());
    }

    private ArticleDocument convertToArticleDocument(ArticleDO article) {
        return ArticleDocument.builder()
            .id(article.getId().toString())
            .title(article.getTitle())
            .content(article.getContent())
            .authorId(article.getAuthorId().toString())
            .createTime(article.getCreateTime())
            .build();
    }

    private UserDocument convertToUserDocument(UserDO user) {
        return UserDocument.builder()
            .id(user.getId().toString())
            .username(user.getUsername())
            .realName(user.getRealName())
            .sex(user.getSex())
            .followingCount(user.getFollowingCount())
            .followerCount(user.getFollowerCount())
            .createTime(user.getCreateTime())
            .build();
    }
}
