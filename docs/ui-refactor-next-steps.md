# mysite UI 重构 — 后续优化计划

> 本文件跟踪 M1–M4 完成后剩余的后端依赖项与后置优化项，按优先级分阶段执行。

## 当前完成度

> 更新于 2025-07-25：N1、N2 阶段全部完成 ✅

设计文档 28 个核心问题：**28 个全部完成。**

| 阶段 | 状态 | 说明 |
|------|------|------|
| M1–M4 | ✅ | 设计系统 + 管理后台 + RAG 模块 + 个人主页 |
| N1 | ✅ | 后端配合项（分页/分块预览/分块模式/上传/个人主页） |
| N2 | ✅ | 纯前端优化（浮层定位/Dashboard CSS/组件迁移/图片增强） |
| 视觉对齐 | ✅ | 侧边栏 mockup 对齐、统计卡片、KB 卡片选中标记 |

---

## 阶段 N1：后端配合项 ✅ 已完成（2025-07-25）

### N1.1 知识库文档服务端分页（R1）

| 项目 | 说明 |
|------|------|
| 前端状态 | `usePagedList` + `DataTable` 组件已就绪 |
| 需要后端 | `GET /v1/rag/knowledge-bases/{kbId}/docs?page=&size=&status=&keyword=&sort=&order=` 支持分页参数 |
| 前端改动 | `KnowledgeManageView` 中 `fetchDocs()` 切换为 `usePagedList`，`docs` 变为响应式分页数据，`pagedDocs` 计算属性可删除 |

### N1.2 分块预览（E3）

| 项目 | 说明 |
|------|------|
| 前端状态 | 已在 `KnowledgeManageView` 文档行预留 `expandedDocId` + 行展开机制 |
| 需要后端 | `GET /v1/rag/knowledge-bases/{kbId}/docs/{docId}/chunks?page=&size=` — 返回 `{ chunks: [{ seq, content, charCount, tokenEstimate }], total }` |
| 前端改动 | 在展开行内渲染切片列表（序号、内容截断、字符数、token 估算），使用 `pagination` 分页 |

### N1.3 分块模式切换（E3 后半）

| 项目 | 说明 |
|------|------|
| 前端状态 | 「分块设置」Tab 已展示当前配置（只读） |
| 需要后端 | `KnowledgeBase` 实体增加 `chunkingMode` 字段枚举：`FIXED_SIZE` / `STRUCTURE_AWARE`；更新 `PUT /v1/rag/knowledge-bases/{id}` 接受此字段 |
| 前端改动 | Tab 内添加 `chunkingMode` 选择器 + 保存按钮，参数带单位 + 推荐值 + 人话说明 |

### N1.4 文件上传（E6 补充）

| 项目 | 说明 |
|------|------|
| 前端状态 | ImageManagerView 已有上传按钮（会提示"功能即将上线"） |
| 需要后端 | `POST /v1/images/upload` multipart 上传；`POST /v1/rag/knowledge-bases/{kbId}/docs/upload` Markdown 文件上传 |
| 前端改动 | ImageManagerView 改为实际调 API + 进度条；KnowledgeManageView「添加文章」Tab 旁新增「上传文档」拖拽区 |

### N1.5 公开个人主页数据接入

| 项目 | 说明 |
|------|------|
| 前端状态 | `UserProfileView.vue` 已创建，含完整的资料卡/统计/Tab UI |
| 需要后端 | `GET /v1/users/{username}/profile` — 返回 `UserProfile`（含 stats）；`GET /v1/users/{username}/articles?page=&size=` |
| 前端改动 | `fetchProfile()` 已调用 API，后端就绪后数据即自动展示 |

---

## 阶段 N2：后置优化 ✅ 已完成（2025-07-25）

### N2.1 DashboardView scoped CSS 收敛（A4）

