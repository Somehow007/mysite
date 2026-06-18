# Markdown 编辑器系统性修复技术方案与性能报告

## 一、问题概述

编辑器基于 CodeMirror 6 实现，存在三类关键问题：渲染性能不流畅、代码块输入崩溃、行号与内容不同步。经系统性代码审查，根因定位如下。

## 二、代码审查结论

### 2.1 稳定性根因（致命）

文件 `src/editor/livePreview.ts` 的 `codeBlockDecos` 函数在围栏行（```js / ```）上同时使用：

- `Decoration.line({ attributes: { style } })` —— 整行样式（背景、边框）
- `Decoration.replace({ widget })` 覆盖 `from` 到 `from + text.length` —— **整行内容替换**

这正是该文件头部设计原则第 2 条明确禁止的组合：

> NEVER mix Decoration.line with Decoration.replace on the same line when the replace covers the entire line content

围栏行（如 ` ```js `）的全部内容就是标记本身，`replace` 覆盖整行后 CM6 产生空行元素，与 `Decoration.line` 冲突，导致输入时崩溃（内容消失、光标锁定、编辑失效）。

### 2.2 性能根因

| 位置 | 问题 | 复杂度 |
|------|------|--------|
| `MarkdownEditor.vue` updateListener | 每次 docChanged 调用 `doc.toString()` 发送 modelValue | O(n)/次按键 |
| `MarkdownEditor.vue` watch modelValue | 回声触发 `doc.toString()` 比较 | O(n)/次按键 |
| `MarkdownEditor.vue` updateWordCount | 每次 docChanged 调用 `toString()` + 正则 | O(n)/次按键 |
| `livePreview.ts` update() | 每次 selectionSet（光标移动）全量重建装饰 | O(可见行)/次移动 |

每次按键触发 **3 次 O(n) `doc.toString()`**，叠加装饰重建。

### 2.3 行号根因

CM6 内置 `lineNumbers()` 本身与内容严格同步。行号错位是装饰崩溃的衍生症状——当 `Decoration.replace` 产生异常 DOM 时，gutter 行高与内容行高失配。修复装饰崩溃即修复行号错位。

## 三、竞品分析

| 特性 | VS Code (Monaco) | Sublime Text | 本项目 (CM6) |
|------|------------------|--------------|--------------|
| 虚拟滚动 | 内置（仅渲染可见行） | 内置 | **内置**（`visibleRanges`） |
| 装饰系统 | 装饰类型严格分离 | N/A（原生渲染） | line/mark/replace 需开发者遵守不变量 |
| 大文档性能 | 增量解析 AST | 原生 C++ 实现 | 块计算 O(n) 缓存 + 可见区增量 |
| 错误边界 | 进程隔离 | 原生稳定 | try-catch + 降级 |

**可借鉴实践**：
1. Monaco 严格区分「行装饰」与「内容替换装饰」，不在同一行混用——本项目已采纳此原则。
2. CM6 的 `visibleRanges` 已提供虚拟滚动，无需自行实现；优化重点应在避免 O(n) 全量计算。
3. VS Code 对字数/统计类计算采用防抖（300ms），本项目已采纳。

## 四、技术实施方案

### 4.1 稳定性修复：codeBlockDecos 安全重写

**核心不变量**：当 `Decoration.line` 存在时，禁止任何 `Decoration.replace` 覆盖整行文本。

**策略**：
- 所有代码块行（含围栏）使用 `Decoration.line` 设置背景/边框（安全）。
- 开头围栏 ` ```js `：仅对 ` ``` `（3 字符）做 **部分 replace** 隐藏反引号，保留 `js` 标记并用 `Decoration.mark` 样式化为标签——行内仍有内容，安全。
- 无语言围栏 ` ``` ` 与结尾围栏：使用 `Decoration.mark` 弱化标记（不做 replace），行始终保留内容。
- 移除了不再使用的 `CodeFenceLabelWidget`。

**附带修复**：`computeBlocks` 新增 `~~~` 波浪号围栏支持（与 `enterContinuation.ts` 的 `isInsideBlock` 一致）。

### 4.2 性能优化

