# MySite 云服务器部署操作文档

**文档版本**: v2.0
**创建日期**: 2026-05-18
**适用项目**: MySite 博客系统 (Spring Boot 3 + Vue 3)

---

## 目录

1. [部署前准备工作](#1-部署前准备工作)
   - 1.1 [服务器环境配置要求](#11-服务器环境配置要求)
   - 1.2 [必要软件安装步骤](#12-必要软件安装步骤)
   - 1.3 [网络安全组设置规范](#13-网络安全组设置规范)
2. [完整部署流程](#2-完整部署流程)
   - 2.1 [部署架构概览](#21-部署架构概览)
   - 2.2 [步骤一：服务器环境初始化](#22-步骤一服务器环境初始化)
   - 2.3 [步骤二：部署数据库与中间件](#23-步骤二部署数据库与中间件)
   - 2.4 [步骤三：获取项目代码](#24-步骤三获取项目代码)
   - 2.5 [步骤四：配置环境变量](#25-步骤四配置环境变量)
   - 2.6 [步骤五：初始化数据库](#26-步骤五初始化数据库)
   - 2.7 [步骤六：执行部署脚本](#27-步骤六执行部署脚本)
   - 2.8 [步骤七：配置 Nginx](#28-步骤七配置-nginx)
   - 2.9 [步骤八：部署 Artalk 评论系统](#29-步骤八部署-artalk-评论系统)
3. [环境变量(.env)配置指南](#3-环境变量env配置指南)
   - 3.1 [.env 文件位置与创建方法](#31-env-文件位置与创建方法)
   - 3.2 [各配置项详细说明](#32-各配置项详细说明)
   - 3.3 [安全设置规范](#33-安全设置规范)
   - 3.4 [修改操作流程](#34-修改操作流程)
4. [涉及文件说明与位置索引](#4-涉及文件说明与位置索引)
   - 4.1 [云服务器文件目录结构](#41-云服务器文件目录结构)
   - 4.2 [配置文件清单](#42-配置文件清单)
   - 4.3 [应用文件清单](#43-应用文件清单)
   - 4.4 [日志文件清单](#44-日志文件清单)
   - 4.5 [项目源码中部署相关文件清单](#45-项目源码中部署相关文件清单)
5. [问题排查指南](#5-问题排查指南)
   - 5.1 [日志查看方法](#51-日志查看方法)
   - 5.2 [常见问题诊断与解决](#52-常见问题诊断与解决)
   - 5.3 [登录系统失败排查流程](#53-登录系统失败排查流程)
   - 5.4 [应急响应流程](#54-应急响应流程)
6. [验证与测试流程](#6-验证与测试流程)
   - 6.1 [基础服务验证](#61-基础服务验证)
   - 6.2 [后端 API 验证](#62-后端-api-验证)
   - 6.3 [前端功能验证](#63-前端功能验证)
   - 6.4 [性能测试步骤](#64-性能测试步骤)
7. [日常运维操作](#7-日常运维操作)
   - 7.1 [应用更新流程](#71-应用更新流程)
   - 7.2 [服务管理命令](#72-服务管理命令)
   - 7.3 [数据备份与恢复](#73-数据备份与恢复)
   - 7.4 [日志管理与轮转](#74-日志管理与轮转)
8. [附录](#8-附录)
   - 8.1 [端口一览表](#81-端口一览表)
   - 8.2 [常用命令速查](#82-常用命令速查)
   - 8.3 [SSL 证书配置](#83-ssl-证书配置)
   - 8.4 [Swap 空间配置](#84-swap-空间配置)

---

## 1. 部署前准备工作

### 1.1 服务器环境配置要求

#### 硬件配置

| 配置项 | 最低配置 | 推荐配置 | 说明 |
|--------|----------|----------|------|
| CPU | 2 核 | 4 核 | 后端编译和运行需要足够算力 |
| 内存 | 2 GB | 4 GB+ | JVM 默认分配 512-768MB，MySQL/Redis 需额外内存 |
| 硬盘 | 50 GB SSD | 100 GB SSD | 日志、数据库、Docker 镜像会持续占用空间 |
| 带宽 | 3 Mbps | 5 Mbps+ | 影响前端资源加载速度 |

#### 操作系统

| 系统 | 版本 | 推荐度 |
|------|------|--------|
| Ubuntu Server | 22.04 LTS / 24.04 LTS | 推荐 |
| Debian | 12 (Bookworm) | 可选 |
| CentOS Stream | 9 | 可选（需替换部分 apt 命令为 dnf） |

#### 软件版本要求

| 软件 | 最低版本 | 推荐版本 | 用途 |
|------|----------|----------|------|
| OpenJDK | 17 | 17 | 后端运行时 |
| Node.js | 18 | 20 LTS | 前端构建 |
| npm | 9 | 10+ | 前端依赖管理 |
| MySQL | 8.0 | 8.4 | 数据库 |
| Redis | 7.0 | 7.x | 缓存 |
| Nginx | 1.24 | 最新稳定版 | 反向代理与静态文件服务 |
| Docker | 24 | 最新稳定版 | 运行 Artalk 等容器化服务 |
| Git | 2.30+ | 最新 | 代码拉取 |

### 1.2 必要软件安装步骤

以下命令基于 Ubuntu 22.04/24.04，以 `root` 或具有 `sudo` 权限的用户执行。

#### 1.2.1 系统更新

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git vim htop net-tools unzip software-properties-common
```

#### 1.2.2 安装 OpenJDK 17

```bash
sudo apt install -y openjdk-17-jdk
java -version
# 预期输出: openjdk version "17.x.x"
```

#### 1.2.3 安装 Node.js 20 LTS

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node -v
# 预期输出: v20.x.x
npm -v
# 预期输出: 10.x.x
```

#### 1.2.4 安装 Nginx

```bash
sudo apt install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
nginx -v
# 预期输出: nginx version: nginx/1.x.x
```

#### 1.2.5 安装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# 重新登录使 docker 组生效
docker --version
# 预期输出: Docker version 2x.x.x
```

#### 1.2.6 安装 MySQL（本地部署方案）

```bash
sudo apt install -y mysql-server
sudo systemctl enable mysql
sudo systemctl start mysql
sudo mysql_secure_installation
mysql --version
# 预期输出: mysql  Ver 8.x.x
```

#### 1.2.7 安装 Redis（本地部署方案）

```bash
sudo apt install -y redis-server
sudo systemctl enable redis-server
sudo systemctl start redis-server
redis-cli ping
# 预期输出: PONG
```

### 1.3 网络安全组设置规范

#### 必须开放的外部端口

| 端口 | 协议 | 用途 | 访问范围 |
|------|------|------|----------|
| 22 | TCP | SSH 远程登录 | 建议限制为管理员 IP |
| 80 | TCP | HTTP Web 访问 | 0.0.0.0/0 |
| 443 | TCP | HTTPS Web 访问 | 0.0.0.0/0 |

#### 仅内网访问的端口（禁止对外暴露）

| 端口 | 协议 | 用途 | 安全风险 |
|------|------|------|----------|
| 3306 | TCP | MySQL 数据库 | 数据泄露、未授权访问 |
| 6379 | TCP | Redis 缓存 | 数据泄露、Redis 攻击 |
| 8081 | TCP | Spring Boot 后端 | 绕过 Nginx 直接访问 |
| 23366 | TCP | Artalk 评论服务 | 未授权管理操作 |
| 9200 | TCP | Elasticsearch（如启用） | 数据泄露、远程代码执行 |

#### UFW 防火墙配置示例

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

---

## 2. 完整部署流程

### 2.1 部署架构概览

```
                    Internet
                       │
                       ▼
              ┌────────────────┐
              │  Nginx (8080)  │  ← 对外服务端口
              │  反向代理/静态  │
              └───────┬────────┘
                      │
           ┌──────────┼──────────┐
           ▼          ▼          ▼
   /var/www/    localhost:8081  localhost:8081
   mysite/      /api/*, /v1/*  /artalk/*
   (前端静态)   (后端 API)     (评论代理)
                      │
           ┌──────────┼──────────┐
           ▼          ▼          ▼
     MySQL:3306  Redis:6379  Artalk:23366
     (数据库)    (缓存)     (评论系统)
```

**关键说明**：
- Nginx 监听 **8080** 端口对外提供服务
- Spring Boot 后端监听 **8081** 端口，仅接受本机请求
- 前端 API 请求通过 Nginx 代理转发到后端 8081 端口
- 前端生产环境 `VITE_API_BASE_URL=/`，所有 API 请求走相对路径

### 2.2 步骤一：服务器环境初始化

```bash
# 创建应用目录
sudo mkdir -p /opt/mysite
sudo mkdir -p /var/log/mysite
sudo mkdir -p /var/log/mysite/deploy
sudo mkdir -p /var/www/mysite
sudo mkdir -p /var/run

# 设置日志目录权限（使用当前用户运行应用）
sudo chown -R $(whoami):$(whoami) /var/log/mysite
```

### 2.3 步骤二：部署数据库与中间件

#### 方案 A：本地安装（适合低预算场景）

**MySQL 配置与初始化**：

```bash
# 登录 MySQL
sudo mysql -u root -p

# 执行以下 SQL
```

```sql
CREATE DATABASE IF NOT EXISTS mysite DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'mysite'@'localhost' IDENTIFIED BY '你的强密码';
GRANT ALL PRIVILEGES ON mysite.* TO 'mysite'@'localhost';
FLUSH PRIVILEGES;
```

**MySQL 低内存优化**（2GB 内存服务器必做）：

```bash
sudo tee /etc/mysql/mysql.conf.d/low-memory.cnf > /dev/null <<'EOF'
[mysqld]
performance_schema = OFF
innodb_buffer_pool_size = 128M
innodb_log_buffer_size = 8M
innodb_log_file_size = 48M
innodb_flush_log_at_trx_commit = 2
max_connections = 50
thread_cache_size = 8
table_open_cache = 64
tmp_table_size = 16M
max_heap_table_size = 16M
skip-external-locking
skip-name-resolve
EOF

sudo systemctl restart mysql
```

**Redis 配置**：

```bash
# 设置内存限制和淘汰策略
sudo bash -c 'cat >> /etc/redis/redis.conf <<EOF
maxmemory 100mb
maxmemory-policy allkeys-lru
EOF'

sudo systemctl restart redis-server
```

#### 方案 B：Docker Compose 部署（推荐）

```bash
# 创建数据目录
sudo mkdir -p /data/mysite/mysql /data/mysite/redis /data/mysite/artalk

# 从项目复制 docker-compose 配置
cd ~/project/mysite/docker
cp .env.example .env
vim .env
# 修改 MYSQL_ROOT_PASSWORD 为强密码

# 启动 MySQL 和 Redis（Elasticsearch 默认注释不启动）
docker compose up -d mysql redis

# 验证
docker ps
# 预期输出: mysite-mysql 和 mysite-redis 两个容器处于 running 状态
```

#### 方案 C：云数据库（生产环境推荐）

使用阿里云/腾讯云 RDS 和 Redis 服务，将数据库和缓存从服务器剥离，释放内存给应用层。配置时注意：
- 将服务器内网 IP 加入云数据库白名单
- 记录连接地址、端口、用户名和密码，后续写入 `.env` 文件

### 2.4 步骤三：获取项目代码

```bash
# 创建项目目录
mkdir -p ~/project

# 克隆代码
git clone https://github.com/Somehow007/mysite.git ~/project/mysite

# 进入项目目录
cd ~/project/mysite

# 确认代码版本
git log --oneline -1
# 预期输出: 最新的 commit 信息
```

### 2.5 步骤四：配置环境变量

> **这是部署中最关键的步骤**。`.env` 文件缺失或配置错误是导致"系统执行出错"的最常见原因。

```bash
# 复制模板创建 .env 文件
cp ~/project/mysite/deploy/config/.env.example ~/project/mysite/deploy/config/.env

# 编辑 .env 文件
vim ~/project/mysite/deploy/config/.env
```

编辑内容（根据实际环境填写）：

```bash
# 数据库配置
DB_HOST=localhost          # 云数据库填写内网地址，如 rds-xxx.mysql.rds.aliyuncs.com
DB_PORT=3306
DB_USER=root               # 专用用户建议使用 mysite
DB_PASSWORD=你的数据库密码   # 必填！没有默认值，留空将导致启动失败

# Redis 配置
REDIS_HOST=localhost       # 云 Redis 填写内网地址
REDIS_PORT=6379
REDIS_PASSWORD=            # 无密码留空即可

# JWT 配置
JWT_SECRET=请替换为至少32字符的随机字符串  # 生产环境必须更换！

# Artalk 评论系统配置
ARTALK_SERVER=http://localhost:23366
ARTALK_SITE=MySite博客
```

**验证 .env 文件是否正确**：

```bash
# 确认文件存在
ls -la ~/project/mysite/deploy/config/.env

# 确认关键变量已填写（不会显示密码值）
grep -E '^[A-Z]' ~/project/mysite/deploy/config/.env
```

### 2.6 步骤五：初始化数据库

> **必须执行此步骤**。生产配置 `ddl-auto: none` 不会自动建表。

```bash
# 使用项目中的 schema.sql 初始化表结构
mysql -u root -p mysite < ~/project/mysite/docker/init/schema.sql

# 验证表是否创建成功
mysql -u root -p -e "USE mysite; SHOW TABLES;"
# 预期输出:
# +------------------+
# | Tables_in_mysite |
# +------------------+
# | t_article        |
# | t_article_tag    |
# | t_category       |
# | t_tag            |
# | t_user           |
# | t_user_article_favorites |
# | t_user_follow    |
# | t_user_operation_log     |
# +------------------+
```

如需初始数据（管理员账号等），可额外执行 `data.sql`：

```bash
mysql -u root -p mysite < ~/project/mysite/docker/init/data.sql
```

### 2.7 步骤六：执行部署脚本

```bash
cd ~/project/mysite

# 添加执行权限
chmod +x deploy/server-deploy.sh
chmod +x deploy/scripts/lib.sh
chmod +x deploy/scripts/start.sh

# 执行部署
./deploy/server-deploy.sh
```

**部署脚本执行流程**：

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| 1 | `git pull origin main` | 代码更新到最新版本 |
| 2 | `mvn clean package -DskipTests` | 后端 JAR 构建成功 |
| 3 | `npm install && npm run build` | 前端 dist 目录生成 |
| 4 | 停止旧服务 | 旧进程终止，端口 8081 释放 |
| 5 | 复制 JAR、配置、脚本到 /opt/mysite/ | 文件部署完成 |
| 6 | 启动新服务 | Java 进程启动，PID 文件生成 |
| 7 | 复制前端文件到 /var/www/mysite/ | 静态资源部署完成 |
| 8 | 同步并重载 Nginx 配置 | Nginx 配置生效 |

**部署完成后验证**：

```bash
# 检查服务进程
/opt/mysite/start.sh status
# 预期输出: mysite is running (PID: xxxx)

# 检查日志是否有报错
tail -50 /var/log/mysite/console.log
# 预期输出: 包含 "Started MysiteApplication" 字样

# 检查健康端点
curl -s http://localhost:8081/actuator/health
# 预期输出: {"status":"UP"}
```

### 2.8 步骤七：配置 Nginx

部署脚本会自动同步 Nginx 配置，但首次部署可能需要手动确认：

```bash
# 确认配置文件已安装
ls -la /etc/nginx/sites-available/mysite.conf
ls -la /etc/nginx/sites-enabled/mysite.conf

# 如果软链接不存在，手动创建
sudo ln -sf /etc/nginx/sites-available/mysite.conf /etc/nginx/sites-enabled/mysite.conf

# 删除默认站点（可选）
sudo rm -f /etc/nginx/sites-enabled/default

# 测试配置
sudo nginx -t
# 预期输出: syntax is ok / test is successful

# 重载 Nginx
sudo systemctl reload nginx
```

**Nginx 配置关键说明**（`mysite.conf`）：

| 路径 | 代理目标 | 说明 |
|------|----------|------|
| `/` | `/var/www/mysite` | 前端静态文件，SPA 路由回退到 index.html |
| `/api/` | `http://localhost:8081/api/` | 后端 API 代理 |
| `/v[0-9]/` | `http://localhost:8081` | 版本化 API 代理（如 /v1/auth/login） |
| `/artalk/` | `http://localhost:8081/artalk/` | 评论系统代理 |

### 2.9 步骤八：部署 Artalk 评论系统

#### Docker 方式（推荐）

```bash
# 创建数据目录
sudo mkdir -p /data/mysite/artalk

# 复制 Artalk 配置
sudo cp ~/project/mysite/docker/artalk.yml /data/mysite/artalk/artalk.yml

# 启动 Artalk 容器
docker run -d \
  --name mysite-artalk \
  --restart unless-stopped \
  -p 23366:23366 \
  -v /data/mysite/artalk:/data \
  -v /data/mysite/artalk/artalk.yml:/data/artalk.yml:ro \
  -e TZ=Asia/Shanghai \
  artalk/artalk-go:latest

# 验证
docker ps | grep artalk
curl -s http://localhost:23366
```

#### Docker Compose 方式

```bash
cd ~/project/mysite/docker
docker compose up -d artalk
```

---

## 3. 环境变量(.env)配置指南

### 3.1 .env 文件位置与创建方法

#### 服务器上的位置

| 位置 | 用途 | 加载方式 |
|------|------|----------|
| `/opt/mysite/.env` | 应用运行时读取 | 由 `/opt/mysite/start.sh` 的 `load_env()` 函数加载 |
| `~/project/mysite/deploy/config/.env` | 部署脚本读取 | 由 `server-deploy.sh` 复制到 `/opt/mysite/.env` |

#### 创建方法

```bash
# 方法一：从模板复制（推荐）
cp ~/project/mysite/deploy/config/.env.example ~/project/mysite/deploy/config/.env
vim ~/project/mysite/deploy/config/.env

# 方法二：直接在应用目录创建
cp /opt/mysite/.env.example /opt/mysite/.env
vim /opt/mysite/.env
```

#### 加载机制说明

`start.sh` 中的 `load_env()` 函数逐行读取 `.env` 文件，将 `KEY=VALUE` 形式的行导出为环境变量：

- 以 `#` 开头的行被忽略
- 空行被忽略
- 等号 `=` 前后的空格会被自动去除
- 变量值**不支持引号包裹**，如需特殊字符请直接写入

### 3.2 各配置项详细说明

#### 数据库配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `DB_HOST` | 否 | `localhost` | MySQL 主机地址。云数据库填写内网地址 |
| `DB_PORT` | 否 | `3306` | MySQL 端口 |
| `DB_USER` | 否 | `root` | MySQL 用户名 |
| `DB_PASSWORD` | **是** | 无 | MySQL 密码。**没有默认值，必须填写**，否则应用无法启动 |

对应 `application-production.yml` 中的引用：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/mysite?...
    username: ${DB_USER:root}
    password: ${DB_PASSWORD}    # 注意：没有 :default，必须从 .env 读取
```

#### Redis 配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `REDIS_HOST` | 否 | `localhost` | Redis 主机地址 |
| `REDIS_PORT` | 否 | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 否 | 空 | Redis 密码。无密码留空即可 |

#### JWT 配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `JWT_SECRET` | 否 | 内置默认值 | JWT 签名密钥。**生产环境必须更换为安全随机字符串**，至少 32 字符 |

默认值来源（`application-production.yml`）：

```yaml
jwt:
  secret: ${JWT_SECRET:mysite-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-2026}
```

#### Artalk 评论系统配置

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `ARTALK_SERVER` | 否 | `http://localhost:23366` | Artalk 服务端地址 |
| `ARTALK_SITE` | 否 | `MySite博客` | Artalk 站点名称 |

### 3.3 安全设置规范

#### 密码强度要求

| 配置项 | 最低要求 | 推荐做法 |
|--------|----------|----------|
| `DB_PASSWORD` | 8 位以上，含大小写字母和数字 | 使用密码管理器生成 16 位以上随机密码 |
| `REDIS_PASSWORD` | 8 位以上 | 生产环境建议设置 |
| `JWT_SECRET` | 32 字符以上 | 使用 `openssl rand -base64 48` 生成 |

#### 生成安全密钥

```bash
# 生成 JWT Secret
openssl rand -base64 48

# 生成数据库密码
openssl rand -base64 24
```

#### 文件权限

```bash
# .env 文件包含敏感信息，必须限制访问权限
chmod 600 /opt/mysite/.env
chown $(whoami):$(whoami) /opt/mysite/.env

# 验证权限
ls -la /opt/mysite/.env
# 预期输出: -rw------- 1 user user ... /opt/mysite/.env
```

#### 禁止事项

- **禁止**将 `.env` 文件提交到 Git 仓库（`.gitignore` 已配置排除）
- **禁止**在多人可访问的终端上以明文方式查看 `.env`
- **禁止**在日志、聊天、文档中记录真实密码
- **禁止**多个环境共用同一密码

### 3.4 修改操作流程

修改 `.env` 后必须重启应用才能生效：

```bash
# 1. 编辑 .env 文件
vim /opt/mysite/.env

# 2. 重启应用
/opt/mysite/start.sh restart

# 3. 等待应用启动（约 15-30 秒）
sleep 20

# 4. 验证服务正常
curl -s http://localhost:8081/actuator/health
# 预期输出: {"status":"UP"}
```

如果修改的是数据库连接信息，还需验证数据库连通性：

```bash
# 测试 MySQL 连接
mysql -h ${DB_HOST} -P ${DB_PORT} -u ${DB_USER} -p${DB_PASSWORD} -e "SELECT 1"
# 预期输出: 1

# 测试 Redis 连接
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} ping
# 预期输出: PONG
```

---

## 4. 涉及文件说明与位置索引

### 4.1 云服务器文件目录结构

```
/
├── opt/mysite/                              # 应用主目录
│   ├── mysite.jar                           # 后端可执行 JAR 包
│   ├── start.sh                             # 应用启动/停止/状态脚本
│   ├── application-production.yml           # Spring Boot 生产配置
│   ├── .env                                 # 环境变量（敏感信息）
│   └── .env.example                         # 环境变量模板
│
├── var/www/mysite/                          # 前端静态文件目录
│   ├── index.html                           # SPA 入口
│   ├── assets/                              # JS/CSS/图片等编译产物
│   └── favicon.ico                          # 网站图标
│
├── var/log/mysite/                          # 应用日志目录
│   ├── console.log                          # 控制台输出（启动日志、未捕获异常）
│   ├── application.log                      # 应用结构化日志（运行时错误）
│   ├── heapdump.hprof                       # OOM 时的堆转储（如触发）
│   └── deploy/                              # 部署日志目录
│       └── deploy_20260518_120000.log       # 每次部署的详细日志
│
├── var/run/mysite.pid                       # 应用进程 PID 文件
│
├── etc/nginx/sites-available/mysite.conf    # Nginx 站点配置源文件
├── etc/nginx/sites-enabled/mysite.conf      # Nginx 站点配置软链接
│
├── var/log/nginx/                           # Nginx 日志目录
│   ├── mysite.access.log                    # 访问日志
│   └── mysite.error.log                     # 错误日志
│
├── data/mysite/                             # Docker 数据目录（如使用 Docker）
│   ├── mysql/                               # MySQL 数据
│   ├── redis/                               # Redis 数据
│   └── artalk/                              # Artalk 数据
│       └── artalk.yml                       # Artalk 配置
│
└── home/{user}/project/mysite/              # 项目源码目录
    ├── deploy/                              # 部署配置目录
    ├── docker/                              # Docker 配置目录
    ├── src/                                 # 后端源码
    ├── mysite-frontend/                     # 前端源码
    └── target/                              # Maven 构建产物
```

### 4.2 配置文件清单

| 文件路径 | 用途 | 修改频率 | 备注 |
|----------|------|----------|------|
| `/opt/mysite/.env` | 环境变量 | 首次部署时必改 | 包含敏感信息，权限 600 |
| `/opt/mysite/application-production.yml` | Spring Boot 生产配置 | 极少修改 | 由部署脚本自动同步 |
| `/etc/nginx/sites-available/mysite.conf` | Nginx 站点配置 | 配置域名/SSL 时修改 | 由部署脚本自动同步 |
| `/data/mysite/artalk/artalk.yml` | Artalk 评论系统配置 | 极少修改 | 需重启 Artalk 容器 |

### 4.3 应用文件清单

| 文件路径 | 用途 | 更新方式 |
|----------|------|----------|
| `/opt/mysite/mysite.jar` | 后端可执行 JAR | 部署脚本自动更新 |
| `/opt/mysite/start.sh` | 应用管理脚本 | 部署脚本自动更新 |
| `/var/www/mysite/*` | 前端静态资源 | 部署脚本自动更新 |
| `/var/run/mysite.pid` | 进程 PID 记录 | 自动创建/删除 |

### 4.4 日志文件清单

| 文件路径 | 内容 | 查看命令 |
|----------|------|----------|
| `/var/log/mysite/console.log` | JVM 控制台输出，包含启动日志和未捕获异常堆栈 | `tail -100 /var/log/mysite/console.log` |
| `/var/log/mysite/application.log` | 应用结构化日志，包含业务错误和请求日志 | `tail -100 /var/log/mysite/application.log` |
| `/var/log/mysite/deploy/deploy_*.log` | 每次部署的完整操作日志 | `ls -lt /var/log/mysite/deploy/` |
| `/var/log/nginx/mysite.access.log` | Nginx 访问日志 | `tail -100 /var/log/nginx/mysite.access.log` |
| `/var/log/nginx/mysite.error.log` | Nginx 错误日志（代理失败等） | `tail -100 /var/log/nginx/mysite.error.log` |
| `/var/log/mysite/heapdump.hprof` | OOM 堆转储文件（如触发） | 使用 MAT 或 jvisualvm 分析 |
| `/var/log/mysite/monitor.log` | 监控脚本日志 | `tail -50 /var/log/mysite/monitor.log` |

### 4.5 项目源码中部署相关文件清单

| 项目内路径 | 服务器目标路径 | 说明 |
|------------|----------------|------|
| `deploy/server-deploy.sh` | 不复制到服务器 | 服务器端部署脚本，在项目目录内执行 |
| `deploy/deploy.sh` | 不复制到服务器 | 本地构建打包脚本 |
| `deploy/scripts/start.sh` | `/opt/mysite/start.sh` | 应用管理脚本 |
| `deploy/scripts/lib.sh` | 不复制到服务器 | 部署脚本公共函数库 |
| `deploy/scripts/monitor.sh` | `/opt/mysite/monitor.sh`（需手动复制） | 监控脚本 |
| `deploy/config/.env.example` | `/opt/mysite/.env.example` | 环境变量模板 |
| `deploy/config/application-production.yml` | `/opt/mysite/application-production.yml` | 生产配置 |
| `deploy/nginx/mysite.conf` | `/etc/nginx/sites-available/mysite.conf` | Nginx 站点配置 |
| `docker/init/schema.sql` | 不复制到服务器 | 数据库表结构初始化脚本 |
| `docker/init/data.sql` | 不复制到服务器 | 数据库初始数据脚本 |
| `docker/docker-compose.yml` | 不复制到服务器 | Docker Compose 编排文件 |
| `docker/artalk.yml` | `/data/mysite/artalk/artalk.yml` | Artalk 配置 |

---

## 5. 问题排查指南

### 5.1 日志查看方法

#### 快捷命令（推荐）

```bash
# 使用 start.sh 内置日志命令
/opt/mysite/start.sh logs          # 跟踪 console.log（默认）
/opt/mysite/start.sh logs app      # 跟踪 application.log
/opt/mysite/start.sh logs console  # 跟踪 console.log
/opt/mysite/start.sh logs deploy   # 跟踪最新部署日志
```

#### 手动查看

```bash
# 查看最近 200 行控制台日志（启动失败首先看这里）
tail -200 /var/log/mysite/console.log

# 查看最近 200 行应用日志（运行时错误看这里）
tail -200 /var/log/mysite/application.log

# 搜索特定错误
grep -i "error\|exception\|failed" /var/log/mysite/console.log | tail -50
grep -i "error\|exception\|failed" /var/log/mysite/application.log | tail -50

# 查看部署日志
ls -lt /var/log/mysite/deploy/
tail -100 /var/log/mysite/deploy/deploy_$(ls -t /var/log/mysite/deploy/ | head -1 | sed 's/deploy_//;s/\.log//')

# 查看 Nginx 错误日志
tail -100 /var/log/nginx/mysite.error.log
```

### 5.2 常见问题诊断与解决

#### 问题 1：应用启动失败 — "系统执行出错"

**症状**：所有 API 返回 `{"code":"B000001","message":"系统执行出错"}`

**根因**：后端抛出未捕获异常，被 `GlobalExceptionHandler.defaultErrorHandler()` 捕获

**排查步骤**：

```bash
# 1. 检查 Java 进程是否存活
/opt/mysite/start.sh status

# 2. 查看控制台日志中的异常堆栈
tail -200 /var/log/mysite/console.log

# 3. 常见异常及解决方案：
```

| 异常信息 | 原因 | 解决方案 |
|----------|------|----------|
| `Could not resolve placeholder 'DB_PASSWORD'` | `.env` 文件缺失或 `DB_PASSWORD` 未设置 | 创建 `/opt/mysite/.env` 并填写 `DB_PASSWORD` |
| `Communications link failure` / `Connection refused` | MySQL 未运行或连接信息错误 | 检查 MySQL 状态和 `.env` 中 `DB_HOST`/`DB_PORT`/`DB_PASSWORD` |
| `Unable to connect to Redis` | Redis 未运行或连接信息错误 | 检查 Redis 状态和 `.env` 中 `REDIS_HOST`/`REDIS_PORT` |
| `Table 'mysite.t_user' doesn't exist` | 数据库表未初始化 | 执行 `schema.sql` 初始化表结构 |
| `OutOfMemoryError` | JVM 内存不足 | 调整 `start.sh` 中 `-Xmx` 参数或增加服务器内存 |

#### 问题 2：部署脚本报错 "服务启动后进程不存在"

```bash
# 查看启动失败原因
tail -100 /var/log/mysite/console.log

# 常见原因：
# 1. .env 文件缺失 → 创建 .env
# 2. 数据库连接失败 → 检查 MySQL 状态和连接参数
# 3. 端口被占用 → sudo lsof -i :8081
# 4. 内存不足 → free -h
```

#### 问题 3：前端页面空白

```bash
# 检查前端文件是否存在
ls -la /var/www/mysite/
# 预期输出: 应包含 index.html 和 assets/ 目录

# 检查 Nginx 配置
sudo nginx -t

# 检查浏览器控制台（F12）中的错误信息
# 常见原因：API 请求 404 → 检查 Nginx 代理配置
```

#### 问题 4：502 Bad Gateway

```bash
# 后端服务未运行
/opt/mysite/start.sh status
# 如果未运行，启动服务
/opt/mysite/start.sh start

# 后端正在启动中（Spring Boot 启动需要 15-30 秒）
# 等待启动完成后重试

# 检查 Nginx 代理配置是否指向正确端口
grep proxy_pass /etc/nginx/sites-available/mysite.conf
# 预期输出: proxy_pass http://localhost:8081
```

#### 问题 5：CORS 跨域错误

本项目后端有自定义 `CorsFilter`（优先级最高），同时 `WebSecurityConfig` 中禁用了 Spring CORS。正常情况下通过 Nginx 同源代理不会有跨域问题。如果出现跨域错误：

```bash
# 确认请求是通过 Nginx 代理的（而非直接访问 8081 端口）
# 正确: http://your-server:8080/api/...
# 错误: http://your-server:8081/api/...
```

### 5.3 登录系统失败排查流程

按以下顺序逐步排查：

```
登录失败
    │
    ▼
[1] Java 进程是否存活？
    /opt/mysite/start.sh status
    │
    ├─ 否 → 查看console.log → 修复启动问题 → 重启服务
    │
    ▼ 是
[2] 数据库是否可连接？
    mysql -h $DB_HOST -u $DB_USER -p
    │
    ├─ 否 → 检查MySQL状态 → 检查.env配置
    │
    ▼ 是
[3] 数据库表是否存在？
    mysql -e "USE mysite; SHOW TABLES;"
    │
    ├─ 否 → 执行 schema.sql
    │
    ▼ 是
[4] 直接测试登录 API
    curl -v -X POST http://localhost:8081/v1/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"test","password":"test"}'
    │
    ├─ 连接拒绝 → 应用未启动或端口错误
    ├─ "系统执行出错" → 查看application.log中的异常堆栈
    ├─ "USER_LOGIN_BAD_CREDENTIALS" → 用户名或密码错误
    └─ 返回token → 后端正常，检查Nginx代理
    │
    ▼ 后端正常
[5] 通过 Nginx 测试
    curl -v -X POST http://localhost:8080/v1/auth/login \
      -H "Content-Type: application/json" \
      -d '{"username":"test","password":"test"}'
    │
    ├─ 502 → 后端未运行
    ├─ 404 → Nginx 代理路径配置错误
    └─ 返回token → 后端和代理均正常，检查前端配置
```

### 5.4 应急响应流程

1. **发现问题** — 监控告警或用户反馈
2. **评估影响** — 确定影响范围（全站/部分功能/特定用户）
3. **快速恢复** — 优先重启服务恢复可用性
   ```bash
   /opt/mysite/start.sh restart
   ```
4. **根因分析** — 查看日志定位具体原因
   ```bash
   tail -200 /var/log/mysite/console.log
   tail -200 /var/log/mysite/application.log
   ```
5. **修复验证** — 修复问题后验证功能正常
6. **文档记录** — 记录问题原因和解决方案

---

## 6. 验证与测试流程

### 6.1 基础服务验证

```bash
# 1. 检查 Java 进程
/opt/mysite/start.sh status
# 预期: mysite is running (PID: xxxx)

# 2. 检查 MySQL
mysql -u root -p -e "SELECT 1"
# 预期: 1

# 3. 检查 Redis
redis-cli ping
# 预期: PONG

# 4. 检查 Nginx
sudo systemctl status nginx
# 预期: active (running)

# 5. 检查 Artalk（如使用 Docker）
docker ps | grep artalk
curl -s http://localhost:23366 | head -5

# 6. 检查端口监听
sudo ss -tlnp | grep -E '8080|8081|3306|6379|23366'
# 预期:
# 8080 - nginx
# 8081 - java
# 3306 - mysql
# 6379 - redis
# 23366 - artalk
```

### 6.2 后端 API 验证

```bash
# 1. 健康检查端点
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
# 预期: {"status": "UP", ...}

# 2. 公开文章列表（无需认证）
curl -s http://localhost:8081/v1/articles | python3 -m json.tool
# 预期: {"code": "0", "data": {...}}

# 3. 登录接口
curl -s -X POST http://localhost:8081/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的用户名","password":"你的密码"}' | python3 -m json.tool
# 预期: {"code": "0", "data": {"accessToken": "eyJ...", ...}}

# 4. 通过 Nginx 代理访问（验证代理配置）
curl -s http://localhost:8080/v1/articles | python3 -m json.tool
# 预期: 与直接访问 8081 相同的响应

# 5. 验证认证保护接口
curl -s http://localhost:8081/v1/auth/me
# 预期: 401 未授权

TOKEN="上一步获取的accessToken"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/v1/auth/me | python3 -m json.tool
# 预期: {"code": "0", "data": {"username": "...", ...}}
```

### 6.3 前端功能验证

| 验证项 | 操作方法 | 预期结果 |
|--------|----------|----------|
| 首页加载 | 浏览器访问 `http://服务器IP:8080` | 博客首页正常显示 |
| 文章列表 | 首页向下滚动 | 文章卡片正常加载 |
| 文章详情 | 点击任意文章 | 文章内容正常渲染 |
| 登录页面 | 点击登录按钮 | 登录表单正常显示 |
| 登录功能 | 输入用户名密码登录 | 登录成功，跳转到首页 |
| 评论系统 | 在文章详情页底部 | Artalk 评论区正常显示 |
| 分类/标签 | 点击分类或标签 | 筛选结果正常显示 |

### 6.4 性能测试步骤

#### 基础响应时间测试

```bash
# 安装 Apache Bench（如未安装）
sudo apt install -y apache2-utils

# 测试首页响应时间
ab -n 100 -c 10 http://localhost:8080/
# 关注: Time per request (mean)

# 测试 API 响应时间
ab -n 100 -c 10 http://localhost:8080/v1/articles
# 关注: Time per request (mean)

# 测试登录接口
ab -n 50 -c 5 -p login.json -T "application/json" http://localhost:8080/v1/auth/login
# 其中 login.json 内容: {"username":"test","password":"test"}
```

#### 系统资源监控

```bash
# 在压测期间另开终端监控
# CPU 和内存
htop

# Java 进程内存
/opt/mysite/start.sh status

# JVM GC 情况
jstat -gcutil $(cat /var/run/mysite.pid) 1000 10
```

#### 性能基线参考

| 指标 | 可接受值 | 理想值 |
|------|----------|--------|
| 首页加载时间 | < 3s | < 1s |
| API 平均响应时间 | < 500ms | < 200ms |
| 内存使用率 | < 85% | < 70% |
| CPU 使用率（空闲时） | < 30% | < 15% |

---

## 7. 日常运维操作

### 7.1 应用更新流程

#### 标准更新（推荐）

```bash
cd ~/project/mysite
./deploy/server-deploy.sh
```

部署脚本会自动完成：拉取代码 → 构建后端 → 构建前端 → 停止旧服务 → 部署新版本 → 启动服务 → 更新前端 → 重载 Nginx

#### 仅更新后端

```bash
cd ~/project/mysite
git pull origin main
./mvnw clean package -DskipTests
sudo cp target/*.jar /opt/mysite/mysite.jar
/opt/mysite/start.sh restart
```

#### 仅更新前端

```bash
cd ~/project/mysite
git pull origin main
cd mysite-frontend
npm install
npm run build
sudo rm -rf /var/www/mysite/*
sudo cp -r dist/* /var/www/mysite/
```

### 7.2 服务管理命令

```bash
# 后端应用管理
/opt/mysite/start.sh start       # 启动
/opt/mysite/start.sh stop        # 停止
/opt/mysite/start.sh restart     # 重启
/opt/mysite/start.sh status      # 查看状态和内存
/opt/mysite/start.sh logs        # 跟踪日志
/opt/mysite/start.sh logs app    # 跟踪应用日志

# Nginx 管理
sudo nginx -t                    # 测试配置
sudo systemctl reload nginx      # 重载配置
sudo systemctl restart nginx     # 重启服务
sudo systemctl status nginx      # 查看状态

# Docker 容器管理
docker ps                        # 查看运行中的容器
docker logs mysite-mysql         # 查看 MySQL 日志
docker logs mysite-redis         # 查看 Redis 日志
docker logs mysite-artalk        # 查看 Artalk 日志
docker restart mysite-mysql      # 重启 MySQL
docker restart mysite-redis      # 重启 Redis
docker restart mysite-artalk     # 重启 Artalk

# MySQL 管理（本地安装方式）
sudo systemctl start mysql
sudo systemctl stop mysql
sudo systemctl restart mysql
sudo systemctl status mysql

# Redis 管理（本地安装方式）
sudo systemctl start redis-server
sudo systemctl stop redis-server
sudo systemctl restart redis-server
sudo systemctl status redis-server
```

### 7.3 数据备份与恢复

#### 手动备份

```bash
# 创建备份目录
sudo mkdir -p /var/backups/mysite

# 备份数据库
mysqldump -h ${DB_HOST} -u ${DB_USER} -p${DB_PASSWORD} mysite | gzip > /var/backups/mysite/mysite_$(date +%Y%m%d_%H%M%S).sql.gz

# 备份 .env 配置
cp /opt/mysite/.env /var/backups/mysite/env_$(date +%Y%m%d_%H%M%S).bak
```

#### 自动备份（定时任务）

```bash
# 创建备份脚本
sudo tee /opt/mysite/backup.sh > /dev/null <<'SCRIPT'
#!/bin/bash
source /opt/mysite/.env
BACKUP_DIR="/var/backups/mysite"
mkdir -p $BACKUP_DIR
DATE=$(date +%Y%m%d_%H%M%S)

mysqldump -h ${DB_HOST} -u ${DB_USER} -p${DB_PASSWORD} mysite | gzip > $BACKUP_DIR/mysite_$DATE.sql.gz

find $BACKUP_DIR -name "mysite_*.sql.gz" -mtime +7 -delete

echo "[$(date)] Backup completed: mysite_$DATE.sql.gz" >> /var/log/mysite/backup.log
SCRIPT

sudo chmod +x /opt/mysite/backup.sh

# 添加定时任务（每天凌晨 2 点执行）
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/mysite/backup.sh") | crontab -
```

#### 数据恢复

```bash
# 解压并恢复数据库
gunzip < /var/backups/mysite/mysite_20260518_020000.sql.gz | mysql -h ${DB_HOST} -u ${DB_USER} -p${DB_PASSWORD} mysite
```

### 7.4 日志管理与轮转

#### 配置 logrotate

```bash
sudo tee /etc/logrotate.d/mysite > /dev/null <<'EOF'
/var/log/mysite/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0640 root root
    sharedscripts
    postrotate
        /opt/mysite/start.sh restart > /dev/null 2>&1 || true
    endscript
}

/var/log/mysite/deploy/*.log {
    rotate 90
    compress
    delaycompress
    missingok
    notifempty
}
EOF
```

#### 手动清理日志

```bash
# 清理 7 天前的应用日志
find /var/log/mysite -name "*.log" -mtime +7 -exec gzip {} \;

# 清理 30 天前的压缩日志
find /var/log/mysite -name "*.gz" -mtime +30 -delete

# 清理堆转储文件（确认不需要后）
rm -f /var/log/mysite/heapdump.hprof
```

---

## 8. 附录

### 8.1 端口一览表

| 端口 | 服务 | 监听地址 | 对外暴露 | 说明 |
|------|------|----------|----------|------|
| 8080 | Nginx | 0.0.0.0 | 是 | Web 入口（HTTP） |
| 8081 | Spring Boot | 127.0.0.1 | 否 | 后端 API |
| 3306 | MySQL | 127.0.0.1 | 否 | 数据库 |
| 6379 | Redis | 127.0.0.1 | 否 | 缓存 |
| 23366 | Artalk | 127.0.0.1 | 否 | 评论系统 |
| 22 | SSH | 0.0.0.0 | 是 | 远程管理 |

### 8.2 常用命令速查

```bash
# 应用管理
/opt/mysite/start.sh {start|stop|restart|status|logs}

# 日志查看
tail -200 /var/log/mysite/console.log       # 启动日志
tail -200 /var/log/mysite/application.log   # 运行日志
tail -100 /var/log/nginx/mysite.error.log   # Nginx 错误

# 健康检查
curl -s http://localhost:8081/actuator/health

# 端口检查
sudo lsof -i :8081                          # 检查后端端口
sudo ss -tlnp | grep -E '8080|8081'         # 检查端口监听

# 进程检查
ps aux | grep java                          # Java 进程
cat /var/run/mysite.pid                     # PID 文件

# 磁盘空间
df -h                                       # 磁盘使用率
du -sh /var/log/mysite/                     # 日志目录大小
du -sh /data/mysite/                        # 数据目录大小

# 内存使用
free -h                                     # 系统内存
/opt/mysite/start.sh status                 # 应用内存

# 数据库连接测试
mysql -h ${DB_HOST} -u ${DB_USER} -p${DB_PASSWORD} -e "SELECT 1"

# Redis 连接测试
redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} ping
```

### 8.3 SSL 证书配置

```bash
# 安装 Certbot
sudo apt install -y certbot python3-certbot-nginx

# 获取证书（需要域名已解析到服务器 IP）
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 验证自动续期
sudo certbot renew --dry-run

# 查看证书信息
sudo certbot certificates
```

配置 SSL 后，Nginx 会自动将 HTTP 80 端口请求重定向到 HTTPS 443 端口。

### 8.4 Swap 空间配置

2GB 内存的服务器建议配置 2GB Swap 防止 OOM：

```bash
# 创建 Swap 文件
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 永久生效
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 优化 Swap 策略（尽量使用物理内存）
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf

# 验证
free -h
# 预期输出: Swap 行显示约 2.0G
```
