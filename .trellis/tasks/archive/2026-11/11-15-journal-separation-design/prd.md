# 11-15 手帐 v6 设计 + 从 mysite 剥离方案（指针任务）

## 问题存档

- **时间**：2026-11-15
- **需求**：手帐 UI 重做（考研向）+ 手帐从博客项目剥离（独立约束/广播/trellis、数据结构重构、迭代方案、技术栈评估）。
- **边界**：mysite 仓库**零代码改动**；全部产物在 `study-journey/`（独立仓库，mysite 已 gitignore）。

## 产物（事实在手帐仓库，本处只做指针）

- 手帐任务记录：`study-journey/.trellis/tasks/archive/2026-11/11-15-journal-v6-design/prd.md`
- 手帐独立规范：`study-journey/AGENTS.md`、`study-journey/.trellis/CHANGES.md`
- 设计文档：`study-journey/docs/{MAP,architecture,data-model,separation-plan,roadmap}.md`
- UI 概念稿：`study-journey/design/concepts-v6/`

## 对 mysite 的影响（未来）

- `study-journey/docs/separation-plan.md` §S3：切换时 nginx 新增 `/journal/api/` → 127.0.0.1:8090。
- §S4：切换稳定 14 天后，在 mysite 开任务移除 journal 模块，消费点 15 项已列（47 个 java 文件、MapperScan、WebSecurityConfig、ErrorCode A11、测试、schema、deploy 脚本、AGENTS/CLAUDE/cursor rules/MAP 文档等）。
- 在此之前 mysite 的 journal 模块与 `.cursor/rules/journal.mdc` 仍有效，**不要提前删除**。

## Resolution

- **日期**：2026-11-15 归档。mysite 侧无待办，等手帐仓库推进到 M4/M5 时再开 mysite 任务。
