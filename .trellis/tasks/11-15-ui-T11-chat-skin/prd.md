# UI 重构 T11：聊天面板换肤

> 对照稿：`.trellis/tasks/11-15-ui-concepts/final/post.html` `?sheet=ask`（移动端 sheet）、
> `final/paper.css` 的 `.ask` / `.bubble-u` / `.answer` / `.ref` / `.send`。
> 任务定义见 `.trellis/tasks/11-15-ui-concepts/prd.md` T11 节。本文件记开工结论与验收结果。

## 开工结论（实现前核对）

- **范围**：`ChatMessageItem` / `ChatSources` / `ChatInput` / `ChatWidget` 头部与面板 chrome、
  `ChatHistory` 抽屉、`ChatStreamWriter` 的 `[n]` 角标。`useChat` 状态机、`api/rag.ts`、
  `useChatMarkdown` 一律不动（§2.3）。
- **换肤分层**（沿用 post.css 先例）：新版式对全主题生效（token 驱动）；
  纸本专属装饰（方角 / 墨线边框 / 硬阴影 / 衬线正文）限 `[data-theme^="paper"]`，
  写在新 `styles/chat.css`，`main.ts` 引入。
- **概念稿要点**：用户消息 = `bg-secondary` 方块（无气泡色）；AI 回答 = 衬线正文裸排
  （无卡片底）；出处卡 = `[n] 标题` + 元信息行，hover accent 描边；输入框 = 墨线边 +
  衬线输入 + 墨色发送键；sheet = `bg-primary` + 顶部 `border-strong`。
- **`[n]` 角标**：`useChatMarkdown` 不做引用转换（marked 原样输出 `[1]`）。
  在 `ChatStreamWriter` 内做 DOM 后处理：v-html 渲染后 TreeWalker 把文本节点中的
  `[n]`（跳过 pre/code/a/katex）包成 `<sup class="chat-cite">`，accent 角标全主题生效
  （旧主题视觉=小号上标 accent，属换肤一部分，非 paper 专属）。
- **出处卡可点的前提（偏差，最小后端补齐）**：总计划写「点击跳文章锚点」，但现状
  `SourceChunkDTO` **没有 articleId 字段**（前端 type 里有，后端从不下发），
  且 `ChatSources` 死链 `/article/:id`（真实路由是 `/post/:id`）——当前出处卡
  **永远不可点**。补齐方式：`t_knowledge_document.source_ref` 对 ARTICLE 文档就是
  articleId，`RagChatService` 检索后 `selectBatchIds(docIds)` 一次批量解析
  （PK 查询，topK 量级，可忽略），`SourceChunkDTO` 加 `articleId`（Long +
  ToStringSerializer，对外 string）。不改 VectorStore/SearchResult/rerank 链路，
  不动 PG schema；UPLOAD 文档与历史旧消息自然降级为不可点卡片。
- **「§章节」元信息**：chunk `content` 把 headingPath（`H1 > H2`）烘焙在开头，
  但无独立列、无法可靠反解（正文首段也可能含 ` > `）。T11 元信息行用
  `kbName · 相关度%`；真章节名需 chunk 表加列另行评审，不在本任务。
- **历史消息无 sources**：`getConversationMessages` 只回 role/content，
  历史会话的出处卡本就不显示（现状如此），本任务不改。

## 改动清单

后端（最小补齐，见上）：

- `ragent/dto/SourceChunkDTO.java`：+ `articleId`（Long，`@JsonSerialize(ToStringSerializer)`）。
- `ragent/service/RagChatService.java`：注入 `KnowledgeDocumentMapper`；
  检索后按 docId 批量解析 ARTICLE 文档的 articleId 写入 DTO。
- 测试调用点：`ChatEventTest`×2、`ConversationManagerTest`×2、`RagChatServiceTest`（构造 + 新增 articleId 断言）、
  `Phase3IntegrationTest` 构造参数。

前端：

