# 学习手帐（花期 Blossom）集成日志与说明

> 本文档记录学习手帐项目（`study-journey/`）集成进 MySite 博客的决策、实施过程、服务器现状与运维方法。
> 创建：2026-07-26 ｜ 状态：**第一期已上线** ✅

---

## 1. 背景

学习手帐「花期」是一个独立的 React 18 + Vite 前端项目（仓库 `git@github.com:Somehow007/study-journel.git`），最初是纯前端 SPA，数据存浏览器 IndexedDB。目标是把它挂到已部署的博客站点（`https://somehow007.top`）上，入口**仅对管理员可见**。

手帐项目自身的规范文档（位于 `study-journey/` 目录内）：

- `🌸 学习手帐 · 花期 Blossom — 开发设计文档 v5.0.md` — 视觉与交互规范
- `📔 学习手帐 — 网站集成与 MySQL 存储方案 v2.0.md` — 后端化集成方案（第二期依据）

## 2. 决策记录（2026-07-26）

### URL 形态：采用子路径 `somehow007.top/journal/`

| 方案 | 形态 | 结论 | 关键理由 |
|------|------|------|----------|
| 子路径 | `somehow007.top/journal/` | ✅ **采用** | 零 DNS/证书/CORS 成本；同源，为第二期复用博客登录态铺路；与手帐集成方案 v2.0 §7.3 的推荐一致 |
| 子域名 | `journal.somehow007.top` | ❌ 放弃 | 需新增 DNS A 记录、`certbot --expand` 扩容证书、后端 CORS 放行；localStorage 按源隔离导致博客 JWT 登录态无法共享，与 v2.0「同源请求携带登录态」假设冲突 |
| 独立端口 | `IP:8082` | ❌ 放弃 | URL 不美观、无 HTTPS、需开安全组端口 |

### 入口可见性：仅 ADMIN

- 博客顶栏通过 `userStore.isAdmin` 条件渲染「手帐」链接（桌面端 + 移动端菜单）。
- 当前阶段手帐数据存在各自浏览器 IndexedDB 中，URL 泄露不会暴露任何数据；第二期后端化后由服务端 `user_id` 隔离做真正鉴权。

### 实施节奏：分两期

- **第一期（已完成 ✅）**：手帐按现状（纯前端 IndexedDB 版）以子路径静态部署，博客加管理员入口。
- **第二期（待定）**：按手帐仓库内《网站集成与 MySQL 存储方案 v2.0》执行后端化——MySQL 新增 `sj_` 前缀 3 张表、后端新增 journal 模块、前端数据层切换为 `/api/journal` 同源接口，实现跨设备同步。

## 3. 架构总览

```
浏览器
  │
  ├─ https://somehow007.top/            → 博客（Vue 3 SPA，/var/www/mysite）
  │     └─ 顶栏「手帐」入口（仅 ADMIN 可见，原生 <a href="/journal/">）
  │
  └─ https://somehow007.top/journal/    → 手帐（React 18 SPA，/var/www/journal）
        ├─ vite base='/journal/'，BrowserRouter basename='/journal'
        ├─ 数据：浏览器 IndexedDB（第一期）
        └─ 第二期将调用同源 /api/journal → Spring Boot(8081) → MySQL sj_ 表

Nginx (443/HTTPS，证书由 Certbot 管理，自动覆盖子路径)
  ├─ location /journal/ { alias /var/www/journal/; try_files … }   ← 手帐
  ├─ location /        { try_files … /index.html; }                ← 博客 SPA
  └─ location ~ ^/v[0-9]/ 与 /api/  →  proxy_pass localhost:8081   ← 后端
```

## 4. 第一期实施记录（2026-07-26）

### 4.1 本地代码改动

**手帐仓库（`study-journey/`，3 个文件）：**

