# UI 重构 T7：提问区 + ChatWidget 预填能力

> 对照稿：`.trellis/tasks/11-15-ui-concepts/final/home.html` 「不想翻目录？直接问。」区块。
> 任务定义见 `.trellis/tasks/11-15-ui-concepts/prd.md` T7 节。本文件记开工结论与验收结果。

## 开工结论（实现前核对）

- **`/v1/rag/knowledge-bases` 列表是 permitAll**，匿名也能拉；返回含 disabled 的全部 KB，前端按 `enabled` 过滤。
  「全部文章」= 所有 enabled KB 的 id 列表（文章发布时同步进所有启用 KB，`ArticleEventListener` → `KnowledgeDocumentService`）。
- **后端限流实际值**：USER 10/h、CREATOR 20/h、ADMIN 不限（`application.yaml rag.rate-limit`）。
  前端没有该配置的下发通道，ChatInput 已硬编码同样数值 → 面板文案沿用硬编码口径，不新增配置。
- **登录回跳链路现成**：`/login?redirect=/` → `LoginView.onMounted` 取 query → `setRedirectUrl` →
  `onLoginSuccess` 后 `router.replace(redirect)`。问题本体走 sessionStorage（登录是整页上下文切换，
  localStorage 也行但 sessionStorage 语义更准：一次性、关 tab 即失效）。
- **ChatWidget 是 DefaultLayout 的异步组件**，跨前台路由不销毁 → `useChat` 状态天然全局；
  DashboardLayout 不含它（dashboard 页 launcher.open 无人消费，pending 会留到下次前台打开时生效——可接受）。
- **FAB 隐藏范围**：`route.name ∈ {home, home-page, post}`。`/page/N`（N≥2）没有提问区但仍属首页，
  按「首页」字面口径一并隐藏（拍板原文）；若走查认为 page≥2 需要入口再放开，一行改动。
- **ChatWidget 打开副作用收口**：原 `open()` 里的 `closingByDrag` 复位 + 桌面聚焦 + 滚底，
  launcher.open 绕过它 → 全部并入 `watch(isOpen)`（isOpen 改为 launcher 单例 ref 后 watcher 仍触发）。

## §2.5 配置变更方案（先列后改）

`GET /v1/site/config` 直接序列化 `SiteProperties`，新增字段即对外契约：

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `site.ask-examples` | `List<String>` | 类内默认 4 条 chips 文案 | 前端 chips；yaml 可整体覆盖（同 navigation 先例，无 env 入口） |
| `site.ask-demo` | 对象 `{question, answer, sources[{title, section, articleId}]}` | **null** | 配置后首页展示示例问答+出处卡；`answer` 为空前端不渲染该块 |

- `articleId` 可空：有值渲染 `/post/:id` 链接，无值渲染静态卡（示例内容不绑定真实文章也能展示）。
- 不引入 env 占位：列表/对象走 yaml（`application.yaml` 或 production additional-location 覆盖），与 `navigation` 一致。
- 前端 `SiteConfig` 两字段均可选，旧后端响应兼容（`askExamples` 缺省时前端用本地 4 条兜底）。

## 改动清单

- 后端：`config/SiteProperties.java` +`askExamples`/`askDemo`（含 `AskDemo`、`AskSource` 嵌套类）。
- 前端新增：`composables/useChatLauncher.ts`（模块级单例：`isOpen`/`pending`/`open(opts)`/`close()`/`consumePending()`）。
- 前端改动：`types/index.ts`（SiteConfig 扩展）、`ChatInput.vue`（quote 引用条 + `setInput`/`focus` expose）、
  `ChatWidget.vue`（isOpen 走 launcher、消费 pending、quote 拼接 `> 引用\n\n问题`、FAB 路由隐藏）、
  `components/home/HomeAskPanel.vue`（新）、`BlogHome.vue`（填 T7 插槽，仅第 1 页）。
- 消费点核对：`api/rag.ts` 签名不动；`useChat.ts` 不改（kbIds 走 `selectedKbIds` 现有通道）；
  ChatWidget 仅 DefaultLayout 一个挂载点。

## 验收记录

- `./scripts/verify.sh`（quick）：绿——后端 hermetic 测试（含 `SiteConfigControllerTest`
  新增 `askExamples`/`askDemo` 序列化断言 + yaml kebab-case 绑定测试）+ 前端 type-check。
