# 11-17 站点配置后台可改（t_site_config + PUT /v1/site/config + /dashboard/site）

## 背景

前台 SEO 上线后暴露两个问题：

1. About 页/作者卡内容为空——`SITE_*` env 只在部署时生效，用户要求「后台那边能改」。
2. 站点标题写死在 env/yaml，改一次要改服务器 `.env` + 重启后端。

目标：站点身份与关于内容改为 DB 持久化、后台表单可编辑、保存即时生效；env/yaml 保留为默认值兜底，存量部署零迁移成本。

## 方案

**单行表 `t_site_config`（id=1）+ SiteProperties 单例覆盖**

- 字段 NULL = 继承 env/yaml 默认；非空 = 后台覆盖值。
- `SiteConfigServiceImpl.init()`（@PostConstruct）：启动时把 DB 非空字段 `apply()` 到 `SiteProperties` 单例；表不存在/异常 → warn 降级纯 env。
- `PUT /v1/site/config`（`SiteConfigUpdateReqDTO`）：字符串空白归一 null = 清除覆盖回退 env；`UpdateWrapper` 全列 set（`updateById` 会跳过 null 写不进「清除」）。
- 保存后立即 `apply()` 到单例 → `GET /v1/site/config` 即时返回新值，前端 store 回写，无需重启。
- `navigation` / `askExamples` / `askDemo` 结构性配置不开放后台编辑，仍走 yaml/env。

**安全**：`WebSecurityConfig` 新增 `PUT /v1/site/config → hasRole("ADMIN")`，置于 `"/v1/site/**"` permitAll 之前（顺序即优先级，反了会公开写接口——已在代码注释标明）。

**前端 `/dashboard/site`（requiresAdmin）**：`SiteSettingsView.vue` 分组表单（基本信息/作者信息/站点链接/关于长文 markdown/评论开关），`api/site.ts` 封装 get/update，保存后 `siteStore.site = 响应` 即时生效；侧栏「站点设置」入口（Globe 图标）。

## 关键决策

- **为什么覆盖单例而不是每次查库**：`SiteProperties` 被全站注入（controller/service/前端 store 唯一数据源），覆盖单例则所有消费点零改动即时生效；代价是多实例部署需各自 PUT（个人博客单实例，可接受）。
- **空白=清除覆盖而非写空串**：env 兜底语义统一，避免 DB 空串把 env 值「顶掉」变成空标题。
- **migration + schema.sql 双写**：存量库走 `docker/init/migrations/20261117-site-config.sql`；新库 schema.sql 已含该表（注意 schema.sql 里 t_* 表区块与 sj_* 区块之间还有一段历史错位，新表插在评论点赞表后）。

## 验证

- `./mvnw compile` ✅；`./scripts/verify.sh`（quick：hermetic 测试 + 前端 type-check）✅
- 新增 `SiteConfigServiceImplTest`（insert/update 两路径 + 空白归一）；`SiteConfigControllerTest` 补 PUT 委托用例 + mock 构造修复
- 前端 `type-check`/`build` ✅、改动文件 eslint ✅（顺手清掉 DashboardSidebar 未使用的 Settings 导入）
- 生产：备份 `/var/backups/mysite-mysql/mysite-20261117-114918.sql.gz` → 应用 migration（`SHOW CREATE TABLE` 确认）→ `server-deploy.sh` 一次过（521b83f，后端 82M，前端 312 文件）
- 生产终验：GET 返回 env 值（DB 空→兜底生效）；未授权 PUT → 401；`/dashboard/site` 未登录跳 `/login`；bundle 含 `SiteSettingsView-*.js`；首页元数据无回归

## 待办 / 后续

- **管理员实测保存**：生产 admin 密码非种子值（admin/000000 登录失败），PUT 200 路径由单测覆盖，UI 保存待用户登录 `/dashboard/site` 实测。
- env `.env` 里的 `SITE_*` 仍是默认值来源；后台清空字段会回退到 env 值（设计使然，前端表单已提示）。
- `t_site_config` 目前仅单行；若将来要多语言/多站点再扩展。
