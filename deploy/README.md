# MySite 快速部署指南

## 🚀 快速开始

### 一、本地构建

```bash
# 进入项目目录
cd /Users/somehow/dev/code/Java/mysite

# 运行一键部署脚本
chmod +x deploy/deploy.sh
./deploy/deploy.sh
```

构建完成后会生成 `mysite-deploy.tar.gz` 部署包。

### 二、服务器准备

#### 方案二（推荐）：使用云数据库

```bash
# 1. 购买云服务
# - 阿里云 RDS MySQL (基础版 1核1G): ¥50-80/月
# - 阿里云 Redis (社区版 256MB): ¥30-50/月

# 2. 在云数据库中执行初始化SQL
mysql -h rds-xxx.mysql.rds.aliyuncs.com -u mysite -p mysite < init.sql
```

#### 方案三：本地数据库

```bash
# 安装MySQL
sudo apt install -y mysql-server

# 创建数据库
sudo mysql -u root
```

```sql
CREATE DATABASE mysite DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'mysite'@'localhost' IDENTIFIED BY 'StrongPassword123!';
GRANT ALL PRIVILEGES ON mysite.* TO 'mysite'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

```bash
# 导入数据
mysql -u mysite -p mysite < init.sql

# 安装Redis
sudo apt install -y redis-server
```

### 三、部署应用

```bash
# 1. 上传部署包
scp mysite-deploy.tar.gz user@your-server:/tmp/

# 2. 解压到服务器
ssh user@your-server
mkdir -p /opt/mysite
tar -xzf /tmp/mysite-deploy.tar.gz -C /opt/mysite

# 3. 配置环境变量
cd /opt/mysite
cp .env.template .env
vim .env
```

编辑 `.env` 文件：

```bash
# 数据库配置
DB_HOST=rds-xxx.mysql.rds.aliyuncs.com  # 或 localhost
DB_PORT=3306
DB_USER=mysite
DB_PASSWORD=your-password

# Redis配置
REDIS_HOST=r-xxx.redis.rds.aliyuncs.com  # 或 localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT配置
JWT_SECRET=your-production-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-2026

# 域名配置
DOMAIN=your-domain.com
```

```bash
# 4. 安装Java
sudo apt install -y openjdk-17-jdk

# 5. 启动应用
chmod +x start.sh
./start.sh start

# 6. 查看状态
./start.sh status

# 7. 查看日志
./start.sh logs
```

### 四、配置Nginx

```bash
# 1. 安装Nginx
sudo apt install -y nginx

# 2. 复制配置文件
sudo cp mysite.conf /etc/nginx/sites-available/mysite
sudo ln -sf /etc/nginx/sites-available/mysite /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# 3. 修改域名
sudo sed -i 's/your-domain.com/your-actual-domain.com/g' /etc/nginx/sites-available/mysite

# 4. 测试配置
sudo nginx -t

# 5. 重载Nginx
sudo systemctl reload nginx
```

### 五、配置SSL证书

```bash
# 1. 安装Certbot
sudo apt install -y certbot python3-certbot-nginx

# 2. 获取证书
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 3. 自动续期
sudo systemctl enable certbot.timer
```

### 六、部署Artalk评论系统

```bash
# 1. 安装Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 2. 运行Artalk
docker run -d \
    --name artalk \
    --restart unless-stopped \
    -p 23366:23366 \
    -v /opt/artalk:/data \
    -e TZ=Asia/Shanghai \
    artalk/artalk-go:latest
```

### 七、验证部署

```bash
# 检查服务状态
./start.sh status
sudo systemctl status nginx
docker ps | grep artalk

# 测试API
curl http://localhost:8081/api/article/search?page=1&size=10

# 检查端口
sudo netstat -tlnp | grep -E '80|443|8081|23366'
```

## 📊 监控设置

```bash
# 1. 复制监控脚本
sudo cp monitor.sh /opt/mysite/
chmod +x /opt/mysite/monitor.sh

# 2. 添加到crontab
crontab -e
```

添加以下内容：

```cron
# 每5分钟执行一次监控
*/5 * * * * /opt/mysite/monitor.sh
```

## 🔄 日常维护

### 查看日志

```bash
# 应用日志
tail -f /var/log/mysite/application.log

# Nginx日志
tail -f /var/log/nginx/mysite.access.log

# 监控日志
tail -f /var/log/mysite/monitor.log
```

### 重启服务

```bash
# 重启应用
./start.sh restart

# 重启Nginx
sudo systemctl restart nginx

# 重启Artalk
docker restart artalk
```

### 备份数据库

```bash
# 创建备份脚本
cat > /opt/mysite/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/var/backups/mysite"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

mysqldump -h $DB_HOST -u $DB_USER -p$DB_PASSWORD mysite | gzip > $BACKUP_DIR/mysite_$DATE.sql.gz
find $BACKUP_DIR -name "mysite_*.sql.gz" -mtime +7 -delete
EOF

chmod +x /opt/mysite/backup.sh

# 添加到crontab（每天凌晨2点备份）
echo "0 2 * * * /opt/mysite/backup.sh" | crontab -
```

## 🔧 故障排查

### 应用无法启动

```bash
# 查看详细日志
tail -100 /var/log/mysite/console.log

# 检查端口占用
sudo lsof -i:8081

# 检查Java进程
ps aux | grep java
```

### 数据库连接失败

```bash
# 测试数据库连接
mysql -h $DB_HOST -u $DB_USER -p

# 检查防火墙
sudo ufw status
```

### 内存不足

```bash
# 查看内存使用
free -h

# 查看进程内存
ps aux --sort=-%mem | head

# 重启应用释放内存
./start.sh restart
```

## 📝 配置文件位置

| 文件 | 位置 |
|------|------|
| 应用配置 | /opt/mysite/application-production.yml |
| 环境变量 | /opt/mysite/.env |
| Nginx配置 | /etc/nginx/sites-available/mysite |
| 应用日志 | /var/log/mysite/ |
| Nginx日志 | /var/log/nginx/ |

## 🆘 获取帮助

详细部署文档：[docs/服务器部署方案.md](../docs/服务器部署方案.md)
