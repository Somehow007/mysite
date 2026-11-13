# 存量项目接管·阶段 0：摸底三件套

## 问题存档

- **时间**：2026-11-12
- **任务来源**：qqbot-brain `tasks/11-12-brownfield-takeover` 定调的四阶段接管 playbook（摸底→立约→安全网→小步翻新），本任务=阶段 0 在 mysite 的执行。
- **边界**：只读——未改任何代码、未装依赖、未格式化。产出为文档与草案。

## 遇到了什么问题

mysite 是 AI 生成的主力项目：代码层已有部分秩序（CLAUDE.md、.cursor/rules、40 测试类、lint/typecheck 脚本），但缺 agent 中立约定（无 AGENTS.md）、无 CI、无 .trellis、git 有 39 项未提交 in-flight 改动，且仓库是 **GitHub 公开库**。

## 怎么解决的（方法与证据路径）

按 playbook 摸底三件套产出，全部结论挂文件证据：

1. **项目地图** → `docs/MAP.md`（仓库布局/三子系统入口/数据流/外部依赖/命令现状/文档新鲜度）
2. **风险清单** → `risks.md`（P0-P2 排序 + TBD 汇总 + 「已验证没问题」防伪证段）
3. **AGENTS.md 草案** → `AGENTS.draft.md`（最小约定集 5 条：验证命令/地图指针/改动纪律/硬约束/留痕约定；待人审后提升根目录）

摸底方法：结构遍历 + 配置/安全/入口精读 + 注入面 grep（`${}`/动态 SQL）+ 密钥面 grep + git 卫生检查 + GitHub API 实测 visibility。未跑 build/test（边界内，验证方式已逐条写在文档里）。

## 关键结论速览

- 最高危：公开仓库含 MySQL 明文密码 + JWT 签名密钥（P0-R1，轮换优先于清历史）。
- 安全面其余：CORS 回显任意域+credentials、actuator/swagger 公开、登录无限流、SSRF 重定向绕过、SVG 存储型 XSS 面。
- 秩序缺口：无 CI + 测试不闭环（5 个 @SpringBootTest 需真实库）+ 部署 `-DskipTests` + 前端零单测。
- 卫生：`study-journey/` 嵌套独立仓库未 ignore；`login-test.html` 遗留；文档漂移（SB 版本/表数/-Pproduction/CHANGELOG 停更）。
- 已验证良好面：MyBatis 全参数化无 `${}`、journal user_id 隔离、密钥走 env/加密入库、gitignore 覆盖到位。

## Key Decisions

- 目标仓库确认 = mysite（cwd + 无 .trellis/AGENTS.md + 体量大长期维护，排除已有 trellis 的 qqbot-brain/patrolInspect）。
- 交付物落点：MAP 进 `docs/`（用户建议位），风险与 AGENTS 草案留任务目录待审（AGENTS 草案**不**先放根目录——人审是生效闸门）。
- trellis 采用手写复刻轻量版（tasks/ + CHANGES.md），完整 `trellis init` 留作阶段 1 可选项。

## 后续优化方向

1. **阶段 1（立约）**：人审 AGENTS.draft.md → 提升为根 `AGENTS.md`；先让 owner 处理 39 项未提交改动做出 git 基线；决定 study-journey 处置（ignore/移出/submodule）。
2. **阶段 1 并行**：R1 密钥轮换（DB/JWT/疑似个人惯用密码外溢检查）+ repo visibility 决策。
3. **阶段 2（安全网）**：一键验证脚本（docker up→test→tsc→lint）；auth/文章/RAG SSE/journal 关键路径 characterization tests。
4. **阶段 3（小步翻新）**：按 risks.md P0→P2 顺序，一次一个关注点。
5. TBD 清单（risks.md 末尾 8 项）需 owner/服务器侧确认，优先 1/2（决定 R1 爆炸半径）。

## 进展记录

- **2026-11-12 阶段 0**：三件套产出（本目录 + docs/MAP.md）。
- **2026-11-13 阶段 1（立约）**：用户决策 = WIP 由我分组提交 / study-journey 加 gitignore。落地 5 commits：
  - `8779ec3` chore: gitignore 忽略 study-journey/
  - `f77afac` fix(image): 草稿引用暂存图不误删（实时引用判定 + 编辑器 flush）
  - `f03628a` feat(rag): 文章页阅读上下文（articleId→ReadingContext→prompt/rewrite/前端全链路）
  - `bd5fd40` docs: 批量收录 17 份未跟踪设计文档（密钥扫描过，只占位符）
  - `9e531d7` docs: 三件套 + AGENTS.md 生效
  - 验证：`./mvnw compile` `test-compile` `npm run type-check` 全过。工作树已干净 = git 基线达成。
  - 遗留：P0-R1 密钥轮换 + repo visibility（服务器/GitHub 侧，owner 执行）；两条线在飞代码均已完整（无半成品遗留）。
