# 风险清单 — 按「改起来的危险度 × 修复收益」排序

> 2026-11-12 · 摸底版；2026-11-13 起随阶段 3 处置更新状态。
> 状态：✅已修(commit) / ⏸延后(owner决策) / 待办。排序原则：正确性/安全 > 验证缺口 > 结构卫生 > 体验。

## 处置进度（2026-11-13）

| 项 | 状态 | 说明 |
|---|---|---|
| R1 密钥 | ⏸ owner 延后 | repo 保持 public；残余=生产 .env 是否覆盖 JWT_SECRET |
| R2 CORS | ✅ `658a117` | CorsFilter 消费白名单；非白名单 Origin 无 CORS 头；`*` 不带 credentials |
| R3 actuator/swagger | ✅ `6772fa0` | health/info 放行、其余 ADMIN；prod 关 knife4j+springdoc |
| R4 登录限流 | ✅ `55c15f3` | LoginRateLimiter：10 次/10 分/IP（Redis 固定窗口） |
| R5 SSRF 重定向 | ✅ `cb2119b` | NEVER + 手动逐跳 checkSsrf，≤5 跳；残留 DNS-rebind TOCTOU |
| R6 SVG 上传 | ✅ `82dc5da` | 三处白名单+前端去 svg；残留=已落盘 .svg 待 owner 清 |
| R7 验证缺口 | 🔶部分 | verify.sh+hermetic 分层完成；无 CI、deploy 仍 -DskipTests |
| R8 git 基线 | ✅ 阶段1 | |
| R9 嵌套 repo | ✅ `8779ec3` | gitignore |
| R10 useMarkdown XSS | ✅ `8e7dc27` | 渲染末尾 DOMPurify，对齐 chat 管线 |
| R12 BeanUtil 注册拷贝 | ✅ `d30a03f` | 显式赋值；同条其余项（双 markdown 管线/login-test.html/死配置）仍待办 |

## P0 — 先堵（安全隐患，收益极高）

### R1. 公开 GitHub 仓库内含真实密钥 ★最高优先
- **证据**：
  - `src/main/resources/application.yaml`：`datasource.password: Sh20060401`（MySQL root 明文）+ `jwt.secret: mysite-jwt-secret-key-...-2026`（JWT 签名密钥明文）。该文件自 Initial commit `caa0eb9` 起在历史中。
  - `docker/.env.example`：`MYSQL_ROOT_PASSWORD=Sh20060401`、`ELASTIC_PASSWORD=Sh20060401`——把**真实密码**写进示例文件。
  - `deploy/config/.env.example`：`JWT_SECRET=` 直接给出与 application.yaml 相同的真实默认值；`application-production.yml` 的 `${JWT_SECRET:...}` 兜底也是它。
  - GitHub API 实测：`Somehow007/mysite` **visibility=public**（2026-11-12 查）。
- **危险度**：极高。若生产 JWT_SECRET 未覆盖默认值，任何人可伪造 ADMIN token 全面接管站点；MySQL 密码疑似个人惯用密码（撞库风险外溢到其他账号）。
- **修复收益**：极高。
- **处置**：轮换全部值（DB 密码、JWT_SECRET 并重发 token、检查该密码是否复用于他处）；`application.yaml` 改 `${ENV:}` 引用；`.env.example` 全部改占位符；历史泄露已成事实（清历史改不了已 fork/克隆者，**轮换优先于洗仓库**）；考虑 repo 转 private 作纵深。
- **⚠ owner 决策（2026-11-13）：延后处理**。理由（owner 提供）：泄露项是本地开发密码，公网未开放 MySQL 等端口，repo 保持 public 不转 private、暂不动轮换。改为**记入待办**，后续随改动顺手做。
  - 残余注意：JWT 密钥不依赖端口——若生产 `/opt/mysite/.env` 未覆盖默认值，公开仓库里的默认值可直接伪造 ADMIN token 打活站。**唯一值得顺手做的检查**：`grep JWT_SECRET /opt/mysite/.env`（覆盖=风险归零，未覆盖=建议仅轮换此项）。
