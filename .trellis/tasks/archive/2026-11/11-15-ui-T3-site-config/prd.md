# T3 站点配置真实化

> 父任务：`.trellis/tasks/11-15-ui-concepts/prd.md` T3 节。目标：站名、副标题、作者、简介、社交链接全部来自后端配置，前端不再出现 "MySite / Admin" 硬编码。

## 方案（§2.5 配置变更，先落方案再动手）

### 后端

- 新增 `config/SiteProperties.java`：`@Component @ConfigurationProperties("site")`，`@Data`（沿用 `JwtProperties`/`ElasticsearchProperties` 风格）。字段：
  `title, subtitle, description, author, authorBio, authorTagline, avatar, url, github, rss, commentEnabled(boolean), navigation(List<NavItem{label,path}>)`。
  类内默认值 = 现有硬编码值（MySite/Admin/极简博客/三项导航），保证不配 env 时行为不变。
- `SiteConfigController`：注入 `SiteProperties`，`GET /v1/site/config` 返回 `Result<SiteProperties>`；路径与 permitAll（`/v1/site/**` 已在白名单）不变。
- `application.yaml` 新增 `site:` 段（kebab-case 键，relaxed binding 到 camelCase）：

  | yaml 键 | 环境变量 | 默认 |
  |---|---|---|
  | `site.title` | `SITE_TITLE` | `MySite` |
  | `site.subtitle` | `SITE_SUBTITLE` | 空 |
  | `site.description` | `SITE_DESCRIPTION` | `一个极简的个人博客` |
  | `site.author` | `SITE_AUTHOR` | `Admin` |
  | `site.author-bio` | `SITE_AUTHOR_BIO` | 空 |
  | `site.author-tagline` | `SITE_AUTHOR_TAGLINE` | 空 |
  | `site.avatar` | `SITE_AVATAR` | 空 |
  | `site.url` | `SITE_URL` | 空 |
  | `site.github` | `SITE_GITHUB` | 空 |
  | `site.rss` | `SITE_RSS` | 空 |
  | `site.comment-enabled` | `SITE_COMMENT_ENABLED` | `true` |
  | `site.navigation` | —（无 env 入口） | 默认值在 `SiteProperties` 类内（首页/归档/关于）；部署侧 yml 可整体覆盖 |

  注意：`${VAR:default}` 只在变量**未定义**时用默认；`.env` 里 `KEY=`（空值）会覆盖成空串。可选字段（subtitle/bio/tagline/avatar/url/github/rss）空串即"未配置"，语义正确；title/author/description 在 env 文件里给非空示例值。
- `deploy/config/.env.example` 追加 `SITE_*` 占位段；本地 `.env`（gitignore）同步追加同名键，值先填当前默认，真实值由 owner 改。
- `deploy/config/application-production.yml` 不动：jar 内 `site:` 段带 env 占位，生产由 `/opt/mysite/.env` 注入；`navigation` 默认值收敛在 `SiteProperties` 类内（避免 yaml 缺失时导航为空），部署侧可用 `site.navigation` 整体覆盖。

### 前端消费点（改前已 grep）

- `types/index.ts` `SiteConfig`：新增可选字段 `subtitle/authorBio/authorTagline/avatar/github/rss`（`url`、`description`、`author`、`title` 保持必填，旧响应兼容）。
- `stores/site.ts`：默认值去 "MySite/Admin"，改中性占位（title/author 等空串，navigation 保留三项结构）。
- `App.vue`：`useHead(() => ({ titleTemplate }))` 集中拼 `页面 - 站名`；站名未加载时退化为纯页面标题。外层 getter 读 `siteStore.site.title` 保证 fetchSite 完成后标题模板重算（unhead `walkResolver` 对 `titleTemplate` 键的函数不提前调用，resolveTags 阶段才以页面 title 为参数调用）。
- 22 个视图/组件的 `useHead` 去掉 ` - MySite` 后缀（列表见下），只传页面标题。
- `DashboardSidebar.vue`：`MySite Admin` → `siteStore.site.title`（副标题位）。

### useHead 改动清单（22 文件）

ArchiveView / AboutView / FavoritesView / CollectionsView / CollectionManageView / CollectionEditView / CollectionView / CategoryView / TagView / SearchView / BlogPost / BlogHome(无 title 改动) / DashboardView / SettingsView / UserManageView / UserProfileView / CommentManageView / CategoryManageView / TagManageView / AiManageView / KnowledgeManageView / ImageManagerView / PostEditorView —— 逐个把 `- MySite` 后缀删掉；`UserProfileView`/`CategoryView`/`TagView`/`SearchView`/`CollectionView`/`BlogPost` 的三元表达式同步处理。

