# 文章编辑器重构设计方案

> 版本：v1.0 · 日期：2026-07-31 · 状态：阶段 1–6 已实施完成（阶段 7 回归验证中）

## 1. 背景与现状

### 1.1 问题历史

当前文章编辑器处于「不可用」状态，根因清晰：

- 原 `MarkdownEditor.vue`（CodeMirror 6）之上叠加了**自研所见即所得装饰层** `src/editor/livePreview.ts`（528 行），通过 `Decoration.replace` / widget 动态替换文档片段实现 Typora 式渲染。该方案与 CM6 的文档位置模型存在根本性冲突（装饰改变视觉位置但不改变文档位置），导致光标跳顶、输入崩溃、点击错位等一系列顽疾。
- 从 git 历史可见，该装饰层经历了 **10+ 轮修复仍无法稳定**（`ad830eb` → `7bf272b`），最终在 `7bf272b` 被整体停用。
- 临时替代品 `SimpleMarkdownEditor.vue` 只是一个 textarea + 分栏预览：无格式化快捷键、无工具栏排版能力、无撤销栈之外的编辑辅助，只能算「能写字」，不具备应用级编辑体验。

### 1.2 核心教训

**不要自研所见即所得渲染层。** CM6 是优秀的源码编辑器内核，但它的 decoration 体系不适合做全量 WYSIWYG。所见即所得应该交给以文档模型为中心的成熟方案（ProseMirror 系），由社区实现兜底稳定性。

### 1.3 现有资产盘点（可复用）

| 资产 | 位置 | 复用方式 |
|---|---|---|
| 图片上传 API | `src/api/image.ts`（`uploadImage` / `uploadImageByUrl`，5MB 限制） | 新编辑器直接调用 |
| Markdown 渲染管线 | `src/composables/useMarkdown.ts`（marked + GFM + callout + KaTeX） | 定义编辑器必须往返兼容的 Markdown 方言 |
| 文章预览组件 | `src/components/article/ArticleContent.vue` | 预览模式复用 |
| 编辑页面容器 | `src/views/PostEditorView.vue` | 仅改 import，接口保持不变 |
| KaTeX / Prism 依赖 | `package.json` 已存在 | 新编辑器的公式 / 代码高亮复用 |

## 2. 目标

### 2.1 功能目标

1. **所见即所得编辑**：输入 Markdown 即时渲染为排版样式（Typora / Notion 式体验），中文输入法（IME）组合输入稳定。
2. **完整编辑能力**：
   - 行内格式：加粗、斜体、删除线、行内代码、链接
   - 块级结构：标题（H1–H6）、引用、有序/无序/任务列表、代码块（语法高亮）、表格、分割线、数学公式（KaTeX）
   - 站点特有：Callout 标注（`> [!NOTE]` 等 22 种类型，与 `useMarkdown.ts` 渲染管线对齐）
   - 斜杠菜单（`/`）与浮动工具栏，降低 Markdown 语法记忆负担
3. **图片工作流**：工具栏选择、粘贴、拖拽三种上传方式；上传中占位与进度反馈；失败可感知（toast）；与现有 `uploadImage` API 对接。
4. **数据契约不变**：存储格式仍为 Markdown 字符串，组件保持 `v-model` + `save` 事件接口，`PostEditorView.vue` 零侵入切换。
5. **健壮性**：组件初始化失败时降级为纯文本编辑（error boundary）；卸载时完整释放资源；外部内容变更（加载文章）正确同步。

### 2.2 非目标（Out of Scope）

- 多人协作编辑
- 文章自动保存 / 草稿箱（属于 `PostEditorView` 层面能力，另立任务）
- 编辑器内 TOC、字数统计等周边面板（后续按需追加）

### 2.3 验收标准

- [ ] 连续输入 30 分钟中文长文（含列表、代码块、公式）无光标错位、无内容丢失
- [ ] 全键盘操作：所有格式均有快捷键，`Cmd/Ctrl+S` 触发保存
- [ ] 粘贴截图 / 拖入图片 → 上传 → 光标处插入图片，失败有提示
- [ ] 打开已有文章（含 callout、公式、表格、代码块）→ 渲染正确 → 保存后 Markdown 往返无损（round-trip）
- [ ] 编辑器在暗色主题下样式与站点设计系统一致
- [ ] `npm run build`（含 `vue-tsc` 类型检查）与 `npm run lint` 通过

