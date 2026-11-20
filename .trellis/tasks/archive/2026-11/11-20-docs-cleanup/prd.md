# 11-20 docs-cleanup — AI 产出文档审计与整理

## 背景

`docs/` 累计 26 篇 AI 产出文档（~2 万行），大量已完成的一次性方案/计划/验证报告与现役文档混杂；根目录 README/CLAUDE/CHANGELOG 均有不同程度漂移。用户要求：评审哪些该删、哪些重复该整合，并按「低质删 / 高质归档 / 同模块迭代文档版本化归档 / 设计稿迭代产物同上」执行。

## 处理结果

### 删除（8 篇，无保留价值）
- `docs/Artalk部署文档.md` — Artalk 已从项目移除（评论走 `t_comment`）
- `docs/验证报告.md`、`docs/ui-refactor-next-steps.md`、`docs/前后端优化生产指导文档.md` — 一次性产物/引用不存在脚本的过时 runbook
- `mysite-frontend/editor-fix-report.md` — 描述已被 Milkdown 替换的 CM6 代码
- `mysite-frontend/README.md` — Vite 模板原文
- `CHANGELOG.md` — 07-29 停更，DESIGN.md + `.trellis/CHANGES.md` 已接管
- `docker/README.md` — 严重过时且与 verify.sh/AGENTS.md 重复

### 归档 `docs/archive/`（按模块分目录，迭代文档带版本号）
- `rag/`：ragent-project-summary、ragent-chunking-strategy（上游学习笔记）、integration-design-v1、integration-plan-v1、frontend-design-v1、integration-log
- `collections/`：implementation-plan-v1、delivery-v1
- `frontend/`：v1-博客前端重构开发计划 → v2-前端多主题切换优化计划 → v3-UI重构设计方案（+v3-mockups/ 5 个 HTML）→ v4-editor-redesign-plan；`配色/` 9 张参考图随 v2 归档
- `features/`：图片管理功能开发方案-v1、study-journey-integration、elasticsearch-可插拔-v1（两篇合并）
- `ops/`：deploy-pg.md

### 现役保留（docs/ 根目录 6 篇 + ragent/ 笔记）
MAP.md、DESIGN.md、DEPLOYMENT_GUIDE.md、分类模块API文档.md、error-code-reference.md、ragent/RAG问题与方案笔记.md

### 同步更新的现役文档
- `README.md` 重写：补 RAG/手帐子系统、SB 3.0.7→3.5.7、表数 13→14+6、角色 Developer/User→ADMIN/CREATOR/USER、删虚构 MIT License（无 LICENSE 文件）、补 PG/LLM 配置、docs 结构
- `CLAUDE.md`：SB 版本、MySQL 14+6 表、PG 13 表（补 6 张 llm_*）、删 `-Pproduction`、文档清单改指 archive
- `docs/DEPLOYMENT_GUIDE.md` v2.1：删 Artalk 全部内容（§2.9 整章+env/端口/验证/运维引用），补 RAG PG/LLM/SITE env 配置节，compose 服务改 mysql+redis+postgres 三件套，注明 deploy.sh 为推荐路径
- `docs/error-code-reference.md`：补 A08 图片/A09 评论/A10 合集/A11 手帐/A12 RAG 全部错误码 + A03 导入 7 码，A06 角色名修正，模块表补 08-12
- `docs/MAP.md`/`docs/DESIGN.md`：docs/ 现状描述与文档引用路径更新

### 引用修复
- `deploy/server-deploy.sh`、`MarkdownWysiwygEditor.vue`、`calloutPlugin.ts`、`createEditor.ts`、`__fixtures__/README.md` → 新 archive 路径
- 归档内部互链：rag 三篇互链、v3 mockups 引用、v2 配色路径、log→design 引用

### 顺带安全修复
- `docker/.env.example`：`MYSQL_ROOT_PASSWORD`/`ELASTIC_PASSWORD` 真实密码 → 占位符（AGENTS.md 密钥零入库；compose 有 `:-` 默认值兜底，本地已有数据卷不受影响）

## 验证
- `grep` 全仓：现役代码/脚本中无指向已删/旧路径文档的引用（.trellis 历史 prd 除外，属时点记录）
- 归档内部 `.md` 相对链接逐一核对有效
- 文档改动不涉及代码，无需跑 verify.sh

## 后续方向
- `study-journey/` 空目录（仅 .vite 缓存，未跟踪）可择机删除
- error-code-reference 已声明「代码为准」，后续新增错误码需同步本文档或考虑从枚举生成

## Resolution

已完成并经用户二次走查（2026-11-20）。走查发现并已修正：CLAUDE.md Ollama 优先级 5→9、MAP.md §1 表数（20/6/13）与 §6 漂移描述、DEPLOYMENT_GUIDE §2.7 构建命令（server-deploy.sh 实为 `-Dmaven.test.skip=true`）、README 主题数 8→10（补纸本昼/夜）、DESIGN.md 一处裸文件名引用。归档文档链接显示文字已与新文件名对齐，归档内无旧文件名残留。

用户决定：`配色/` 9 张图维持 gitignore 不入库（本地资产）；`study-journey/` 空目录（仅 .vite 缓存）已删除。

安全修复：`docker/.env.example` 真实密码 → 占位符。

验证：`grep` 全仓现役代码/脚本零旧路径引用；归档互链逐一核对有效；error-code-reference 与 `ErrorCode.java` 逐条比对一致。
