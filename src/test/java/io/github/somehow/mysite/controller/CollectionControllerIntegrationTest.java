package io.github.somehow.mysite.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.commons.framework.web.GlobalExceptionHandler;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleBatchReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionArticleSortReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionCreateReqDTO;
import io.github.somehow.mysite.dto.req.collection.CollectionUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.collection.ArticleNavInfoRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionDetailRespDTO;
import io.github.somehow.mysite.dto.resp.collection.CollectionPageQueryRespDTO;
import io.github.somehow.mysite.service.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 合集控制器集成测试。
 * 使用 MockMvc standalone 模式，仅加载控制器 + 全局异常处理器，
 * 通过 Mock 模拟 Service 层，专注于验证：
 * 1. HTTP 请求/响应契约
 * 2. 参数校验（JSR-303）
 * 3. 异常处理与错误码
 * 4. 服务层调用契约
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("合集控制器 API 集成测试")
class CollectionControllerIntegrationTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CollectionService collectionService;

    @InjectMocks
    private CollectionController collectionController;

    private static final long COLLECTION_ID = 1001L;
    private static final long ARTICLE_ID = 2001L;

    @BeforeEach
    void setUp() {
        // standalone 模式：手动注册控制器和全局异常处理器，避免加载完整 Spring 上下文
        mockMvc = MockMvcBuilders.standaloneSetup(collectionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 创建合集 ====================

    @Test
    @DisplayName("创建合集 - 成功")
    void createCollection_shouldReturnSuccess() throws Exception {
        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("Spring Boot 实战合集");
        req.setDescription("从入门到精通");
        req.setSortOrder(1);

        when(collectionService.createCollection(any(CollectionCreateReqDTO.class)))
                .thenReturn(COLLECTION_ID);

        mockMvc.perform(post("/v1/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(String.valueOf(COLLECTION_ID)));

        verify(collectionService).createCollection(any(CollectionCreateReqDTO.class));
    }

    @Test
    @DisplayName("创建合集 - 标题为空应返回校验错误")
    void createCollection_blankTitle_shouldReturnValidationError() throws Exception {
        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("");
        req.setDescription("描述");

        mockMvc.perform(post("/v1/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALIDATION_ERROR.code()))
                .andExpect(jsonPath("$.message").exists());

        verify(collectionService, never()).createCollection(any(CollectionCreateReqDTO.class));
    }

    @Test
    @DisplayName("创建合集 - 标题超长应返回校验错误")
    void createCollection_titleTooLong_shouldReturnValidationError() throws Exception {
        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("a".repeat(201));

        mockMvc.perform(post("/v1/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALIDATION_ERROR.code()));

        verify(collectionService, never()).createCollection(any(CollectionCreateReqDTO.class));
    }

    @Test
    @DisplayName("创建合集 - 描述超长应返回校验错误")
    void createCollection_descriptionTooLong_shouldReturnValidationError() throws Exception {
        CollectionCreateReqDTO req = new CollectionCreateReqDTO();
        req.setTitle("合法标题");
        req.setDescription("d".repeat(501));

        mockMvc.perform(post("/v1/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALIDATION_ERROR.code()));

        verify(collectionService, never()).createCollection(any(CollectionCreateReqDTO.class));
    }

    // ==================== 更新合集 ====================

    @Test
    @DisplayName("更新合集 - 成功")
    void updateCollection_shouldReturnSuccess() throws Exception {
        CollectionUpdateReqDTO req = new CollectionUpdateReqDTO();
        req.setTitle("更新后的标题");

        mockMvc.perform(put("/v1/collections/{id}", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).updateCollection(anyLong(), any(CollectionUpdateReqDTO.class));
    }

    @Test
    @DisplayName("更新合集 - 合集不存在应返回业务错误")
    void updateCollection_notFound_shouldReturnBusinessError() throws Exception {
        CollectionUpdateReqDTO req = new CollectionUpdateReqDTO();
        req.setTitle("标题");

        doThrow(new ClientException(ErrorCode.COLLECTION_NOT_FOUND))
                .when(collectionService).updateCollection(anyLong(), any(CollectionUpdateReqDTO.class));

        mockMvc.perform(put("/v1/collections/{id}", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.COLLECTION_NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.COLLECTION_NOT_FOUND.message()));
    }

    @Test
    @DisplayName("更新合集 - 非作者应返回权限错误")
    void updateCollection_notOwner_shouldReturnPermissionError() throws Exception {
        CollectionUpdateReqDTO req = new CollectionUpdateReqDTO();
        req.setTitle("标题");

        doThrow(new ClientException(ErrorCode.COLLECTION_PERMISSION_DENIED))
                .when(collectionService).updateCollection(anyLong(), any(CollectionUpdateReqDTO.class));

        mockMvc.perform(put("/v1/collections/{id}", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.COLLECTION_PERMISSION_DENIED.code()));
    }

    // ==================== 删除合集 ====================

    @Test
    @DisplayName("删除合集 - 成功")
    void deleteCollection_shouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/v1/collections/{id}", COLLECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).deleteCollection(COLLECTION_ID);
    }

    @Test
    @DisplayName("删除合集 - 不存在应返回业务错误")
    void deleteCollection_notFound_shouldReturnBusinessError() throws Exception {
        doThrow(new ClientException(ErrorCode.COLLECTION_NOT_FOUND))
                .when(collectionService).deleteCollection(anyLong());

        mockMvc.perform(delete("/v1/collections/{id}", COLLECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.COLLECTION_NOT_FOUND.code()));
    }

    // ==================== 分页查询合集 ====================

    @Test
    @DisplayName("分页查询合集 - 成功返回列表")
    void pageQueryCollection_shouldReturnPage() throws Exception {
        CollectionPageQueryRespDTO item = new CollectionPageQueryRespDTO();
        item.setId(COLLECTION_ID);
        item.setTitle("合集一");
        item.setAuthorName("作者");

        Page<CollectionPageQueryRespDTO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(item));

        when(collectionService.pageQueryCollection(any())).thenReturn(page);

        mockMvc.perform(get("/v1/collections")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].title").value("合集一"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("分页查询合集 - 关键词搜索")
    void pageQueryCollection_withKeyword_shouldReturnFilteredPage() throws Exception {
        IPage<CollectionPageQueryRespDTO> emptyPage = new Page<>(1, 10, 0);
        when(collectionService.pageQueryCollection(any())).thenReturn(emptyPage);

        mockMvc.perform(get("/v1/collections")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ==================== 查询合集详情 ====================

    @Test
    @DisplayName("查询合集详情 - 成功返回详情含文章列表")
    void getCollectionDetail_shouldReturnDetail() throws Exception {
        CollectionDetailRespDTO detail = new CollectionDetailRespDTO();
        detail.setId(COLLECTION_ID);
        detail.setTitle("合集详情");
        detail.setAuthorName("作者");
        detail.setArticleCount(2);

        CollectionDetailRespDTO.CollectionArticleItemDTO articleItem =
                new CollectionDetailRespDTO.CollectionArticleItemDTO();
        articleItem.setId(ARTICLE_ID);
        articleItem.setTitle("文章一");
        articleItem.setSortOrder(0);
        detail.setArticles(List.of(articleItem));

        when(collectionService.getCollectionDetail(anyLong(), anyInt(), anyInt()))
                .thenReturn(detail);

        mockMvc.perform(get("/v1/collections/{id}", COLLECTION_ID)
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.title").value("合集详情"))
                .andExpect(jsonPath("$.data.articles[0].title").value("文章一"));
    }

    @Test
    @DisplayName("查询合集详情 - 使用默认分页参数")
    void getCollectionDetail_defaultPaging_shouldWork() throws Exception {
        CollectionDetailRespDTO detail = new CollectionDetailRespDTO();
        detail.setId(COLLECTION_ID);
        detail.setTitle("合集");
        detail.setArticles(Collections.emptyList());

        when(collectionService.getCollectionDetail(anyLong(), anyInt(), anyInt()))
                .thenReturn(detail);

        mockMvc.perform(get("/v1/collections/{id}", COLLECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).getCollectionDetail(COLLECTION_ID, 1, 10);
    }

    @Test
    @DisplayName("查询合集详情 - 不存在应返回业务错误")
    void getCollectionDetail_notFound_shouldReturnBusinessError() throws Exception {
        doThrow(new ClientException(ErrorCode.COLLECTION_NOT_FOUND))
                .when(collectionService).getCollectionDetail(anyLong(), anyInt(), anyInt());

        mockMvc.perform(get("/v1/collections/{id}", COLLECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.COLLECTION_NOT_FOUND.code()));
    }

    // ==================== 添加文章到合集 ====================

    @Test
    @DisplayName("添加文章到合集 - 成功")
    void addArticleToCollection_shouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/v1/collections/{collectionId}/articles/{articleId}",
                        COLLECTION_ID, ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).addArticleToCollection(COLLECTION_ID, ARTICLE_ID);
    }

    @Test
    @DisplayName("添加文章到合集 - 文章已存在应返回业务错误")
    void addArticleToCollection_alreadyExists_shouldReturnBusinessError() throws Exception {
        doThrow(new ClientException(ErrorCode.COLLECTION_ARTICLE_ALREADY_EXISTS))
                .when(collectionService).addArticleToCollection(anyLong(), anyLong());

        mockMvc.perform(post("/v1/collections/{collectionId}/articles/{articleId}",
                        COLLECTION_ID, ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.COLLECTION_ARTICLE_ALREADY_EXISTS.code()));
    }

    @Test
    @DisplayName("添加文章到合集 - 文章不存在应返回业务错误")
    void addArticleToCollection_articleNotFound_shouldReturnBusinessError() throws Exception {
        doThrow(new ClientException(ErrorCode.ARTICLE_NOT_FOUND))
                .when(collectionService).addArticleToCollection(anyLong(), anyLong());

        mockMvc.perform(post("/v1/collections/{collectionId}/articles/{articleId}",
                        COLLECTION_ID, ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));
    }

    // ==================== 从合集移除文章 ====================

    @Test
    @DisplayName("从合集移除文章 - 成功")
    void removeArticleFromCollection_shouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/v1/collections/{collectionId}/articles/{articleId}",
                        COLLECTION_ID, ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).removeArticleFromCollection(COLLECTION_ID, ARTICLE_ID);
    }

    @Test
    @DisplayName("从合集移除文章 - 文章不在合集中应返回业务错误")
    void removeArticleFromCollection_notInCollection_shouldReturnBusinessError() throws Exception {
        doThrow(new ClientException(ErrorCode.COLLECTION_ARTICLE_NOT_IN_COLLECTION))
                .when(collectionService).removeArticleFromCollection(anyLong(), anyLong());

        mockMvc.perform(delete("/v1/collections/{collectionId}/articles/{articleId}",
                        COLLECTION_ID, ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.COLLECTION_ARTICLE_NOT_IN_COLLECTION.code()));
    }

    // ==================== 批量添加文章 ====================

    @Test
    @DisplayName("批量添加文章 - 成功")
    void batchAddArticles_shouldReturnSuccess() throws Exception {
        CollectionArticleBatchReqDTO req = new CollectionArticleBatchReqDTO();
        req.setArticleIds(List.of(1L, 2L, 3L));

        mockMvc.perform(post("/v1/collections/{collectionId}/articles/batch", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).batchAddArticles(anyLong(), any(CollectionArticleBatchReqDTO.class));
    }

    @Test
    @DisplayName("批量添加文章 - 空列表应返回校验错误")
    void batchAddArticles_emptyList_shouldReturnValidationError() throws Exception {
        CollectionArticleBatchReqDTO req = new CollectionArticleBatchReqDTO();
        req.setArticleIds(Collections.emptyList());

        mockMvc.perform(post("/v1/collections/{collectionId}/articles/batch", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALIDATION_ERROR.code()));

        verify(collectionService, never()).batchAddArticles(anyLong(), any(CollectionArticleBatchReqDTO.class));
    }

    @Test
    @DisplayName("批量添加文章 - 缺少 articleIds 字段应返回校验错误")
    void batchAddArticles_missingField_shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/v1/collections/{collectionId}/articles/batch", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALIDATION_ERROR.code()));

        verify(collectionService, never()).batchAddArticles(anyLong(), any(CollectionArticleBatchReqDTO.class));
    }

    // ==================== 调整文章排序 ====================

    @Test
    @DisplayName("调整文章排序 - 成功")
    void updateArticleSort_shouldReturnSuccess() throws Exception {
        CollectionArticleSortReqDTO req = new CollectionArticleSortReqDTO();
        req.setArticleIds(List.of(3L, 1L, 2L));

        mockMvc.perform(put("/v1/collections/{collectionId}/articles/sort", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(collectionService).updateArticleSort(anyLong(), any(CollectionArticleSortReqDTO.class));
    }

    @Test
    @DisplayName("调整文章排序 - 空列表应返回校验错误")
    void updateArticleSort_emptyList_shouldReturnValidationError() throws Exception {
        CollectionArticleSortReqDTO req = new CollectionArticleSortReqDTO();
        req.setArticleIds(Collections.emptyList());

        mockMvc.perform(put("/v1/collections/{collectionId}/articles/sort", COLLECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALIDATION_ERROR.code()));

        verify(collectionService, never()).updateArticleSort(anyLong(), any(CollectionArticleSortReqDTO.class));
    }

    // ==================== 文章导航 ====================

    @Test
    @DisplayName("获取文章导航 - 文章属于合集")
    void getArticleNavigation_inCollection_shouldReturnNavInfo() throws Exception {
        ArticleNavInfoRespDTO navInfo = new ArticleNavInfoRespDTO();
        navInfo.setInCollection(true);
        navInfo.setCollectionId(String.valueOf(COLLECTION_ID));
        navInfo.setCollectionTitle("合集标题");

        ArticleNavInfoRespDTO.NavArticle prev = new ArticleNavInfoRespDTO.NavArticle();
        prev.setId(String.valueOf(ARTICLE_ID - 1));
        prev.setTitle("上一篇文章");
        navInfo.setPrev(prev);

        ArticleNavInfoRespDTO.NavArticle next = new ArticleNavInfoRespDTO.NavArticle();
        next.setId(String.valueOf(ARTICLE_ID + 1));
        next.setTitle("下一篇文章");
        navInfo.setNext(next);

        when(collectionService.getArticleNavigation(anyLong())).thenReturn(navInfo);

        mockMvc.perform(get("/v1/articles/{articleId}/navigation", ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.inCollection").value(true))
                .andExpect(jsonPath("$.data.collectionTitle").value("合集标题"))
                .andExpect(jsonPath("$.data.prev.title").value("上一篇文章"))
                .andExpect(jsonPath("$.data.next.title").value("下一篇文章"));
    }

    @Test
    @DisplayName("获取文章导航 - 文章不属于合集")
    void getArticleNavigation_notInCollection_shouldReturnTimeBasedNav() throws Exception {
        ArticleNavInfoRespDTO navInfo = new ArticleNavInfoRespDTO();
        navInfo.setInCollection(false);

        when(collectionService.getArticleNavigation(anyLong())).thenReturn(navInfo);

        mockMvc.perform(get("/v1/articles/{articleId}/navigation", ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.inCollection").value(false))
                .andExpect(jsonPath("$.data.collectionId").doesNotExist());
    }

    @Test
    @DisplayName("获取文章导航 - 第一篇文章无上一篇")
    void getArticleNavigation_firstArticle_shouldHaveNoPrev() throws Exception {
        ArticleNavInfoRespDTO navInfo = new ArticleNavInfoRespDTO();
        navInfo.setInCollection(true);
        navInfo.setPrev(null);

        ArticleNavInfoRespDTO.NavArticle next = new ArticleNavInfoRespDTO.NavArticle();
        next.setId(String.valueOf(ARTICLE_ID + 1));
        next.setTitle("下一篇");
        navInfo.setNext(next);

        when(collectionService.getArticleNavigation(anyLong())).thenReturn(navInfo);

        mockMvc.perform(get("/v1/articles/{articleId}/navigation", ARTICLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prev").doesNotExist())
                .andExpect(jsonPath("$.data.next.title").value("下一篇"));
    }
}
