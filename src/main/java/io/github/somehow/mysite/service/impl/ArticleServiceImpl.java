package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.dao.mapper.ArticleEsRepository;
import io.github.somehow.mysite.dao.mapper.ArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserFavoriteArticleMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.article.*;
import io.github.somehow.mysite.dto.resp.ArticlePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO;
import io.github.somehow.mysite.elasticsearch.ArticleDocument;
import io.github.somehow.mysite.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文章业务逻辑实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, ArticleDO> implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleEsRepository esRepository;
    private final UserMapper userMapper;
    private final UserFavoriteArticleMapper userFavoriteArticleMapper;

    @Override
    @Transactional
    public void createArticle(ArticleCreateReqDTO requestParam) {
        // 防止恶意调用接口，创建大量空文章
        if (Objects.isNull(requestParam)) {
            throw new ClientException("Parameter is required.");
        }
        if (StrUtil.isBlank(requestParam.getAuthorId())) {
            throw new ClientException("Author is required!");
        }
        if (StrUtil.isBlank(requestParam.getTitle())) {
            throw new ClientException("Title is required!");
        }
        if (StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException("Content is required!");
        }

        ArticleDO articleDO = BeanUtil.toBean(requestParam, ArticleDO.class);
        articleDO.setId(IdUtil.getSnowflakeNextId());
        // todo 后续改用 UserContext，尝试采用处理器和过滤器写入用户信息，目前先让前端传用户id
//        articleDO.setAuthorId(Optional.ofNullable(UserContext.getUserId()).map(Long::parseLong).orElse(0L));
        articleDO.setAuthorId(Long.parseLong(requestParam.getAuthorId()));
        articleMapper.insert(articleDO);

        ArticleDocument doc = ArticleDocument.builder()
                .id(articleDO.getId().toString())
                .title(requestParam.getTitle())
                .content(requestParam.getContent())
                .authorId(articleDO.getAuthorId().toString())
                .createTime(articleDO.getCreateTime())
                .build();
        esRepository.save(doc);
    }

    @Override
    public void updateArticle(ArticleUpdateReqDTO requestParam) {
        if (Objects.isNull(requestParam)) {
            throw new ClientException("更新失败，未传递更新参数");
        }
        
        LambdaUpdateWrapper<ArticleDO> updateWrapper = Wrappers.lambdaUpdate(ArticleDO.class)
                .eq(ArticleDO::getId, requestParam.getId())
                .set(StrUtil.isNotBlank(requestParam.getTitle()), ArticleDO::getTitle, requestParam.getTitle())
                .set(StrUtil.isNotBlank(requestParam.getContent()), ArticleDO::getContent, requestParam.getContent())
                .set(StrUtil.isNotBlank(requestParam.getSummary()), ArticleDO::getSummary, requestParam.getSummary())
                .set(!Objects.isNull(requestParam.getPublished()), ArticleDO::getPublished, requestParam.getPublished())
                .eq(ArticleDO::getDelFlag, 0);
        int rows = baseMapper.update(updateWrapper);
        if (rows <= 0) {
            throw new ClientException("更新文章失败，文章不存在");
        }

        // 更新ES中的文档
        ArticleDO updatedArticle = baseMapper.selectOne(Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, requestParam.getId())
                .eq(ArticleDO::getDelFlag, 0));
        
        if (updatedArticle != null) {
            ArticleDocument articleDocument = ArticleDocument.builder()
                    .id(updatedArticle.getId().toString())
                    .title(updatedArticle.getTitle())
                    .content(updatedArticle.getContent())
                    .authorId(updatedArticle.getAuthorId().toString())
                    .createTime(updatedArticle.getCreateTime())
                    .build();

            esRepository.save(articleDocument);
        }
    }

    @Override
    public void deleteArticle(Long id) {
        LambdaUpdateWrapper<ArticleDO> updateWrapper = Wrappers.lambdaUpdate(ArticleDO.class)
                .eq(ArticleDO::getId, id)
                .eq(ArticleDO::getDelFlag, 0);
        ArticleDO articleDO = new ArticleDO();
        articleDO.setDelFlag(1);

        int rows = baseMapper.update(articleDO, updateWrapper);
        if (rows <= 0) {
            throw new ClientException("删除文章失败，文章不存在");
        }

        esRepository.deleteById(id.toString());
    }

    @Override
    public IPage<ArticlePageQueryRespDTO> pageQueryArticle(ArticlePageQueryReqDTO requestParam) {
        // 构建ES分页参数
        PageRequest pageRequest = PageRequest.of(
                (int) (requestParam.getCurrent() - 1), 
                (int) requestParam.getSize());
        
        // 获取搜索关键词
        String keyword = StrUtil.blankToDefault(requestParam.getKeyword(), "");
        
        // 获取搜索类型，默认按标题搜索
        String searchType = StrUtil.blankToDefault(requestParam.getSearchType(), "title");
        
        org.springframework.data.domain.Page<ArticleDocument> esPage;
        switch (searchType) {
            case "title":
                // 按标题搜索
                esPage = esRepository.findByTitleContaining(keyword, pageRequest);
                break;
            case "content":
                // 按内容搜索
                esPage = esRepository.findByContentContaining(keyword, pageRequest);
                break;
            case "author":
                // 按作者搜索
                List<UserDO> matchedUsers = userMapper.selectByUsernameLike(keyword);
                if (!CollectionUtils.isEmpty(matchedUsers)) {
                    List<String> authorIds = matchedUsers.stream()
                            .map(user -> user.getId().toString())
                            .collect(Collectors.toList());
                    esPage = esRepository.findByAuthorIdIn(authorIds, pageRequest);
                } else {
                    // 没有匹配的用户，返回空结果
                    return new Page<>();
                }
                break;
            default:
                // 默认按标题搜索
                esPage = esRepository.findByTitleContaining(keyword, pageRequest);
                break;
        }
        
        // 将ES查询结果转换为需要的DTO格式
        if (esPage.hasContent()) {
            List<Long> articleIds = esPage.getContent().stream()
                    .map(doc -> Long.valueOf(doc.getId()))
                    .collect(Collectors.toList());
                    
            // 从数据库获取完整文章信息
            List<ArticleDO> articles = baseMapper.selectList(Wrappers.<ArticleDO>lambdaQuery()
                    .in(ArticleDO::getId, articleIds)
                    .eq(ArticleDO::getDelFlag, 0));
            
            // 获取作者信息
            List<Long> authorIds = articles.stream()
                    .map(ArticleDO::getAuthorId)
                    .distinct()
                    .collect(Collectors.toList());
            
            List<UserDO> authors = userMapper.selectList(Wrappers.<UserDO>lambdaQuery()
                    .in(UserDO::getId, authorIds));
            
            // 构建返回结果
            List<ArticlePageQueryRespDTO> records = articles.stream()
                    .map(article -> {
                        ArticlePageQueryRespDTO dto = new ArticlePageQueryRespDTO();
                        dto.setId(article.getId());
                        dto.setTitle(article.getTitle());
                        dto.setSummary(article.getSummary());
                        dto.setViewCount(article.getViewCount());
                        dto.setFavoriteCount(article.getFavoriteCount());
                        
                        // 设置作者名称
                        String authorName = authors.stream()
                                .filter(user -> user.getId().equals(article.getAuthorId()))
                                .map(UserDO::getUsername)
                                .findFirst()
                                .orElse("");
                        dto.setAuthorName(authorName);
                        
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            // 构造MyBatis Plus分页对象
            IPage<ArticlePageQueryRespDTO> result = new Page<>(requestParam.getCurrent(), requestParam.getSize(), esPage.getTotalElements());
            result.setRecords(records);
            return result;
        } else {
            return new Page<>();
        }
    }

    @Override
    public IPage<ArticlePageQueryRespDTO> pageQueryFavoriteArticle(ArticleFavoritePageQueryReqDTO requestParam) {
        return baseMapper.pageFavoriteArticleResults(requestParam);
    }

    @Override
    public ArticleSelectRespDTO selectOneArticle(Long id) {
        LambdaQueryWrapper<ArticleDO> queryWrapper = Wrappers.lambdaQuery(ArticleDO.class)
                .eq(ArticleDO::getId, id)
                .eq(ArticleDO::getDelFlag, 0);
        ArticleDO articleDO = baseMapper.selectOne(queryWrapper);
        if (Objects.isNull(articleDO)) {
            throw new ClientException("获取文章信息失败，文章不存在");
        }
        baseMapper.incrementViewCount(id, 1);
        return BeanUtil.toBean(articleDO, ArticleSelectRespDTO.class);
    }

    @Override
    @Transactional
    public void favoriteArticle(ArticleFavoriteReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getArticleId()) || StrUtil.isBlank(requestParam.getUserId())) {
            throw new ClientException("收藏相关操作失败，未传入正确参数");
        }
        UserFavoriteArticleDO isExist = userFavoriteArticleMapper.selectOne(Wrappers.lambdaQuery(UserFavoriteArticleDO.class)
                .eq(UserFavoriteArticleDO::getArticleId, requestParam.getArticleId())
                .eq(UserFavoriteArticleDO::getUserId, requestParam.getUserId()));
        if (Objects.isNull(isExist)) {
            UserFavoriteArticleDO userFavoriteArticleDO = BeanUtil.toBean(requestParam, UserFavoriteArticleDO.class);
            userFavoriteArticleDO.setId(IdUtil.getSnowflakeNextId());
            try {
                userFavoriteArticleMapper.insert(userFavoriteArticleDO);
                articleMapper.incrementFavoriteCount(Long.parseLong(requestParam.getArticleId()), 1);
            } catch (DuplicateKeyException e) {
                throw new ClientException("已经收藏过了噢～");
            }
        } else if (isExist.getDelFlag() == 1) {
            userFavoriteArticleMapper.update(Wrappers.lambdaUpdate(UserFavoriteArticleDO.class)
                    .eq(UserFavoriteArticleDO::getArticleId, requestParam.getArticleId())
                    .eq(UserFavoriteArticleDO::getUserId, requestParam.getUserId())
                    .eq(UserFavoriteArticleDO::getDelFlag, 1)
                    .set(UserFavoriteArticleDO::getDelFlag, 0));
            articleMapper.incrementFavoriteCount(Long.parseLong(requestParam.getArticleId()), 1);
        } else if (isExist.getDelFlag() == 0) {
            userFavoriteArticleMapper.update(Wrappers.lambdaUpdate(UserFavoriteArticleDO.class)
                    .eq(UserFavoriteArticleDO::getArticleId, requestParam.getArticleId())
                    .eq(UserFavoriteArticleDO::getUserId, requestParam.getUserId())
                    .eq(UserFavoriteArticleDO::getDelFlag, 0)
                    .set(UserFavoriteArticleDO::getDelFlag, 1));
            articleMapper.decrementFavoriteCount(Long.parseLong(requestParam.getArticleId()), 1);
        }
    }
}