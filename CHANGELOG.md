# Changelog

本项目所有值得注意的变更都记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)（Added / Changed / Fixed 分类）。
项目尚未引入 SemVer 发布节奏，条目按日期组织；架构与设计决策的"为什么"另见 [`docs/DESIGN.md`](docs/DESIGN.md)。

## [2026-07-29]

### Added

- **合集可见性功能（手动隐藏）**：合集新增 `visibility` 字段（0=公开，1=私有），语义与文章一致——私有合集仅作者本人和管理员可见。
  - 后端：`t_collection` 增加 `visibility` 列；创建/更新合集接口支持设置 `visibility`（校验 0/1）；无权访问私有合集详情时统一返回 `COLLECTION_NOT_FOUND`（不暴露存在性）。
  - 前端：新建/编辑合集页增加「公开 / 仅自己可见」切换；合集管理页增加快速公开/隐藏按钮（仅作者与管理员可见）与「私有」徽章；合集详情页对私有合集展示提示横幅。
- **空合集自动隐藏**：合集列表中，对访问者可见文章数为 0 的合集自动对其隐藏（所有者始终可见自己的合集以便管理；管理员不受任何过滤影响）。
- 合集可见性单元测试扩充至 11 个用例（`CollectionServiceVisibilityTest`），覆盖 游客 / 其他用户 / 作者 / 管理员 × 详情 / 导航 / 可见性判定场景。
- 存量数据库迁移脚本 `docker/init/migrations/20260729-collection-visibility.sql`（全新数据库由 `schema.sql` 自动包含该字段）。

### Changed

- **合集文章计数动态化**：合集列表的 `articleCount` 与 `totalViewCount` 改为按「访问者可见的文章」动态统计（他人私有文章不计入），与合集详情页保持一致，消除"列表显示 7 篇、进去可见 0 篇"的不一致；按文章数排序同样改用动态计数。
- **私有合集信息防泄露打通**：文章的上/下一篇导航遇到私有合集时回退为时间线导航；文章详情页的合集归属信息按请求者实时校验后抹除（文章详情缓存跨用户共享，不能在缓存内做按用户过滤）。
- `home_collections`、`collection_detail`、`article_nav` 三个缓存的键加入访问者身份（userId + isAdmin），防止不同访问者的视图经缓存互相污染。

### Fixed

- **私有文章在合集中对游客可见的漏洞**（commit `81e0175`）：`getCollectionDetail` 查询合集文章时只过滤 `delFlag` 而未检查文章 `visibility`，设为"仅自己可见"的文章会原样展示给游客和其他用户；合集上/下一篇导航同样会泄露私有文章标题。两处均已按"作者 + 管理员可见"规则过滤。
