# Elasticsearch 可插拔配置说明

## 概述

本项目实现了 Elasticsearch (ES) 的可插拔架构，允许通过配置文件动态控制 ES 的启用/禁用状态。当 ES 禁用时，系统会自动降级到数据库搜索方案，确保系统平稳运行。

## 配置方式

### 配置项说明

在 `application.yaml` 中添加以下配置：

```yaml
elasticsearch:
  enabled: true                    # ES启用开关，默认为true
  uris:                            # ES服务器地址列表
    - http://localhost:9200
  connection-timeout: 5000         # 连接超时时间（毫秒）
  socket-timeout: 30000            # Socket超时时间（毫秒）
```

### 配置项详解

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `elasticsearch.enabled` | Boolean | true | ES功能总开关 |
| `elasticsearch.uris` | List<String> | ["http://localhost:9200"] | ES服务器地址列表 |
| `elasticsearch.connection-timeout` | Integer | 5000 | 连接超时时间（毫秒） |
| `elasticsearch.socket-timeout` | Integer | 30000 | Socket超时时间（毫秒） |

## 使用场景

### 场景一：启用 Elasticsearch（推荐）

```yaml
elasticsearch:
  enabled: true
  uris:
    - http://localhost:9200
```

**特点**：
- ✅ 高性能全文搜索
- ✅ 支持复杂查询
- ✅ 搜索结果相关性排序
- ✅ 支持中文分词（IK分词器）

**适用环境**：
- 生产环境
- 需要高性能搜索的场景
- 文章数量较多的博客系统

### 场景二：禁用 Elasticsearch

```yaml
elasticsearch:
  enabled: false
```

**特点**：
- ✅ 无需部署ES服务
- ✅ 降低系统资源占用
- ✅ 简化部署流程
- ⚠️ 搜索性能相对较低
- ⚠️ 搜索功能受限

**适用环境**：
- 开发测试环境
- 资源有限的服务器
- 文章数量较少的场景
- 快速部署原型系统

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────┐
│                    ArticleServiceImpl                    │
│                    (业务逻辑层)                          │
└────────────────────┬────────────────────────────────────┘
                     │ 依赖注入
                     ↓
┌─────────────────────────────────────────────────────────┐
│              ArticleSearchService (接口)                 │
└────────────┬───────────────────────┬────────────────────┘
             │                       │
             ↓                       ↓
┌──────────────────────┐  ┌──────────────────────────────┐
│ ElasticsearchArticle │  │ DatabaseArticleSearchService │
│ SearchServiceImpl    │  │ Impl                         │
│ (ES实现)             │  │ (数据库实现)                 │
└──────────────────────┘  └──────────────────────────────┘
        ↑                           ↑
        │                           │
        └───────────┬───────────────┘
                    │
          @ConditionalOnProperty
          (条件装配)
