# 11-14 服务器 Phase1 资源实测

## 背景

承接 RAG→Agent 演进调研（结论：不换栈、保留 Java 单体，1–2 个月内做 Agent）。
Phase1 = 上生产服务器实测内存余量，判断是否需要先调资源配置。
服务器：`ubuntu@124.222.65.169`（SSH 直连，本机 `~/.ssh/id_ed25519`）。

## 实测结果（2026-11-14）

### 整机

- Ubuntu, kernel 6.8.0-101, uptime 209 天, load ~0.05（CPU 无压力）
- **MemTotal 1.9GiB**（非满 2G）
- free: used 1.5Gi / free 89Mi / buff+cache 533Mi / **available ~432Mi**
- **Swap: /swap.img 1.9G 文件，已用 ~910–916MiB（45%）**，swappiness=60，无 zram
- SwapCached 255MiB → 存在活跃换页回读（thrash 信号）
- 磁盘 50G 用 49%，健康
- 近 14 天 kernel log 无 OOM kill；3 个 docker 容器 up 13 天稳定

### 进程级（RSS + VmSwap，KB）

| 进程 | RSS | Swap | 说明 |
|---|---|---|---|
| mysite.jar :8081 | ~313M | **~220M** | Xmx512m，systemd 外 nohup，production |
| mysqld（docker） | ~277M | **~300M** | **cgroup cap 300MiB，实测 97.8% 顶格** |
| patrolinspect.jar :8082 | ~274M | ~70M | Xmx256m，systemd `patrolinspect.service`（另一项目） |
| node qqbot harness | ~200M | — | `/opt/deepseek-harness dsh --profile qqbot`，实测时已退出（瞬时进程） |
| pgvector / redis / nginx | 小 | — | pgvector cap 256M 实际 ~16M；redis 无 cap 实际很小 |
| dockerd+containerd / QCloud 监控件 | ~120M | — | YunJing、barad_agent 等腾讯云 agent |

### 关键结论

1. **"暂时没感觉卡"≠没压力**：~910MiB 已换出到磁盘 swap，且换出最多的恰是两个大户——mysite JVM（~220M）和 mysqld（~300M）。负载低谷时无感，Agent 上线后 heap/流式缓冲增加会进一步收紧。
2. **MySQL 容器顶格**：cap=300MiB（docker inspect 确认），mysqld RSS ~283M + swap 300M，cgroup 持续回收。innodb-buffer-pool 仅 64M，说明大头在连接/每线程缓冲与页缓存，非 buffer pool。
3. **swap 已存在** → Phase1 原预案"若无 swap 考虑加 swapfile"不适用；但 zram（压缩内存 swap，优先级高于磁盘 swap）仍可显著降低 JVM/mysql 换页延迟，属可选优化。
4. **结构性问题**：单台 2G 跑 mysite + patrolinspect + qqbot + MySQL/PG/Redis + nginx + QCloud agents，已超订。CPU/磁盘都宽裕，纯内存约束。

## 决策点（待 owner 定）

- [ ] A. MySQL：容器内存 cap 上调（如 384–512M）**或** mysqld 调优降占用（performance_schema 等）。上调需先腾内存，二者互斥取舍。
- [ ] B. zram：加 ~1–2G zram swap（高优先级），把换页从磁盘挪进压缩内存。低风险可逆，系统级改动需确认。
- [ ] C. mysite `-Xmx512m` 是否下调：RSS 321M 说明 committed heap ~256–300M，Agent 前不建议动；也可保持。
- [ ] D. 结构性：Agent 期内存预算要预留；patrolinspect/qqbot 是否值得迁出本机（用户另一项目，需 owner 定）。

## Resolution

owner 授权"你来定"。执行结果（2026-11-14）：

1. **zram 已上线**：`apt install systemd-zram-generator` + `/etc/systemd/zram-generator.conf`（zram0 = ram/2 ≈ 983MiB，zstd，swap-priority=100）。重启后自动生效。新换页全部先进 zram（压缩内存）而非磁盘 swap。
2. **MySQL `performance_schema=OFF`**：实测 P_S 相关 instrument 占 ~150–300MB（总 instrumented 547MB）。compose `command` 加一行（repo `docker/docker-compose.yml` 与服务器 `/home/ubuntu/project/mysite/docker/docker-compose.yml` 同步，服务器留 .bak-20261114），`docker compose up -d mysql` 重建容器（停机 ~5s）。
3. **效果**：mysqld 293→220MiB/300MiB（97.8%→73%）；整机 swap 占用 989→685MiB（旧 mysqld 换出页随容器重建释放 ~300MB）；MemAvailable 432→574Mi；`/v1/articles` 200 / 0.23s 验证链路正常。
4. **未动**：mysite `-Xmx512m`（RSS 303M 合理，Agent 期再评估）；patrolinspect/qqbot（另一项目，未动）；磁盘 swap 既有 685M 不迁移（swapoff 需 ~700M 空闲 > 可用 574M，不安全；新压力走 zram 已够）。

**遗留观察项**：
- compose 中 mysql `3306:3306`、pg `5432:5432` 绑 0.0.0.0（靠云安全组挡）；redis 已绑 127.0.0.1。收紧需确认无外部直连依赖，另行处理。
- 服务器 MySQL 还承载 `patrol_inspect` 库（patrolinspect 共用实例）——P_S 关闭对两边都无影响（均不依赖 P_S）。
- Agent 上线前建议复测 `free -h`/`docker stats`；若 mysite heap 需求涨，优先考虑迁出 patrolinspect/qqbot 而非继续压 MySQL。

## 证据指针

- SSH 实测命令输出见会话记录；关键值：`free -h`、`swapon -s`、`docker stats`、`docker inspect .HostConfig.Memory`、`/proc/*/status VmRSS/VmSwap`、P_S `memory_summary_global_by_event_name`
- compose cap 来源：`docker/docker-compose.yml` `deploy.resources.limits.memory: 300M`（repo 与服务器一致）
