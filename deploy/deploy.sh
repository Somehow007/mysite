#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/lib.sh"

PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PKG_DIR="$PROJECT_ROOT/deploy-package"

die() {
    log_error "$1"
    exit 1
}

build_backend() {
    log_step "构建后端"
    cd "$PROJECT_ROOT"
    local mvn_cmd
    mvn_cmd=$(find_maven "$PROJECT_ROOT")
    [ "$mvn_cmd" = "$PROJECT_ROOT/mvnw" ] && chmod +x "$mvn_cmd"
    if ! "$mvn_cmd" clean package -DskipTests; then
        die "后端构建失败"
    fi
    local fat_jar
    fat_jar=$(find_fat_jar "$PROJECT_ROOT") || die "未找到 fat JAR 文件"
    log_success "后端构建成功: $(basename "$fat_jar") ($(du -h "$fat_jar" | awk '{print $1}'))"
}

build_frontend() {
    log_step "构建前端"
    cd "$PROJECT_ROOT/mysite-frontend"
    if [ ! -d "node_modules" ] || [ "package-lock.json" -nt "node_modules" ] 2>/dev/null; then
        log_info "安装前端依赖..."
        npm install || die "前端依赖安装失败"
    fi
    npm run build || die "前端构建失败"
    log_success "前端构建成功 ($(find dist -type f | wc -l) 个文件)"
}

create_package() {
    log_step "打包部署文件"
    cd "$PROJECT_ROOT"
    rm -rf "$PKG_DIR"
    mkdir -p "$PKG_DIR"

    local fat_jar
    fat_jar=$(find_fat_jar "$PROJECT_ROOT") || die "未找到 fat JAR 文件"
    cp "$fat_jar" "$PKG_DIR/mysite.jar"

    mkdir -p "$PKG_DIR/frontend"
    cp -r mysite-frontend/dist/* "$PKG_DIR/frontend/"

    cp deploy/scripts/start.sh "$PKG_DIR/"
    cp deploy/config/application-production.yml "$PKG_DIR/"
    cp deploy/config/.env.example "$PKG_DIR/"
    cp deploy/nginx/mysite.conf "$PKG_DIR/"

    tar -czf "$PROJECT_ROOT/mysite-deploy.tar.gz" -C "$PKG_DIR" .
    local pkg_size
    pkg_size=$(du -h "$PROJECT_ROOT/mysite-deploy.tar.gz" | awk '{print $1}')
    log_success "部署包创建成功: mysite-deploy.tar.gz ($pkg_size)"
}

main() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}    MySite 本地构建打包脚本${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo ""

    build_backend
    build_frontend
    create_package

    echo ""
    log_success "构建完成！"
    log_info "部署包: $PROJECT_ROOT/mysite-deploy.tar.gz"
    log_info ""
    log_info "下一步:"
    log_info "  1. scp mysite-deploy.tar.gz user@server:/tmp/"
    log_info "  2. mkdir -p /opt/mysite && tar -xzf /tmp/mysite-deploy.tar.gz -C /opt/mysite"
    log_info "  3. cp /opt/mysite/.env.example /opt/mysite/.env && vim /opt/mysite/.env"
    log_info "  4. cd /opt/mysite && chmod +x start.sh && ./start.sh start"
}

main "$@"
