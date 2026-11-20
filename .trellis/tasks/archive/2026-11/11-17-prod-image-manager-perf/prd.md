# 11-17 生产图片管理页卡顿/报错定位与修复

## 现象

用户反馈：浏览器刷新后点「图片管理」，快速切换多个管理页面，出现长时间加载
和报错，刷新后恢复。

## 排查结论（后端无辜）

- nginx access.log（用户 IP 183.213.198.86，22:33–22:41 窗口）180+ 请求全部
  200/304/401，**零 5xx**；application.log 同期干净；服务器 CPU 98% 空闲。
- 实为**前端资源被大图拖死**：图片管理页每页 12 张 `<img>` 直接加载全尺寸原图，
  DB 第 1 页 PNG 合计 ~13.9MB（单张最大 2.7MB），无缩略图机制。
- nginx WebP 内容协商**从未生效**：`if + rewrite (.*)$1.webp last` 的 `(.*)`
  把整个 URI（含 `.png`）抓进 `$1`，目标是不存在的 `x.png.webp`；本地 nginx
  原样复现确认 Accept: image/webp 仍返回 PNG。webp 衍生文件其实都生成好了
  （后端上传时自动转，直连 200），只是协商不触发。
- 站点 HTTP/1.1（`listen 443 ssl` 无 http2）→ 浏览器同域 6 连接上限，
  12 张大图占满连接十几秒；快速切页时各页 axios 请求在浏览器连接池排队，
  超过 axios `timeout:10000` → toast 报错 + 页面卡 loading。
- 刷新后图片命中 `immutable 1y` 缓存 → 秒开，即「恢复」。
- 次要发现：`/uploads/` 配 `access_log off` 使图片流量在日志中不可见；
  access token 24h 但前端存了 refresh_token 却从不调用 `/v1/auth/refresh`，
  导致每天首次访问被 401 踢去重新登录（refresh 端点后端早已实现且 permitAll）。

## 修复（已上线/已提交）

### 1. nginx WebP 协商 + HTTP/2（生产已生效）

- 弃用 `if + rewrite`（if 内 rewrite break 会破坏 try_files 多级回退，实测），
  改 **map + try_files**：
  - `map $http_accept $accepts_webp` → `map $accepts_webp:$uri $img_first`
    把 png/jpg/jpeg URI 映射为同名 `.webp` 路径（不接受 webp 时 = 原 URI）
  - images location：`try_files $img_first $uri =404` —— webp 存在发 webp、
    缺失自动回退原图、都缺 404；Content-Type 按命中文件推导（本地+生产实测）
  - `alias /data/mysite/uploads/` → `root /data/mysite`（等价映射，
    避开 alias+try_files 已知坑）
- `listen 443 ssl` → `listen 443 ssl http2`（nginx 1.24 支持 listen 参数）
- 服务器备份 `mysite.conf.bak-20261117-webp`；仓库 `deploy/nginx/mysite.conf` 同步
- 实测：Accept: image/webp → `image/webp` 141KB（原 PNG 2.09MB，-93%）；
  无 Accept → PNG；http_version=2

### 2. 存量 webp 补齐

- 服务器原本无 cwebp（webp 由后端 ImageIO 生成）；`apt install webp` 后
  手工补 5 张 5 月老图（30 张 png+6 jpg 中缺 webp 的），chown www-data。
  现存量 37 张 png/jpg/jpeg 全部有 webp 衍生（missing=0）。

### 3. 前端 refresh token 静默续期（`api/client.ts`）

- 响应拦截器 401 分支：先 `refreshAccessToken()`（共享 Promise 防并发
  互相覆盖轮换后的 refresh token）→ 成功后 `apiClient.request(config)` 带
  新 token 重试一次（`__retried` 防无限循环）→ refresh 失败才走原
  clearAuthAndRedirect 登出路径。
- refresh 响应轮换 access+refresh token 均回写 storage。
- 消费点：全部 `api/*.ts` 经 client.ts 拦截器，对调用方透明（原来 401 即踢
  登录，现在静默续期）；SSE（rag.ts fetch）不经 axios，仍读旧 token——
  但每次路由导航 ensureUser→/me 会先触发续期，实际影响极小，遗留。

### 4. 图片缩略图（后端+前端）

- `ImageServiceImpl`：新增 `generateThumbCopy`——WebP 可用、非 SVG、
  原图最长边 >512px 时生成 `{原名去扩展名}_thumb.webp`（最长边 512，
  白底铺底防透明 PNG 变黑）；`resolveThumbPath` + 两处删除路径同步清理。
  两个上传入口（multipart / upload-url）均接入。
- `ImageManagerView.vue`：网格 `<img>` 改用 `gridSrc()`（缩略图优先），
  `@error` 记入 `thumbFailedIds` 回退原图；lightbox 仍用原图。
- 存量回填：服务器 python 脚本解析 PNG IHDR/JPEG SOF 取尺寸，
  `cwebp -resize` 生成 37 张 `_thumb.webp`（如 4.1MB png → 6.8KB thumb），
  chown www-data，curl 实测 200 image/webp。
- 缩略图命名不冲突：storedName 服务端生成 `DD_{8hex}{ext}`，不可能含 `_thumb`。

## 验证

- `./scripts/verify.sh` quick 全绿（hermetic 测试 + vue-tsc）
- 生产 curl：webp 协商/回退/http2/thumb URL 全部 200 正确 content-type
- nginx -t 通过，reload 无中断

## 遗留/后续

- SSE 聊天流不走 axios 拦截器，401 不会自动续期（影响小，见上）
- `deploy/scripts/monitor.sh` 仍不监控 Java 进程（早前已记录）
- 后台保存 LLM 供应商缺模型名冒烟校验（embedding-400 任务遗留）
- 前端可考虑给 `/uploads/` 重新打开 access_log 或单独采样，便于排查流量问题

## Resolution

nginx webp 协商+http2 已于 11-17 晚间直接上线生效；refresh 续期与缩略图
代码经 verify 后随 deploy.sh/server-deploy.sh 上线。图片管理页单页负载
从 ~14MB PNG 降至 ~0.3MB webp 缩略图量级。
