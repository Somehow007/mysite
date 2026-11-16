# 测试债跟进修复（2026-11-16）

> 来源：后台纸本化终验观察项。

## 问题

1. `scripts/t9-verify.mjs` / `t11-verify.mjs` 断言过期：T15 把匿名聊天改为登录门后，匿名页不再渲染 `.chat-empty-chip` / `.chat-input-box`，t11 直接在 `.chat-input-box textarea` 超时崩溃。
2. `KnowledgeManageView.fetchKbs` 空 catch（`/* ignore */`），违反 AGENTS.md §3「catch 必须落日志或转业务码」。
3. 脚本默认 `http://localhost:5173`：本机 `localhost` 解析到 `[::1]` 会撞其他项目的 Vite（已实测 JSON parse 报错），应统一 `127.0.0.1`（dash-shots/ragsec 已是）。

## 修法

- t9 C 段（匿名移动 sheet）：chips 断言改登录门断言（文案+登录按钮+chips=0）；chips 登录态覆盖已在 F 段。
- t11：补 T9 同款注册/登录前导（T11_USER），`newPageWithMocks` 与 B/C 段透传 tokens；A 段删「匿名提示条」改为新增 A0 匿名登录门小节；chips 断言保留（登录态恰好 3 个）。
- fetchKbs：`catch (e) { toast.error(...) }`，与文件内其他失败路径一致。
- 全部 `scripts/*.mjs` 的 `localhost:5173` 字面量改 `127.0.0.1:5173`（含注释）。

## 验收

- t9-verify / t11-verify 全绿（显式 127.0.0.1 与默认各跑一次）。
- `./scripts/verify.sh` quick 绿；lint 16 基线不新增。

## Resolution（2026-11-16）

全部修复并验证：

- **t9-verify**：C 段匿名断言改登录门（文案/登录按钮/chips=0），RESULT 64 pass / 0 fail（原 61/1）。
- **t11-verify**：新增 T11_USER 注册/登录前导（同 t9 模式），`newPageWithMocks` 与 B/C 段透传 tokens；新增 A0 匿名登录门小节（5 断言）；A 段删过期「匿名提示条」；**额外发现并修**：conversations mock 返回裸数组，但 axios client 走 `response.data.data` Result 解包 → conversations=undefined、历史抽屉 0 条。改为 `{code:'0',data}` 包壳（对齐 ragsec-walkthrough 的 jsonRes），B 段同修。RESULT 53 pass / 0 fail（原崩溃）。
- **fetchKbs**：空 catch → `toast.error`，与文件内其他失败路径一致。
- **localhost→127.0.0.1**：19 个脚本默认地址统一改（含注释），t9/t11 不传参直跑验证通过。
- 验收：`./scripts/verify.sh` quick 绿、`npm run build` 绿、lint 16 基线 0 新增。

遗留观察（不在本次）：KnowledgeManageView 其余降级型 catch（docs/chunks/available 置空数组）属「失败静默但有空态」模式，暂保留；如需统一口径另行评审。
