#!/bin/bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    MySite 一键部署脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo

check_command() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${RED}✗ $1 未安装${NC}"
        return 1
    else
        echo -e "${GREEN}✓ $1 已安装${NC}"
        return 0
    fi
}

check_environment() {
    echo -e "${YELLOW}[1/6] 检查环境...${NC}"
    
    local missing=0
    
    check_command java || missing=1
    check_command node || missing=1
    check_command npm || missing=1
    check_command mvn || missing=1
    
    if [ $missing -eq 1 ]; then
        echo -e "${RED}环境检查失败，请先安装缺失的依赖${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}环境检查通过${NC}"
    echo
}

build_backend() {
    echo -e "${YELLOW}[2/6] 构建后端...${NC}"
    
    cd "$PROJECT_ROOT"
    
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        ./mvnw clean package -DskipTests
    else
        mvn clean package -DskipTests
    fi
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}后端构建成功${NC}"
    else
        echo -e "${RED}后端构建失败${NC}"
        exit 1
    fi
    echo
}

build_frontend() {
    echo -e "${YELLOW}[3/6] 构建前端...${NC}"
    
    cd "$PROJECT_ROOT/mysite-frontend"
    
    if [ ! -d "node_modules" ]; then
        echo "安装前端依赖..."
        npm install
    fi
    
    npm run build
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}前端构建成功${NC}"
    else
        echo -e "${RED}前端构建失败${NC}"
        exit 1
    fi
    echo
}

create_deployment_package() {
    echo -e "${YELLOW}[4/6] 创建部署包...${NC}"
    
    cd "$PROJECT_ROOT"
    
    DEPLOY_DIR="$PROJECT_ROOT/deploy/dist"
    mkdir -p "$DEPLOY_DIR"
    
    cp target/*.jar "$DEPLOY_DIR/mysite.jar"
    
    mkdir -p "$DEPLOY_DIR/frontend"
    cp -r mysite-frontend/dist/* "$DEPLOY_DIR/frontend/"
    
    cp docker/init/init.sql "$DEPLOY_DIR/"
    cp docker/artalk.yml "$DEPLOY_DIR/"
    
    cp deploy/config/application-production.yml "$DEPLOY_DIR/"
    cp deploy/scripts/start.sh "$DEPLOY_DIR/"
    cp deploy/scripts/stop.sh "$DEPLOY_DIR/"
    cp deploy/nginx/mysite.conf "$DEPLOY_DIR/"
    
    tar -czf "$PROJECT_ROOT/mysite-deploy.tar.gz" -C "$DEPLOY_DIR" .
    
    echo -e "${GREEN}部署包创建成功: $PROJECT_ROOT/mysite-deploy.tar.gz${NC}"
    echo
}

generate_env_template() {
    echo -e "${YELLOW}[5/6] 生成环境配置模板...${NC}"
    
    cat > "$PROJECT_ROOT/deploy/dist/.env.template" << 'EOF'
# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=mysite
DB_PASSWORD=your-db-password

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT配置
JWT_SECRET=your-production-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-2026

# Artalk配置
ARTALK_SERVER=http://localhost:23366
ARTALK_SITE=MySite博客

# 域名配置
DOMAIN=your-domain.com
EOF
    
    echo -e "${GREEN}环境配置模板已生成${NC}"
    echo
}

print_summary() {
    echo -e "${YELLOW}[6/6] 部署摘要${NC}"
    echo
    echo -e "${BLUE}========================================${NC}"
    echo -e "${GREEN}构建完成！${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo
    echo -e "部署包位置: ${GREEN}$PROJECT_ROOT/mysite-deploy.tar.gz${NC}"
    echo
    echo -e "${YELLOW}下一步操作:${NC}"
    echo "1. 上传部署包到服务器:"
    echo "   scp mysite-deploy.tar.gz user@your-server:/tmp/"
    echo
    echo "2. 在服务器上解压:"
    echo "   mkdir -p /opt/mysite"
    echo "   tar -xzf /tmp/mysite-deploy.tar.gz -C /opt/mysite"
    echo
    echo "3. 配置环境变量:"
    echo "   cp /opt/mysite/.env.template /opt/mysite/.env"
    echo "   vim /opt/mysite/.env"
    echo
    echo "4. 启动服务:"
    echo "   cd /opt/mysite"
    echo "   chmod +x start.sh"
    echo "   ./start.sh"
    echo
    echo -e "${BLUE}详细部署文档: docs/服务器部署方案.md${NC}"
    echo
}

main() {
    check_environment
    build_backend
    build_frontend
    create_deployment_package
    generate_env_template
    print_summary
}

main "$@"
