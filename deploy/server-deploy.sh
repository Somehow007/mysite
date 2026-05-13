#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/lib.sh"

PROJECT_DIR="$HOME/project/mysite"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
APP_START_SCRIPT="$APP_DIR/start.sh"
NGINX_WEB_ROOT="/var/www/mysite"
DEPLOY_LOG_DIR="/var/log/mysite/deploy"

DEPLOY_LOG=""
DEPLOY_START_TIME=""

init_log() {
    sudo mkdir -p "$DEPLOY_LOG_DIR"
    DEPLOY_START_TIME=$(date '+%Y%m%d_%H%M%S')
    DEPLOY_LOG="$DEPLOY_LOG_DIR/deploy_${DEPLOY_START_TIME}.log"
    sudo touch "$DEPLOY_LOG"
    sudo chown "$(whoami)" "$DEPLOY_LOG"
}

log() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
    echo -e "$msg" | tee -a "$DEPLOY_LOG"
}

log_info()    { log "${GREEN}[INFO]${NC} $*"; }
log_warn()    { log "${YELLOW}[WARN]${NC} $*"; }
log_error()   { log "${RED}[ERROR]${NC} $*"; }
log_step()    { log "${BLUE}[STEP]${NC} $*"; }
log_success() { log "${GREEN}[OK]${NC} $*"; }

die() {
    log_error "$1"
    log_error "排查建议:"
    log_error "  查看部署日志: $DEPLOY_LOG"
    log_error "  查看应用日志: tail -100 /var/log/mysite/console.log"
    log_error "  检查服务状态: $APP_START_SCRIPT status"
    exit 1
}

step_git_pull() {
    log_step "拉取最新代码"
    cd "$PROJECT_DIR"
    if ! git pull origin main 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "git pull 失败，请检查网络连接"
    fi
    log_success "代码已更新: $(git log --oneline -1)"
}

step_build_backend() {
    log_step "构建后端"
    cd "$PROJECT_DIR"
    local mvn_cmd
    mvn_cmd=$(find_maven "$PROJECT_DIR")
    [ "$mvn_cmd" = "$PROJECT_DIR/mvnw" ] && chmod +x "$mvn_cmd"
    if ! "$mvn_cmd" clean package -DskipTests 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "后端构建失败，请查看日志: $DEPLOY_LOG"
    fi
    local fat_jar
    fat_jar=$(find_fat_jar "$PROJECT_DIR") || die "未找到 fat JAR 文件"
    log_success "后端构建成功: $(basename "$fat_jar") ($(du -h "$fat_jar" | awk '{print $1}'))"
}

step_build_frontend() {
    log_step "构建前端"
    cd "$PROJECT_DIR/mysite-frontend"
    if [ ! -d "node_modules" ] || [ "package-lock.json" -nt "node_modules" ] 2>/dev/null; then
        log_info "安装前端依赖..."
        npm install 2>&1 | tee -a "$DEPLOY_LOG" || die "前端依赖安装失败"
    fi
    npm run build 2>&1 | tee -a "$DEPLOY_LOG" || die "前端构建失败"
    log_success "前端构建成功 ($(find dist -type f | wc -l) 个文件)"
}

step_stop_service() {
    log_step "停止旧服务"
    if [ -f "$APP_START_SCRIPT" ]; then
        sudo "$APP_START_SCRIPT" stop 2>&1 | tee -a "$DEPLOY_LOG" || true
    fi
    local port_pid
    port_pid=$(sudo lsof -t -i :8081 2>/dev/null || true)
    if [ -n "$port_pid" ]; then
        log_warn "端口 8081 仍被占用 (PID: $port_pid)，强制释放"
        echo "$port_pid" | xargs sudo kill -9 2>/dev/null || true
        sleep 2
    fi
    log_success "旧服务已停止"
}

