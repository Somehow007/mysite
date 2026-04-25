# MySite 云服务器部署与运维完整指南

## 目录

1. [首次部署流程](#1-首次部署流程)
2. [日常更新维护](#2-日常更新维护)
3. [常规运维工作](#3-常规运维工作)
4. [文档与记录](#4-文档与记录)
5. [安全最佳实践](#5-安全最佳实践)
6. [故障排查手册](#6-故障排查手册)

---

## 1. 首次部署流程

### 1.1 云服务器选型与配置

#### 推荐配置
| 配置项 | 最低配置 | 推荐配置 |
|--------|----------|----------|
| CPU | 2核 | 4核 |
| 内存 | 4GB | 8GB |
| 硬盘 | 50GB SSD | 100GB SSD |
| 带宽 | 3Mbps | 5Mbps |
| 操作系统 | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |

#### 安全组配置
```bash
# 必须开放的端口
22    - SSH
80    - HTTP
443   - HTTPS
8080  - 应用端口（如需要）

# 内部服务端口（仅内网访问）
3306  - MySQL
6379  - Redis
23366 - Artalk
```

### 1.2 服务器环境初始化

#### 1.2.1 系统更新
```bash
# 更新系统包
sudo apt update && sudo apt upgrade -y

# 安装基础工具
sudo apt install -y curl wget git vim htop net-tools unzip
```

#### 1.2.2 安装 Docker
```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 添加当前用户到 docker 组
sudo usermod -aG docker $USER

# 启动 Docker 服务
sudo systemctl enable docker
sudo systemctl start docker

# 验证安装
docker --version
```

#### 1.2.3 安装 Nginx
```bash
# 安装 Nginx
sudo apt install -y nginx

# 启动并设置开机自启
sudo systemctl enable nginx
sudo systemctl start nginx

# 验证安装
nginx -v
```

#### 1.2.4 安装 Java（如不使用 Docker 运行后端）
```bash
# 安装 OpenJDK 17
sudo apt install -y openjdk-17-jdk

# 验证安装
java -version
```

### 1.3 数据库与中间件部署

#### 1.3.1 创建 Docker 网络
```bash
docker network create mysite-network
```

#### 1.3.2 部署 MySQL
```bash
# 创建数据目录
sudo mkdir -p /data/mysql

# 启动 MySQL 容器
docker run -d \
  --name mysite-mysql \
  --network mysite-network \
  --restart always \
  -e MYSQL_ROOT_PASSWORD=你的密码 \
  -e MYSQL_DATABASE=mysite \
  -v /data/mysql:/var/lib/mysql \
  -p 3306:3306 \
  mysql:8.4 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci

# 验证运行状态
docker ps | grep mysql
```

#### 1.3.3 部署 Redis
```bash
# 启动 Redis 容器
docker run -d \
  --name mysite-redis \
  --network mysite-network \
  --restart always \
  -p 6379:6379 \
  redis:7-alpine

# 验证运行状态
docker ps | grep redis
```

#### 1.3.4 部署 Artalk（评论系统）
```bash
# 创建数据目录
sudo mkdir -p /data/artalk

# 启动 Artalk 容器
docker run -d \
  --name mysite-artalk \
  --network mysite-network \
  --restart always \
  -v /data/artalk:/data \
  -p 23366:23366 \
  artalk/artalk-go:latest

# 验证运行状态
docker ps | grep artalk
```

### 1.4 应用部署

#### 1.4.1 克隆项目
```bash
# 创建项目目录
mkdir -p ~/project

# 克隆代码
git clone https://github.com/Somehow007/mysite.git ~/project/mysite

# 进入项目目录
cd ~/project/mysite
```

#### 1.4.2 配置环境变量
```bash
# 复制环境变量模板
cp deploy/config/.env.example deploy/config/.env

# 编辑环境变量
nano deploy/config/.env
```

配置内容：
```bash
# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=你的数据库密码

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT配置
JWT_SECRET=mysite-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-2026

# Artalk配置
ARTALK_SERVER=http://localhost:23366
ARTALK_SITE=MySite博客
```

#### 1.4.3 配置 Nginx
```bash
# 复制 Nginx 配置
sudo cp deploy/nginx/mysite-port-8080.conf /etc/nginx/sites-available/mysite

# 创建软链接
sudo ln -sf /etc/nginx/sites-available/mysite /etc/nginx/sites-enabled/mysite

# 删除默认配置（可选）
sudo rm -f /etc/nginx/sites-enabled/default

# 测试配置
sudo nginx -t

# 重载 Nginx
sudo systemctl reload nginx
```

#### 1.4.4 执行部署脚本
```bash
# 添加执行权限
chmod +x deploy/server-deploy.sh

# 执行部署
./deploy/server-deploy.sh
```

### 1.5 验证部署

#### 1.5.1 检查服务状态
```bash
# 检查后端服务
/opt/mysite/start.sh status

# 检查 Docker 容器
docker ps

# 检查 Nginx
sudo systemctl status nginx
```

#### 1.5.2 测试访问
```bash
# 测试后端 API
curl http://localhost:8080/v1/site/info

# 测试前端页面
curl http://localhost:8080
```

---

## 2. 日常更新维护

### 2.1 应用更新流程

#### 标准更新步骤
```bash
# 1. 进入项目目录
cd ~/project/mysite

# 2. 拉取最新代码
git pull origin main

# 3. 执行部署脚本
./deploy/server-deploy.sh
```

#### 部署脚本执行内容
1. 拉取最新代码
2. 构建后端 JAR 包
3. 构建前端静态文件
4. 复制配置文件、环境变量、启动脚本
5. 重启后端服务
6. 部署前端文件
7. 重载 Nginx

### 2.2 手动更新特定组件

#### 仅更新后端
```bash
cd ~/project/mysite

# 拉取代码
git pull origin main

# 构建后端
./mvnw clean package -DskipTests

# 复制 JAR 包
sudo cp target/*.jar /opt/mysite/mysite.jar

# 重启服务
/opt/mysite/start.sh restart
```

#### 仅更新前端
```bash
cd ~/project/mysite

# 拉取代码
git pull origin main

# 构建前端
cd mysite-frontend
npm install
npm run build

# 部署前端
sudo cp -r dist/* /var/www/mysite/

# 重载 Nginx
sudo systemctl reload nginx
```

### 2.3 配置文件更新

#### 更新环境变量
```bash
# 编辑环境变量文件
nano ~/project/mysite/deploy/config/.env

# 重新部署
cd ~/project/mysite
./deploy/server-deploy.sh
```

#### 更新 Nginx 配置
```bash
# 编辑配置
sudo nano /etc/nginx/sites-available/mysite

# 测试配置
sudo nginx -t

# 重载配置
sudo systemctl reload nginx
```

### 2.4 数据库迁移

```bash
# 备份数据库（迁移前必须）
docker exec mysite-mysql mysqldump -u root -p mysite > ~/backup/mysite_$(date +%Y%m%d).sql

# 执行迁移脚本（如有）
# mysql -h localhost -u root -p mysite < migration.sql
```

---

## 3. 常规运维工作

### 3.1 服务器监控

#### 3.1.1 系统资源监控
```bash
# 查看系统资源
htop

# 查看磁盘使用
df -h

# 查看内存使用
free -h

# 查看 CPU 信息
lscpu
```

#### 3.1.2 应用监控
```bash
# 查看后端服务状态
/opt/mysite/start.sh status

# 查看后端日志
/opt/mysite/start.sh logs

# 查看 Nginx 访问日志
sudo tail -f /var/log/nginx/mysite.access.log

# 查看 Nginx 错误日志
sudo tail -f /var/log/nginx/mysite.error.log
```

#### 3.1.3 Docker 容器监控
```bash
# 查看容器状态
docker ps

# 查看容器资源使用
docker stats

# 查看容器日志
docker logs mysite-mysql
docker logs mysite-redis
docker logs mysite-artalk
```

### 3.2 数据备份策略

#### 3.2.1 数据库备份脚本
```bash
#!/bin/bash
# 创建备份目录
BACKUP_DIR=~/backup
mkdir -p $BACKUP_DIR

# 备份 MySQL
docker exec mysite-mysql mysqldump -u root -p你的密码 mysite > $BACKUP_DIR/mysite_$(date +%Y%m%d_%H%M%S).sql

# 保留最近7天的备份
find $BACKUP_DIR -name "mysite_*.sql" -mtime +7 -delete

echo "Backup completed: $(date)"
```

#### 3.2.2 设置自动备份
```bash
# 编辑 crontab
crontab -e

# 添加每天凌晨2点备份
0 2 * * * ~/project/mysite/scripts/backup.sh >> ~/backup/backup.log 2>&1
```

#### 3.2.3 备份恢复
```bash
# 恢复数据库
docker exec -i mysite-mysql mysql -u root -p你的密码 mysite < ~/backup/mysite_20260425.sql
```

### 3.3 日志管理

#### 3.3.1 日志位置
| 服务 | 日志路径 |
|------|----------|
| 后端应用 | /var/log/mysite/console.log |
| Nginx 访问 | /var/log/nginx/mysite.access.log |
| Nginx 错误 | /var/log/nginx/mysite.error.log |
| MySQL | docker logs mysite-mysql |
| Redis | docker logs mysite-redis |

#### 3.3.2 日志轮转配置
```bash
# 编辑 logrotate 配置
sudo nano /etc/logrotate.d/mysite
```

内容：
```
/var/log/mysite/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0640 root root
}
```

### 3.4 性能优化

#### 3.4.1 JVM 调优
编辑 `/opt/mysite/start.sh`：
```bash
JAVA_OPTS="-Xms512m -Xmx768m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"
```

#### 3.4.2 MySQL 优化
```sql
-- 查看当前配置
SHOW VARIABLES LIKE 'max_connections';
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';

-- 优化建议
SET GLOBAL max_connections = 200;
```

#### 3.4.3 Nginx 优化
```nginx
# 在 http 块中添加
worker_processes auto;
worker_connections 1024;
keepalive_timeout 65;
gzip on;
gzip_types text/plain text/css application/json application/javascript;
```

---

## 4. 文档与记录

### 4.1 服务器配置信息

#### 建议记录内容
```
服务器信息：
- IP 地址：
- SSH 端口：
- 操作系统：
- 配置：CPU / 内存 / 硬盘

服务信息：
- 后端端口：8081
- 前端端口：8080
- MySQL 端口：3306
- Redis 端口：6379
- Artalk 端口：23366

账号信息：
- 服务器 SSH：
- MySQL root：
- Redis（如有密码）：
- JWT Secret：
```

### 4.2 维护日志模板

```markdown
## 维护日志 - YYYY-MM-DD

### 操作类型
- [ ] 部署更新
- [ ] 配置变更
- [ ] 故障处理
- [ ] 性能优化
- [ ] 安全更新

### 操作内容
描述具体操作内容...

### 变更文件
- 文件1
- 文件2

### 验证结果
- [ ] 功能测试通过
- [ ] 性能测试通过
- [ ] 安全测试通过

### 备注
其他需要记录的信息...
```

---

## 5. 安全最佳实践

### 5.1 访问控制

#### 5.1.1 SSH 安全配置
```bash
# 编辑 SSH 配置
sudo nano /etc/ssh/sshd_config

# 建议配置
Port 22                          # 或使用非标准端口
PermitRootLogin no               # 禁止 root 登录
PasswordAuthentication no        # 禁用密码登录，使用密钥
PubkeyAuthentication yes         # 启用密钥登录

# 重启 SSH 服务
sudo systemctl restart sshd
```

#### 5.1.2 防火墙配置
```bash
# 使用 ufw 防火墙
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp

# 启用防火墙
sudo ufw enable

# 查看状态
sudo ufw status
```

### 5.2 数据安全

#### 5.2.1 敏感数据加密
- 环境变量文件不提交到 Git
- 数据库密码使用强密码
- JWT Secret 定期更换

#### 5.2.2 HTTPS 配置
```bash
# 安装 Certbot
sudo apt install -y certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

### 5.3 安全更新

```bash
# 定期更新系统
sudo apt update && sudo apt upgrade -y

# 更新 Docker 镜像
docker pull mysql:8.4
docker pull redis:7-alpine
docker pull artalk/artalk-go:latest

# 重启容器使用新镜像
docker-compose up -d
```

---

## 6. 故障排查手册

### 6.1 常见问题

#### 后端服务无法启动
```bash
# 检查日志
/opt/mysite/start.sh logs

# 常见原因：
# 1. 数据库连接失败 - 检查 DB_HOST, DB_PASSWORD
# 2. 端口被占用 - lsof -i:8081
# 3. 内存不足 - free -h
```

#### 数据库连接失败
```bash
# 检查 MySQL 容器状态
docker ps | grep mysql

# 检查 MySQL 日志
docker logs mysite-mysql

# 测试连接
docker exec -it mysite-mysql mysql -u root -p
```

#### CORS 错误
```bash
# 检查 Nginx 配置
sudo nginx -T | grep -A 10 "Access-Control"

# 确保 Nginx 配置包含 CORS 头
# 参考 deploy/nginx/mysite-port-8080.conf
```

#### 前端页面空白
```bash
# 检查前端文件
ls -la /var/www/mysite/

# 检查 Nginx 配置
sudo nginx -t

# 检查浏览器控制台错误
```

### 6.2 应急响应流程

1. **发现问题** - 监控告警或用户反馈
2. **评估影响** - 确定影响范围和严重程度
3. **快速恢复** - 重启服务或回滚版本
4. **根因分析** - 查看日志定位问题
5. **修复验证** - 修复问题并验证
6. **文档记录** - 记录问题和解决方案

### 6.3 服务重启命令

```bash
# 重启后端服务
/opt/mysite/start.sh restart

# 重启 Nginx
sudo systemctl restart nginx

# 重启 MySQL
docker restart mysite-mysql

# 重启 Redis
docker restart mysite-redis

# 重启 Artalk
docker restart mysite-artalk

# 重启所有服务
/opt/mysite/start.sh restart && \
sudo systemctl restart nginx && \
docker restart mysite-mysql mysite-redis mysite-artalk
```

---

## 附录

### A. 常用命令速查

```bash
# 服务管理
/opt/mysite/start.sh {start|stop|restart|status|logs}

# Docker 管理
docker ps                    # 查看运行容器
docker logs <container>      # 查看容器日志
docker restart <container>   # 重启容器
docker exec -it <container> bash  # 进入容器

# Nginx 管理
sudo nginx -t               # 测试配置
sudo systemctl reload nginx # 重载配置
sudo systemctl restart nginx # 重启服务

# 系统监控
htop                        # 系统资源
df -h                       # 磁盘使用
free -h                     # 内存使用
```

### B. 目录结构

```
~/project/mysite/           # 项目目录
/opt/mysite/                # 应用目录
  ├── mysite.jar            # 后端 JAR 包
  ├── application-production.yml  # 生产配置
  ├── .env                  # 环境变量
  └── start.sh              # 启动脚本
/var/www/mysite/            # 前端静态文件
/var/log/mysite/            # 应用日志
/data/                      # Docker 数据目录
  ├── mysql/                # MySQL 数据
  └── artalk/               # Artalk 数据
```

### C. 联系方式

- 项目仓库：https://github.com/Somehow007/mysite
- 问题反馈：GitHub Issues
