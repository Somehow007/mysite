# T1 纸本主题 token（2026-11-15）

> 上游：`.trellis/tasks/11-15-ui-concepts/prd.md` T1 节。目标：新增 `paper-light` / `paper-dark` 两套主题可在切换器选到；默认主题不改（T13 再切）。

## 改动清单

| 文件 | 内容 |
|---|---|
| `src/styles/themes/paper.css`（新增） | 两个 `[data-theme]` 块，变量结构对齐 classic.css 全集（semantic 色、bg/text/accent/border、shadow、glass、code token）；尾部纸本作用域样式 |
| `src/styles/themes/tokens.css` | `:root` 默认值补 `--border-strong: var(--border)`、`--shadow-hard`（普通阴影公式） |
| `src/styles/themes/{classic,aurora→classic.css,rose-garden,ocean-breeze,warm-sunset,liquid-glass}.css` | 8 个旧主题块全部补 `--border-strong`（=各自 `--border` 值）+ `--shadow-hard`（普通阴影），旧主题视觉不变 |
| `src/styles/main.css` | `@import paper.css`；`@theme` 映射 `--color-border-strong`、`--shadow-hard`（Tailwind 类 `border-border-strong` / `shadow-hard`，用到才生成） |
| `src/composables/useTheme.ts` | `ThemeId` + registry 各加 paper-light（纸本·昼）/ paper-dark（纸本·夜），group `paper` 互为 variant；`isThemeDark` 无需改（`includes('dark')` 已命中） |
| `scripts/ui-shots.mjs`（新增） | 截图脚本：主题×视口×路由，查横向溢出。用法见文件头注释 |

## 关键取值决策（概念稿未覆盖处的取舍）

- **代码块**：日/夜主题共用暖棕深色底 `--code-bg:#1C1915`（post.html `.code` 写死深色底，T8 也明确"深色底保持不变"）；token 暖色系 keyword `#E0794F` / string `#B5C98A` / comment `#7D7466` / function `#E9C46A`，其余（number/operator/variable/type/property/tag/attr/punctuation/plain）按暖棕深色底配色补全。
- **`--code-border:#3A332A` 两主题同值**：概念稿里它是块内 header 分隔线色；app 里该 token 同时管外框和 header 分隔线，取折中值，header 分隔线与概念一致。
- **`--text-inverse`**：paper-light `#FFFFFF`（锈红底白字对比 ~5.5:1）；paper-dark `#16130F`（亮橙底墨字 ~6:1）。
- **glass 变量**：paper 不用玻璃拟态但现有组件（下拉/聊天面板）还在用 `.glass`，给了"纸感"降级——bg-elevated 88% + blur 12px + `4px 4px 0` 硬阴影；paper-light glass-border 用墨色 12% alpha。
- **`--grain`**：纸纹 token（日 `rgba(0,0,0,.028)` 点 / 夜 `rgba(255,255,255,.025)` 点），作用域规则 `[data-theme^="paper"] body` 应用，`background-size:3px 3px`。
- **paper-light 代码头部可读性**：`--text-muted #7D7466` 是为纸面设计的，在 `#1C1915` 深色块上对比不足 → 作用域覆盖 `.code-block-header/.code-block-copy` 为概念稿的 `#8D8475`；copy hover 换成 `--code-token-function` + 12% 同色底，避免浅色 accent-subtle 底配亮橙字。
- **作用域工具类**（paper.css 尾部，其他主题不生效）：`.paper-card`（elevated 底+border+直角）、`.paper-card--strong`（墨线边）、`.paper-hard-shadow`（`var(--shadow-hard)`）。T4+ 组件在 Tailwind rounded 类之外加这些类即可。

## 验证结论

- `./scripts/verify.sh`（quick）：**VERIFY OK**（后端 hermetic + 前端 type-check）。
- `npm run build`：通过。`npm run type-check`：通过。
- `npm run lint`：**基线即挂**，与本次无关——oxlint 16 个 correctness 错误（useMarkdown/useChatMarkdown 的 `\x00` 转义、未用 import 等）+ eslint 42 个 unused-vars，全部在未触碰文件；T1 文件 eslint 单独跑 0 错误。建议后续单独开 lint 清理任务（触及 §2.3 共享层，需评审）。
- 运行时实测（dev server 5173 + 真后端 8081）：
  - computed style：paper-light body `#F6F1E7`+grain、paper-dark `#16130F`+`.dark` class、classic-light 各值不变；`--accent/--border-strong/--shadow-hard/--code-*` 三主题均正确解析。
  - 文章页代码块实测：bg `rgb(28,25,21)`=#1C1915、border `rgb(58,51,42)`=#3A332A。
  - 主题切换器菜单含「纸本·昼」；paper-light↔paper-dark variant 互切正常（`toggleDarkMode` 走 darkVariant/lightVariant）。
  - dashboard（admin/000000 登录注入 token）paper-light/paper-dark 均正常渲染文章管理页。
- 截图：`scripts/ui-shots.mjs` 跑 paper-light/paper-dark/classic-light × 1440/390 × home/archive/about/首篇文章 = 24 张，**横向溢出全部 0**，产物在 `mysite-frontend/scripts/output/ui-shots/`。
- Tailwind 类注意：`border-border-strong`/`shadow-hard` 目前无组件使用，Tailwind v4 按需生成故 bundle 里暂无——T4+ 用到时自动生成，机制与 `border-border-subtle` 相同已验证。

## 后续提示

- T4+ 使用新类：`border-border-strong`、`shadow-hard`、`.paper-card`、`.paper-card--strong`、`.paper-hard-shadow`。
- `--font-serif` 仍指向系统字体栈，webfont 自托管是 T2 的事。
- 默认主题仍是 `classic-light`，storage key `mysite_theme_v2` 未动；已手动选过主题的用户不受影响。
- 未提交 commit（工作区还有 11-15-ui-concepts/audit 未跟踪文件，是否一起提交由 owner 定）。

## Resolution

T1 完成：两套纸本主题上线可选，旧主题零视觉变化，verify quick 绿。阶段验收通过并随 359f61a 提交。遗留：lint 基线问题（建议单独任务）。
