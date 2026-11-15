# UI 重构 T9：移动端文章 dock + sheet

> 对照稿：`.trellis/tasks/11-15-ui-concepts/final/mobile.html` ②③④ + `final/post.html` 的
> `.dock` / `.sheet` / `#sheetToc` / `#sheetAsk`。
> 任务定义见 `.trellis/tasks/11-15-ui-concepts/prd.md` T9 节。本文件记开工结论与验收结果。

## 开工结论（实现前核对）

- **断点偏差（有意）**：总计划写「`sm` 以下」。但右栏（目录 + AskArticleCard）只在
  ≥lg 渲染，原 `MobileTocDrawer` FAB 覆盖 <lg 全部区间；若 dock 限 <sm，
  640–1023 会失去目录入口（功能回退）。**dock 与右栏按 lg 断点互补**：
  ≥lg 右栏，<lg dock。BackToTop 让位同步按 <lg。
- **PostDock 结构**：`components/article/PostDock.vue`，fixed 底栏（左右 12px、
  bottom 12px + `env(safe-area-inset-bottom)`），三格 = 目录（副标已读 %）/
  ✦ 问这篇（accent 底，副标 AI 助手）/ 收藏（Heart 图标，副标数量）。
  `MobileTocDrawer` 收进 PostDock 内部以 v-model 控制，dock 与 sheet 是一个单元。
- **收藏**：复用 `useFavorite`（与 `FavoriteButton.handleToggle` 同路径），
  未登录 emit `login-required` → BlogPost 现有 handler 跳登录；计数同步
  `article.favoriteCount` 与缓存。
- **MobileTocDrawer → 底部 sheet**：改为受控组件（`open` prop + `update:open`），
  删 FAB，`useFabStack('mobile-toc')` 注销并移除 SLOTS 槽位。形态：遮罩 +
  底 sheet（grab、头部「目录 / 已读 N% / 全展收 / X」、TocTree 列表），
  Esc / 点遮罩 / 头部下滑关闭；保留全展开树 + 逐组折叠能力（长文 126 标题需要）。
- **ChatWidget 移动 sheet 纸本化**：T11 已完成 token 换肤（bg-primary、border-strong、
  方角、出处卡）。T9 只补移动 + 文章上下文两处标题：标题行「✦ 问这篇文章」、
  副标「正在阅读：{文章标题}」（`useChatLauncher` 新增 `readingTitle` 模块级 ref，
  由 BlogPost 注入，免去再取一次详情接口）；空态 chips 换成文章向示例
  （复用 AskArticleCard 同组文案）。桌面面板、桌面标题、状态机零改动。
- **dock 不挡正文**：`PostDock` mount 时给 `body` 加 `has-post-dock` 类，
  CSS `body.has-post-dock { padding-bottom }`（<lg 生效），把页脚也推出 dock 覆盖区；
  unmount 移除。这比给 article 加 padding 更彻底（评论区/页脚同样不被遮）。
- **BackToTop**：`route.name === 'post' && <lg` 时不渲染且 `setVisible(false)`。
- **匿名问这篇**：与 FAB 语义一致——`launcher.open()` 直接开面板（匿名可见登录提示条），
  不带问题不暂存；匿名额度 UI 的统一清理属 T15 评审范围。
- **虚拟键盘**：沿用 ChatWidget 现有 `--vvh` 适配；toc sheet 无输入框不涉及。

## 改动清单

- `composables/useFabStack.ts`：移除 `mobile-toc` 槽位（注释同步）。
- `components/article/MobileTocDrawer.vue`：重写为受控底部 sheet（删 FAB/useFabStack）。
- `components/article/PostDock.vue`：新增，dock + 内嵌 MobileTocDrawer。
- `views/BlogPost.vue`：`MobileTocDrawer` → `PostDock`（传 items/readPct/article，
  转发 toggle/login-required）；`launcher.setReadingTitle` 同步文章标题。
