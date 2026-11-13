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

## Resolution（待归档时补）

阶段 0 已完成，三件套餐齐；待用户评审后进入阶段 1。
