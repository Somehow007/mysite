# UI 重构 T2：衬线 webfont（2026-11-15）

> 任务定义见 `11-15-ui-concepts/prd.md` T2。目标：跨平台衬线渲染一致，自托管 Noto Serif SC。

## 产物

| 文件 | 说明 |
|---|---|
| `mysite-frontend/public/fonts/noto-serif-sc-{core,hanzi2,rest-1,rest-2,rest-3}.woff2` | 5 个 unicode-range 分片，**可变字体 wght 200–900**（一个文件覆盖全部字重） |
| `mysite-frontend/src/styles/fonts.css` | 5 个 `@font-face`，`font-display: swap`，unicode-range 精确不相交 |
| `mysite-frontend/src/styles/main.css` | 头部 `@import "./fonts.css"`（与 T1 的 paper.css import 并存） |
| `mysite-frontend/index.html` | 删 `Inter Fallback` 死代码；加 core 片 `<link rel="preload" as="font" crossorigin>` |
| `subset-fonts.py` + `unicode-ranges.txt`（本目录） | 生成脚本与 unicode-range 清单，供重新生成 |

## 与任务书的两处偏差（均为改进，记录备查）

1. **可变字体替代 400/700/900 三个静态字重**：源文件用 google/fonts 仓库 `ofl/notoserifsc/NotoSerifSC[wght].ttf`（wght 200–900）。一个 @font-face `font-weight: 200 900` 覆盖所有字重 → fonts.css 体积降到 1/3（82KB raw / **33KB gz**，静态方案约 97KB gz），字体文件总量 ~11.7MB vs 静态 ~18MB，且 500/600 等中间字重也能正确渲染。preload 也从"两片"变成单个 core 文件。
2. **`tokens.css` 未改**：`--font-serif` 第一项本来就是 `'Noto Serif SC'`，与 @font-face 族名一致即解析到自托管字体（@font-face 优先于本机同名字体），后续回退链不变，无需编辑。

## 字体获取与再生成

- 源文件：GitHub 直连不通，走 `https://gh-proxy.com/https://raw.githubusercontent.com/google/fonts/main/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf`（25MB TTF，fvar/gvar 齐全）。
- 备选已验证可用：`https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;700;900` 返回三个完整静态 TTF 的 gstatic 直链（本次未用，留档）。
- 本地工具：`pyftsubset` + `brotli`（fonttools 4.68，pip --user 安装，**不进项目依赖**）。
- 再生成：`python3 subset-fonts.py /tmp/t2-fonts`（源文件放 `/tmp/t2-fonts/NotoSerifSC-var.ttf`）。

## 分片策略

cmap 共 30,928 字符（覆盖整个 CJK 基本区 + 扩展 A + 兼容表意）。unicode-range 精确不相交，浏览器按需下载：

| 分片 | 字符集 | 码点数 | woff2 大小 |
|---|---|---|---|
| `core` | cp<U+2E80（拉丁/希腊/西里尔/标点/符号/假名注音外的基础面）+ CJK 标点 U+3000-303F + 平/片假名 U+3040-30FF + 注音 U+3100-312F + 全角 U+FF00-FFEF + **GB2312 一级字库 3755** | 5,407 | 1,707 KiB |
| `hanzi2` | GB2312 二级字库 | 3,008 | 1,190 KiB |
| `rest-1/2/3` | 其余 22,513 字符按码点均分三片（扩展 A、非常用汉字、兼容表意、上古文字等） | ~7,504 each | 2,694 / 2,924 / 3,167 KiB |

- "常用 3500 汉字"取 **GB2312 一级字库（3755 个常用汉字）**——国标一级字库即"常用汉字"定义，可用 Python `codecs` 离线生成，无需外部字表文件；二级字库（3008）作第二片。任务书的"3500"为约数，3755 吻合。
- rest 三片均分是因为非常用字在真实文章中极少出现，细分意义不大；若 T16 发现某片被频繁整片下载可再拆。

## 验证结论

