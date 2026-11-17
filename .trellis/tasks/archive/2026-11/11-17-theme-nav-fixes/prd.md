# 11-17 生产反馈修复：主题死胡同 + 控制台菜单延迟

## 背景（用户反馈）

生产部署（11-17，新 bundle `index-DTSOlVcg.js` 已生效）后两条反馈：

1. 「部署的内容还是原先的主题？无法切换配色」
2. 「控制台卡顿，无法点击到其他管理页面（刚进入时，过段时间加载即可正常点击）」

## 根因诊断

### 问题 1：主题无法切换 = 存储旧值 × UI 死胡同（两因叠加）

- **存储旧值**：T13 前 `useTheme` 用 `useStorage('mysite_theme_v2', 'classic-light')`，
  writeDefaults 默认开 → 所有老访客 localStorage 里都是 `classic-light`（或自选值）。
  T13 改为"只记手动选择、无值=跟随系统 paper"，但**刻意保留存量存储值**
  → 老用户部署后仍渲染 classic = 「原先的主题」。部署本身无问题。
- **UI 死胡同**（T4 拍板「已确认接受」的设计副作用）：`AppHeader.vue` 中
  `canToggleDark`（当前主题有明暗 variant）为真时只渲染日/月直切钮，
  `ThemeToggle` 面板 `v-else` 不渲染 → classic/paper/glass 三对 variant 主题
  **永远没有主题族切换入口**，只能族内明暗互切。dashboard / 移动菜单 /
  SettingsView 均无主题入口（`useTheme` 消费方仅 AppHeader + ThemeToggle）。
  → 停在 classic-* 的用户被困死，无法到达 paper。

### 问题 2：控制台菜单延迟 = 侧栏被 userStore.user 门控 × 引导串行

- `DashboardSidebar.navGroups` 按 `usePermission()`（读 `userStore.role`）过滤，
  `user` 未加载时 `isAdmin/isCreator=false` → 侧栏只剩「合集管理」，
  其余管理页**不渲染** → 「无法点击」。
- `user.value` 依赖 `GET /v1/auth/me`（`fetchCurrentUser`），而 `App.vue`
  `onMounted` 是 `await siteStore.fetchSite()` 之后才 `userStore.init()` →
  auth/me 起跑晚；生产小机（1.9G 内存全家桶）API 延迟数秒 →
  「过段时间加载即可正常点击」。
- **附带竞态**：`DashboardView.fetchArticles` 与 auth/me 并发，文章接口先返回时
  `isAdmin=false` → admin 列表被 `authorId===undefined` 客户端过滤成空
  （显示「还没有文章」直到下次拉取）。

## 方案（设计师取舍）

### 主题侧

- **A1 `AppHeader`**：variant 主题下日/月直切钮与调色板入口**并存**
  （`ThemeToggle` 去掉 `v-else` 改常驻）；无 variant 主题维持现状仅面板。
  主题族选择是一级入口，明暗切换是快捷方式，不再互斥。
- **A2 抽 `components/common/ThemePicker.vue`**：面板内容（跟随系统行 +
  明暗切换行 + 主题网格）从 ThemeToggle 抽出，emit `close` 供下拉宿主使用；
  ThemeToggle 瘦身为 Dropdown 外壳。SettingsView 新增「外观」分区复用 →
  后台内也有切换路径。
- **A3 顺手修潜伏 bug**：明暗切换行对无 variant 主题是死按钮
  （改动前面板本就只对无 variant 主题可达，行恒 no-op）→ 加 `v-if` 收敛。
- **否决：存量主题静默迁移**（classic-* → paper-*）。尊重用户已做选择，
  修的是"够不着入口"而非替用户改选择；owner 自行清一次 localStorage 即可。

### 控制台侧

- **B1 `userStore` profile 水合**：`fetchCurrentUser`/`setUser`/`updateUser`
  成功时写 `mysite_user_profile`（utils/storage 体系，存完整 User JSON）；
  fetch 失败 / logout 清除。新增 `ensureUser()`：token 存在则先同步水合
  `user.value`（侧栏首帧即有角色），再去重拉取（`loadPromise` 单例）。
  信任级别与现有路由守卫信任 `user_role` 一致，服务端鉴权不变。