| 项目 | 说明 |
|------|------|
| 优先级 | 中 |
| 当前状态 | `DashboardView.vue` 仍有 ~580 行 `<style scoped>` CSS |
| 目标 | 逐类替换为 Tailwind 工具类，CSS 收敛到 `<style scoped>` 最多 50 行 |
| 关键项 | `.filter-bar`、`.batch-bar`、`.table-wrap`、`.empty-icon`、`.fade-enter` 等 → Tailwind |
| 风险 | 改动分散，需逐类对照替换，建议每次替换 100 行 + 构建验证 |

### N2.2 浮层定位替换（R4）

| 项目 | 说明 |
|------|------|
| 优先级 | 中 |
| 当前状态 | `AppHeader.vue:46-53` 使用 `getBoundingClientRect` 手动定位用户菜单；`ThemeToggle.vue` 有类似实现 |
| 目标 | 引入 `@floating-ui/vue`（零依赖、轻量），替换手写 rect 计算 |
| 改动 | `npm install @floating-ui/vue`；在 `AppHeader` 中使用 `useFloating` composable 自动定位 + 视口翻转 |
| 组件 | 可抽象为 `Dropdown.vue` 组件（P2 优先级）供全局复用 |

### N2.3 统计卡片（§5.3）

| 项目 | 说明 |
|------|------|
| 优先级 | 低 |
| 目标 | 用户管理、评论管理、文章管理页面顶部添加统计行（总数/今日新增/异常数） |
| 实现 | 复用 `DataTable` 的 `total` prop + 可选聚合接口；创建 `StatsCard` 小组件 |

### N2.4 图片管理增强

| 项目 | 说明 |
|------|------|
| 优先级 | 低 |
| 已完成 | 上传按钮 UI + 灯箱预览（已在本轮优化中添加） |
| 待完成 | 批量选择删除（已有点击复选框 → 批量删除）；拖拽上传区（模态框形式） |

### N2.5 推荐问题可配置化

| 项目 | 说明 |
|------|------|
| 优先级 | 低 |
| 已完成 | `SUGGESTED_QUESTIONS` 数组已添加 `v-if` 守卫，清空数组则不渲染 |
| 待完成 | 从站点设置或知识库配置中获取推荐问题列表（需要后端配置接口） |

### N2.6 输入区剩余额度显示

| 项目 | 说明 |
|------|------|
| 优先级 | 低 |
| 目标 | ChatInput 底部显示「剩余 N 次/小时」 |
| 实现 | 需要后端在 SSE meta 事件中返回 `rateLimit` 对象 `{ remaining, limit, resetTime }`；前端缓存并显示 |

### N2.7 各管理页迁移到共享组件

以下页面可逐步迁移到 `DataTable` + `SearchFilterBar` + `Pagination`：

| 页面 | 当前实现 | 迁移目标 |
|------|----------|----------|
| 仪表盘/文章 | ✅ 已最优，保留 | 筛选栏 → `SearchFilterBar` |
| 用户管理 | 自定义 table | → `DataTable` |
| 评论管理 | 卡片列表 | → `DataTable` mobile card mode |
| 标签管理 | 自定义 table | → `DataTable` |
| 合集管理 | 卡片列表 | 已较好，保留 |
| 图片管理 | 卡片网格 | 已较好，保留 |

---

## 阶段 N3：长期项目

### N3.1 Dashboard scoped CSS 完全消除
目标：`DashboardView.vue` 标签为 `<style scoped>` 的空文件（或仅保留 1-2 个动画关键帧）。

### N3.2 ESLint 规则落地
添加 `no-restricted-syntax` 拦截 `alert()` / `confirm()` 调用。（当前全站已无，添加规则防回归）

### N3.3 组件文档 / Storybook
`components/ui/` 下 11 个组件编写使用文档或接入 Storybook。

### N3.4 移动端全站适配审查
逐个页面过桌面/平板/手机三档断点，确保无横向溢出。

---

## 执行策略

每个阶段内部的任务相互独立，可自由组合执行。建议：

1. **N1 优先**：联系后端补充 API，前端改动小、收效大
2. **N2 穿插**：后端等待期可先做 N2.1（Dashboard CSS）和 N2.2（浮层定位）
3. **N3 顺带**：做其他任务时顺手治理（如迁移页面时同步接入 ESLint）