- `./scripts/verify.sh`（quick）：**绿**（后端 hermetic + 前端 type-check）。
- `npm run build`：**过**（type-check + vite build）；dist 正确产出 `fonts/` 5 个 woff2，主 CSS 218KB raw / **60KB gz**（fonts.css 贡献 ~33KB gz，T16 复核）。
- `npm run lint`：**未跑 --fix**（并行会话同树，避免误改他人文件）；oxlint/eslint 直查报 16+42 个错，全部位于存量文件与 T1/T3/T5 的在改文件，与本任务文件（css/html/字体二进制）无关。
- Playwright（`scripts/ui-shots.mjs`，paper-light/paper-dark/classic-light × 1440/390 × 首页/归档/关于/首篇文章）：**24 张截图，横向溢出全 0**。截图在 `/tmp/t2-shots/`。
- 字体实测（Playwright + 长文页 `/post/2069036181964537856`）：
  - `noto-serif-sc-core.woff2` 请求 200，`content-type: font/woff2`（preload 生效）；
  - `document.fonts`：5 个 face 注册，core 片 `loaded`，其余 `unloaded`——**unicode-range 按需加载实测生效**，长文（仅常用字）只下载 core 一片；
  - `.prose` 计算样式首项即 `"Noto Serif SC"`；`document.fonts.check` 400/900 均 true；
  - 首页也只请求 core（preload 行为，T6/T8 落地后首页才真正用 serif）。
- Nginx 核对（`deploy/nginx/mysite.conf` 443+8080 两 server 块）：静态正则已含 `woff2` → `expires 1y` + `immutable`，gzip_types 含 `font/woff2`，mime.types 默认 `font/woff2`——**无需改动**。

## 走查补验（同日晚间复查）

- **声明↔字形一致性**：程序化交叉验证通过——5 片 unicode-range 两两不相交（0 重叠）；每片声明码点集 == 对应 woff2 cmap（missing=0, extra=0）；五片并集 == 源字体 cmap 30,928（全覆盖，无盲区）。
- **按需加载实测**（`document.fonts.load` 逐字符触发）：页面初始只下载 core；加载 `丌`(U+4E10, GB2312二级) → 仅拉 hanzi2；`䴿`(U+4D3F) → 仅 rest-1；`穕`(U+7A55) → 仅 rest-2；`屮`(U+2F878 补充面) → 仅 rest-3；集外字符 `가`(U+AC00) → 零下载直接回退系统字体。分片下载语义完全正确。
- **协作面**：index.html diff 仍仅本任务两处改动；main.css 仅 fonts.css 一行 import（T1 的 paper.css/import 与 token 映射为独立 hunk）。
- **遗留事项**：`public/fonts/` 文件名不带内容指纹，而 Nginx 给 `woff2` 配 `immutable 1y`——**今后重新生成字体必须改文件名**（如 `noto-serif-sc-v37-*`）并同步 fonts.css/index.html，否则老用户拿到旧字体配新 unicode-range。
- 斜体说明：CJK 字体本无 italic，`.prose em` 走浏览器合成倾斜，与上线前行为一致，非回归。

## T16 需要的数据（性能基线）

- 首屏字体传输：core 片 **1.75MB**（可变字体含 gvar 增量，比静态单字重 ~2×，但替代 3 个静态文件）；hanzi2 1.19MB、rest 各 ~2.7–3.2MB 按需。
- CSS 增量：fonts.css raw 82KB → gz ~33KB。
- 若 LCP/传输不达标，退路预案：① wght 轴用 instancer 裁到 300–900 再子集；② core 再拆"拉丁+标点"与"一级字库"两片，preload 只留拉丁片；③ 退到"仅标题用 webfont"（任务书已拍板退路）。

## 注意

- **未提交**：与 T1/T3/T5 并行同树，遵循同树任务惯例留待统一提交；本任务文件清单：`index.html`、`main.css`（仅 fonts.css import 一行，他人 hunk 未动）、`fonts.css`、`public/fonts/`、本任务目录。
- 生效范围：`--font-serif` 全局生效，当前仅 `.prose`（文章正文）消费；T6/T8 落地后标题/提问框等会逐步用上。旧主题下 serif 同样换成 Noto（字体与主题正交，属预期）。
- `public/fonts/` 未被 gitignore（`git check-ignore` 通过）。

## Resolution

T2 完成：Noto Serif SC 可变字体（wght 200–900）5 片 unicode-range 子集自托管上线，按需加载实测生效（页面初始仅 core 片）；verify quick 绿、build 过、ui-shots 24 张横向溢出=0。阶段验收通过并随 359f61a 提交。遗留：字体文件名无内容指纹，重新生成必须改名（Nginx immutable 1y）；首屏字体体积数据待 T16 走查判定。
