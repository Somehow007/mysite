# UI 重构 T4：顶栏 / 页脚

> 依据 `.trellis/tasks/11-15-ui-concepts/prd.md` T4 节。对照稿 `final/home.html` 顶栏/页脚、`mobile.html` ①。

## 方案要点（开工前记录）

- **断点**：概念稿 900px → Tailwind `lg`（1024）。`lg` 以下顶栏只留 logo + 明暗切换 + 菜单按钮；导航/搜索提示框/登录与用户菜单收进全宽下拉。
- **搜索**：`useSearch` 改模块级单例（原实现每次调用新建 ref，AppHeader 曾挂两个 SearchDialog 实例 → ⌘K 会同时开两个弹层）。`SearchDialog` 改为纯弹层（teleport），触发入口由调用方 `useSearch().open()`；`isOpen` watch 自动聚焦输入框。⌘K/Ctrl+K、Esc、点遮罩关闭逻辑保留在 SearchDialog 内（全局唯一实例）。
- **明暗切换**（已与 owner 确认「混合：能切就直切」）：
  - 当前主题有 `darkVariant`/`lightVariant`（classic、liquid-glass、paper）→ 单按钮直接 `toggleDarkMode()`，Sun/Moon 图标随 `isDark`。
  - 无 variant 主题（aurora、rose-garden、ocean-breeze、warm-sunset）→ 同位置渲染 `ThemeToggle` 下拉面板（现有行为，含主题网格）。
  - 代价（已确认接受）：variant 主题下顶栏不再有主题选择入口。
- **logo**：衬线 `font-serif` + `font-black`，`site.title` 按 `·` 切分，分隔符着 `text-accent`；无 `·` 时纯文本。去掉原来的渐变 M 方块。
- **导航**：取 `site.navigation`；`/` 仅 `home`/`home-page` 路由高亮（`/page/N` 也算首页），其他项 `path` 精确或 `path/` 前缀匹配 → `/post/*` 等文章页不高亮任何项（对齐概念稿）。当前项 = `text-text-primary font-semibold` + `shadow-[0_2px_0_var(--accent)]` 下划线。ADMIN 的 `/journal/` 外链保留在导航末尾。
- **登录按钮**：概念稿 `.btn` 描边按钮 → `border-border-strong`（paper 下自动成墨线）+ `paper-flat` 方角；旧主题 `border-strong=border` 视觉接近原样。
- **背景**：常驻 `bg-bg-primary/90 backdrop-blur` + `border-b border-border`（不再随滚动切换，删 `scrolled` 状态）。
- **页脚**：左 `© year author · site.description`（description 为空则不拼接）+ 备案两行保留；右链接 归档(/archive) / GitHub(site.github,空不渲染) / RSS(site.rss,空不渲染) / 关于(/about)。`border-t border-border-strong`。
- **宽度**：DefaultLayout 主区与顶栏/页脚统一 `max-w-[1180px]`（prd 总约定）。
- **样式落点**：`.icon-btn`（34px 描边透明 icon 按钮）加 `main.css` @layer components，AppHeader/ThemeToggle 共用；`.paper-flat`（border-radius:0）加 `paper.css` 作用域段，配合 `rounded-md` 使用——paper 方角、旧主题圆角。

## 涉及文件

- `src/composables/useSearch.ts`（改共享单例）
- `src/components/common/SearchDialog.vue`（去触发器、watch 聚焦）
- `src/components/common/ThemeToggle.vue`（触发器换 `.icon-btn`）
- `src/components/common/AppHeader.vue`（重写）
- `src/components/common/AppFooter.vue`（重写）
- `src/app/layouts/DefaultLayout.vue`（1180px）
- `src/styles/main.css`（+.icon-btn）、`src/styles/themes/paper.css`（+.paper-flat）

## 验收记录

- `./scripts/verify.sh`（quick）：**VERIFY OK**（后端 hermetic + 前端 type-check）。
- `npm run build` 通过；`npx eslint` 对本任务 7 个改动文件 0 问题（oxlint 16 个存量基线错误与本次无关，stash 前后一致）。
- `scripts/ui-shots.mjs`：paper-light / paper-dark / classic-light × 1440/390 × home/archive/about/首篇文章 = 24 张，**横向溢出全部 0**（产物 `scripts/output/ui-shots-t4/`，gitignore 内）。
- 行为断言（Playwright，临时脚本跑完即删）**26/26 PASS**：
  - logo 衬线（Noto Serif SC）渲染、首页导航 accent 2px 下划线高亮、文章页无高亮；
  - 搜索提示框点击 / Ctrl+K 开合搜索弹层；
  - paper-light↔paper-dark、classic-light↔classic-dark 直切；rose-garden 回落 ThemeToggle 面板可展开；
  - 桌面登录按钮可见、移动端隐藏；移动菜单含导航+搜索入口+登录入口；
  - 菜单内搜索入口开弹层并关菜单；Esc / 路由跳转关菜单；
  - 页脚版权/归档/关于/备案齐全，github/rss 未配置不渲染；
  - 登录态（注册临时账号 t4check 验证后已删）：桌面头像下拉 6 项齐全且非 admin 无「手帐」、移动菜单用户链接齐全。
- 断点实测：768/900px 显示汉堡菜单、1024/1280px 显示完整顶栏，四档 overflow=0。
- computed style 抽查：header `bg-bg-primary/88`+`blur(8px)`+总高 59px（58+1 边）、logo 21px/900、paper 下搜索框/登录钮 `border-radius:0`+墨线描边、footer `border-strong` 墨线顶边；classic-light 下圆角与细边框保持原样。
- 走查二轮补：SearchDialog 关闭焦点归还触发入口（`openerEl.isConnected` 兜底移动菜单卸载）；两个 `<nav>` 加 `aria-label`；`UserProfileView` 残留 `max-w-[1080px]`→1180；顶栏尺寸对齐概念稿精确值（52/58px、gap 28px、logo 21px、bg 88%）。
- 回归验证：对话框内实测搜索（debounce 单例生效、结果渲染）、Esc 后焦点回到提示框按钮、console 0 error。

## Resolution

T4 完成。顶栏/页脚按概念稿落地：衬线 logo（`·` 分隔符 accent）、导航 accent 下划线高亮、搜索提示框（共享单例 `useSearch` + 纯弹层 `SearchDialog`，顺带修掉原来双实例 ⌘K 会同时开两个弹层的问题）、明暗控件按 owner 拍板走「variant 直切 / 无 variant 回落面板」混合方案、移动端全宽菜单（导航+搜索+登录/用户链接，Esc 与路由变化自动关闭）、页脚配置化链接（github/rss 为空不渲染，备案保留）。前台布局宽统一 1180px。

遗留/注意：
- variant 主题（classic/paper/glass）顶栏无主题选择入口（已确认接受）；无 variant 主题的用户可从 ThemeToggle 面板换到有 variant 的主题。
- SearchDialog 弹层本体仍是 glass 风格，纸本化留给后续任务（T11 管聊天面板）。
- 移动菜单未做头像/用户名头部行（沿用原链接列表形态）。
- t4-check 验证用临时账号已从本地库删除；截图与断言脚本未入库（脚本用完即删）。