### 不做

- 不动 `WebSecurityConfig`（`/v1/site/**` 已 permitAll）。
- 不动 `index.html` 静态 `<title>MySite</title>`（src 外，T2 会碰 index.html；它只是 JS 加载前的占位）。
- 不动 `docker/init/data.sql` 的欢迎文章文案（种子数据，非配置）。
- 不引入 DB 配置表 / 后台可编辑（本轮只到 env + yaml）。

## 验证结论（2026-11-15）

**意外发现并修复的存量 bug**：`main.ts` 用的是 `createUnhead`（unhead 核心，无 DOM 渲染插件），导致所有 `useHead` 解析正常但 title/meta 永远不写进 document——各视图的 `- MySite` 标题后缀本来就是死代码，`document.title` 一直停在 index.html 的 "MySite"，`head.dirty` 常驻 true。改为 `@unhead/vue/client` 的 `createHead` 后标题/OG meta 才真正生效。该修复是 T3 验收的前置条件，消费点已核查：全站 useHead 只产出 title + meta(name/property)，无 script/bodyAttrs 等危险标签。

**验证记录**：

- `./mvnw compile` / `test -Dtest=SiteConfigControllerTest`：3/3 通过（Result 契约、JSON camelCase 键、kebab-case 绑定 + navigation 列表绑定）。
- `./scripts/verify.sh`（quick）：通过（hermetic 测试 + 前端 type-check）。
- `npm run build`：通过。
- `npm run lint`：16 个 error 全部位于未改动的存量文件（`useMarkdown`/`useChatMarkdown`/`useToc`/`useCollection`/`useFabStack`/`useScrollReveal`/`gravatar`），为 HEAD 上 pre-existing，本任务文件零新增 lint 问题；按「一个 commit 一个关注点」未顺手修。
- 端到端：另起 `SERVER_PORT=8091` 实例（不动用户 8081），`/v1/site/config` 返回完整 `SiteProperties` JSON（12 个 camelCase 键 + navigation 三项）；带 `SITE_TITLE='某某·手记' SITE_AUTHOR='某某' SITE_SUBTITLE=… SITE_GITHUB=…` 重启后对应字段全部变为注入值，未设变量回落 yaml 默认。
- Playwright（5173 + 旧后端）：`/` → `MySite`（无页标题时=站名）、`/archive` → `归档 - MySite`、`/about` → `关于 - MySite`、`/collections` → `合集 - MySite`、`/post/:id` → `文章标题 - MySite`、`/search?q=` → `搜索: q - MySite`、不存在路径 → `MySite`；文章页 `og:title`/`og:image` meta 正常渲染；console 零报错。
- `grep -rn "MySite" mysite-frontend/src`：零结果。

**待办/边界**：

- 8081 上正在跑的后端是旧代码，需重启后 `/v1/site/config` 才返回新结构；真实站名/作者在 `.env` 的 `SITE_*` 里改（本地 `.env` 已补占位段，值暂为默认）。
- 生产：`/opt/mysite/.env` 加 `SITE_*` 键即可，`deploy/config/.env.example` 已补模板。
- `index.html` 静态 `<title>MySite</title>`/meta description 是 JS 加载前占位，未动（T2 会碰 index.html）。
- 工作区有并行任务改动（T1 主题 token、T5 分页）：`CollectionView.vue` 等文件同时含 T3（标题）与 T5（分页）改动，提交时注意按关注点拆。
- `npm run lint --fix` 在验证时对全仓执行过，可能对并行任务文件应用了自动修复（均为语义保持的 lint fix）。

## Resolution

T3 完成：site.* 配置端到端打通（yaml ${SITE_*:} → /v1/site/config → 前端 store/titleTemplate），前端 "MySite/Admin" 硬编码清零；顺手修复 createUnhead 不渲染 DOM 的存量 bug。verify quick 绿、build 过、8091 实例 env 注入端到端验证、Playwright 逐页标题核对。阶段验收通过并随 359f61a 提交。遗留：8081 运行中后端需重启才返回新结构；真实站名/作者由 owner 在 .env 的 SITE_* 配置。
