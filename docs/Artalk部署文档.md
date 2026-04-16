# Artalk 评论系统部署文档

## 一、系统概述

Artalk 是一款轻量级的评论系统，本项目已将其集成到 MySite 博客系统中。

### 架构说明

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Vue 前端      │────▶│   Nginx 代理    │────▶│  Artalk 后端    │
│  (Artalk SDK)   │     │   (HTTPS)       │     │  (Docker)       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                        │
                                                        ▼
                                                ┌─────────────────┐
                                                │   SQLite 数据库 │
                                                └─────────────────┘
```

## 二、环境要求

- Docker 20.10+
- Docker Compose 2.0+
- Nginx 1.18+（可选，用于反向代理）
- 域名及 SSL 证书（生产环境推荐）

## 三、快速部署

### 3.1 开发环境部署

```bash
# 进入项目目录
cd /home/g15/code/java/mysite

# 启动 Artalk 服务
docker-compose -f docker-compose.artalk.yml up -d

# 查看服务状态
docker-compose -f docker-compose.artalk.yml ps

# 查看日志
docker-compose -f docker-compose.artalk.yml logs -f
```

### 3.2 创建管理员账户

```bash
# 进入容器创建管理员
docker exec -it artalk artalk admin

# 按提示输入：
# 用户名: admin
# 邮箱: somehow007@163.com
# 密码: 000000
```

### 3.3 访问服务

- 评论管理后台: http://localhost:23366
- API 接口: http://localhost:23366/api/

## 四、生产环境部署

### 4.1 配置文件修改

#### 4.1.1 修改 Artalk 配置文件

编辑 `artalk.yml`：

```yaml
# 修改应用密钥（必须修改！）
app_key: "your-unique-secret-key-here"

# 配置信任域名
security:
  trusted_domains:
    - your-domain.com
    - www.your-domain.com

# 配置站点信息
site:
  default: "MySite博客"
  sites:
    - name: "MySite博客"
      urls:
        - https://your-domain.com
```

#### 4.1.2 配置邮件通知（可选）

编辑 `artalk.yml` 中的邮件配置：

```yaml
notify:
  email:
    enabled: true
    sender:
      name: "MySite 评论通知"
      email: noreply@your-domain.com
    smtp:
      host: smtp.163.com
      port: 465
      username: your-email@163.com
      password: your-smtp-password
    notify:
      admin: true
      user: true
```

#### 4.1.3 修改前端配置

编辑 `mysite-frontend/.env.production`：

```env
VITE_ARTALK_SERVER=https://artalk.your-domain.com
VITE_ARTALK_SITE=MySite博客
```

### 4.2 Nginx 反向代理配置

#### 4.2.1 复制配置文件

```bash
# 复制配置到 Nginx 配置目录
sudo cp nginx/artalk.conf /etc/nginx/conf.d/

# 修改配置中的域名和 SSL 证书路径
sudo vim /etc/nginx/conf.d/artalk.conf
```

#### 4.2.2 配置说明

修改以下配置项：

```nginx
# 修改域名
server_name artalk.your-domain.com;

# 修改 SSL 证书路径
ssl_certificate /etc/nginx/ssl/your-domain.com.pem;
ssl_certificate_key /etc/nginx/ssl/your-domain.com.key;
```

#### 4.2.3 测试并重载 Nginx

```bash
# 测试配置
sudo nginx -t

# 重载配置
sudo nginx -s reload
```

### 4.3 启动生产服务

```bash
# 重启 Artalk 服务
docker-compose -f docker-compose.artalk.yml down
docker-compose -f docker-compose.artalk.yml up -d
```

## 五、安全配置

### 5.1 必须修改的配置

1. **应用密钥**: 修改 `artalk.yml` 中的 `app_key`
2. **管理员密码**: 登录后台修改默认密码
3. **信任域名**: 配置 `security.trusted_domains`

### 5.2 防火墙配置

```bash
# 如果使用 Nginx 代理，建议关闭外部对 23366 端口的访问
# 仅允许本地访问
sudo ufw deny 23366
sudo ufw allow 80
sudo ufw allow 443
```

### 5.3 定期备份

```bash
# 备份数据目录
tar -czvf artalk-backup-$(date +%Y%m%d).tar.gz artalk-data/
```

## 六、前端集成说明

### 6.1 组件使用

```vue
<template>
  <ArtalkComment
    :page-key="`/post/${postId}`"
    :page-title="postTitle"
  />
</template>

<script setup>
import ArtalkComment from '@/components/ArtalkComment.vue'
</script>
```

### 6.2 配置项说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| VITE_ARTALK_SERVER | Artalk 服务地址 | http://localhost:23366 |
| VITE_ARTALK_SITE | 站点名称 | MySite博客 |

## 七、API 接口

### 7.1 获取评论列表

```
GET /api/v2/comments?page_key=/post/123
```

### 7.2 创建评论

```
POST /api/v2/comments
Content-Type: application/json

{
  "page_key": "/post/123",
  "name": "用户名",
  "email": "user@example.com",
  "content": "评论内容"
}
```

### 7.3 获取页面统计

```
GET /api/v2/stats?type=pv&page_key=/post/123
```

## 八、常见问题

### Q1: 评论无法提交？

检查以下配置：
1. Artalk 服务是否正常运行
2. 前端配置的服务地址是否正确
3. 跨域配置是否正确

### Q2: 无法登录管理后台？

1. 确认管理员账户已创建
2. 检查浏览器控制台是否有错误
3. 尝试清除浏览器缓存

### Q3: 邮件通知不工作？

1. 检查 SMTP 配置是否正确
2. 确认邮箱密码是授权码而非登录密码
3. 查看 Artalk 日志排查问题

## 九、维护命令

```bash
# 查看服务状态
docker-compose -f docker-compose.artalk.yml ps

# 查看日志
docker-compose -f docker-compose.artalk.yml logs -f

# 重启服务
docker-compose -f docker-compose.artalk.yml restart

# 停止服务
docker-compose -f docker-compose.artalk.yml down

# 更新镜像
docker-compose -f docker-compose.artalk.yml pull
docker-compose -f docker-compose.artalk.yml up -d

# 进入容器
docker exec -it artalk sh
```

## 十、文件清单

| 文件 | 说明 |
|------|------|
| `docker-compose.artalk.yml` | Docker Compose 配置 |
| `artalk.yml` | Artalk 服务配置 |
| `nginx/artalk.conf` | Nginx 反向代理配置 |
| `mysite-frontend/.env.development` | 前端开发环境配置 |
| `mysite-frontend/.env.production` | 前端生产环境配置 |
| `src/main/java/.../config/ArtalkConfig.java` | 后端配置类 |
| `mysite-frontend/src/components/ArtalkComment.vue` | 前端评论组件 |