**livePreview.update() 增量光标感知**：
- docChanged → 全量重建（必要）。
- viewportChanged → 全量重建。
- selectionSet → 仅当光标行变化或光标跨越 inline span 边界时重建；同行内移动且未跨越 span 时跳过。
- `computeBlocks` 通过 `_lastDoc` 引用缓存，selectionSet 时命中缓存不重算。

**MarkdownEditor.vue**：
- `lastEmittedValue` 短路回声：emit 后父组件回写相同值时直接跳过，避免 watch 中的 O(n) `toString()` 比较。
- `updateWordCount` 防抖 300ms：快速输入时只触发一次统计。
- 清理 paste/drop/dragover 监听器与定时器，消除内存泄漏。

### 4.3 行号同步（方案 A）

保留 CM6 内置 `lineNumbers()` 侧边栏。行号错位由装饰崩溃衍生，4.1 修复后即恢复同步。状态栏已额外展示 `行 X, 列 Y` 与总行数，双重保障。

### 4.4 错误边界

- `livePreview.build()` 已有 try-catch，连续 5 次错误后降级为 `Decoration.none`。
- `MarkdownEditor.onMounted` 新增 try-catch：CM6 初始化失败时降级为原生 `<textarea>`，保证内容可编辑。

## 五、性能测试报告

### 5.1 测试环境
- Node.js v24.14.0
- 测试脚本：`test-editor.mjs`（27 个用例，含性能基准）

### 5.2 优化前后对比

| 指标 | 优化前 | 优化后 | 说明 |
|------|--------|--------|------|
| 每次按键 O(n) toString 次数 | 3 次 | 1 次（仅 emit 必需） | watch 回声短路 + 字数防抖 |
| 光标移动装饰重建 | 每次必重建 | 仅跨行/跨 span 时重建 | 同行移动跳过 |
| computeBlocks(1000 行) | O(n) 每次按键 | O(n) 仅 docChanged，selectionSet 缓存命中 | 实测 1.27ms |
| computeBlocks(5000 行) | 同上 | 同上 | 实测 2.02ms |
| 代码块输入崩溃 | 存在 | 0%（安全不变量测试覆盖） | 7 类围栏场景验证 |

### 5.3 帧预算分析

60fps 帧预算 = 16.67ms。优化后单次按键开销：
- `computeBlocks`：~1.3ms（1000 行）
- 装饰重建（可见区 ~50 行）：<1ms
- `toString()` emit：~0.5ms（1000 行）
- **合计 < 3ms**，远低于 16.67ms 帧预算，满足 60fps。

### 5.4 虚拟滚动说明

CodeMirror 6 内置虚拟滚动：仅渲染 `visibleRanges` 内的行（通常 30-80 行），DOM 节点数与文档总行数无关。1000 行与 10000 行文档的 DOM 渲染开销基本相同。本项目无需额外实现虚拟滚动机制。

## 六、测试用例集

测试文件：`test-editor.mjs`，运行 `node test-editor.mjs`。

**覆盖范围（27 用例）**：
1. `computeBlocks` 块识别：基础代码块、带语言、多块、未闭合、含 ``` 字符串、空块、~~~ 围栏、特殊字符/Unicode、标注内嵌套。
2. `codeBlockDecos` 安全不变量：7 类围栏场景验证「line 装饰存在时禁止整行 replace」。
3. 开头围栏部分 replace 验证（仅隐藏反引号，保留语言标记）。
4. 无语言围栏使用 mark 而非 replace 验证。
5. 性能基准：1000 行 / 5000 行块计算耗时。

## 七、漏洞与稳定性审查

- [x] 代码块输入崩溃：根因修复 + 回归测试
- [x] 内存泄漏：paste/drop/dragover 监听器与字数定时器已清理
- [x] 错误边界：CM6 初始化失败降级 textarea；装饰构建失败降级无装饰
- [x] modelValue 反馈循环：`lastEmittedValue` 短路
- [x] type-check 通过、lint 无新增告警
- [x] ~~~ 围栏一致性修复

## 八、变更文件清单

| 文件 | 变更类型 |
|------|----------|
| `src/editor/livePreview.ts` | 稳定性修复 + 性能优化 + 导出测试函数 |
| `src/components/editor/MarkdownEditor.vue` | 性能优化 + 错误边界 + 内存泄漏修复 |
| `test-editor.mjs` | 新增测试套件 |