- **验证**：`curl https://api.github.com/repos/Somehow007/mysite` 看 visibility；`git log -p application.yaml` 确认引入提交；服务器 `cat /opt/mysite/.env` 确认生产是否覆盖了 JWT_SECRET（TBD，需服务器侧确认）。

### R2. CorsFilter 回显任意 Origin + 携带凭证，且使 yaml 白名单成死配置
- **证据**：`config/CorsFilter.java:32-43`——`Access-Control-Allow-Origin` 直接回显请求 Origin（无白名单判断），`Allow-Credentials: true`，`Allow-Headers: *`；`application.yaml`/`application-production.yml` 的 `cors.allowed-origins` **在 Java 中无任何消费者**（grep 全仓库 0 命中）。
- **危险度**：中高。当前 token 走 `Authorization` 头+localStorage（非 cookie），实际可利用面收窄；但回显 Origin+Credentials 属于教科书级错误配置，且给维护者「已配白名单」的错觉。
- **收益**：高（改成真正消费 `cors.allowed-origins`，或确认全靠 nginx 同源后删除该 filter）。
- **验证**：`curl -sI -H 'Origin: https://evil.example' http://localhost:8081/v1/articles | grep -i access-control` → 看是否回显 evil 域。

### R3. 生产暴露面：actuator + swagger 公开
- **证据**：`WebSecurityConfig:78-84` permitAll `/actuator/**`、`/doc.html`、`/swagger-ui/**`、`/v3/api-docs/**`；`application-production.yml` management 仅收敛到 `health,info,metrics` 但仍匿名可达；`knife4j.enable: true` 单 profile 全局开。
- **危险度**：中。metrics 泄内部指标；API 文档全公开降低攻击门槛。未达 P0 是因为 exposure include 已收窄（无 env/heapdump）。
- **收益**：高（一行 matcher 收紧或生产 profile 关 knife4j）。
- **验证**：`curl https://<生产域>/actuator/metrics`、`/v3/api-docs` 匿名可达性实测（TBD-服务器侧）。

### R4. 登录无频次限制 → 在线爆破面
- **证据**：`/v1/auth/login` permitAll（WebSecurityConfig:53）；全仓库限流只存在于 `ImageServiceImpl`/`ChatRateLimiter`/`CommentServiceImpl`（grep `RateLimit|attempt` 无 login 命中）；`data.sql` 种子含 `admin`（DEVELOPER→ADMIN 映射）——攻击者知道必有 admin 账号。
- **危险度**：中高。bcrypt 校验不慢，公网+无锁定+无验证码 = 可长时间字典爆破。
- **收益**：高（Redis 计数限流即可，现成模式可抄 ChatRateLimiter）。
- **验证**：脚本连发 20 次错误密码登录，观察是否仍 200/业务码而非 429。

### R5. SSRF：图片 URL 上传的重定向绕过
- **证据**：`ImageServiceImpl.uploadImageByUrl` — `checkSsrf`（718-740，拦 loopback/内网/链路本地，✓做得对）只作用于**初始 URI**；`HttpClient.followRedirects(NORMAL)`（~250 行）后续重定向目标不再过 checkSsrf → 公网 URL 302 到 `http://169.254.169.254/` 或内网可绕过。
- **危险度**：中。需登录用户触发，且只拉图片型响应落地（不是任意回显）；可探测内网存活。
- **收益**：中高（fetch 时改 `NEVER` 或逐跳校验）。
- **验证**：本地起个 302→127.0.0.1 的端点，用 upload-url 指过去观察是否被拦（TBD-建议补测试）。