## 3. 技术选型

### 3.1 候选方案对比

| 方案 | 所见即所得 | Markdown 原生性 | Vue 3 集成 | 维护活跃度 | 风险 |
|---|---|---|---|---|---|
| **Milkdown Crepe** | ✅ 完整 | ✅ Markdown 即数据源，往返由 remark 保证 | ✅ 框架无关，挂 DOM 即可 | ✅ 活跃（[官方文档](https://milkdown.dev/docs/guide/using-crepe)） | 低 |
| TipTap 3 | ✅ 完整 | ⚠️ Markdown 需社区插件，往返有损风险 | ✅ 官方 Vue 支持 | ✅ 活跃 | 中（Markdown 是二等公民） |
| 继续 CM6 + 删 livePreview | ❌ 只有源码+预览 | ✅ | 已有 | — | 低，但不满足「所见即所得」目标 |
| Vditor | ✅（IR 模式） | ✅ | 框架无关 | ⚠️ 更新放缓，UI 风格陈旧难定制 | 中 |

### 3.2 决策：Milkdown Crepe

选择 `@milkdown/crepe`（基于 Milkdown v7 / ProseMirror），理由：

1. **Markdown 是一等公民**：底层经 remark 解析/序列化，与后端存储格式、前端 `useMarkdown` 渲染管线天然对齐，round-trip 有保证。
2. **开箱即用的完整体验**：Crepe 内置斜杠菜单、浮动格式工具栏、图片块（含上传插件）、代码块（CM6 内核，自带语法高亮）、表格编辑、KaTeX 公式块、链接悬浮编辑——正好覆盖 §2.1 的全部功能目标，无需自研交互。
3. **图片上传官方支持**：内置 `@milkdown/plugin-upload` 处理粘贴/拖拽，只需提供 `uploader` 回调对接现有 `uploadImage`。
4. **稳定性兜底**：IME 组合输入、光标管理、撤销栈由 ProseMirror 核心处理，避开此前自研装饰层的全部坑。

替代方案说明：若实施中发现 Crepe 定制受阻（如 callout 扩展成本过高），降级路径是退回纯 Milkdown core（`@milkdown/core` + 按需 preset），架构设计对此保持兼容（见 §6 风险）。

### 3.3 新增依赖

```jsonc
{
  "@milkdown/crepe": "^7.x",   // 编辑器本体（实施时锁定具体版本）
  "@milkdown/kit": "^7.x"      // 可选：定制扩展时用（core/ctx/preset 等聚合包）
}
```

注意：`@milkdown/crepe` 与 `@milkdown/kit` 的 semver 必须严格一致（Milkdown 官方要求）。katex 已有依赖，公式渲染直接复用。

## 4. 总体架构

### 4.1 文件结构

```
mysite-frontend/src/
├── components/editor/
│   ├── MarkdownWysiwygEditor.vue    # 新增：Vue 包装组件（对外唯一入口）
│   └── editorTheme.css              # 新增：Crepe 主题覆盖（对接站点 CSS 变量）
├── editor/                          # 新增（替代旧目录，整体重写）
│   ├── createEditor.ts              # Crepe 实例创建与 feature 配置
│   ├── imageUploader.ts             # 对接 @/api/image 的上传适配器
│   └── calloutPlugin.ts             # Callout 扩展（Milkdown 插件）
└── （删除）components/editor/MarkdownEditor.vue
└── （删除）components/editor/SimpleMarkdownEditor.vue
└── （删除）editor/livePreview.ts / autoConvert.ts / enterContinuation.ts
```

设计要点：**所有 Milkdown 相关代码收敛在 `src/editor/` 与包装组件内**，业务页面只面对一个普通 Vue 组件。未来若更换编辑器内核，业务侧零改动。

### 4.2 组件接口（与现有契约保持一致）

```ts
// MarkdownWysiwygEditor.vue
defineProps<{
  modelValue: string        // Markdown 源文本
  placeholder?: string
}>()

defineEmits<{
  'update:modelValue': [value: string]  // 编辑时同步输出 Markdown（防抖）
  'save': []                            // Cmd/Ctrl+S
}>()
```

`PostEditorView.vue` 只需把 `SimpleMarkdownEditor` 的 import 换成新组件，模板标签不变。

### 4.3 数据流

```
DB (markdown) ──► PostEditorView.content ──► MarkdownWysiwygEditor (v-model)
                                                  │
                                    remark parse  │  一次性解析为 ProseMirror 文档
                                                  ▼
                                          Crepe 编辑器（WYSIWYG）
                                                  │
                                    remark serialize（防抖 ~300ms）
                                                  ▼
                              emit('update:modelValue', markdown) ──► v-model 回写
```

关键约束：

- **单向回显保护**：沿用旧组件的 `lastEmittedValue` 回声短路模式——组件自己 emit 的值再经 props 回来时直接忽略，避免「编辑→回写→重置文档→光标跳动」循环。
- **外部赋值重建文档**：仅当 `modelValue` 变化且 ≠ `lastEmittedValue`（典型场景：打开文章时 `getArticleById` 回填）才重新设置编辑器内容；此时光标重置到文首是可接受的。
- **序列化防抖**：`getMarkdown()` 是 O(n) 操作，编辑期间防抖 300ms emit；`save` 事件触发时立即 flush 一次，保证保存拿到最新内容。

### 4.4 Markdown 方言契约（round-trip 兼容矩阵）

编辑器序列化输出必须被 `useMarkdown.ts`（marked + GFM + callout + KaTeX）正确渲染，反之渲染管线支持的语法打开时必须可编辑：

| 语法 | 存储形式 | 编辑器内表现 | 兼容方案 |
|---|---|---|---|
| 标题/列表/引用/表格 | 标准 GFM | 所见即所得 | Crepe 内置（commonmark + GFM preset） |
| 代码块 | ```` ```lang ```` | CM6 代码块，语法高亮 | Crepe `codeMirror` feature |
| 数学公式 | `$...$` / `$$...$$` | KaTeX 实时渲染 | Crepe `latex` feature（复用已有 katex 依赖） |
| Callout | `> [!NOTE] 标题` | 带颜色/图标的卡片块 | **自研插件** `calloutPlugin.ts`（见 §5.4） |
| 图片 | `![alt](url)` | 图片块，支持替换/删除 | Crepe `imageBlock` + `plugin-upload` |
| 任务列表 | `- [ ]` / `- [x]` | 可勾选复选框 | Crepe GFM 内置 |
| 下划线 | `<u>...</u>` | 行内 HTML 原样保留 | 不进 WYSIWYG，序列化保留原文 |

兜底原则：**任何编辑器无法识别的语法，序列化时必须原样保留**（remark 对未知语法按原样通过，这是选 Milkdown 而非 TipTap 的关键原因之一）。实施时用 §7 的 round-trip 测试用例集验证。

## 5. 详细设计

### 5.1 Crepe Feature 配置（`createEditor.ts`）

```ts
// 伪代码，实施时按 @milkdown/crepe 实际 API 调整
const crepe = new Crepe({
  root: containerEl,
  defaultValue: initialMarkdown,
  features: {
    [Crepe.Feature.CodeMirror]: true,     // 代码块（CM6 内核 + 高亮）
    [Crepe.Feature.Latex]: true,          // KaTeX 公式
    [Crepe.Feature.ImageBlock]: true,     // 图片块
    [Crepe.Feature.Table]: true,
    [Crepe.Feature.ListItem]: true,       // 含任务列表
    [Crepe.Feature.BlockEdit]: true,      // 斜杠菜单 + 块拖拽手柄
    [Crepe.Feature.Toolbar]: true,        // 选中文本浮动工具栏
    [Crepe.Feature.Cursor]: true,
    [Crepe.Feature.Placeholder]: true,
    [Crepe.Feature.LinkTooltip]: true,    // 链接悬浮编辑
  },
  featureConfigs: {
    [Crepe.Feature.Placeholder]: { text: placeholder },
    [Crepe.Feature.ImageBlock]: {
      onUpload: milkdownUploader,        // 见 §5.3
      proxyDomURL: (url) => url,         // 同源图片不做代理
    },
  },
})
crepe.on((listener) => {
  listener.markdownUpdated((ctx, markdown, prev) => { /* 防抖 emit */ })
})
```

### 5.2 Vue 包装组件生命周期（`MarkdownWysiwygEditor.vue`）

- `onMounted`：调用 `createEditor()`，注册 `markdownUpdated` 监听与 `Mod-s` 快捷键（Crepe 支持自定义 keymap 注入，save 时先 flush 防抖再 emit）。
- `watch(modelValue)`：回声短路（§4.3）；外部变更时 `crepe.setMarkdown(newVal)`。
- `onUnmounted`：`await crepe.destroy()`，清空定时器，置空引用。ProseMirror 不 destroy 会泄漏 DOM 监听，必须严格执行。
- **Error boundary**：`create()` 过程 try/catch，失败时渲染内置 `<textarea>` 降级界面（复用旧 SimpleMarkdownEditor 的极简逻辑，约 40 行），保证「编辑器坏了也能改文章」。

### 5.3 图片上传适配器（`imageUploader.ts`）

```ts
// 对接 Crepe 的 uploader 签名：(file: File) => Promise<string /*url*/>
export async function milkdownUploader(file: File): Promise<string> {
  validate(file)                       // 类型白名单 + MAX_IMAGE_FILE_SIZE，复用现有规则
  const result = await uploadImage(file, onProgress)
  return result.url
}
```

- 粘贴、拖拽、斜杠菜单插入图片三种入口统一走 `plugin-upload` → 该适配器，**删除旧组件里三套各自实现的上传逻辑**。
- 进度反馈：Crepe 图片块自带上传占位；失败时 catch 后 `toast.error`（沿用现有错误文案规则：413/大小限制单独提示）。
- URL 插入图片（`uploadImageByUrl`）：保留为斜杠菜单/工具栏的一个入口，优先级 P1（首版可只保留本地上传，URL 上传作为后续项）。

### 5.4 Callout 扩展（`calloutPlugin.ts`）— 本项目唯一的自研插件

目标：输入/打开 `> [!NOTE] 标题` 时渲染为带颜色和图标的卡片，编辑后以同样的 Markdown 序列化回去，与 `useMarkdown.ts` 的 22 种类型完全对齐。

实现路径（Milkdown 标准扩展三段式）：

1. **remark 侧**：解析阶段将 `blockquote` 首段匹配 `[!TYPE]` 的节点标记为自定义 `callout` 节点（remark 插件，纯 AST 变换，无 DOM 操作）；序列化时还原为 `> [!TYPE]` 语法。
2. **schema 侧**：定义 `callout` ProseMirror node（attrs: `type`, `title`），content 为 `block+`。
3. **视图侧**：NodeView 渲染卡片外壳（复用 `useMarkdown.ts` 中的类型→颜色/图标映射表，抽到共享常量），内容区仍由 ProseMirror 正常编辑。

交互：

- 斜杠菜单增加「标注/Callout」项 → 弹出类型选择（复用旧组件的 5 组 22 类数据，迁移为共享常量）。
- 在普通引用块首行输入 `[!` 触发自动转换（input rule）。

风险控制：这是方案中唯一有自研复杂度的部分。若实施受阻，**降级方案**是不做 NodeView，callout 以普通引用块编辑、仅在序列化时保留 `[!TYPE]` 原文——功能可用但编辑时不渲染卡片样式。验收标准中的 callout 项相应降级。

### 5.5 主题适配（`editorTheme.css`）

- 引入 `@milkdown/crepe/theme/common/style.css` + 一个官方基础主题（frame），再用站点 CSS 变量（`--text-primary`、`--bg-secondary`、`--accent`、`--border` 等）逐项覆盖，保证亮/暗主题跟随站点切换。
- 编辑区排版字体、行高对齐 `.prose` 正文样式（`styles/typography.css`），做到「编辑即所得 ≈ 阅读所见」。
- 代码块内字体沿用现有 monospace 字体栈。

### 5.6 快捷键

| 快捷键 | 行为 |
|---|---|
| `Cmd/Ctrl + S` | 保存（emit `save`，先 flush 内容） |
| `Cmd/Ctrl + B / I / E` | 加粗 / 斜体 / 行内代码（Crepe 内置） |
| `Cmd/Ctrl + Shift + X` | 删除线 |
| `Enter / Shift+Enter / Tab` | 列表续接、软换行、缩进（ProseMirror 内置） |
| `/` | 斜杠菜单 |
| 选中后悬浮工具栏 | 链接、格式转换 |

旧组件的快捷键帮助弹窗迁移为简化版「快捷键」说明（数据从新 keymap 配置生成，不再手维护两份）。

### 5.7 性能

- 编辑器路由组件已有独立 chunk；将 `@milkdown/*` 配置为异步加载（动态 `import()`），避免拖慢首屏。
- 预估新增体积：Crepe + ProseMirror 运行时 gzip 后约 250–350KB，仅在编辑页加载，可接受。
- 长文档：ProseMirror 单文档数万字无压力；防抖序列化（§4.3）避免每击键 O(n) 开销。

## 6. 实施步骤

按可独立验收的阶段推进，每阶段结束 `npm run build` + `npm run lint` 必须通过：

1. **准备**：安装依赖（锁定 `@milkdown/crepe` 与 `@milkdown/kit` 同版本）；建立 round-trip 测试用例集（一篇覆盖全部方言的示例文章 markdown，放 `src/editor/__fixtures__/`）。
2. **骨架**：`createEditor.ts` + `MarkdownWysiwygEditor.vue` 最小可用版（内置 feature 全开、v-model 双向同步、destroy 生命周期、textarea 降级），`PostEditorView` 切换引用。验收：能编辑、能保存、打开旧文章内容不丢。
3. **图片**：`imageUploader.ts` 对接三种上传入口 + 错误提示。验收：粘贴截图→上传→插入。
4. **主题**：`editorTheme.css` 变量覆盖，暗色适配，排版对齐 `.prose`。
5. **Callout 插件**：§5.4 三段式实现 + 斜杠菜单入口。验收：22 种类型打开渲染正确、编辑后序列化无损。
6. **清理**：删除 `MarkdownEditor.vue`、`SimpleMarkdownEditor.vue`、旧 `src/editor/*.ts`；移除不再使用的 CodeMirror 依赖（`@codemirror/*` 6 个包，确认无其他引用后卸载）；更新 `CLAUDE.md`/`README` 中编辑器相关描述。
7. **回归验证**：按 §2.3 验收清单逐项实测，重点跑 round-trip 用例集与 30 分钟中文输入稳定性测试。

## 7. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| Round-trip 有损（打开旧文章再保存，Markdown 被改写） | 数据污染，最高危 | remark 原样通过未知语法；实施第一步先建用例集，每个阶段跑一遍；重点验证 callout、`$$`、行内 HTML |
| Callout 自研插件复杂 | 阶段 5 延期 | §5.4 降级方案（退化为引用块编辑，保留语法） |
| Crepe API 与文档版本漂移 | 集成卡壳 | 依赖锁版本；架构上 `createEditor.ts` 单点封装，必要时降级到 Milkdown core 自行组装 |
| 暗色主题覆盖不全 | 视觉瑕疵 | 阶段 4 单列验收；用 CSS 变量而非硬编码颜色 |
| 中文 IME 兼容 | 输入体验 | ProseMirror 原生处理 IME composition，远优于自研方案；回归测试中专项验证 |
| 包体积增大 | 首屏变慢 | 编辑器动态 import，仅编辑页加载 |

## 8. 删除清单（迁移完成后执行）

| 文件 | 处置 |
|---|---|
| `src/components/editor/MarkdownEditor.vue`（1196 行） | 删除 |
| `src/components/editor/SimpleMarkdownEditor.vue`（322 行） | 删除（降级 textarea 逻辑内嵌新组件） |
| `src/editor/livePreview.ts`（528 行） | 删除——历史问题根源 |
| `src/editor/autoConvert.ts`、`enterContinuation.ts` | 删除（ProseMirror 内置等价能力） |
| `@codemirror/*` 6 个依赖 | 确认 Crepe 代码块自带 CM6 无冲突后，从 `package.json` 移除项目直接依赖 |
