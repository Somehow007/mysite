package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.CollectionDO;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleBatchReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleSortReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionCreateReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionPageQueryReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.collection.ArticleNavInfoRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionDetailRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionPageQueryRespDTO;

import java.util.List;

public interface CollectionService extends IService<CollectionDO> {

    Long createCollection(CollectionCreateReqDTO requestParam);

    void updateCollection(Long id, CollectionUpdateReqDTO requestParam);

    void deleteCollection(Long id);

    IPage<CollectionPageQueryRespDTO> pageQueryCollection(CollectionPageQueryReqDTO requestParam);

    CollectionDetailRespDTO getCollectionDetail(Long id, Integer current, Integer size);

    void addArticleToCollection(Long collectionId, Long articleId);

    void removeArticleFromCollection(Long collectionId, Long articleId);

    void batchAddArticles(Long collectionId, CollectionArticleBatchReqDTO requestParam);

    void updateArticleSort(Long collectionId, CollectionArticleSortReqDTO requestParam);

    ArticleNavInfoRespDTO getArticleNavigation(Long articleId);

    /**
     * 查询文章所属的合集信息
     */
    CollectionDO getCollectionByArticleId(Long articleId);

    /**
     * 清除合集相关缓存
     */
    void evictCollectionCache();
}
