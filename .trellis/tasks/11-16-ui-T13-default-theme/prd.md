# UI 重构 T13：切换默认主题到纸本

> 任务定义见 `.trellis/tasks/11-15-ui-concepts/prd.md` T13 节。
> 本文件记开工结论（含对「跟随系统」的实现判定）与验收结果。

## 开工结论（实现前核对）

- **「跟随系统」现状**：代码里**没有**跟随系统模式。`useTheme.ts` 虽 import 了
  `usePreferredDark` 并把它放进 watch 依赖，但回调只 `applyTheme(currentTheme)`，
  系统明暗变化实际不产生任何效果（等价死代码）。主题 UI 也只有
  `ThemeToggle.vue`（主题面板）+ `AppHeader` 明暗直切钮，无「system」选项。
- **验收反推**：T13 验收写「清掉 localStorage 后首次访问是纸本；**系统切暗色后
  自动切到 paper-dark**」。后者要求未手动选择时真实跟随 OS
  `prefers-color-scheme`，不是静态默认值能满足的 → 本任务把「无存储值」定义为
  跟随系统态，生效主题解析为 `paper-light` / `paper-dark`。
- **存储语义**：`mysite_theme_v2` 只在用户**手动选定**时写入具体 `ThemeId`；
  无值 = 跟随系统。key 不变（符合总计划约束）。
  - `useStorage` 默认 `writeDefaults: true` 会把默认值回写 localStorage——
    必须显式关掉，否则「无值」永远不会出现，跟随系统失效。
- **存量用户影响（已知边界）**：`useStorage` 过去一直在首次访问时把
  `classic-light` 写进所有访客的 localStorage，无法区分「自动写入」与
  「手动选过经典·白」。因此**已访问过的老访客保持各自存储主题不变**
  （多数停在 classic-light），只有真正无存储值的新访客进入跟随系统。
  个人博客量级可接受；要迁移只能换 key（被计划禁止）或误伤显式选择，均不取。
  站长本人清一次 localStorage 即可看到新默认。
- **消费点**（§2.3 共享层纪律）：`useTheme` 仅 2 个消费方——
  `AppHeader.vue`（明暗钮/canToggleDark/aria）与 `ThemeToggle.vue`
  （面板网格/当前态）。两者只读 `currentTheme.value`，改成 computed
  对外契约不变；写路径收敛在 `setTheme` 内部。
- **首屏防闪**：`index.html` 内联脚本必须与 useTheme 同步解析
  （无存储值时按 `matchMedia('(prefers-color-scheme: dark)')` 定 paper-light/dark），
  否则 Vue 接管前会先上错主题闪一下。
- **「跟随系统」入口（自作主张项，评审可砍）**：跟随系统态一旦手动选过主题就
  无法回去。ThemeToggle 面板加一行「跟随系统」（SunMoon 图标，激活时显示
  当前解析出的主题名），点击清掉存储值恢复跟随。改动约 15 行，属本任务
  「跟随系统」语义的必要补全；若评审认为超范围，删该入口不影响核心逻辑。
- **theme-color meta 不动**：`#FAFAFA`/`#09090B` 按 OS 明暗给浏览器 chrome
  着色，本来就与所选主题解耦（选 rose-garden 也一样错），改值不解决根因，
  保持最小 diff。

## 改动清单

- `src/composables/useTheme.ts`：
  - `storedTheme = useStorage<ThemeId | null>('mysite_theme_v2', null, undefined,
    { writeDefaults: false })`——存储的只是手动选择；
  - `currentTheme` 改 computed：`storedTheme ?? (preferredDark ? 'paper-dark' : 'paper-light')`；
  - watch 简化为 `watch(currentTheme, applyTheme, { immediate: true })`
    （computed 已含 preferredDark 依赖，系统切换自动重解析）；
  - `setTheme` 写 `storedTheme`；新增 `isSystemFollow` / `followSystem()`
    （`storedTheme = null` → removeItem → 回到跟随系统）。
- `index.html`：首屏内联脚本同步「无存储值按系统明暗解析 paper 对」。
- `src/components/common/ThemeToggle.vue`：面板加「跟随系统」行
  （激活态 = `isSystemFollow`，副标显示当前解析主题名）。

## 验收

- [x] 清掉 localStorage 后首次访问：亮色系统 → `data-theme=paper-light`；
      暗色系统（Playwright `colorScheme:'dark'`）→ `paper-dark`。
- [x] 跟随系统态下 `page.emulateMedia({colorScheme})` 切换 → 主题实时跟随
      （paper-light ↔ paper-dark），无需手动操作。
- [x] 手动选过主题（classic-light / rose-garden / paper-dark × 反向系统）
      的存储值不被系统明暗改写；「跟随系统」入口可清回自动态。
- [x] `verify.sh` quick 通过（复跑）；`vue-tsc` / `vite build` 通过；
      oxlint 15 个全为存量基线（本任务文件 0 新增）。
- [x] `scripts/t13-verify.mjs` **27/27 PASS**；computed style 实测
      paper-light `rgb(246,241,231)` / paper-dark `rgb(22,19,15)`；
      390/1440 两档横向溢出 = 0；截图存 `scripts/output/t13/`（gitignored）。
- [x] 独立 commit（仅本任务文件），`.trellis/CHANGES.md` 追加一行。

## 验证计划

- 新增 `scripts/t13-verify.mjs`（沿用 t9/t11 脚本模式，dev server + playwright）：
  无存储 × 亮/暗系统、emulateMedia 实时切换、手动选择保持、面板交互、溢出断言。
- 截图存 `scripts/output/t13/`。

## 验证结论（2026-11-16）

- 首跑 `verify.sh` quick 时 `Phase3IntegrationTest.
  relevantQueryShouldReturnHighQualitySources` 报 `results.isEmpty()` 失败——
  该测试走真实百炼 embedding+rerank API，属全量套件顺序/外部 API 相关的
  存量 flaky；单类复跑 10/10 绿，verify.sh 复跑整体 VERIFY OK。本任务
  纯前端改动，与该失败无因果。
- 工作树注意：开工期间检测到**并行会话的 T12 改动**（AboutView/
  NotFoundView 路由、SiteProperties.aboutContent/techStack、
  client.ts err.code、t12-verify.mjs 等未提交文件），本任务 commit
  已按文件白名单隔离，未卷入。

## 备注 / 后续

- **「跟随系统」入口可达性（已知边界）**：AppHeader 混合方案下，有明暗
  variant 的主题（paper/classic/liquid-glass）顶栏只显示明暗钮、不渲染
  ThemeToggle 面板——这是 T4 owner 拍板的现状，非本任务引入。推论：
  跟随系统态的用户点明暗钮会落到具体 paper-light/dark（退出跟随），
  之后面板不可达，「回到跟随系统」只能清 localStorage；而停在
  rose/ocean/sunset/aurora 的用户倒是能在面板里点回跟随系统。
  如需让 paper/classic 用户也能进面板，属顶栏方案变更，另立任务评审。
- 存量访客的 `classic-light`（含 useStorage 历史自动回写）保持原样，
  见「开工结论」；站长清一次 localStorage 即见新默认。
