# AGENTS.md — MySite 工程约定（v1）

> 2026-11-12 由 `.trellis/tasks/11-12-takeover-survey/AGENTS.draft.md` 经评审提升生效。
> 本文件只放最小约定集；新增纪律先跑通验证再沉淀进来。

## 0. 先读哪

- 项目地图：`docs/MAP.md`（模块/入口/数据流/命令，带证据指针）
- 编码约定：`.cursor/rules/`（架构、后端、前端、journal、ragent 五份，仍然有效，本文件不重复）
- 设计全貌：`CLAUDE.md`（注意：部分数字已漂移，冲突以代码和 MAP 为准）

## 1. 验证命令（动代码前必须能跑）

```bash
# 基础设施（MySQL/Redis/PG 起齐，后端测试依赖真实库）
cd docker && docker compose up -d

# 后端
./mvnw compile          # 编译必须过
./mvnw test             # 已知不闭环：5 个 @SpringBootTest 需 docker 全量起；补齐 hermetic 测试进行中

# 前端（mysite-frontend/）
npm run type-check      # vue-tsc，必须过
npm run lint            # oxlint+eslint（注意：默认 --fix 会改文件，提交前跑）
npm run build           # 构建必须过
```

无 CI。部署构建 `./deploy/deploy.sh` 内部 `-DskipTests`——**部署前必须本地先过 `./mvnw test`**。
前端当前无单测框架；playwright 已装未用，新测试落地前先用 `type-check + lint + build` 当网。

## 2. 改动纪律（先讨论再动手的情形）

以下任一命中，**先写方案/列消费点再改**，禁止边改边想：

1. 动 `WebSecurityConfig`、`CorsFilter`、`security/`、`AuthController`/`AuthServiceImpl` 的任何行为（公开面/认证）。
2. 新增/放开 permitAll 端点，或改动任何匿名可达路径（RAG chat、comments like、search、conversations）。
3. 动共享层：`commons/`、`Result`、mapper XML 公共 SQL、`useMarkdown`/`useChatMarkdown`、axios `client.ts`、`api/rag.ts`。
   → 先 `grep -rn` 全部调用方，把消费点列进方案。
4. DB schema 变更（MySQL 走 `docker/init/migrations/` 新文件；PG 走 `RagentSchemaMigration` 幂等 DDL）。
5. `application*.yml` / `docker-compose.yml` / `deploy/` 配置变更；新增 LLM provider/新注入字段要先过成本评估（LLM 计费是硬约束，见 CLAUDE.md 成本节）。
6. 一次性改 >1 个关注点（修 bug + 改名 + 升依赖必须拆 commit）。

## 3. 硬约束（沿用既有约定，违者必返工）

- **密钥零入库**：任何 secret 只走 `${ENV:}` / 本地 `.env`（已 gitignore）。`.env.example` 只写占位符。提交前 `git diff --cached | grep -iE 'key|secret|password'` 自查。（历史已泄露项见 `.trellis/tasks/11-12-takeover-survey/risks.md` R1）
- Snowflake `Long` ID：DTO 一律 `@JsonSerialize(ToStringSerializer)`，前端类型一律 `string`。
- 响应统一 `Result`/`Results`，错误走 `GlobalExceptionHandler`，不裸抛、不吞（catch 必须落日志或转业务码；RAG「失败静默」仅限已列明的降级点）。
- 角色只认 `ADMIN/CREATOR/USER`；`/api/journal/**` 仅 ADMIN 且按 `user_id` 隔离。
- **commit 作者=用户本人**，commit message 禁止 `Co-authored-by`（沿用 .cursor 硬约束）。
- 一次 commit 一个关注点；commit 前跑 §1 验证命令 + `git diff` 自查。

## 4. 留痕约定（手写复刻 trellis 轻量版）

- 任务级：`.trellis/tasks/<MM-DD>-<名>/prd.md`——记「遇到什么问题/怎么解决的/验证结论/后续方向」。讨论型任务也建（可只有 prd）。
- 广播位：`.trellis/CHANGES.md` 尾追加一行 `日期 | 事件 | → 指针`；开工先 `tail -30` 增量补课。
- 归档：`task.py` 脚本暂缺（手写复刻期），完成即把任务目录移 `.trellis/tasks/archive/<YYYY-MM>/` 并在 prd 补 `## Resolution`。
- 经验沉淀：踩坑→最小规则进本文件；成体系了再考虑 `trellis init` 拿全套（spec 分层/skill 路由/journal）。

## 5. 明确不做（防 AI 自作主张）

- 不格式化全仓、不批量改名、不升级依赖大版本（单独任务再做）。
- 不在摸底/非部署任务里动 `study-journey/`（它是独立仓库，有独立工作流）。
- 不为「看起来更干净」删任何未读过的文件；删除前先列消费点。
- 不把 `.env`、真实 IP/域名口令写进任何提交（含 docs、prd）。