- **2026-11-13 owner 决策（R1）**：repo 保持 public；密钥轮换延后（理由：本地密码、公网未开 DB 端口）；commits 留本地不 push。残余：JWT 默认值是否被生产 .env 覆盖仍待顺手验证（唯一非「端口」性质的泄露项）。本次存档编辑按指示**暂不 commit**。
- **2026-11-13 阶段 2（安全网，进行中）**：
  - `scripts/verify.sh`：`quick`（hermetic：`-DexcludedGroups=needs-infra` + 前端 type-check）/ `full`（需 docker：compose up → 全量 test → lint(no-fix) → build）。chmod +x。
  - 5 个 `@SpringBootTest` 打 `@Tag("needs-infra")`： MysiteApplicationTests / ImageStagingIntegrationSmokeTest / ElasticsearchConfigurationTest / ElasticsearchEnabledIntegrationTest(保留@Disabled) / ElasticsearchDisabledIntegrationTest。
  - 新 characterization tests：AuthServiceImplTest（16 例：登录/注册/刷新/禁用/重复）+ CustomMoodServiceImplTest（11 例：user 隔离/CRUD/幂等导入/dark_colors 兜底）。
  - 修复存量顺序依赖测试：CategoryServiceImplTest、CollectionServiceVisibilityTest 此前依赖与 @SpringBootTest 同 JVM 被顺带填充的 lambda cache；新增 `support/TestTableInfo.initAll()` 统一初始化全部 26 实体表元数据。
  - 验证：`./scripts/verify.sh quick` 绿（415 tests + type-check）。存量洞察：无 src/test/resources → 5 个 infra 测试实际跑 dev 配置真实库（ImageStagingIntegrationSmokeTest 直写 dev 库）；full 模式需 Docker daemon（当前未运行，TBD-3 仍开）。
- **2026-11-13 阶段 2 收尾（characterization tests 补齐）**：
  - `ArticleServiceImplWriteTest`（18 例）：create 参数校验链/默认值(tagIds/合集/索引/缓存/暂存图/RAG事件) + update 部分更新语义/tagIds 三态(null 保留/空清空/非空替换)/归属校验(ADMIN 绕过)/更新后回查缺失降级。
  - `RagChatControllerTest`（11 例）：SSE 事件顺序与 JSON 线格式(meta→content→done, conversationId 序列化为 string)、done/error 后 complete、XFF 取首 IP、kbIds 解析、同步异常→error 事件、会话历史两个端点。SseEmitter Handler 为包级私有接口 → 反射+动态代理注入；executor 用捕获式同步执行。
  - **钉住的现状边界**：`kbIds=非法值` 在进 SSE 流前同步 `Long.parseLong` 抛 NumberFormatException → HTTP 500 而非 SSE error 事件（前端自产 kbIds，正常不触发，记为已知边界）。
  - 最终验证：`./scripts/verify.sh quick` 绿（后端 hermetic 全过 + 前端 type-check）。阶段 2 完成；full 模式待 Docker daemon（TBD-3）。
- **2026-11-13 阶段 3（小步翻新，安全链完成）**：按 risks.md 顺序一次一关注点：
  - `658a117` R2 CorsFilter 消费白名单（非白名单 Origin 无 CORS 头；`*` 规范合规不发 credentials）
  - `6772fa0` R3 actuator 拆分（health/info 放行，其余 ADMIN）+ prod 关 knife4j/springdoc
  - `55c15f3` R4 LoginRateLimiter（Redis 10/10min/IP）+ 抽 ClientIpResolver 消第三处复制
  - `cb2119b` R5 SSRF 重定向逐跳校验（NEVER+手动跟随≤5 跳，包私有方法可 mock 测试；残留 DNS-rebind TOCTOU）
  - `82dc5da` R6 上传白名单去 svg（残留：已落盘 .svg 待 owner 清）
  - `8e7dc27` R10 useMarkdown 末尾 DOMPurify（无 iframe 用法，默认 profile 直用）
  - `d30a03f` R12 注册 BeanUtil.toBean → 显式赋值
  - 每 commit 均过 `verify.sh quick`。剩余：R11（匿名接口口径需决策）、R12 余项（login-test.html/双 markdown 管线）、R13 运维卫生、CI 缺失。

## Resolution（待归档时补）

阶段 0+1 完成；阶段 2 安全网（一键验证脚本 + 关键路径 characterization tests）与 R1 密钥轮换为下一步。
