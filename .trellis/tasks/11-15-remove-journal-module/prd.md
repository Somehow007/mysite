# M5：移除 mysite journal 模块（手帐剥离 S4）

> 路线图：`study-journey/docs/roadmap.md` M5；执行清单：`study-journey/docs/separation-plan.md` §S4。
> **状态更新（2026-11-16）**：M4-3 切流已完成（新系统上线，回滚组件全验证）；原「14 天稳定期」经 owner 讨论**改为 owner 自定观察期**（单人自用项目，14 天系保守约定非技术常数）。**commit 3（deploy 脚本清理）已提前执行**——mysite `c16e1e0`（nginx `/journal/api/` 双块）+ `860976e`（server-deploy.sh 手帐段移除 + backup 注释），mysite 部署通道已解冻。剩余执行 = journal 模块代码/schema/文档删除 + 生产 `sj_*` DROP，等 owner 观察期满意后进行。

## 问题 / 目标

手帐已独立为 `../study-journey`（Go+SQLite，自有部署/鉴权/规范）。mysite 里的 journal 模块（47 java + 装配点 + schema + 部署段）在 14 天观察期无问题后整体下线，博客恢复「两子系统」（博客+RAG）。前端入口链接保留——那是博客指向手帐的唯一入口。

## 消费点复核（2026-11-15 实测，替代 separation-plan §S4 清单）

`rg -il 'journal|sj_|手帐'` 全仓（含 hidden）66 文件命中 = journal 模块自身 49 文件 + 下列消费点。**journal 包外部消费者仅 3 处**（`MysiteApplication` @MapperScan、`SjDayRecordMapper.xml` namespace、`TestTableInfo`），全部随本任务同批删除，无遗漏调用方。