### R6. SVG 上传 = 同源存储型 XSS 面（叠加 localStorage token）
- **证据**：`image.upload.allowed-types` 含 `image/svg+xml`；`ImageServiceImpl:74` 白名单含 svg、`:196` webp 转换跳过 svg、`:664` 魔数校验对 svg 直接放行；`/uploads/**` permitAll 同源直出（WebSecurityConfig:86，nginx `mysite.conf:33`）。token 在 localStorage → XSS 即盗 token。
- **危险度**：中。上传需认证（anyRequest authenticated），但任何注册用户可传带 `<script>` 的 svg，管理员点开链接即中招→ADMIN 失陷。
- **收益**：高（方案二选一：白名单去 svg；或 svg 强制 `Content-Disposition: attachment`/独立域）。
- **验证**：上传 `<svg onload=alert(1)>` 后直接访问 URL 看响应头与执行。

## P1 — 改动前的「尺子」缺口

### R7. 验证不闭环：无 CI、测试非 hermetic、部署跳测试
- **证据**：无 `.github/`/流水线；无 `src/test/resources`（测试复用 application.yaml 真实库）；5 个 `@SpringBootTest` 需 docker 全量起；`deploy.sh` 用 `-DskipTests`；前端无单测脚本。
- **危险度**：高（任何翻新都缺回归网）×**这是翻新前置条件**。
- **收益**：按 playbook 阶段 2——先补「一键验证脚本」（docker up→mvnw test→vue-tsc→lint），再补关键路径 characterization test（auth、文章 CRUD、RAG SSE、手帐写路径）。
- **验证**：`./mvnw test` 在无 docker 时应明确报哪些测试失败（TBD-实跑一次确认失败集）。

### R8. git 基线缺失：39 项未提交（19 modified + ~20 untracked 含 2 个新源码文件+2 个新测试+17 份 docs）
- **证据**：`git status --short` 全量清单（RagChatController/RagChatService/PromptTemplate/QueryRewriter/useChat 等在改中）。
- **危险度**：高——接手改动会混进这堆 in-flight 改动，diff 不可审。
- **收益**：高（先让 owner 提交/暂存出干净基线；摸底文档本身也 commit）。
- **验证**：`git status` 为空后再动代码。

### R9. `study-journey/` 嵌套独立仓库
- **证据**：自带 `.git`（remote=Somehow007/study-journel.git，pkg=`study-journal@0.2.0`），不在 `.gitignore`、不是 submodule，`git status` 显示 `??`。
- **危险度**：中高——`git add .` 会触发 embedded-repo 警告/产生 gitlink 幽灵条目；clone 此仓库的人拿不到手帐代码。
- **收益**：中高（三选一：gitignore 之 / 移出主仓 / 正式 submodule + 文档钉版本）。
- **验证**：`git add -A --dry-run` 看 gitlink 警告。

### R10. 前端 `ArticleContent` v-html 走无净化的 useMarkdown；chat 管线有 DOMPurify
- **证据**：`ArticleContent.vue:103` v-html ← `useMarkdown.ts`（marked，全文无 DOMPurify/sanitize 命中）；`ChatStreamWriter.vue` v-html ← `useChatMarkdown.ts`（`DOMPurify.sanitize` ✓）。
- **危险度**：中低——文章只有 ADMIN/CREATOR 能写（信任度高），且 marked 默认转义部分 HTML 的程度 TBD；但纵深上缺一道，且与 chat 管线标准不一致。
- **收益**：中（useMarkdown 尾部加一道 DOMPurify，对齐 chat）。
- **验证**：发文案内嵌 `<img src=x onerror=alert(1)>` 走查渲染产物（TBD）。

## P2 — 结构卫生（动到才处理）

### R11. 匿名接口的滥用/越权面
- `/v1/rag/conversations?visitorId=`：**visitorId 即唯一凭证**（RagChatController:139-146），且 `log.info` 明文打 visitorId（:90）→ 日志泄露=会话泄露；UUID 不可枚举但可被泄露。TBD：是否该换 capability token。
- `/v1/comments/*/like` POST permitAll：匿名点赞 dedup 口径 TBD（匿名无法稳定去重→刷赞面）。
- `/v1/search/**` permitAll：LIKE 全模糊搜索无门槛 → 放大查询成本，无独立限流（TBD）。