| 文件 | 改动 |
|------|------|
| `vite.config.ts` | 加 `base: '/journal/'`；移除 VitePWA 插件（集成方案 §7.4：子路径下 SW 作用域易与主站冲突）；dev server 自动打开 `/journal/` |
| `src/main.tsx` | `<BrowserRouter basename="/journal">` |
| `index.html` | 清理 PWA 相关 meta（favicon 等绝对路径由 Vite 按 base 自动重写） |

**博客仓库（mysite，3 个文件）：**

| 文件 | 改动 |
|------|------|
| `mysite-frontend/src/components/common/AppHeader.vue` | 桌面导航与移动端菜单各加「手帐」入口，`v-if="userStore.isAdmin"`；用原生 `<a>` 而非 RouterLink（避免被 vue-router 拦截） |
| `deploy/nginx/mysite.conf` | 443 与 8080 两个 server 块各加 `location /journal/`（见 §5.2） |
| `docs/DESIGN.md` | 更新日志追加集成决策条目 |

### 4.2 验证结果

| 检查项 | 命令 | 结果 |
|--------|------|------|
| 手帐构建 | `npm run build`（tsc && vite build） | ✅ 产物路径全部带 `/journal/` 前缀，无 PWA 残留文件 |
| 博客类型检查 | `npx vue-tsc --noEmit --pretty` | ✅ |
| 博客构建 | `npx vite build` | ✅ |
| Nginx 语法 | `sudo nginx -t` | ✅ |
| `/journal/` 首页 | `curl -sk https://somehow007.top/journal/` | ✅ 200，资源引用均为 `/journal/...` |
| SPA 路由 fallback | `curl -sk /journal/stats` | ✅ 200（返回 index.html） |
| 博客主站 / 文章页 / 后端 API | curl | ✅ 全部 200，无回归 |
| 线上 bundle 含入口 | grep DefaultLayout chunk | ✅ 含 `/journal/`，与本地构建产物一致 |

### 4.3 服务器操作流水

1. 备份 Nginx 配置 → `/etc/nginx/sites-available/mysite.conf.bak-20260726`
2. 备份旧博客前端 → `/var/www/mysite.bak-20260726.tar.gz`
3. 上传并替换 Nginx 配置（先 diff 确认服务器版本与仓库版本**仅差新增的 journal 块**，无漂移，才整体覆盖）
4. 手帐 `dist/` → `/var/www/journal/`（`rsync -a --delete`，owner 设为 `www-data`）
5. 博客前端 `dist/` → `/var/www/mysite/`（仅同步静态产物，未动后端服务）
6. `sudo nginx -t && sudo systemctl reload nginx`，线上 curl 全量验证

## 5. 服务器现状说明

### 5.1 服务器上有什么、没有什么

| 路径 | 内容 | 说明 |
|------|------|------|
| `/var/www/journal/` | 手帐**构建产物**（index.html、assets/、book.svg） | 纯静态文件，无源码、无 node_modules |
| `/var/www/mysite/` | 博客前端构建产物 | 同上 |
| `~/project/mysite` | mysite 仓库检出（标准部署脚本 `server-deploy.sh` 的 git pull + 服务器构建流程用） | 手帐源码**不在**这里 |

**结论：服务器上没有任何手帐源码。** 当前模式是「本地构建 → 上传产物 → 服务器只托管静态文件」。

### 5.2 Nginx 改了哪里、改了什么

- **文件**：`/etc/nginx/sites-available/mysite.conf`（通过 `/etc/nginx/sites-enabled/mysite.conf` 软链接启用）。仓库内副本 `deploy/nginx/mysite.conf` 已同步为相同内容。
- **备份**：`/etc/nginx/sites-available/mysite.conf.bak-20260726`。
- **改动**：在 443 HTTPS server 块（`/uploads/` 块之后、`location /` 之前）和 8080 兼容 server 块中，各加入同一段：

```nginx
# 学习手帐（花期 Blossom）子应用：独立构建产物部署于 /var/www/journal/，SPA fallback
location /journal/ {
    alias /var/www/journal/;
    try_files $uri $uri/ /journal/index.html;
}
```

