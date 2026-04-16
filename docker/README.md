# MySite 开发环境快速启动指南

本指南帮助你在新设备上快速启动 MySite 项目。

## 前置要求

- Docker 和 Docker Compose
- JDK 17+
- Node.js 18+ (前端开发)
- Maven 3.8+

## 快速启动

### 1. 启动基础服务

```bash
cd docker
chmod +x start.sh
./start.sh
```

这将启动以下服务：
- **MySQL** (端口 3306) - 数据库
- **Redis** (端口 6379) - 缓存
- **Elasticsearch** (端口 9200) - 搜索引擎
- **Artalk** (端口 23366) - 评论系统

### 2. 配置后端

修改 `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mysite?useUnicode=true&characterEncoding=UTF-8&rewriteBatchedStatements=true&allowMultiQueries=true&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: root123456  # 与 docker/.env 中配置一致
  data:
    redis:
      host: localhost
      port: 6379
  elasticsearch:
    uris: http://localhost:9200
    username: elastic
    password: elastic123456  # 与 docker/.env 中配置一致
```

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

后端服务将在 `http://localhost:8081` 启动。

### 4. 启动前端

```bash
cd mysite-frontend
npm install
npm run dev
```

前端服务将在 `http://localhost:5173` 启动。

## 默认账号

| 服务 | 用户名 | 密码 |
|------|--------|------|
| MySQL | root | root123456 |
| Elasticsearch | elastic | elastic123456 |
| 后端管理员 | admin | admin123 |

## 目录结构

```
docker/
├── docker-compose.yml   # Docker Compose 配置
├── .env.example         # 环境变量示例
├── init/
│   └── init.sql         # 数据库初始化脚本
├── start.sh             # 启动脚本
└── stop.sh              # 停止脚本
```

## 常用命令

```bash
# 启动所有服务
cd docker && ./start.sh

# 停止所有服务
cd docker && ./stop.sh

# 查看服务日志
docker-compose logs -f [服务名]

# 进入 MySQL 容器
docker exec -it mysite-mysql mysql -uroot -proot123456

# 进入 Redis 容器
docker exec -it mysite-redis redis-cli

# 重启单个服务
docker-compose restart [服务名]
```

## 注意事项

1. **首次启动**：首次启动 MySQL 时会自动执行 `init/init.sql` 初始化数据库
2. **数据持久化**：数据存储在 Docker volumes 中，删除容器不会丢失数据
3. **端口冲突**：如果端口被占用，请修改 `docker-compose.yml` 中的端口映射
4. **内存要求**：Elasticsearch 默认使用 512MB 内存，可在 `docker-compose.yml` 中调整

## 生产环境部署

生产环境部署时，请务必：

1. 修改 `.env` 中的默认密码
2. 配置防火墙规则，限制端口访问
3. 使用 HTTPS
4. 配置数据备份策略
