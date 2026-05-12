#!/bin/bash

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_LOG="$PROJECT_ROOT/deploy/dist/build.log"

log_info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "${CYAN}[STEP]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }

die() {
    log_error "$1"
    exit 1
}

check_command() {
    if command -v "$1" &> /dev/null; then
        local ver
        ver=$("$1" --version 2>&1 | head -1)
        log_success "$1: $ver"
        return 0
    else
        log_error "$1 未安装"
        return 1
    fi
}

check_environment() {
    log_step "[1/6] 检查环境..."

    local missing=0
    check_command java || missing=1
    check_command node || missing=1
    check_command npm  || missing=1

    if [ -f "$PROJECT_ROOT/mvnw" ]; then
        log_success "Maven Wrapper 可用"
    else
        check_command mvn || missing=1
    fi

    if [ "$missing" -eq 1 ]; then
        die "环境检查失败，请先安装缺失的依赖"
    fi

    local disk_usage=$(df -h "$PROJECT_ROOT" | awk 'NR==2{print $5}' | sed 's/%//')
    if [ "$disk_usage" -gt 90 ]; then
        die "磁盘空间不足 (使用率: ${disk_usage}%)"
    fi
    log_success "磁盘空间充足 (使用: ${disk_usage}%)"

    log_success "环境检查通过"
    echo
}

build_backend() {
    log_step "[2/6] 构建后端..."

    cd "$PROJECT_ROOT"
    mkdir -p "$(dirname "$BUILD_LOG")"

    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        if ! ./mvnw clean package -DskipTests 2>&1 | tee "$BUILD_LOG"; then
            die "后端构建失败，查看日志: $BUILD_LOG"
        fi
    else
        if ! mvn clean package -DskipTests 2>&1 | tee "$BUILD_LOG"; then
            die "后端构建失败，查看日志: $BUILD_LOG"
        fi
    fi

    local fat_jar=""
    for jar in target/*.jar; do
        if [[ ! "$jar" =~ \.original$ ]]; then
            local size=$(stat -c%s "$jar" 2>/dev/null || stat -f%z "$jar" 2>/dev/null || echo 0)
            if [ "$size" -gt 10000000 ]; then
                fat_jar="$jar"
                break
            fi
        fi
    done

    if [ -z "$fat_jar" ]; then
        die "构建成功但未找到 fat JAR (含依赖的可执行 JAR)"
    fi

    log_success "后端构建成功: $(basename "$fat_jar") ($(du -h "$fat_jar" | awk '{print $1}'))"
    echo
}

build_frontend() {
    log_step "[3/6] 构建前端..."

    cd "$PROJECT_ROOT/mysite-frontend"

    local pkg_lock="package-lock.json"
    local need_install=false

    if [ ! -d "node_modules" ]; then
        need_install=true
    elif [ "$pkg_lock" -nt "node_modules" ] 2>/dev/null; then
        need_install=true
        log_info "package-lock.json 比 node_modules 更新，需要重新安装"
    fi

    if [ "$need_install" = true ]; then
        log_info "安装前端依赖..."
        if ! npm install 2>&1 | tee -a "$BUILD_LOG"; then
            die "前端依赖安装失败"
        fi
    fi

    if ! npm run build 2>&1 | tee -a "$BUILD_LOG"; then
        die "前端构建失败"
    fi

    if [ ! -d "dist" ] || [ -z "$(ls -A dist 2>/dev/null)" ]; then
        die "前端构建成功但 dist 目录为空"
    fi

    local file_count=$(find dist -type f | wc -l)
    log_success "前端构建成功 ($file_count 个文件)"
    echo
}

create_deployment_package() {
    log_step "[4/6] 创建部署包..."

    cd "$PROJECT_ROOT"

    local DEPLOY_DIR="$PROJECT_ROOT/deploy/dist"
    rm -rf "$DEPLOY_DIR"
    mkdir -p "$DEPLOY_DIR"

    cp target/*.jar "$DEPLOY_DIR/mysite.jar" 2>/dev/null || die "未找到 JAR 文件"

    mkdir -p "$DEPLOY_DIR/frontend"
    cp -r mysite-frontend/dist/* "$DEPLOY_DIR/frontend/"

    if [ -f "docker/init/init.sql" ]; then
        cp docker/init/init.sql "$DEPLOY_DIR/"
    fi
    if [ -f "docker/artalk.yml" ]; then
        cp docker/artalk.yml "$DEPLOY_DIR/"
    fi

    cp deploy/config/application-production.yml "$DEPLOY_DIR/"
    cp deploy/scripts/start.sh "$DEPLOY_DIR/"
    cp deploy/nginx/mysite.conf "$DEPLOY_DIR/"

    tar -czf "$PROJECT_ROOT/mysite-deploy.tar.gz" -C "$DEPLOY_DIR" .

    local pkg_size=$(du -h "$PROJECT_ROOT/mysite-deploy.tar.gz" | awk '{print $1}')
    log_success "部署包创建成功: mysite-deploy.tar.gz ($pkg_size)"
    echo
}

generate_env_template() {
    log_step "[5/6] 生成环境配置模板..."

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

    log_success "环境配置模板已生成"
    echo
}

print_summary() {
    log_step "[6/6] 部署摘要"
    echo
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}构建完成！${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
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
    echo -e "${YELLOW}或使用一键更新部署 (服务器上已有项目时):${NC}"
    echo "   cd ~/project/mysite"
    echo "   ./deploy/server-deploy.sh"
    echo
}

main() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}    MySite 本地构建打包脚本 v2.0${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo

    check_environment
    build_backend
    build_frontend
    create_deployment_package
    generate_env_template
    print_summary
}

main "$@"