```

### 条件装配机制

系统使用 Spring Boot 的条件装配注解实现自动切换：

1. **ES启用时**：
   ```java
   @ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", 
                          havingValue = "true", matchIfMissing = true)
   ```
   - 加载 `ElasticsearchConfiguration`
   - 加载 `ElasticsearchArticleSearchServiceImpl`
   - 加载 `ElasticsearchUserSyncServiceImpl`
   - 加载 `ElasticsearchDataInitializer`

2. **ES禁用时**：
   ```java
   @ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", 
                          havingValue = "false")
   ```
   - 不加载ES相关配置
   - 加载 `DatabaseArticleSearchServiceImpl`
   - 加载 `DatabaseUserSyncServiceImpl`

## 功能对比

### 搜索功能对比

| 功能 | ES实现 | 数据库实现 |
|------|--------|-----------|
| 标题搜索 | ✅ 全文索引 | ✅ LIKE查询 |
| 内容搜索 | ✅ 全文索引 | ✅ LIKE查询 |
| 作者搜索 | ✅ 索引查询 | ✅ 关联查询 |
| 分类筛选 | ✅ 索引查询 | ✅ WHERE条件 |
| 标签筛选 | ✅ 索引查询 | ✅ 关联查询 |
| 组合搜索 | ✅ 复杂查询 | ⚠️ 多表JOIN |
| 搜索性能 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 相关性排序 | ✅ 支持 | ❌ 不支持 |
| 中文分词 | ✅ IK分词 | ❌ 不支持 |

### 数据同步

| 操作 | ES启用 | ES禁用 |
|------|--------|--------|
| 创建文章 | ✅ 同步到ES | ⚠️ 跳过同步 |
| 更新文章 | ✅ 更新ES索引 | ⚠️ 跳过更新 |
| 删除文章 | ✅ 删除ES索引 | ⚠️ 跳过删除 |
| 批量同步 | ✅ 支持 | ⚠️ 跳过 |

## 日志说明

### 启用ES时的日志

```
========================================
Elasticsearch 已启用
ES Repositories 已加载
========================================
开始 Elasticsearch 数据初始化检查...
ES 状态: 已启用
========================================
[文章数据检查] ES索引文档数: 10
[文章数据检查] ES索引已存在数据，跳过同步
[用户数据检查] 开始检查用户数据...
[ES同步] 开始同步所有用户到Elasticsearch...
[ES同步] 成功同步 5 个用户到Elasticsearch
========================================
Elasticsearch 数据初始化完成
========================================
```

### 禁用ES时的日志

```
[数据库模式] Elasticsearch已禁用，跳过数据同步
[数据库搜索] 开始搜索文章，参数: keyword=test, searchType=title
[数据库搜索] 搜索完成，返回 5 条记录
```

## 性能建议

### 何时启用ES

1. **文章数量 > 1000篇**
   - ES提供更好的搜索性能
   - 支持复杂查询和聚合

2. **需要高级搜索功能**
   - 全文搜索
   - 相关性排序
   - 中文分词

3. **服务器资源充足**
   - 至少2GB内存
   - 建议独立部署ES服务

### 何时禁用ES

1. **文章数量 < 100篇**
   - 数据库LIKE查询足够快
   - 无需额外资源

2. **开发测试环境**
   - 简化环境配置
   - 加快启动速度

3. **资源有限的服务器**
   - 节省内存（约512MB-1GB）
   - 降低系统复杂度

## 迁移指南

### 从数据库搜索迁移到ES

1. **部署ES服务**
   ```bash
   # Docker方式
   docker run -d --name elasticsearch \
     -p 9200:9200 \
     -e "discovery.type=single-node" \
     elasticsearch:7.17.21
   ```

2. **修改配置**
   ```yaml
   elasticsearch:
     enabled: true
     uris:
       - http://localhost:9200
   ```

3. **重启应用**
   - 系统自动初始化ES索引
   - 自动同步数据库数据到ES

### 从ES迁移到数据库搜索

1. **修改配置**
   ```yaml
   elasticsearch:
     enabled: false
   ```

2. **重启应用**
   - ES相关Bean不会被加载
   - 自动切换到数据库搜索

3. **可选：停止ES服务**
   ```bash
   docker stop elasticsearch
   ```

## 故障排查

### 问题1：ES启用后无法连接

**症状**：
```
Connection refused: localhost/127.0.0.1:9200
```

**解决方案**：
1. 检查ES服务是否启动
2. 检查端口是否正确
3. 检查防火墙配置

### 问题2：搜索结果不一致

**症状**：ES搜索和数据库搜索结果不同

**解决方案**：
1. 执行数据同步
   ```bash
   curl -X POST http://localhost:8081/api/admin/sync/es
   ```
2. 检查ES索引配置

### 问题3：启动时报错找不到ArticleSearchService Bean

**症状**：
```
No qualifying bean of type 'ArticleSearchService'
```

**解决方案**：
1. 检查配置文件中是否有 `elasticsearch.enabled` 配置
2. 确保配置值是 `true` 或 `false`
3. 清理并重新编译项目

## 最佳实践

1. **开发环境**：禁用ES，简化开发
2. **测试环境**：启用ES，验证功能
3. **生产环境**：启用ES，保证性能
4. **资源监控**：定期检查ES内存使用
5. **数据备份**：定期备份ES索引数据

## 相关文档

- [Elasticsearch官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/7.17/index.html)
- [Spring Data Elasticsearch](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/)
- [IK中文分词器](https://github.com/medcl/elasticsearch-analysis-ik)