- **B2 `App.vue`**：`ensureUser()` 提到 setup 顶层（hydration 先于首帧渲染），
  `fetchSite` 留 onMounted，两者不再串行。
- **B3 `DashboardView`**：`fetchArticles` 前 `await userStore.ensureUser()`，
  消除 isAdmin 分支竞态；categories/collections 不依赖 user 保持并行。
- **否决：侧栏直接读 `user_role` localStorage**——会与 store 双源，
  水合方案已覆盖且数据更全（avatar/username 同步可见）。

## 消费点复核（§2.3 共享层纪律）

- `useTheme`/`THEME_REGISTRY`：消费方 AppHeader、ThemeToggle；
  新增 ThemePicker、SettingsView。**无签名变更，纯消费**。
- `userStore`：消费面极广（isLoggedIn/user/role/displayName/logout…），
  本次只**新增** `ensureUser` + profile 缓存读写；`init()` 唯一调用方
  App.vue 同步改名。`fetchCurrentUser`/`setUser`/`updateUser`/`logout`
  行为对外不变（内部多写/清一个 key）。
- `ThemeToggle`：外部行为不变（触发器 + 下拉面板），内部换 ThemePicker。
- `DashboardView`：仅 onMounted 内 fetchArticles 前移一个 await。
- 路由守卫 `user_role` 读取不动。

## 验证

- `./scripts/verify.sh`（quick：后端 hermetic + 前端 type-check）
- `mysite-frontend/`：`npm run lint`、`npm run build`
- 自查：hydration 在侧栏首帧前生效；ensureUser 并发去重；登出清缓存；
  ThemePicker 在 paper/classic dash-mode 下 token 自适应。

## Resolution

已实施（2026-11-17）：

- **主题侧**：新增 `components/common/ThemePicker.vue`（跟随系统行 +
  条件明暗行 + 主题网格，emit close）；`ThemeToggle.vue` 瘦身为 Dropdown
  外壳复用 ThemePicker；`AppHeader` 日/月直切钮（`canToggleDark`）与
  ThemeToggle **并存**（去掉 v-else），移动菜单加「主题外观」行内嵌
  ThemePicker；`SettingsView` 新增「外观」分区复用 ThemePicker。
  存量 `mysite_theme_v2` 值不动。
- **控制台侧**：`user.ts` 新增 `mysite_user_profile` 本地缓存
  （fetchCurrentUser/setUser/updateUser 写入，失败/logout 清除），
  新增 `ensureUser()`（同步水合 + `loadPromise` 单例去重 + `fetched`
  每会话一次）；`init()` 更名 `ensureUser`，`App.vue` 改到 setup 顶层
  先于首帧渲染调用，`fetchSite` 留 onMounted 不再串行；
  `DashboardView.fetchArticles` 内 `await userStore.ensureUser()`
  消除 isAdmin/authorId 竞态。
- **验证**：`./scripts/verify.sh`（quick）绿；`npm run build` 绿；
  `npm run lint` 16 errors 全为存量（useMarkdown/useChatMarkdown/
  gravatar/test-callout 等，本次文件 0 命中）。
- **走查**：新增 `scripts/theme-nav-walkthrough.mjs`（全 API mock +
  /me 延迟 2.5s），14/14 PASS——水合收益、ensureUser 时序、主题入口
  并存/切换持久、设置页外观分区、移动菜单、无缓存对照组全覆盖。
  插曲：fixture 初版误用 JSON 带引号写 theme（实际 useStorage 'any'
  序列化存原始串）+ addInitScript 每次导航重播种，修正后全绿。
- **部署**：2026-11-17 11:00 server-deploy 一次过——git FF 至 9170f4e、
  后端 82M、前端 307 文件、nginx 重载；终验 site/config 真数据、
  新 bundle `index-hyhICCql.js` 含 `user_profile`、health/401/200 全过。
- **插曲**：ThemePicker/ThemeToggle 两文件尾部各混入一行 `</file-view>`
  残留（早前写入事故），build 报 Invalid end tag，已清除重建通过。