- `alias` 把 URL 前缀 `/journal/` 映射到目录 `/var/www/journal/`；
- `try_files` 最后一项 fallback 到 `/journal/index.html`，保证 React Router 的前端路由（如 `/journal/stats`）刷新不 404；
- 443 与 8080 两块都改是为了让 `IP:8080` 兼容入口也能访问手帐；
- 生效方式：`sudo nginx -t && sudo systemctl reload nginx`（已执行，纯静态文件更新以后无需再 reload）。

## 6. 运维手册

### 6.1 更新手帐（改完手帐代码后）

```bash
# 本地
cd study-journey && npm run build
scp -r dist ubuntu@124.222.65.169:/tmp/journal-dist

# 服务器
ssh ubuntu@124.222.65.169 \
  'sudo rsync -a --delete /tmp/journal-dist/ /var/www/journal/ \
   && sudo chown -R www-data:www-data /var/www/journal'
# 刷新页面即生效，无需 reload nginx
```

### 6.2 更新博客前端

走标准流程（`./deploy/deploy.sh` 打包 → 服务器 `server-deploy.sh`）；若只改前端不想重启后端，可参照 §4.3 步骤 5 手工同步 `dist/`（先 tar 备份）。

### 6.3 改 Nginx 配置

```bash
sudo cp /etc/nginx/sites-available/mysite.conf \
        /etc/nginx/sites-available/mysite.conf.bak-$(date +%Y%m%d)
sudo vim /etc/nginx/sites-available/mysite.conf
sudo nginx -t && sudo systemctl reload nginx   # -t 不过不要 reload
# 记得把改动同步回仓库 deploy/nginx/mysite.conf
```

### 6.4 回滚（完全撤销手帐集成）

```bash
sudo cp /etc/nginx/sites-available/mysite.conf.bak-20260726 \
        /etc/nginx/sites-available/mysite.conf
sudo nginx -t && sudo systemctl reload nginx
sudo rm -rf /var/www/journal
# 博客前端如需回滚：解压 /var/www/mysite.bak-20260726.tar.gz 覆盖 /var/www/mysite
```

## 7. 仓库结构注意事项

- `study-journey/` 目录物理上位于 mysite 工作区内，但它是**独立 git 仓库**（remote：`git@github.com:Somehow007/study-journel.git`，注意仓库名原拼写为 journel）。
- mysite 仓库的 `.gitignore` **未**忽略该目录，但因嵌套 `.git` 的存在，mysite 不会跟踪其内容。⚠️ 在 mysite 根目录执行 `git add .` 时留意不要把 `study-journey/` 作为嵌入式仓库（gitlink）误加入。
- 两个仓库的提交相互独立，按功能模块分别提交。

## 8. 第二期规划（待定）

依据手帐仓库内《网站集成与 MySQL 存储方案 v2.0》：

1. **P0/P1 后端**：MySQL 现有库新建 `sj_day_record`、`sj_learning_item`、`sj_custom_mood` 三张表；后端新增 journal 包（controller/service/entity/dto），API 前缀 `/api/journal`，登录态从博客 JWT 解析 `user_id`。
2. **P2 前端**：手帐数据层由 IndexedDB 切换为 HTTP API（同源，无 CORS 成本——正是第一期选子路径的核心收益）。
3. **P3/P4**：历史数据经 `/import` 迁移、服务端用户隔离验收。

## 9. 当前阶段的已知限制

| 限制 | 说明 | 计划 |
|------|------|------|
| 数据不跨设备 | 存各自浏览器 IndexedDB | 第二期后端化解决 |
| URL 无服务端鉴权 | 仅隐藏入口；但数据本地存储，暴露 URL 无数据泄露风险 | 第二期随登录态打通解决 |
| 字体走 Google Fonts CDN | 国内偶发加载慢 | 集成方案 v2.0 §6 体验清单第 7 项：字体本地化 |
| 手帐产物未做不可变缓存头 | 仅 ETag 协商缓存，对管理员自用场景无感 | 有需要时在 `location /journal/` 内补静态资源缓存规则 |