step_deploy_backend() {
    log_step "部署后端 JAR"
    sudo mkdir -p "$APP_DIR"
    local fat_jar
    fat_jar=$(find_fat_jar "$PROJECT_DIR") || die "未找到 fat JAR 文件"
    sudo cp "$fat_jar" "$APP_JAR"
    log_success "JAR 已复制到 $APP_JAR"
    sudo cp "$PROJECT_DIR/deploy/scripts/start.sh" "$APP_START_SCRIPT"
    sudo chmod +x "$APP_START_SCRIPT"
    log_success "start.sh 已更新"
    sudo cp "$PROJECT_DIR/deploy/config/application-production.yml" "$APP_DIR/"
    log_success "application-production.yml 已更新"
    if [ -f "$PROJECT_DIR/deploy/config/.env" ]; then
        sudo cp "$PROJECT_DIR/deploy/config/.env" "$APP_DIR/"
        log_success ".env 已更新"
    elif [ ! -f "$APP_DIR/.env" ]; then
        log_warn ".env 文件缺失，请手动创建: $APP_DIR/.env"
    fi
}

step_start_service() {
    log_step "启动新服务"
    sudo "$APP_START_SCRIPT" start 2>&1 | tee -a "$DEPLOY_LOG" || die "服务启动失败"
    sleep 2
    if [ -f /var/run/mysite.pid ]; then
        local pid
        pid=$(cat /var/run/mysite.pid 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            log_success "服务启动成功 (PID: $pid)"
        else
            die "服务启动后进程不存在，请查看日志: tail -100 /var/log/mysite/console.log"
        fi
    else
        die "服务启动后未找到 PID 文件"
    fi
}

step_deploy_frontend() {
    log_step "部署前端文件"
    sudo mkdir -p "$NGINX_WEB_ROOT"
    sudo rm -rf "$NGINX_WEB_ROOT"/* "$NGINX_WEB_ROOT"/.[!.]* 2>/dev/null || true
    sudo cp -r "$PROJECT_DIR/mysite-frontend/dist/"* "$NGINX_WEB_ROOT/"
    log_success "前端部署成功 ($(find "$NGINX_WEB_ROOT" -type f | wc -l) 个文件)"
}

step_sync_nginx() {
    log_step "同步 Nginx 配置"
    local project_conf="$PROJECT_DIR/deploy/nginx/mysite.conf"
    local sites_available="/etc/nginx/sites-available/mysite.conf"
    local sites_enabled="/etc/nginx/sites-enabled/mysite.conf"
    if [ -f "$project_conf" ]; then
        if [ -f "$sites_available" ]; then
            if ! diff -q "$project_conf" "$sites_available" > /dev/null 2>&1; then
                sudo cp "$project_conf" "$sites_available"
                log_success "Nginx 配置已更新"
            else
                log_info "Nginx 配置无变更"
            fi
        else
            sudo cp "$project_conf" "$sites_available"
            sudo ln -sf "$sites_available" "$sites_enabled" 2>/dev/null || true
            log_success "Nginx 配置已安装"
        fi
    fi
    sudo nginx -t 2>&1 | tee -a "$DEPLOY_LOG" || die "Nginx 配置测试失败"
    sudo systemctl reload nginx || sudo systemctl restart nginx || die "Nginx 重载失败"
    log_success "Nginx 已重载"
}

main() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}    MySite 服务器部署脚本${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo ""

    init_log

    step_git_pull
    step_build_backend
    step_build_frontend
    step_stop_service
    step_deploy_backend
    step_start_service
    step_deploy_frontend
    step_sync_nginx

    echo ""
    log_success "部署完成！"
    log_info "代码版本: $(cd "$PROJECT_DIR" && git log --oneline -1)"
    log_info "后端 JAR:  $(ls -lh "$APP_JAR" | awk '{print $5}')"
    log_info "前端文件:  $(find "$NGINX_WEB_ROOT" -type f | wc -l) 个"
    log_info "部署日志:  $DEPLOY_LOG"
}

main "$@"