### R12. 明面重复与死代码
- 双 markdown 管线：`useMarkdown.ts`(~330 行) vs `useChatMarkdown.ts`(~330 行)，marked 实例/Renderer/数学保护/Callout 段大体重构——流式场景需要分叉但公共段可抽。
- `login-test.html`：根目录遗留手工测试页，硬编码 testuser2/123456，已追踪。
- `playwright` devDep + `eslint-plugin-oxlint` 等装了未用；`jpa.show-sql` 出现在 prod yml（项目无 JPA）；`cors.allowed-origins` 死配置（同 R2）。
- `BeanUtil.toBean(requestParam, UserDO.class)`（AuthServiceImpl:113）全量拷贝注册字段：当前 DTO 无 role 安全，**DTO 加字段即变越权面**——改显式赋值。

### R13. 运维卫生
- `application.yaml` 默认 `log-impl: StdOutImpl`（全量 SQL 打日志，dev 吵 prod 已被 prod yml 覆盖为 NoLogging ✓）；MySQL URL `allowMultiQueries=true` dev/prod 同（放大器，配合参数化注入面虽小但建议关）。
- `docker-compose` MySQL `3306:3306`、PG `5432:5432` 绑全网卡（dev 便利；若服务器也跑这套 compose → DB 公网裸奔，TBD-服务器侧确认）。
- `data.sql` 种子 admin/testuser 真实密码强度 TBD（bcrypt 哈希无法反推，需在 staging 试登或问 owner）。
- CHANGELOG 停更 3.5 个月；CLAUDE.md/.cursor 架构文档多处数据漂移（见 MAP §6）。

## TBD 汇总（不许猜，逐项问/验）
1. 生产 `/opt/mysite/.env` 是否覆盖了 JWT_SECRET/DB 密码（决定 R1 实际爆炸半径）——服务器侧查。
2. 仓库是否曾被 clone/fork（GitHub Insights）——影响轮换紧迫度评估。
3. `./mvnw test` 在无 docker 环境的真实失败集——跑一次。
4. 评论匿名点赞去重口径、search 是否有限流——读 CommentServiceImpl.likeComment/ArticleSearchService。
5. 手帐生产是否由本仓库 journal 后端 + study-journey 前端构成、版本如何对齐——server-deploy.sh 显示是，但版本锁定方式未见。
6. useMarkdown 对文章内嵌 HTML 的实际放行为何（marked v18 默认）——发文走查。
7. `visitorId` 会话是否可被遍历利用的更细口径（未登录 conversations 列表究竟暴露什么）。
8. ES 在生产是否启用（prod yml 未见 elasticsearch 段，TBD）。

## 「看起来没问题」且已验证的项（防伪证）
- MyBatis 全部 `#{}` 参数化，无 `${}` 拼接 → SQL 注入面低。验证：`grep '\${' src/main/resources/mapper/ src/main/java` 0 命中。
- journal 各 service 以显式 `userId` 参数 + `eq(UserId)` 过滤 → user_id 隔离落实。抽查：`CustomMoodServiceImpl` 全方法。
- `.env`/`uploads/`/`target/`/`node_modules` 均 gitignore 且 `.env` 从未提交（`git log --all -- .env` 空）。
- LLM API key 走环境变量/加密入库（SecretCryptoTest、application.yaml `${..._API_KEY:}`），未硬编码。
- 点赞有 toggle 去重逻辑（CommentServiceImpl:245-260，登录态口径）。
- 注册 DTO 无 role 字段 + DB 默认 USER → 注册越权当前安全（但 BeanUtil 拷贝模式脆弱，R12）。