- `composables/useChatLauncher.ts`：+ `readingTitle` / `setReadingTitle`。
- `components/chat/ChatWidget.vue`：移动+文章上下文标题/副标/空态 chips。
- `components/common/BackToTop.vue`：post 路由 + <lg 隐藏。
- `styles/post.css`：`.post-dock*`、`body.has-post-dock` 留白、paper 作用域覆盖
  （dock 硬阴影/方角、sheet 方角墨线）。

## 验收

- [x] 390×844：dock 固定底部三格，不挡正文最后一段与页脚（可滚到完全露出）。
- [x] 目录 sheet：已读 % 正确、点遮罩 / Esc / 下滑关闭、条目点击跳转并关闭。
- [x] 问这篇 → 聊天 sheet 标题「✦ 问这篇文章 · 正在阅读：{title}」，articleId 上下文生效。
- [x] 收藏格：登录切换 + 计数同步刊头/文末；未登录跳登录回跳。
- [x] ≥lg 无 dock（右栏在）；640–1023 有 dock（目录入口不失）。
- [x] BackToTop：post <lg 隐藏，其他页面/桌面正常。
- [x] 三主题溢出 = 0；sheet 开合动画 + 键盘联动无回归。
- [x] verify quick + 前端 build/lint。

## 验证结论（2026-11-15）

- `verify.sh` quick 绿；`vue-tsc` / `vite build` 过；oxlint 15 个全为存量基线
  （本任务文件 0，顺手清掉 useFabStack 一个存量 unused import，基线 16→15）。
- `scripts/t9-verify.mjs`：**62/62 PASS**（paper-light/dark/classic × 390、tablet-800、
  桌面 1440、WebKit 冒烟）——dock 三格/accent 色值/留白/关闭三路径/条目跳转/
  收藏同步与匿名回跳/聊天 sheet 标题链/archive·桌面回归全绿。
- 截图：`scripts/output/t9/`（dock、暗色、聊天 sheet、webkit）。
- 走查中发现并修复 1 处自身 bug：dock 收藏计数原地不变——`article.favoriteCount`
  被父级原地 +1/-1（对象引用不变），PostDock 补字段级 watcher 后与刊头同步。

## 走查记录（2026-11-15，t9-walkthrough.mjs 27/27）

- **发现并修复 1 个存量 bug（本次移植带入）**：`drawerAllExpanded` 用
  `Object.keys(collapsedIds).length === 0` 判定，但逐节点 toggle 展开时会留下
  `id:false` 残留键 → 「全部收起」按钮在先点过单组后失效（点了反而全展开）。
  改按值判断 `!Object.values(...).some(Boolean)`。旧右栏抽屉同款逻辑，一并修复。
- 复核通过项：body.has-post-dock 生命周期（进/出/返回重挂载）、sheet 内滚动
  （176 标题长文）、折叠/全展收往返、sheet 开着时 dock 被遮挡不可点、tablet 800
  BackToTop 隐藏 + dock 问这篇开桌面 520×680 面板（非 sheet）、匿名问这篇开面板
  见登录提示不跳页、短文章 sheet 最小形态、dock 反色实测（paper-dark 浅底/
  classic 深底）+ sheet 随主题实时换色、横屏矮视口、pageerror=0。
- 数据说明：当前 9 篇文章均 ≥2 标题，无零标题样本；目录按钮 disabled 分支
  （`items.length===0`）为一行逻辑，代码复核确认。

## 备注 / 后续

- dev server 的 vue-devtools 浮层会盖住 dock 中部拦截 Playwright 指针事件
  （仅 dev 注入，生产无）；测试里条目点击选首位、webkit 提问用 dispatchEvent 绕过。
- 概念稿 sheet 底部「继续追问」chips 行未做——属会话内动态建议新功能，
  本任务取「空态 chips 文章向化」的最小实现；若要还原稿可立项。
- 匿名「问这篇」按现状直接开面板（内有登录提示条）；统一收口在 T15 评审。