- `npm run lint`：16 个存量基线错误（useMarkdown/useChatMarkdown/useScrollReveal/gravatar
  等），T7 全部文件 0 错误。
- `npm run build`：通过。
- `scripts/t7-verify.mjs`（Playwright，dev server 5173 + 真实后端 8081）：**41/41 通过**。
  - A 匿名：面板渲染（标题/chips=4/额度文案/无 FAB/溢出=0）、chip 填充、提交 →
    `/login?redirect=/` + sessionStorage 暂存、未登录回首页 stash 清理且不自动开面板。
  - B 登录回跳：注册/登录 → 回首页 → 面板自动打开 → 用户消息发出 → assistant 内容渲染
    → 无「正在生成」残留。**SSE 已改 mock**（meta→sources→content→done 固定流），
    消除真实 LLM 时延/抖动；真实链路此前已用直连 curl + 浏览器诊断分别验证过
    content 事件可达且渲染。
  - C launcher：`/about` FAB 可见且可开合（Esc 回归）、文章页与 `/page/2` FAB 隐藏、
    `/page/2` 无提问区、`launcher.open({question})` 预填、`{quote}` 引用条渲染/删除。
  - D 配置驱动：mock `site/config` 注入 `askDemo` → 示例块 + sup 角标×2 + 出处卡 +
    articleId 渲染为 `/post/:id` 链接；`askExamples` 覆盖 chips；mock KB 空 →
    「AI 助手暂未开放」降级且无输入框。
  - E 三主题（paper-light/paper-dark/classic-light）× 双视口：横向溢出均 = 0。
- 设计核对（getComputedStyle 实测）：paper-light 下面板为 `border-strong` 墨线边框 +
  `6px 6px 0` 硬阴影 + 衬线 17px 输入框；classic-light 保持圆角浅色卡，旧主题不受影响。
- 截图：`mysite-frontend/scripts/output/t7/`（a-home-ask / b-after-login-chat /
  d-askdemo / d-degraded / e-ask-*-{d,m}）。

## Resolution

已完成并提交。

- 后端 `SiteProperties` 新增 `askExamples`（默认 4 条）与 `askDemo`（默认 null），
  经既有 `GET /v1/site/config` 直接序列化对外；契约测试同步补齐。
- 前端新增 `useChatLauncher` 模块级单例（`isOpen`/`pending`/`open`/`close`/`consumePending`），
  ChatWidget 的 `isOpen` 与 FAB/打开副作用全部收口到 launcher + `watch(isOpen)`，
  FAB 点击与 `launcher.open()` 行为等价。
- `ChatInput` 新增 quote 引用条（accent 左边线、可删除）与 `setInput`/`focus` expose；
  发送时引用拼成 `> 引用\n\n问题`。
- `ChatWidget` 新增 pending 消费（`send` 立即发、`question` 预填、`quote` 引用条、
  `kbIds` 覆盖检索范围），FAB 按 `route.name ∈ {home, home-page, post}` 隐藏
  （`/page/N` 按首页口径一并隐藏）。
- `HomeAskPanel` 落位 T6 插槽（仅第 1 页）：衬线标题 + accent 副题、输入行
  （scope「全部文章」+ 提问按钮）、chips、额度/登录文案、可选 askDemo 示例块
  （`[n]` → sup 角标 + 出处卡，articleId 有值渲染 `/post/:id` 链接）。
  未登录提交 → sessionStorage 暂存 + `/login?redirect=/`；`onMounted` 消费 stash
  自动 `launcher.open({send:true, kbIds})`。无可用 KB → 整块降级一行提示。
- 偏差/取舍：
  - launcher `open()` 比总计划多一个 `kbIds` 字段——「全部文章」范围需要，
    走 `useChat.selectedKbIds` 现有通道，未改 `api/rag.ts`。
  - 匿名额度文案沿用硬编码「每小时 10 次」口径（限流配置无下发通道，
    与 ChatInput 现状一致）；匿名实际不可用由后端 `ChatRateLimiter` 兜底，
    T15 再统一清理 visitor 通道。
  - B 段验证改用 SSE mock 保证确定性；真实 LLM 链路在调试阶段单独验证过。
- 遗留：文章页 `AskArticleCard` 与移动端「✦ 问这篇」入口属 T8/T9；
  `quote`/`articleId` 上下文能力已就绪待消费。