| # | 位置 | 实测内容 | 动作 |
|---|---|---|---|
| 1 | `src/main/java/.../journal/**` | 47 个 java（controller 3 / dao.entity 6 / dao.mapper 6 / dto 24 / service+impl 6 / util 3） | 删除整目录 |
| 2 | `src/main/resources/mapper/SjDayRecordMapper.xml` | 唯一 journal mapper XML（其余 Sj mapper 为接口式） | 删除 |
| 3 | `MysiteApplication.java:8` | `@MapperScan` 数组含 `io.github.somehow.mysite.journal.dao.mapper` | 移除该串 |
| 4 | `config/WebSecurityConfig.java:~172-175` | 注释 + `requestMatchers("/api/journal/**").hasRole("ADMIN")` | 删除规则与注释（命中 AGENTS §2.1，先列消费点✅已列） |
| 5 | `commons/framework/errorcode/ErrorCode.java:125-137` | A11 段 12 个 `JOURNAL_*` 码（A110001/A110100-104/A110200-205） | 删除；已验证 journal 包外零引用（rg 全 src 仅本文件命中） |
| 6 | `config/PrimaryDataSourceConfig.java:62-71` | javadoc 提「journal 模块」「2026-07-27 手帐 upsert 线上事故」 | 改通用表述，**事故教训保留**（PG 事务管理器抢占主库的机制说明与手帐无关，仍然有效） |
| 7 | `src/test/.../journal/service/impl/CustomMoodServiceImplTest.java` | journal 唯一测试 | 删除（连同 `src/test/.../journal/` 空目录） |
| 8 | `src/test/.../support/TestTableInfo.java:18-23,57-58` | import + 注册 6 个 `Sj*DO` | 移除 import 与数组项 |
| 9 | `docker/init/schema.sql:288-408` | §14–19 六张 `sj_*` 表（day_record/learning_item/custom_mood/goal/goal_task/goal_checkin） | 删建表段；新装环境不再建手帐表 |
| 10 | `docker/init/journal-schema.sql` | 111 行增量脚本，6 个 CREATE TABLE（头注释「3 张」已过期） | 整文件删除 |
| 11 | `deploy/server-deploy.sh` | `JOURNAL_DIR/REPO/WEB_ROOT`（9-15）、`step_git_pull_journal`（91-109）、`step_build_journal`（142-152）、`step_deploy_journal`（239-245）、健康检查手帐段（343-366）、调用点（405/408/413）、版本日志（421-424） | 全段删除；⚠️ 此前已有「M4 前勿跑」警告，本任务落地后该警告自然失效 |
| 12 | `deploy/backup-mysql.sh:2` | 注释「blog t_* + journal sj_*」 | 改注释（sj_* 归档后 mysqldump 不再覆盖） |
| 13 | `deploy/nginx/mysite.conf:60,189` | `/journal/` alias 块出现 **两处**（HTTP+HTTPS server） | **保留**；`/journal/api/` 代理块已加（`c16e1e0`，2026-11-16 已同步服务器并 reload 生效） |
| 14 | `mysite-frontend` AppHeader.vue（115/284 两处）、DashboardSidebar.vue（186） | 「手帐」链接 `href="/journal/"`，`isAdmin` 门控 | **保留不动**——博客→手帐唯一入口，剥离后仍指向新应用 |
| 15 | `AGENTS.md` | §3 硬约束 journal 条、§5 study-journey 条款 | 改「手帐已独立 → ../study-journey」指针 |
| 16 | `CLAUDE.md` | journal 包结构段、DB schema 手帐段、角色表无关但架构图含 | 删手帐段落/改指针 |
| 17 | `.cursor/rules/journal.mdc` | 整文件 | 删除 |
| 18 | `.cursor/rules/mysite-architecture.mdc` | 三子系统→两子系统、MySQL 行 sj_*、入口清单 journal/controller | 更新 |
| 19 | `.cursor/rules/backend-conventions.mdc:45` | 权限表行「手帐 /api/journal/** 仅 ADMIN」 | **清单外新发现**，删行 |
| 20 | `docs/MAP.md` | 多处：概述（9-12）、仓库表（23/25/28）、子系统表（36-47）、部署（62/84/91-93/98） | 子系统表手帐行改「已剥离→study-journey」；表数等顺手校正（journal-schema.sql 实为 6 表非 7） |
| 21 | `docs/study-journey-integration.md` | 集成日志全文 | 顶部加「已于剥离完成日归档，历史文档」标注，正文不动 |
| 22 | `docs/DESIGN.md` | 历史决策日志含手帐条目（227/254-273） | **保留原样**——它是带日期的历史记录，不是现状文档 |
| 23 | `.gitignore:54` | `study-journey/` | 保留（本地仍可能嵌套检出） |

**已验证零命中（不需要动）**：`application*.yml`、`pom.xml`、`docker/docker-compose.yml`（mysql 服务本就不挂 init 脚本）、`scripts/verify.sh`、`.github/workflows/`、`docker/init/migrations/`（仅 collection-visibility）、`docker/init/data.sql`、前端 `api/`/`router/`/`views/`（手帐前端早已整体迁出，仅剩导航链接）。

## 方案

闸门通过后按「一次一个关注点」拆 commit（~~commit 3 已提前完成~~）：

1. `refactor: 移除 journal 后端模块` — #1-8（journal 包 + mapper XML + MapperScan + 安全规则 + ErrorCode A11 + 测试 + TestTableInfo）。`./scripts/verify.sh quick` 必须绿。
2. `chore(db): 移除 sj_* 建表脚本` — #9-10。
3. ~~`chore(deploy): server-deploy.sh 移除手帐部署段`~~ — **已提前执行**（`860976e` + `c16e1e0`，2026-11-16；nginx `/journal/api/` 块已在服务器生效）。
4. `docs: 手帐剥离指针化` — #15-22 + 本仓库 `.trellis/CHANGES.md` 记一行；study-journey 侧 CHANGES 同步一行（跨仓库约定）。

**生产手工步骤（不进 commit，需 owner 确认，M5 执行日操作）**：
- 生产 MySQL：`mysqldump` 归档 6 张 `sj_*` 表 → `DROP`（数据已在 SQLite，归档是双保险）。
- 核对服务器 nginx 实际配置含 `/journal/api/` 块（M4-3 产物）；`/v1/*` 回归冒烟。
- 观察项结转：备份 cron（`journal-server backup`）与 RSS 稳定属 study-journey S5 观察清单，不在本任务。

## 验收

- `./scripts/verify.sh quick` 绿（hermetic，不依赖 docker）。
- `rg -i 'journal|sj_|手帐' src/` 零业务命中（历史 .trellis/docs 记录除外）。
- 前端 `npm run type-check` 绿（链接保留，无代码改动预期）。
- 生产 `/v1/*` 回归 200；`/journal/` 手帐页与 `/journal/api/` 链路不受影响（前端链接未动）。

## 风险 / 注意

- **最大风险是提前执行**：旧 `/api/journal/*` 是回滚底座。删代码进 main ≠ 立即影响生产（要等下次 server-deploy.sh），但博客在活跃开发期，任何一次例行部署都会把删除带上线——这正是 14 天闸门存在的原因。
- `server-deploy.sh` 手帐段删除后，脚本不再拉 study-journey 仓库；若服务器 `~/project/study-journey` 仍被 journal.service 使用则不动它（那是 study-journey 自己的部署物料）。
- dev 本地 MySQL 若已有 `sj_*` 表会成为孤儿表（无害）；不为此加 drop migration——生产 DROP 走手工归档流程，本地库可重建。
- ErrorCode 删除 A11 段后错误码段位空缺属正常（错误码不复用）。
