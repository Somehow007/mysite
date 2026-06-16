# 文章合集功能实施方案

> **版本**: v1.0  
> **日期**: 2026-06-16  
> **状态**: 待评审  

---

## 目录

1. [需求概述](#1-需求概述)
2. [业界参考方案](#2-业界参考方案)
3. [数据结构设计](#3-数据结构设计)
4. [后端接口设计](#4-后端接口设计)
5. [前端界面设计](#5-前端界面设计)
6. [文章导航功能设计](#6-文章导航功能设计)
7. [搜索功能适配](#7-搜索功能适配)
8. [性能优化策略](#8-性能优化策略)
9. [主题适配方案](#9-主题适配方案)
10. [实施步骤](#10-实施步骤)
11. [测试计划](#11-测试计划)
12. [风险与注意事项](#12-风险与注意事项)

---

## 1. 需求概述

### 1.1 核心功能

| 功能模块 | 功能点 | 说明 |
|---------|--------|------|
| 合集管理 | 创建合集 | 设置标题、描述等基本信息 |
| 合集管理 | 添加文章 | 方式一：绑定已有文章；方式二：在合集中新建文章 |
| 合集管理 | 编辑合集 | 修改标题、描述等信息 |
| 合集管理 | 删除合集 | 删除合集（不影响文章本身） |
| 合集管理 | 排序管理 | 拖拽/按钮调整合集中文章的顺序 |
| 主页展示 | 合集标识 | 合集内容使用特殊视觉标识区分 |
| 主页展示 | 归类展示 | 合集文章归类在合集中，不单独分散展示 |
| 主页展示 | 非合集文章 | 保持现有展示方式不变 |
| 文章导航 | 上下篇导航 | 文章详情页显示"上一篇""下一篇"按钮，含标题 |
| 文章导航 | 导航顺序 | 合集内文章按合集设定顺序；非合集文章按时间排序 |
| 搜索功能 | 合集可检索 | 搜索可命中合集及其中的文章，结果中明确标识 |

### 1.2 非功能性需求

- 与现有六个主题完全兼容，保持视觉一致性
- 合集功能不影响页面加载速度和系统整体性能
- 支持 Elasticsearch 和数据库两种搜索模式
- 遵循现有项目的代码规范与架构模式

---

## 2. 业界参考方案

### 2.1 Medium 的 Series（系列）

Medium 的 Series 功能允许作者将相关文章组织成一个有序列表：

- **数据结构**: 一篇 Article 通过 `seriesId` 字段关联到 Series，通过 `position` 字段控制顺序
- **展示方式**: 文章顶部显示系列名称和序号（如 "Part 2 of My Series"），底部提供上一篇/下一篇导航
- **主页表现**: 系列文章在作者主页上以独立卡片展示，区别于普通文章
- **导航体验**: 阅读完一篇后，底部自动出现下一篇的入口，形成连续阅读流

### 2.2 Notion 的 Database View

Notion 将合集视为数据库的不同视图：

- **灵活分组**: 支持按标签、分类、自定义属性对文章进行分组展示
- **视图切换**: 支持列表、看板、画廊、日历等多种视图
- **拖拽排序**: 通过拖拽自由调整文章顺序

### 2.3 掘金的"专栏"

掘金专栏将主题性文章聚合为一个独立产品：

- **独立页面**: 专栏有独立的封面、简介、订阅功能
- **强归属感**: 文章明确归属于某个专栏，展示时强调归属关系
- **目录结构**: 专栏内文章按章节组织，形成清晰的目录结构

### 2.4 方案选择

综合以上参考，本项目采用 **Medium 的 Series 模式**作为主要参考，理由如下：

1. 数据结构简单清晰，与现有 `ArticleDO` 模型兼容性好
2. 导航体验自然，符合读者的阅读习惯
3. 实现复杂度适中，不会显著增加维护负担
4. 与博客系统的场景最为匹配

---

## 3. 数据结构设计

### 3.1 新增数据库表

#### 3.1.1 合集主表 `t_collection`

```sql
CREATE TABLE IF NOT EXISTS `t_collection` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '合集标题',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '合集描述',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '合集封面图片URL',
    `author_id` BIGINT NOT NULL COMMENT '创建者ID',
    `article_count` INT NOT NULL DEFAULT 0 COMMENT '合集内文章数量',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_sort_order` (`sort_order`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章合集表';
```

#### 3.1.2 合集-文章关联表 `t_collection_article`

```sql
CREATE TABLE IF NOT EXISTS `t_collection_article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collection_id` BIGINT NOT NULL COMMENT '合集ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '在合集内的排序序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_article` (`collection_id`, `article_id`),
    KEY `idx_collection_id` (`collection_id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_collection_sort` (`collection_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合集文章关联表';
```

### 3.2 后端实体类设计

#### 3.2.1 CollectionDO

```java
// 路径: src/main/java/io/github/somehow/mysite/dao/entity/CollectionDO.java

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_collection")
public class CollectionDO extends BaseDO {
    private Long id;
    private String title;
    private String description;
    private String coverImage;
    private Long authorId;
    private Integer articleCount;
    private Integer sortOrder;
}
```

#### 3.2.2 CollectionArticleDO

```java
// 路径: src/main/java/io/github/somehow/mysite/dao/entity/CollectionArticleDO.java

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_collection_article")
public class CollectionArticleDO extends BaseDO {
    private Long id;
    private Long collectionId;
    private Long articleId;
    private Integer sortOrder;
}
```

### 3.3 前端类型定义

在 `mysite-frontend/src/types/index.ts` 中新增：

```typescript
// 合集基本信息
export interface Collection {
  id: string
  title: string
  description: string
  coverImage: string | null
  authorId: string
  authorName: string
  articleCount: number
  sortOrder: number
  createTime: string
  updateTime: string
}

// 合集内文章项（带回合集上下文）
export interface CollectionArticle {
  id: string
  title: string
  summary: string
  coverImage: string | null
  authorName: string
  viewCount: number
  favoriteCount: number
  readingTime?: number
  collectionId: string
  collectionTitle: string
  sortOrder: number       // 在合集内的位置
  prevArticleId: string | null  // 上一篇ID
  nextArticleId: string | null  // 下一篇ID
  createTime: string
  updateTime: string
}

// 文章导航信息
export interface ArticleNavInfo {
  prev: { id: string; title: string } | null
  next: { id: string; title: string } | null
  inCollection: boolean           // 是否属于合集
  collectionId: string | null
  collectionTitle: string | null
}
```

### 3.4 数据关系图

```
┌─────────────────┐       ┌──────────────────────┐       ┌─────────────────┐
│  t_collection   │ 1───N │ t_collection_article  │ N───1 │   t_article     │
│─────────────────│       │──────────────────────│       │─────────────────│
│ id              │       │ id                   │       │ id              │
│ title           │       │ collection_id (FK)   │       │ title           │
│ description     │       │ article_id (FK)      │       │ content         │
│ cover_image     │       │ sort_order           │       │ ...             │
│ author_id       │       │ create_time          │       │                 │
│ article_count   │       │ del_flag             │       │                 │
│ sort_order      │       │                      │       │                 │
│ del_flag        │       │                      │       │                 │
└─────────────────┘       └──────────────────────┘       └─────────────────┘
```

---

## 4. 后端接口设计

### 4.1 合集管理接口

#### 4.1.1 创建合集

```
POST /v1/collections
```

请求体：
```json
{
  "title": "我的Spring Boot教程",
  "description": "从入门到精通的Spring Boot系列教程",
  "coverImage": "https://example.com/cover.jpg",
  "sortOrder": 0
}
```

响应：
```json
{
  "code": "00000",
  "message": null,
  "data": { "id": "123456789" },
  "success": true
}
```

#### 4.1.2 更新合集

```
PUT /v1/collections/{id}
```

请求体：
```json
{
  "title": "更新后的标题",
  "description": "更新后的描述",
  "coverImage": "https://example.com/new-cover.jpg"
}
```

#### 4.1.3 删除合集

```
DELETE /v1/collections/{id}
```

说明：删除合集只删除关联记录，不影响文章本身。

#### 4.1.4 查询合集列表

```
GET /v1/collections
```

参数：
| 参数 | 类型 | 说明 |
|------|------|------|
| current | int | 页码（默认1） |
| size | int | 每页数量（默认10） |
| keyword | string | 搜索关键词（可选） |
| authorId | string | 按作者筛选（可选） |

响应：
```json
{
  "code": "00000",
  "data": {
    "records": [
      {
        "id": "123456789",
        "title": "我的Spring Boot教程",
        "description": "从入门到精通的Spring Boot系列教程",
        "coverImage": "https://example.com/cover.jpg",
        "authorId": "111",
        "authorName": "somehow",
        "articleCount": 5,
        "sortOrder": 0,
        "createTime": "2026-06-01T10:00:00",
        "updateTime": "2026-06-10T15:00:00"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 10
  }
}
```

#### 4.1.5 查询单个合集详情（含文章列表）

```
GET /v1/collections/{id}
```

参数：
| 参数 | 类型 | 说明 |
|------|------|------|
| current | int | 页码（默认1） |
| size | int | 每页数量（默认10） |

响应：
```json
{
  "code": "00000",
  "data": {
    "id": "123456789",
    "title": "我的Spring Boot教程",
    "description": "从入门到精通的Spring Boot系列教程",
    "coverImage": "https://example.com/cover.jpg",
    "authorId": "111",
    "authorName": "somehow",
    "articleCount": 5,
    "articles": {
      "records": [
        {
          "id": "1",
          "title": "Spring Boot 入门",
          "summary": "...",
          "coverImage": null,
          "authorName": "somehow",
          "viewCount": 100,
          "favoriteCount": 5,
          "readingTime": 10,
          "sortOrder": 0,
          "createTime": "2026-06-01T10:00:00"
        }
      ],
      "total": 5,
      "current": 1,
      "size": 10
    }
  }
}
```

### 4.2 合集文章管理接口

#### 4.2.1 向合集添加已有文章

```
POST /v1/collections/{collectionId}/articles
```

请求体：
```json
{
  "articleId": "987654321"
}
```

#### 4.2.2 从合集中移除文章

```
DELETE /v1/collections/{collectionId}/articles/{articleId}
```

#### 4.2.3 调整合集中文章排序

```
PUT /v1/collections/{collectionId}/articles/sort
```

请求体：
```json
{
  "articleIds": ["3", "1", "5", "2", "4"]
}
```

说明：传入按新顺序排列的文章ID数组，后端据此更新每条记录的 `sort_order`。

#### 4.2.4 批量添加文章到合集

```
POST /v1/collections/{collectionId}/articles/batch
```

请求体：
```json
{
  "articleIds": ["1", "2", "3"]
}
```

### 4.3 文章导航接口

#### 4.3.1 获取文章导航信息

```
GET /v1/articles/{id}/navigation
```

响应：
```json
{
  "code": "00000",
  "data": {
    "prev": { "id": "1", "title": "Spring Boot 入门" },
    "next": { "id": "3", "title": "Spring Boot 进阶" },
    "inCollection": true,
    "collectionId": "123456789",
    "collectionTitle": "我的Spring Boot教程"
  }
}
```

逻辑说明：
- 如果文章属于某个合集，导航顺序遵循合集中的 `sort_order`
- 如果文章不属于合集，导航顺序按发布时间倒序
- 如果当前文章是第一篇，`prev` 为 null
- 如果当前文章是最后一篇，`next` 为 null

### 4.4 主页文章列表接口适配

现有 `GET /v1/articles` 接口需要增强，支持按合集模式返回：

新增参数：
| 参数 | 类型 | 说明 |
|------|------|------|
| includeCollections | boolean | 是否包含合集信息（默认 true） |

响应中为合集文章新增字段：
```json
{
  "id": "1",
  "title": "Spring Boot 入门",
  "collectionId": "123456789",
  "collectionTitle": "我的Spring Boot教程",
  "collectionSortOrder": 0,
  ...
}
```

### 4.5 后端服务层设计

#### 4.5.1 CollectionService 接口

```java
public interface CollectionService extends IService<CollectionDO> {
    Long createCollection(CollectionCreateReqDTO requestParam);
    void updateCollection(CollectionUpdateReqDTO requestParam);
    void deleteCollection(Long id);
    IPage<CollectionPageQueryRespDTO> pageQueryCollection(CollectionPageQueryReqDTO requestParam);
    CollectionDetailRespDTO getCollectionDetail(Long id, Integer current, Integer size);
    void addArticleToCollection(Long collectionId, Long articleId);
    void removeArticleFromCollection(Long collectionId, Long articleId);
    void batchAddArticles(Long collectionId, List<Long> articleIds);
    void updateArticleSort(Long collectionId, List<Long> articleIds);
    ArticleNavInfoRespDTO getArticleNavigation(Long articleId);
}
```

#### 4.5.2 关键实现逻辑

**主页文章列表合并逻辑**（在 `ArticleServiceImpl.pageQueryArticle` 中）：

```
1. 查询所有已发布文章（分页）
2. 查询这些文章中属于合集的文章及其合集信息
3. 对于属于合集的文章：
   - 按合集分组
   - 每组取 sort_order 最小的文章作为合集代表
   - 标记 collectionId 和 collectionTitle
4. 对于不属于合集的文章，保持原有展示
5. 返回合并后的列表
```

**文章导航计算逻辑**（在 `CollectionServiceImpl.getArticleNavigation` 中）：

```
1. 查询文章是否属于合集（查 t_collection_article 表）
2. 如果属于合集：
   - 查询同合集中所有文章的 sort_order
   - 找到当前文章的前一条和后一条
3. 如果不属于合集：
   - 按 create_time 降序查询文章
   - 找到当前文章的前一条和后一条
4. 返回导航信息
```

### 4.6 搜索接口适配

#### 4.6.1 数据库搜索模式适配

在 `DatabaseArticleSearchServiceImpl.searchArticles` 中：
- 搜索时同时检索 `t_collection` 表的 `title` 和 `description` 字段
- 搜索结果中标记文章所属合集信息

#### 4.6.2 Elasticsearch 搜索模式适配

在 `ElasticsearchArticleSearchServiceImpl` 中：
- 新增 `collection_id` 和 `collection_title` 字段到 `ArticleDocument`
- 搜索时额外检索合集表
- 扩展 `ArticleDocument` 索引结构

**ArticleDocument 扩展**：
```java
@Field(type = FieldType.Keyword)
private String collectionId;

@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
private String collectionTitle;
```

---

## 5. 前端界面设计

### 5.1 路由设计

在 `mysite-frontend/src/app/router/index.ts` 中新增路由：

```typescript
// 前台路由
{
  path: 'collection/:id',
  name: 'collection',
  component: () => import('@/views/CollectionView.vue'),
}

// 后台管理路由
{
  path: 'collections',
  name: 'collections',
  component: () => import('@/views/CollectionManageView.vue'),
  meta: { requiresDeveloper: true },
},
{
  path: 'collections/:id',
  name: 'collection-edit',
  component: () => import('@/views/CollectionEditView.vue'),
  meta: { requiresDeveloper: true },
},
```

### 5.2 主页合集展示

#### 5.2.1 展示逻辑

在 `BlogHome.vue` 中，文章列表前先展示合集区域：

```
+--------------------------------------------------+
|  [合集] 我的Spring Boot教程 (5篇)                  |  ← 合集卡片
|  +----------------------------------------------+  |
|  | 代表文章: Spring Boot 入门                     |  |
|  +----------------------------------------------+  |
+--------------------------------------------------+

+--------------------------------------------------+
|  [普通文章] Spring Boot 实战经验分享               |  ← 普通文章卡片
+--------------------------------------------------+

+--------------------------------------------------+
|  [合集] TypeScript 高级技巧 (3篇)                  |  ← 合集卡片
|  +----------------------------------------------+  |
|  | 代表文章: TypeScript 类型体操                   |  |
|  +----------------------------------------------+  |
+--------------------------------------------------+
```

#### 5.2.2 合集卡片组件 `CollectionCard.vue`

新增组件 `mysite-frontend/src/components/article/CollectionCard.vue`：

- 使用独特的视觉标识（合集图标 + 主题色边框/背景）
- 显示合集标题、描述、文章数量
- 点击进入合集详情页
- 与现有 `ArticleCard.vue` 保持一致的间距和圆角风格

#### 5.2.3 视觉区分设计

合集卡片使用以下视觉差异化元素：

| 元素 | 普通文章 | 合集文章 |
|------|---------|---------|
| 左侧边框 | 无 | 3px 主题色竖线 |
| 标题前缀 | 无 | 📑 合集图标（或 CSS 图标） |
| 背景色 | 默认 | 使用 `bg-accent` 微调（透明度 0.03-0.05） |
| 标签 | 分类标签 | 合集标签（使用不同颜色或样式） |
| 角标 | 无 | 文章数量角标 |

### 5.3 合集详情页 `CollectionView.vue`

```
+--------------------------------------------------+
|  ← 返回                                           |
|                                                    |
|  [封面图]                                          |
|                                                    |
|  我的Spring Boot教程                               |
|  从入门到精通的Spring Boot系列教程                    |
|  作者: somehow · 5篇文章 · 更新于 2026-06-10        |
|                                                    |
+--------------------------------------------------+
|  目录                                              |
|                                                    |
|  1. Spring Boot 入门                          →    |
|  2. Spring Boot 配置详解                       →    |
|  3. Spring Boot 数据访问                       →    |
|  4. Spring Boot 安全认证                       →    |
|  5. Spring Boot 部署实战                       →    |
|                                                    |
+--------------------------------------------------+
```

### 5.4 合集管理页面

#### 5.4.1 合集列表管理 `CollectionManageView.vue`

```
+--------------------------------------------------+
|  合集管理                            [+ 创建合集]  |
+--------------------------------------------------+
|  | 合集名称              | 文章数 | 操作           |
|  | 我的Spring Boot教程    | 5     | 编辑 删除      |
|  | TypeScript 高级技巧    | 3     | 编辑 删除      |
+--------------------------------------------------+
```

#### 5.4.2 合集编辑页面 `CollectionEditView.vue`

```
+--------------------------------------------------+
|  ← 返回合集列表                                    |
|                                                    |
|  编辑合集                                         |
|                                                    |
|  标题: [___________________________]               |
|  描述: [___________________________]               |
|                                                    |
|  ─── 文章管理 ───                                  |
|                                                    |
|  [+ 添加已有文章]  [+ 新建文章]                     |
|                                                    |
|  拖拽排序区域:                                      |
|  ☰ 1. Spring Boot 入门                      [移除] |
|  ☰ 2. Spring Boot 配置详解                   [移除] |
|  ☰ 3. Spring Boot 数据访问                   [移除] |
|  ☰ 4. Spring Boot 安全认证                   [移除] |
|  ☰ 5. Spring Boot 部署实战                   [移除] |
|                                                    |
|  [保存修改]                                        |
+--------------------------------------------------+
```

### 5.5 文章详情页合集导航

在 `BlogPost.vue` 中，文章底部（评论区上方）新增导航区域：

```
+--------------------------------------------------+
|  [文章内容]                                        |
+--------------------------------------------------+
|  ┌──────────────────────────────────────────────┐ |
|  │ 📑 本文属于合集：我的Spring Boot教程            │ |
|  │                                               │ |
|  │ ← 上一篇：Spring Boot 入门                    │ |
|  │ → 下一篇：Spring Boot 数据访问                 │ |
|  └──────────────────────────────────────────────┘ |
+--------------------------------------------------+
|  [评论区]                                         |
+--------------------------------------------------+
```

### 5.6 文章编辑器适配

在 `PostEditorView.vue` 中，元数据面板新增合集的选项：

- 如果正在编辑合集内的文章，自动关联集合
- 新建文章时可通过下拉框选择目标合集

---

## 6. 文章导航功能设计

### 6.1 导航组件 `ArticleNav.vue`

新增组件 `mysite-frontend/src/components/article/ArticleNav.vue`：

```
┌──────────────────────────────────────────────────┐
│  ← 上一篇                             下一篇 →   │
│  Spring Boot 入门              Spring Boot 数据访问│
└──────────────────────────────────────────────────┘
```

### 6.2 导航逻辑

```
                       ┌──────────────┐
                       │  当前文章ID   │
                       └──────┬───────┘
                              │
                    ┌─────────▼─────────┐
                    │ 是否属于合集？     │
                    └────┬──────────┬───┘
                         │ 是       │ 否
                         ▼          ▼
              ┌──────────────┐  ┌──────────────┐
              │ 按合集内      │  │ 按发布时间    │
              │ sort_order    │  │ 倒序查找     │
              │ 查找前后文章   │  │ 前后文章     │
              └──────┬───────┘  └──────┬───────┘
                     │                 │
                     └────────┬────────┘
                              ▼
              ┌──────────────────────────┐
              │ 返回 prev / next 导航信息  │
              └──────────────────────────┘
```

### 6.3 性能优化

- 导航信息与文章详情接口分离，独立请求，避免阻塞主内容加载
- 使用 Redis 缓存导航信息（缓存时间 30 分钟），文章更新时清除缓存
- 合集内文章排序变更时，批量清除相关导航缓存

---

## 7. 搜索功能适配

### 7.1 搜索范围扩展

| 搜索目标 | 搜索字段 | 说明 |
|---------|---------|------|
| 合集标题 | `t_collection.title` | 全文搜索 |
| 合集描述 | `t_collection.description` | 全文搜索 |
| 合集内文章标题 | `t_article.title` | 通过关联表搜索 |

### 7.2 搜索结果标识

搜索结果中，属于合集的文章需要明确标识：

```json
{
  "id": "1",
  "title": "Spring Boot 入门",
  "collectionId": "123456789",
  "collectionTitle": "我的Spring Boot教程",
  "matchType": "article"  // "article" | "collection_title" | "collection_desc"
}
```

### 7.3 前端搜索结果展示

```
+--------------------------------------------------+
| 搜索结果：「Spring Boot」                          |
+--------------------------------------------------+
|  [合集] 我的Spring Boot教程                       |
|  从入门到精通的Spring Boot系列教程 · 5篇文章        |
+--------------------------------------------------+
|  [文章] Spring Boot 入门                          |
|  📑 来自合集：我的Spring Boot教程                  |
+--------------------------------------------------+
|  [文章] Spring Boot 实战经验分享                   |
+--------------------------------------------------+
```

---

## 8. 性能优化策略

### 8.1 数据库层面

| 优化项 | 策略 | 说明 |
|--------|------|------|
| 索引 | `t_collection_article` 添加 `(collection_id, sort_order)` 联合索引 | 加速合集内文章排序查询 |
| 索引 | `t_collection_article` 添加 `article_id` 索引 | 加速反向查询（文章属于哪个合集） |
| 批量查询 | 主页文章列表使用 JOIN 或子查询批量获取合集信息 | 避免 N+1 查询问题 |
| 计数器 | `t_collection.article_count` 使用数据库更新维护 | 避免每次 COUNT 查询 |

### 8.2 缓存层面

| 缓存项 | 缓存策略 | TTL | 清除时机 |
|--------|---------|-----|---------|
| 合集详情 | `@Cacheable("collection_detail")` | 30分钟 | 合集更新/删除时 |
| 合集文章列表 | `@Cacheable("collection_articles")` | 30分钟 | 文章增删/排序变更时 |
| 文章导航 | `@Cacheable("article_nav")` | 30分钟 | 文章更新/合集变更时 |
| 主页合集列表 | `@Cacheable("home_collections")` | 10分钟 | 合集增删改时 |

### 8.3 前端层面

| 优化项 | 策略 |
|--------|------|
| 导航信息 | 文章详情页加载后异步请求，不阻塞主要内容渲染 |
| 合集卡片 | 首屏可见合集使用优先加载，后续合集懒加载 |
| 骨架屏 | 合集列表加载时显示 SkeletonCard 组件 |
| 图片优化 | 合集封面使用 OptimizedImage 组件，支持 WebP 和懒加载 |

### 8.4 Elasticsearch 层面

| 优化项 | 策略 |
|--------|------|
| 索引字段 | 仅索引搜索相关字段（title, description），不索引全文内容 |
| 合集文档 | 合集单独建立索引或存入现有文章索引的扩展字段 |
| 刷新策略 | 合集变更时异步刷新索引，不阻塞主流程 |

---

## 9. 主题适配方案

### 9.1 现有主题列表

项目共有 8 个主题变体（6 个主题组）：

| 主题 | 属性值 | 类型 |
|------|--------|------|
| 默认 | (无 data-theme) | 浅色 |
| Classic Light | `data-theme="classic-light"` | 浅色 |
| Classic Dark | `data-theme="classic-dark"` | 深色 |
| Aurora | `data-theme="aurora"` | 深色 |
| Ocean Breeze | `data-theme="ocean-breeze"` | 浅色 |
| Rose Garden | `data-theme="rose-garden"` | 浅色 |
| Warm Sunset | `data-theme="warm-sunset"` | 浅色 |
| Liquid Glass | `data-theme="liquid-glass"` | 浅色 |
| Liquid Glass Dark | `data-theme="liquid-glass-dark"` | 深色 |

### 9.2 合集组件样式规范

合集相关组件统一使用项目现有的 CSS 变量体系，确保与所有主题兼容：

```css
/* 合集卡片容器 */
.collection-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-xl);
  transition: border-color var(--ease-smooth) 200ms, box-shadow var(--ease-smooth) 200ms;
}

.collection-card:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 12px var(--shadow-color-md);
}

/* 合集标识徽章 */
.collection-badge {
  background: var(--bg-accent);
  color: var(--accent);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  padding: 0.125rem 0.5rem;
}

/* 合集文章导航 */
.collection-nav {
  background: var(--bg-code);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 1rem;
}

.collection-nav a {
  color: var(--text-secondary);
  transition: color var(--ease-smooth) 200ms;
}

.collection-nav a:hover {
  color: var(--accent);
}
```

### 9.3 合集图标

使用 [Lucide](https://lucide.dev) 图标库（项目已使用）中的 `Library`、`BookOpen` 或 `Layers` 图标作为合集标识，与现有图标体系保持一致。

---

## 10. 实施步骤

### 第一阶段：数据层（预计 1 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 1.1 | 创建数据库迁移脚本 | `t_collection`、`t_collection_article` 建表 SQL |
| 1.2 | 创建实体类 | `CollectionDO.java`、`CollectionArticleDO.java` |
| 1.3 | 创建 Mapper 接口和 XML | `CollectionMapper.java`、`CollectionArticleMapper.java` |
| 1.4 | 创建 DTO 类 | 请求/响应 DTO 类 |

### 第二阶段：服务层（预计 1-2 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 2.1 | 实现 CollectionService | CRUD 操作、文章关联管理 |
| 2.2 | 实现文章导航逻辑 | `getArticleNavigation` 方法 |
| 2.3 | 适配 ArticleSearchService | 数据库和 ES 两种模式的搜索适配 |
| 2.4 | 实现缓存策略 | Spring Cache 注解配置 |

### 第三阶段：控制器层（预计 0.5 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 3.1 | 实现 CollectionController | REST API 端点 |
| 3.2 | 扩展现有 ArticleController | 导航端点、列表接口适配 |
| 3.3 | 编写 Swagger 文档 | API 文档注解 |

### 第四阶段：前端组件（预计 2 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 4.1 | 新增类型定义 | TypeScript 类型 |
| 4.2 | 新增 API 调用函数 | `collection.ts` API 模块 |
| 4.3 | 实现 CollectionCard 组件 | 合集卡片展示 |
| 4.4 | 实现 ArticleNav 组件 | 文章导航条 |
| 4.5 | 实现 CollectionView 页面 | 合集详情展示页 |
| 4.6 | 实现 CollectionManageView 页面 | 合集管理列表 |
| 4.7 | 实现 CollectionEditView 页面 | 合集编辑（含拖拽排序） |
| 4.8 | 适配 BlogHome 页面 | 主页合集展示 |
| 4.9 | 适配 BlogPost 页面 | 文章详情导航 |
| 4.10 | 适配 PostEditorView | 编辑器合集选择 |
| 4.11 | 适配 SearchView | 搜索结果合集标识 |

### 第五阶段：测试与优化（预计 1-2 天）

| 步骤 | 任务 | 产出 |
|------|------|------|
| 5.1 | 单元测试 | 服务层测试用例 |
| 5.2 | 集成测试 | API 端点测试 |
| 5.3 | 前端测试 | 组件渲染和交互验证 |
| 5.4 | 性能测试 | 页面加载速度、缓存效果验证 |
| 5.5 | 主题兼容性测试 | 六个主题下的视觉一致性验证 |

---

## 11. 测试计划

### 11.1 单元测试

| 测试类 | 测试内容 |
|--------|---------|
| `CollectionServiceImplTest` | 合集 CRUD、文章添加/移除、排序调整 |
| `ArticleNavigationTest` | 导航逻辑：合集内导航、非合集导航、边界情况 |
| `CollectionArticleSearchTest` | 搜索适配：合集搜索、文章搜索中的合集标识 |

测试用例覆盖：

```
合集管理测试:
  ✓ 创建合集 - 正常创建、标题为空、重复标题
  ✓ 更新合集 - 正常更新、合集不存在
  ✓ 删除合集 - 正常删除、级联删除关联记录
  ✓ 添加文章 - 正常添加、重复添加、文章不存在
  ✓ 移除文章 - 正常移除、文章不在合集中
  ✓ 批量添加 - 正常添加、部分失败回滚
  ✓ 排序调整 - 正常排序、排序后顺序验证

导航测试:
  ✓ 合集内第一篇文章 - prev 为 null
  ✓ 合集内最后一篇文章 - next 为 null
  ✓ 合集内中间文章 - 正确返回前后篇
  ✓ 非合集文章 - 按时间顺序返回
  ✓ 文章不属于任何合集 - 时间排序导航

搜索测试:
  ✓ 搜索合集标题 - 返回合集及其中文章
  ✓ 搜索合集描述 - 返回合集
  ✓ 搜索合集内文章 - 返回文章并标记合集
  ✓ 搜索结果中合集标识正确
```

### 11.2 前端测试

| 测试项 | 验证点 |
|--------|--------|
| 合集卡片渲染 | 标题、描述、文章数量、封面图正确显示 |
| 合集详情页 | 文章列表排序正确、可点击跳转 |
| 文章导航条 | 上一篇/下一篇标题正确、链接正确 |
| 主页合集展示 | 合集与普通文章区分明显、排序正确 |
| 拖拽排序 | 拖拽交互流畅、排序结果正确保存 |
| 主题兼容 | 8 个主题变体下合集组件样式正确 |
| 响应式布局 | 移动端/平板/桌面端下合集展示正常 |

### 11.3 性能测试

| 指标 | 目标 | 测试方法 |
|------|------|---------|
| 主页加载时间 | 合集功能新增后增量 < 100ms | 对比添加前后 TTFB |
| 文章详情页导航加载 | < 200ms | 独立测量导航 API 响应时间 |
| 合集详情页加载 | 含 20 篇文章 < 500ms | 模拟大数据量合集 |
| 缓存命中率 | > 80% | 监控 Redis 缓存命中统计 |

---

## 12. 风险与注意事项

### 12.1 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 现有文章列表接口改动影响范围大 | 可能影响现有功能 | 保持向后兼容，新增字段使用可选模式 |
| 数据库索引影响写入性能 | 文章创建变慢 | 仅在关联表添加必要索引，避免过多索引 |
| Elasticsearch 索引同步延迟 | 搜索结果不一致 | 合集变更后异步刷新 ES 索引 |
| 大合集性能问题 | 含上百篇文章的合集查询慢 | 分页加载 + 缓存策略 |

### 12.2 兼容性注意事项

- **接口向后兼容**：现有 `GET /v1/articles` 接口新增字段为可选，不影响现有调用方
- **数据库向后兼容**：新增表不影响现有表结构和数据
- **前端组件向后兼容**：合集组件为独立组件，不影响现有页面
- **主题兼容**：所有新组件使用 CSS 变量，不硬编码颜色值

### 12.3 交互注意事项

- **删除合集确认**：删除合集前弹出确认对话框，明确告知"删除合集不会删除文章"
- **排序保存提示**：排序变更后显示保存成功提示
- **空状态处理**：合集为空时显示引导提示
- **加载状态**：合集列表、导航信息加载时显示骨架屏或加载动画
- **错误处理**：API 请求失败时显示友好的错误提示

---

## 附录 A：项目文件变更清单

### 后端新增文件

```
src/main/java/io/github/somehow/mysite/
├── controller/
│   └── CollectionController.java              # 合集管理控制器
├── dao/
│   ├── entity/
│   │   ├── CollectionDO.java                  # 合集实体
│   │   └── CollectionArticleDO.java           # 合集-文章关联实体
│   └── mapper/
│       ├── CollectionMapper.java              # 合集 Mapper
│       └── CollectionArticleMapper.java       # 合集-文章关联 Mapper
├── dto/
│   ├── req/
│   │   └── collection/
│   │       ├── CollectionCreateReqDTO.java
│   │       ├── CollectionUpdateReqDTO.java
│   │       ├── CollectionPageQueryReqDTO.java
│   │       └── CollectionArticleSortReqDTO.java
│   └── resp/
│       ├── CollectionPageQueryRespDTO.java
│       ├── CollectionDetailRespDTO.java
│       └── ArticleNavInfoRespDTO.java
├── service/
│   ├── CollectionService.java                 # 合集服务接口
│   └── impl/
│       └── CollectionServiceImpl.java         # 合集服务实现
└── resources/
    └── mapper/
        ├── CollectionMapper.xml
        └── CollectionArticleMapper.xml
```

### 后端修改文件

```
src/main/java/io/github/somehow/mysite/
├── controller/
│   └── ArticleController.java                 # 新增导航端点
├── service/
│   ├── ArticleService.java                    # 新增集合相关方法
│   ├── ArticleSearchService.java              # 适配搜索
│   └── impl/
│       ├── ArticleServiceImpl.java            # 适配列表和导航
│       ├── DatabaseArticleSearchServiceImpl.java
│       └── ElasticsearchArticleSearchServiceImpl.java
├── elasticsearch/
│   └── ArticleDocument.java                   # 新增合集字段
└── dto/resp/
    └── ArticlePageQueryRespDTO.java           # 新增合集字段
```

### 前端新增文件

```
mysite-frontend/src/
├── api/
│   └── collection.ts                          # 合集 API 调用
├── components/
│   └── article/
│       ├── CollectionCard.vue                 # 合集卡片组件
│       └── ArticleNav.vue                     # 文章导航组件
├── composables/
│   └── useCollection.ts                       # 合集相关逻辑
└── views/
    ├── CollectionView.vue                     # 合集详情页
    ├── CollectionManageView.vue               # 合集管理列表
    └── CollectionEditView.vue                 # 合集编辑页
```

### 前端修改文件

```
mysite-frontend/src/
├── types/index.ts                             # 新增合集类型定义
├── api/article.ts                             # 新增导航 API
├── app/router/index.ts                        # 新增合集路由
├── views/
│   ├── BlogHome.vue                           # 主页合集展示
│   ├── BlogPost.vue                           # 文章导航
│   ├── PostEditorView.vue                     # 合集选择
│   └── SearchView.vue                         # 搜索结果合集标识
├── components/
│   └── article/
│       └── ArticleList.vue                    # 支持合集卡片渲染
```

### 数据库变更

```sql
-- 新增表
CREATE TABLE t_collection (...);
CREATE TABLE t_collection_article (...);
```

---

## 附录 B：关键交互流程

### B.1 创建合集并添加文章

```
用户 → 点击"创建合集" → 填写标题/描述 → 保存
     → 进入合集编辑页 → 点击"添加已有文章"
     → 弹出文章选择器 → 搜索/筛选文章 → 勾选文章 → 确认添加
     → 拖拽调整顺序 → 点击"保存"
```

### B.2 在合集中新建文章

```
用户 → 合集编辑页 → 点击"新建文章"
     → 跳转文章编辑器（自动关联当前合集）
     → 编写文章 → 发布
     → 自动添加到合集中
```

### B.3 读者浏览合集文章

```
读者 → 主页看到合集卡片 → 点击进入合集详情页
     → 浏览合集目录 → 点击某篇文章
     → 文章详情页底部显示合集导航
     → 点击"下一篇"继续阅读
```

---

> **文档维护者**: AI Assistant  
> **下次评审时间**: 待定  
> **变更记录**: 初始版本