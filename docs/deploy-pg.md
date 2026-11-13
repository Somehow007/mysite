你们目前云服务器已经用 Docker 跑 MySQL 和 Redis 了，PostgreSQL 最自然的部署方式也是加一个 Docker 容器，和本地保持一致。

---

## 推荐方案：统一用 Docker

### 本地（Mac）

你当前 `docker-compose.yml` 已经配好了，直接启动就行：

```bash
cd docker
# 设置密码环境变量（临时）
export RAGENT_PG_PASSWORD=ragent123
docker compose up -d postgres
```

Homebrew 装的 PostgreSQL 可以留着不用，或者停掉避免端口冲突：

```bash
brew services stop postgresql@17   # 停掉，释放 5432 端口
```

**为什么不用 brew 的 PG？** brew 装的是原生 PG，没有 pgvector 插件。RAG 需要 pgvector 扩展做向量检索，Docker 镜像 `pgvector/pgvector:pg17` 是带 pgvector 的 PG 17，开箱即用。

### 云服务器

服务器上已有一个 `docker-compose.yml` 在跑 MySQL + Redis。部署步骤：

**1. 把更新后的 `docker-compose.yml` 同步到服务器：**

```bash
scp docker/docker-compose.yml ubuntu@124.222.65.169:/path/to/mysite/docker/
scp docker/init/ragent-schema.sql ubuntu@124.222.65.169:/path/to/mysite/docker/init/
```

**2. 在服务器上设置密码并启动 PG：**

```bash
# SSH 到服务器
ssh ubuntu@124.222.65.169

# 在 .bashrc 或 .env 中设密码（持久化）
echo 'export RAGENT_PG_PASSWORD=your-strong-password' >> ~/.bashrc
source ~/.bashrc

# 启动 PG（不影响已在运行的 mysql 和 redis）
cd /path/to/mysite/docker
docker compose up -d postgres
```

**3. 验证：**

```bash
docker exec mysite-pgvector psql -U ragent -d ragent -c "\dt"
# 应该看到 6 张表

docker exec mysite-pgvector psql -U ragent -d ragent -c "SELECT * FROM pg_extension WHERE extname='vector';"
# 确认 pgvector 扩展已安装
```

**4. 应用配置同步**：确保服务器上的 `application.yaml`（生产环境）中 `rag.datasource.password` 和服务器环境变量一致。

### 本地 vs 服务器连接地址

| 环境 | PG 地址 | 说明 |
|------|--------|------|
| Mac 本地 | `jdbc:postgresql://localhost:5432/ragent` | 当前 yaml 配置 |
| 云服务器 | `jdbc:postgresql://localhost:5432/ragent` | 同样是 localhost（Docker 端口映射到宿主机） |

不需要改连接地址，两边都是 `localhost:5432`。只是密码记得用环境变量 `${RAGENT_PG_PASSWORD}` 区分。

### 数据安全注意事项

- **PG 数据挂载到宿主机目录**：docker-compose 中 `./pgvector_data:/var/lib/postgresql/data`，容器挂了数据不丢
- **云服务器定期备份**：后续可以考虑加个 cron 定时 `pg_dump`
- **不要暴露 5432 到公网**：docker-compose 里 `ports: "5432:5432"` 只绑定了 localhost（Docker 默认不绑 0.0.0.0），云安全组也不需要放行 5432