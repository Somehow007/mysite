# 11-17 生产部署（含 knife4j 启动事故）

## 背景

76 个提交（前台纸本重构 T1–T18 + 后台 D1–D5 + RAG 安全收口 + 置顶/合集进度）推送后，
DB 迁移已于 11-16 预应用（article-top / collection-status），服务器仓库已同步。
用户确认上线，执行 `deploy/server-deploy.sh`。

## 事故

部署脚本在 `step_start_service` 就绪检查处 `die`：

- `deploy/config/application-production.yml` 新增「生产关 API 文档」配置：
  `knife4j.production: true` + `springdoc.api-docs.enabled: false` +
  `springdoc.swagger-ui.enabled: false`。
- springdoc 关闭后 `SpringDocConfigProperties` bean 不再注册，而 knife4j 4.5.0 的
  `Knife4jAutoConfiguration` 无条件创建 `knife4jOpenApiCustomizer`（构造注入
  `SpringDocConfigProperties`）→ `UnsatisfiedDependencyException` → 上下文刷新取消，
  进程退出。后端 API 自 ~23:57 起中断。

## 处置（按时间序）

1. **应急回滚**（先恢复服务）：服务器 `/opt/mysite/application-production.yml`
   删除 `springdoc:` 禁用块、保留 `knife4j.production: true` → springdoc 恢复启用、
   bean 齐全 → 重启成功（PID 2191912）。文档暴露回到部署前水平，可接受的临时态。
2. **根因修复**（本地精确复现）：
   - 反编译 knife4j-starter 4.5.0 jar：`Knife4jAutoConfiguration` 类级
     `@ConditionalOnProperty(name="knife4j.enable", havingValue="true")` ——
     官方开关即 `knife4j.enable=false`，整组关闭自动装配，无需 exclude。
   - 应用代码侧无任何 bean 注入 `SpringDocConfigProperties`（仅 swagger 注解 +
     `SwaggerConfiguration` 的 OpenAPI model bean），`knife4j.enable=false` 安全。
   - `knife4j-openapi3-ui` jar 把 `doc.html`/`webjars` 打进 `META-INF/resources`，
     静态资源兜底仍会 200——但 api-docs 已死该页是死壳，且 8081 不对公网开放、
     nginx :8080 的 `/doc.html` 走 SPA fallback 返回前端 index.html，公网无暴露。
3. **验证**：本地 `java -Dspring.profiles.active=production
   -Dspring.config.additional-location=file:... -jar`（与服务器 start.sh 同参）
   起 18081：`/v1/site/config` `/v1/rag/knowledge-bases` `/actuator/health` =200，
   `/v3/api-docs` `/swagger-ui/index.html` 返回 B000001 统一错误（无 schema）。
4. **提交推送**：`50db2df`（verify.sh quick 绿）。
5. **补完部署**（不重跑整脚本，手工执行剩余步骤）：
   - 服务器 git pull → 50db2df；cp 修正后 yml → `/opt/mysite/` → restart（PID 2193713）。
   - 前端 dist（失败那次已构建 307 文件，本次提交不动前端）→ `/var/www/mysite/`。
   - nginx conf 与仓库一致（M4-3 切流时已同步），无需重启。
6. **终验**：`/v1/site/config`、`/v1/articles` 返真实数据；`/actuator/health` 200；
   `/v1/admin/ai/providers` 401；`/uploads/images/` 403；公网 :8080 全通；
   新前端 bundle `index-DTSOlVcg.js` 生效；`:8081/v3/api-docs` B000001。

## Resolution

- 生产已跑新版本：后端新 JAR + 新前端，全部健康检查通过。
- 生产文档端点已按预期关闭（api-docs/swagger-ui 无 schema；doc.html 仅剩
  localhost 静态死壳，公网不可达）。
- 服务器 `/opt/mysite/application-production.yml.bak-20261116` 留有回滚前配置备份。
- 经验：`knife4j.enable=false` 是关 knife4j 的官方属性；springdoc 关闭前必须先确认
  没有其他自动装配依赖 `SpringDocConfigProperties`。
