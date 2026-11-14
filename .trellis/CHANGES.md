# CHANGES — 跨会话广播位

> 用法：开工先 `tail -30` 增量补课；落地即尾部追加 `日期 | 事件 | → 指针`，不写细节；只留最近 ~100 条。
> 本文件 2026-11-12 由接管摸底任务建立（手写复刻 trellis 轻量版，见 `.trellis/tasks/archive/2026-11/11-12-takeover-survey/`）。

- 2026-11-12 | 接管摸底三件套落地：docs/MAP.md + 风险清单 + AGENTS.draft.md；最高危=公开库含 DB/JWT 明文密钥待轮换 | → `.trellis/tasks/archive/2026-11/11-12-takeover-survey/`
- 2026-11-13 | 阶段1立约完成：AGENTS.md 生效（9e531d7）、git 基线清零（5 commits：gitignore嵌套repo/图片草稿修复/RAG阅读上下文/批量docs/三件套），工作树干净 | → 同任务 prd + `git log --oneline -5`
- 2026-11-13 | 待办提醒：P0-R1 密钥轮换（DB+JWT+个人惯用密码外溢排查）与 repo visibility 决策仍在服务器/GitHub 侧待办 | → `.trellis/tasks/archive/2026-11/11-12-takeover-survey/risks.md` R1
- 2026-11-13 | owner 决策：repo 保持 public、密钥轮换延后（本地密码+端口未开）、commits 不 push；残留唯一项=生产 .env 是否覆盖 JWT 默认值（服务器侧顺手验证） | → `risks.md` R1 决策段
- 2026-11-13 | 阶段2安全网首轮：scripts/verify.sh（quick=hermetic+type-check / full=docker 全量）+ needs-infra tag×5 + characterization tests（Auth 16/CustomMood 11）+ TestTableInfo 修存量顺序依赖测试；quick 全绿 415 tests；存量发现=infra 测试无 test profile 直跑 dev 真实库 | → `.trellis/tasks/archive/2026-11/11-12-takeover-survey/prd.md`
- 2026-11-13 | 阶段2收尾：ArticleServiceImplWriteTest(18)+RagChatControllerTest(11) 补齐文章写路径与 SSE 控制器契约；钉住 kbIds 非法值→500 边界行为；quick 复绿。阶段2完成，剩 full 模式待 Docker | → 同上 prd.md
- 2026-11-13 | 阶段3安全链 7 commits 落地：R2 CORS白名单/R3 actuator+swagger/R4 登录限流/R5 SSRF逐跳/R6 去svg/R10 文章DOMPurify/R12 注册显式赋值；每步过 verify quick | → `risks.md` 处置进度表 + `git log 6e77e94..HEAD`
- 2026-11-13 | 接管任务归档：四阶段完成（摸底→立约→安全网→小步翻新安全链），Resolution 补全；项目进入日常维护态；背景项=双markdown去重/文档漂移/存量svg/JWT.env确认 | → `.trellis/tasks/archive/2026-11/11-12-takeover-survey/prd.md` Resolution
- 2026-11-14 | 漂移修复：11-12-takeover-survey 移入 archive/2026-11/（归档最后一步）；AGENTS.md §1 重写为 verify.sh quick/full + needs-infra×5 + ci.yml + deploy 跑 hermetic 现状；AGENTS/MAP/CHANGES 任务路径指针同步归档位 | → `AGENTS.md` §1 + 本 commit
- 2026-11-14 | 服务器 Phase1 实测：2G 机已超订——swap 用 ~910M（mysite JVM/mysqld 换出最多），MySQL 容器顶格 300MiB cap 97.8%，available 仅 ~432Mi；无 OOM、负载近零；决策点=A cap/调优、B zram、C Xmx、D 迁出另项目 | → `.trellis/tasks/11-14-server-phase1-resource-check/prd.md`
- 2026-11-14 | 服务器 Phase1 处置落地：zram 983MiB(prio100,zstd) 上线 + MySQL performance_schema=OFF（compose 同步 repo/服务器，重建容器）；mysqld 97.8%→73% cap，swap 989→685Mi，available 432→574Mi；Xmx/patrolinspect 未动 | → `.trellis/tasks/11-14-server-phase1-resource-check/prd.md` Resolution
