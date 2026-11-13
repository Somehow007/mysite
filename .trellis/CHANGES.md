# CHANGES — 跨会话广播位

> 用法：开工先 `tail -30` 增量补课；落地即尾部追加 `日期 | 事件 | → 指针`，不写细节；只留最近 ~100 条。
> 本文件 2026-11-12 由接管摸底任务建立（手写复刻 trellis 轻量版，见 `.trellis/tasks/11-12-takeover-survey/`）。

- 2026-11-12 | 接管摸底三件套落地：docs/MAP.md + 风险清单 + AGENTS.draft.md；最高危=公开库含 DB/JWT 明文密钥待轮换 | → `.trellis/tasks/11-12-takeover-survey/`
- 2026-11-13 | 阶段1立约完成：AGENTS.md 生效（9e531d7）、git 基线清零（5 commits：gitignore嵌套repo/图片草稿修复/RAG阅读上下文/批量docs/三件套），工作树干净 | → 同任务 prd + `git log --oneline -5`
- 2026-11-13 | 待办提醒：P0-R1 密钥轮换（DB+JWT+个人惯用密码外溢排查）与 repo visibility 决策仍在服务器/GitHub 侧待办 | → `.trellis/tasks/11-12-takeover-survey/risks.md` R1
