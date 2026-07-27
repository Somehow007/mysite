# MySite 项目设计文档

## 更新日志

### 2026-07-26: 学习手帐（花期 Blossom）集成方案确定

> 更新于 2026-07-26：与用户讨论后确定集成路线，第一期已实施。

#### 决策背景

学习手帐项目（`study-journey/`，React 18 + Vite 独立前端，当前数据存浏览器 IndexedDB）需要集成进已部署的博客站点（`somehow007.top`）。讨论了三种 URL 方案：

| 方案 | 形态 | 结论 |
|------|------|------|
| 子路径 | `somehow007.top/journal/` | ✅ **采用** |
| 子域名 | `journal.somehow007.top` | ❌ 放弃 |
| 独立端口 | `IP:8082` | ❌ 放弃 |

#### 决策内容

1. **URL 形态：子路径 `somehow007.top/journal/`**
   - 理由：与手帐《网站集成与 MySQL 存储方案 v2.0》§7.3 的推荐一致；无需变更 DNS、无需扩容 SSL 证书（现有 Let's Encrypt 证书自动覆盖）、无 CORS 与跨域 cookie 问题；**同源**为第二期后端化（`sj_` 表 + 复用博客 JWT 登录态）铺平道路。
   - 放弃子域名的理由：需要新增 DNS A 记录、`certbot --expand` 扩容证书、后端 CORS 放行，且 localStorage 按源隔离导致博客登录态无法直接共享，与 v2.0 方案"同源请求携带登录态"的核心假设冲突。

2. **入口可见性：仅 ADMIN 可见**
   - 博客顶栏（AppHeader）通过 `userStore.isAdmin` 条件渲染「手帐」链接，桌面端与移动端菜单同步。
   - 当前阶段数据存各自浏览器 IndexedDB，URL 暴露不泄露数据；第二期后端化后由服务端 `user_id` 隔离做真正鉴权。

3. **分两期实施**
   - **第一期（2026-07-26 实施 ✅）**：手帐按现状（纯前端 IndexedDB 版）部署到 `/journal/`；手帐侧 `vite.config.ts` 设 `base: '/journal/'`、`BrowserRouter basename="/journal"`、按集成文档 §7.4 移除 PWA 注册；博客侧 Nginx（443 与 8080 两个 server 块）新增 `location /journal/ { alias /var/www/journal/; try_files ... }`；产物部署至服务器 `/var/www/journal/`。
   - **第二期（待定）**：按《网站集成与 MySQL 存储方案 v2.0》执行后端化——MySQL 新增 `sj_` 前缀 3 张表、后端新增 journal 模块、前端数据层由 IndexedDB 切换为 `/api/journal` 同源接口，实现跨设备同步与真实用户隔离。

#### 影响范围

- `study-journey/vite.config.ts`、`src/main.tsx`、`index.html`：子路径构建改造
- `mysite-frontend/src/components/common/AppHeader.vue`：管理员入口
- `deploy/nginx/mysite.conf` + 服务器 `/etc/nginx/sites-available/mysite.conf`：`/journal/` location

### 2026-06-22: 管理页面排序和筛选功能优化

#### 问题描述
- 文章管理页面内容显示不全，缺少关键信息
- 所有管理页面都是默认排序，无法多维度定位数据
- 缺少筛选功能，管理效率低下

#### 解决方案
对所有管理页面进行全面的排序和筛选功能增强：

##### 1. DashboardView (文章管理)
- **筛选功能**:
  - 关键词搜索: 支持按标题、内容、作者三种方式搜索
  - 状态筛选: 全部状态、已发布、草稿
  - 分类筛选: 从分类列表中选择
  - 重置功能: 一键清空所有筛选条件

- **排序功能**:
  - 排序字段: 创建时间、浏览量
  - 排序方向: 升序/降序切换

- **分页功能**:
  - 完整的分页组件: 页码按钮、上一页/下一页
  - 显示文章总数

- **信息展示优化**:
  - 封面图: 显示文章封面缩略图
  - 摘要: 显示文章摘要（限制2行）
  - 作者信息: 显示作者名
  - 统计数据: 显示浏览量、收藏数、阅读时长
  - 状态标签: 已发布(绿色)、草稿(黄色)

##### 2. CommentManageView (评论管理)
- **新增排序功能**:
  - 排序字段: 创建时间、点赞数、回复数
  - 排序方向: 升序/降序切换

##### 3. UserManageView (用户管理)
- **新增排序功能**:
  - 排序字段: 注册时间、用户名、最后登录时间
  - 排序方向: 升序/降序切换

##### 4. ImageManagerView (图片管理)
- **新增排序功能**:
  - 排序字段: 创建时间、文件大小、文件名
  - 排序方向: 升序/降序切换

##### 5. CollectionManageView (合集管理)
- **新增排序功能**:
  - 排序字段: 创建时间、标题、文章数
  - 排序方向: 升序/降序切换

#### 技术实现
- 前端: Vue 3 + TypeScript + Tailwind CSS
- 后端: Spring Boot + MyBatis-Plus，完全支持所有筛选和排序参数
- API: 前端 API 层已完整支持 keyword, searchType, categorySlug, published, sortField, sortOrder 等参数

#### 验证结果
- ✅ 前端类型检查: vue-tsc --noEmit --pretty 通过
- ✅ 前端构建: vite build 成功
- ✅ 后端编译: mvnw compile 成功
- ✅ 所有管理页面的排序和筛选功能正常工作

#### 影响范围
- DashboardView.vue: 289 行修改
- CommentManageView.vue: 33 行修改
- UserManageView.vue: 38 行修改
- ImageManagerView.vue: 38 行修改
- CollectionManageView.vue: 58 行修改
- 总计: 385 行新增，71 行删除

#### 优势
1. **多维度定位**: 支持通过多个条件快速定位到目标数据
2. **灵活排序**: 支持按不同字段排序，升序/降序可切换
3. **管理效率**: 大幅提升管理页面的操作效率
4. **用户体验**: 界面直观，操作简单
5. **信息完整**: 文章卡片信息丰富，便于快速识别
