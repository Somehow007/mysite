# Elasticsearch 可插拔架构（实现总结 + 配置说明，已合并）

> 由 `Elasticsearch可插拔架构实现总结.md` 与 `Elasticsearch可插拔配置说明.md` 合并而成（2026-11 文档整理）。
> 当前 `application.yaml` 默认 `elasticsearch.enabled: false`，搜索走数据库 LIKE 降级。

## 概述

通过配置文件动态控制 ES 启用/禁用，禁用时自动降级到数据库搜索，业务代码零改动。

## 架构

```
ArticleServiceImpl (业务层)
        │ 依赖注入
        ▼
ArticleSearchService / UserSyncService (接口)
        │
   ┌────┴─────────────────────────┐
   ▼                              ▼
ElasticsearchArticleSearch   DatabaseArticleSearch
ServiceImpl (ES实现)          ServiceImpl (DB实现)
   ↑                              ↑
   └──── @ConditionalOnProperty ──┘
   elasticsearch.enabled=true/false
```

- `ElasticsearchConfiguration`：`@ConditionalOnProperty(enabled=true, matchIfMissing=true)`，仅启用时加载 ES Repositories
- ES 实现类：`havingValue="true"`；DB 实现类：`havingValue="false"`
- `ElasticsearchDataInitializer` 仅启用时加载，负责索引与数据初始化
- 用户侧另有 `UserIndexService`（`elasticsearch/service/`），同样 ES/DB 双实现

## 配置

```yaml
elasticsearch:
  enabled: false                   # 总开关（当前默认 false）
  uris:
    - http://localhost:9200
  connection-timeout: 5000         # 连接超时 ms
  socket-timeout: 30000            # Socket 超时 ms
```

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `elasticsearch.enabled` | Boolean | true（yaml 中显式设为 false） | ES 功能总开关 |
| `elasticsearch.uris` | List | `[http://localhost:9200]` | ES 地址列表 |
| `elasticsearch.connection-timeout` | Integer | 5000 | 连接超时 ms |
| `elasticsearch.socket-timeout` | Integer | 30000 | Socket 超时 ms |

### 启用 / 禁用

```yaml
elasticsearch:
  enabled: true    # 启用 ES
# enabled: false   # 禁用，降级数据库搜索
```

## 日志特征

启用时：
```
========================================
Elasticsearch 已启用
ES Repositories 已加载
========================================
开始 Elasticsearch 数据初始化检查...
[文章数据检查] ES索引文档数: 10
```

禁用时：
```
[数据库模式] Elasticsearch已禁用，跳过数据同步
[数据库搜索] 开始搜索文章，参数: keyword=test, searchType=title
```

## 功能对比

| 功能 | ES 实现 | 数据库实现 |
|------|---------|-----------|
| 标题/内容搜索 | 全文索引 | LIKE 查询 |
| 组合搜索 | 复杂查询 | 多表 JOIN |
| 相关性排序 | 支持 | 不支持 |
| 中文分词 | IK 分词器 | 不支持 |
| 数据同步 | 创建/更新/删除同步索引 | 跳过 |

性能量级（文档作者实测，供参考）：100 篇 ~10ms vs ~50ms；万级 ~20ms vs ~1500ms。

## 何时启用 / 禁用

- **启用**：文章量 >1000、需要全文搜索/相关性排序/中文分词、服务器内存 ≥2GB
- **禁用**：开发测试环境、文章量 <100、资源有限服务器（省 ~512MB-1GB）

## 迁移

- **DB → ES**：部署 ES（`docker run -d -p 9200:9200 -e "discovery.type=single-node" elasticsearch:7.17.21`）→ `enabled: true` → 重启，自动建索引并同步数据
- **ES → DB**：`enabled: false` → 重启，ES Bean 不加载，自动切换；可选停 ES 服务

## 故障排查

| 症状 | 处理 |
|------|------|
| `Connection refused ...:9200` | 确认 ES 已启动、端口、防火墙 |
| ES 与 DB 搜索结果不一致 | 执行数据同步，检查索引配置 |
| `No qualifying bean of type 'ArticleSearchService'` | 检查 `elasticsearch.enabled` 配置值，重新编译 |

## 文件清单（当前代码）

| 文件 | 说明 |
|------|------|
| `config/ElasticsearchProperties.java` | `@ConfigurationProperties("elasticsearch")` |
| `config/ElasticsearchConfiguration.java` | 条件装配 ES Repositories |
| `service/ArticleSearchService.java`、`service/UserSyncService.java` | 服务接口 |
| `service/impl/Elasticsearch*Impl.java` / `Database*Impl.java` | ES / DB 双实现 |
| `elasticsearch/` | `ArticleDocument`、`UserDocument`、`repository/`、`service/`（UserIndexService 双实现）、`ElasticsearchDataInitializer` |
| `src/test/.../elasticsearch/` | 启用/禁用两个集成测试 |

## 相关文档

- [Elasticsearch 7.17 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/7.17/index.html)
- [Spring Data Elasticsearch](https://docs.spring.io/spring-data/elasticsearch/docs/current/reference/html/)
- [IK 中文分词器](https://github.com/medcl/elasticsearch-analysis-ik)
- 本项目 ES 相关代码均在 `src/main/java/io/github/somehow/mysite/elasticsearch/` 与 `service/impl/` 双实现中