- 新增 `styles/chat.css`：`.chat-bubble-user` / `.chat-bubble-ai` / `.chat-sources*` /
  `.chat-input-*` / `.chat-cite` 等语义类；`html[data-theme^="paper"]` 作用域覆盖
  （方角、`--border-strong`、`--shadow-hard`、衬线正文、墨色发送键）。
- `main.ts`：import chat.css。
- `ChatStreamWriter.vue`：`[n]` → `sup.chat-cite` DOM 后处理（watch renderedHtml + nextTick）。
- `ChatMessageItem.vue`：用户气泡 → `bg-bg-secondary` 墨块；AI 容器 → 无底衬线正文
  （失败态保留 danger 框）；挂 `chat-bubble-*` 钩子类。
- `ChatSources.vue`：单行卡 → 两行引用卡（`[n] 标题` serif + `kbName · NN%` 元信息），
  修 `/article/` → `/post/`；保留折叠开关与重试无关逻辑。
- `ChatInput.vue`：输入框衬线化、发送键墨色化、挂钩子类；quote 引用条/额度行不动逻辑。
- `ChatWidget.vue`：面板/FAB/KB 标签/空态 chips/提示条/历史抽屉挂钩子类；
  标题衬线化；结构与开合/拖拽/launcher 逻辑零改动。
- `ChatHistory.vue`：列表项/新建按钮挂钩子类（paper 方角）。

## 验收记录

验证脚本：`mysite-frontend/scripts/t11-verify.mjs`（SSE/会话接口全 mock，
复用 T7 的 `data: {"type":...}` 事件序列；截图落 `scripts/output/t11/`）。

- `node scripts/t11-verify.mjs`：**49/49 PASS**
  - A 桌面 paper-light 全要素：面板 bg-elevated/方角/墨线边、标题衬线、空态 chips、
    匿名提示条、输入框衬线、发送键禁用态、流式「正在生成」、用户气泡 bg-secondary 方块、
    AI 正文衬线、`sup.chat-cite`×2 accent 色、出处卡×2（可点→`/post/<真实id>` +
    静态降级卡）、`kbName · NN%` 元信息、发送后清空、历史抽屉开合/Esc 分级、无 pageerror。
  - B 失败态：SSE error 事件 → danger 框 + 「重新生成」+ 错误横幅。
  - C 主题×视口：paper-light/paper-dark/classic-light × 1440/390，溢出=0，
    paper 方角、classic 旧圆角保持。
- `./scripts/verify.sh`（quick）：**VERIFY OK**（后端 hermetic 测试 + 前端 type-check）。
- `./mvnw test` RAG 相关：29/29（含新增 articleId 断言）。
- `npm run build`：通过；`npm run lint`：16 个存量错误，T11 文件 0 新增。
- 踩坑记录：chat.css 头部注释里 `rounded-*/border-*` 的 `*/` 提前闭合注释，
  导致整个 `@layer components` 块被浏览器丢弃（unlayered 规则幸存）——
  表现为 paper 覆盖生效但基础换肤全部缺失。改写注释措辞后修复；
  另确认本仓 cascade 层序为 theme<base<utilities<components（components 反超
  utilities），paper 覆盖仍按惯例写 unlayered 保险。

## Resolution

完成。聊天面板完成纸本换肤且六旧主题行为不变：用户消息改 `bg-secondary` 墨块、
AI 回答衬线裸排、`[n]` 引用角标（ChatStreamWriter DOM 后处理，未动 useChatMarkdown）、
出处卡改两行引用卡并修复 `/article/`→`/post/` 死链；后端最小补齐
`SourceChunkDTO.articleId`（ARTICLE 文档 source_ref 解析，UPLOAD/旧消息降级静态卡）。
`useChat` 状态机、SSE 协议、launcher 语义零改动。验证 49/49 + verify quick 绿。

已知边界（不在本任务）：历史会话消息无 sources（后端不下发，现状如此）；
出处卡「§章节」元信息需 chunk 表加列另行评审；T15 匿名聊天清理未动。
