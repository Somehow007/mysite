# UI 重构 T12：404 + About

> 总计划：`.trellis/tasks/11-15-ui-concepts/prd.md` T12 节。概念稿无 404/About 专稿，
> 按纸本 token 与现有组件语言（HomeLead 编号列表 / HomeSidebar 作者卡 / post.css 版式）实现。

## 范围

1. **404**：路由 catch-all + `NotFoundView` + 共享 `NotFoundPanel`；`BlogPost` 文章不存在错误态复用同一面板（不改 URL）。
2. **About**：重写 `AboutView`——作者卡（头像/名字/tagline/bio/链接）+ `aboutContent` markdown 正文（`.prose`）+ `techStack` 一句话。

## §2.5 配置变更方案（先列后写）

`SiteProperties` 新增两个可选字段，均由 `GET /v1/site/config` 原样序列化：

| yaml 键 | Java 字段 | env | 说明 |
|---|---|---|---|
| `site.about-content` | `aboutContent` | `${SITE_ABOUT_CONTENT:}`（占位与其他标量一致；多行 markdown 建议走 yaml 覆盖，env 单行值也可用） | About 页长文，markdown，经 `useMarkdown` → `.prose` 渲染 |
| `site.tech-stack` | `techStack` | `${SITE_TECH_STACK:}` | 技术栈一句话，如 `Vue 3 · Spring Boot · MySQL · pgvector` |

- `authorBio` 保持短简介语义（HomeSidebar / 作者卡沿用），不承担长文。
- `deploy/config/.env.example` 补 `SITE_TECH_STACK=` 占位；`about-content` 无 env 占位，在 prd 记录 yaml 写法。
- 前端 `SiteConfig` 类型加 `aboutContent?: string`、`techStack?: string`（可选，兼容旧响应）。

## 前端改动点

- `api/client.ts`：业务错误（`data.code !== '0'`）reject 的 Error 上**附加** `code` 属性（纯增量，现有消费者只读 `message`，已 grep 确认无 `.code` 读取方）。`BlogPost` 据此区分 `A030100`（ARTICLE_NOT_FOUND）与其他错误。
- `components/common/NotFoundPanel.vue`（新）：kicker + 大号衬线 404 + 文案 + [回首页][搜索一下] + 「最受欢迎」3 篇（`getArticles sortField=viewCount desc size=3`，编号列表样式对齐 HomeLead）。搜索入口走 `useSearch().open()`（SearchDialog 挂在 AppHeader，全局可用）。
- `views/NotFoundView.vue`（新）：`useHead` title「页面不存在」+ `robots noindex`；渲染 NotFoundPanel。
- `router/index.ts`：DefaultLayout children 末尾加 `{ path: ':pathMatch(.*)*', name: 'not-found' }`。静态路由优先级更高，`/dashboard/*` 不受影响；`/dashboard/不存在的路径` 会落到前台 404（可接受，停留在站内）。
- `BlogPost.vue`：`error` 改 `errorKind: 'notfound' | 'error' | null`；`A030100` 或非数字 id（snowflake 必为数字，短路不发请求）→ notfound；其他 → 保留现有错误块。notfound 渲染 `<NotFoundPanel />`，URL 不变。
- `AboutView.vue`：重写，删死 class `prose-custom`。

## 验收

- [x] `./scripts/verify.sh` quick 绿；`npm run lint && npm run build` 过（lint 15 基线 0 新增）
- [x] Playwright 1440×900 / 390×844：不存在路径显示 404 且不跳出站；`/post/<不存在id>` 同款面板；About 随配置渲染 → `scripts/t12-verify.mjs` **46/46**
- [x] paper-light / paper-dark / classic-light 三主题无掉色，横向溢出 = 0（ui-shots 24 张全 0）

## Resolution

- **404**：`NotFoundPanel`（kicker + clamp(72–150px) 衬线 404 + 文案 + 回首页/搜索按钮 + 最受欢迎 3 篇编号列表）由 `NotFoundView`（catch-all 路由 `:pathMatch(.*)*`，带 `robots noindex`）与 `BlogPost` notfound 错误态共用。`client.ts` 业务错误附加 `code`（纯增量），BlogPost 凭 `A030100` 区分 notfound/error，非数字 id 短路不发请求。
- **About**：页头（About kicker + 衬线「关于」+ description）+ 作者卡（paper-card--strong 硬阴影，头像/名/tagline/bio + GitHub/RSS/技术栈行）+ `aboutContent` markdown 正文复用 `ArticleContent`（含代码块增强/图片优化/表格包裹，与文章页完全一致）。
- **顺手修存量 bug**：`useMarkdown.protectMath` 恢复 inline code 占位符时丢反引号 → 文章管线行内代码恒为纯文本（T11 在 useChatMarkdown 修过的同款 bug，文章侧漏网）。修复后真实文章验证 `<code>` 正常渲染、fenced code 不受影响。
- **配置**：`site.about-content` / `site.tech-stack` 两个可选字段（SiteProperties + yaml `${SITE_*:}` + .env.example 占位），前端 SiteConfig 同步可选字段。
- **并行提示**：工作树存在另一会话的 T10 在途改动（`usePendingAsk.ts`、`useCommentDraft.ts`、`11-16-ui-T10-selection/`），提交时已隔离未卷入。
- **遗留**：SPA 无法返回真实 HTTP 404 状态（仅 noindex meta）；`/dashboard/不存在路径` 会落到前台 404（可接受）。
