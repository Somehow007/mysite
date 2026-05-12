#!/bin/bash

set -euo pipefail

GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
BLUE="\033[0;34m"
CYAN="\033[0;36m"
NC="\033[0m"

PROJECT_DIR="$HOME/project/mysite"
NGINX_WEB_ROOT="/var/www/mysite"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
APP_START_SCRIPT="$APP_DIR/start.sh"
PID_FILE="/var/run/mysite.pid"
LOG_DIR="/var/log/mysite"
DEPLOY_LOG_DIR="$LOG_DIR/deploy"
CHECKPOINT_FILE="$APP_DIR/.deploy-checkpoint"
BACKUP_DIR="$APP_DIR/backups"
MAX_BACKUPS=5

DEPLOY_LOG=""
DEPLOY_START_TIME=""
CURRENT_STEP=""
PREV_COMMIT=""
ROLLBACK_NEEDED=false
ROLLBACK_BACKEND_JAR=""
ROLLBACK_FRONTEND_DIR=""
ROLLBACK_NGINX_CONF=""

log() {
    local level="$1"
    shift
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
    echo -e "$msg" | tee -a "$DEPLOY_LOG"
}

log_info()    { log "INFO"  "${GREEN}$*${NC}"; }
log_warn()    { log "WARN"  "${YELLOW}$*${NC}"; }
log_error()   { log "ERROR" "${RED}$*${NC}"; }
log_step()    { log "STEP"  "${CYAN}$*${NC}"; }
log_success() { log "OK"    "${GREEN}$*${NC}"; }

print_error_guide() {
    local error_type="$1"
    local detail="$2"
    echo ""
    log_error "╔══════════════════════════════════════════════════════════════╗"
    log_error "║  部署错误: $error_type"
    log_error "║  详情: $detail"
    log_error "╠══════════════════════════════════════════════════════════════╣"
    case "$error_type" in
        NETWORK)
            log_error "║  可能原因:"
            log_error "║    1. 服务器无法访问外网或 Git 仓库"
            log_error "║    2. DNS 解析失败"
            log_error "║    3. 代理配置不正确"
            log_error "║  排查步骤:"
            log_error "║    $ ping github.com"
            log_error "║    $ curl -I https://github.com"
            log_error "║    $ git config --global http.proxy"
            log_error "║    $ cat /etc/resolv.conf"
            ;;
        DEPENDENCY)
            log_error "║  可能原因:"
            log_error "║    1. Java/Node/npm/Maven 版本不满足要求"
            log_error "║    2. 可执行文件不在 PATH 中"
            log_error "║    3. 版本兼容性问题"
            log_error "║  排查步骤:"
            log_error "║    $ java -version"
            log_error "║    $ node -v && npm -v"
            log_error "║    $ mvn -v 或 ./mvnw -v"
            log_error "║    $ which java node npm mvn"
            ;;
        BUILD_BACKEND)
            log_error "║  可能原因:"
            log_error "║    1. 编译错误: 代码语法或依赖问题"
            log_error "║    2. 依赖下载失败: Maven 仓库不可达"
            log_error "║    3. 磁盘空间不足"
            log_error "║  排查步骤:"
            log_error "║    $ cd $PROJECT_DIR && mvn clean package -DskipTests"
            log_error "║    $ df -h"
            log_error "║    $ tail -100 $DEPLOY_LOG"
            ;;
        BUILD_FRONTEND)
            log_error "║  可能原因:"
            log_error "║    1. npm 依赖安装失败或版本冲突"
            log_error "║    2. node_modules 与 package.json 不同步"
            log_error "║    3. Node.js 版本不兼容"
            log_error "║  排查步骤:"
            log_error "║    $ cd $PROJECT_DIR/mysite-frontend && rm -rf node_modules"
            log_error "║    $ npm install && npm run build"
            log_error "║    $ node -v (检查是否满足 package.json 的 engines 要求)"
            ;;
        DEPLOY_BACKEND)
            log_error "║  可能原因:"
            log_error "║    1. JAR 文件不存在或损坏"
            log_error "║    2. 旧进程未完全停止 (端口 8081 被占用)"
            log_error "║    3. .env 文件配置错误或缺失"
            log_error "║    4. 数据库/Redis 连接失败"
            log_error "║  排查步骤:"
            log_error "║    $ ls -lh $APP_DIR/mysite.jar"
            log_error "║    $ sudo lsof -i :8081"
            log_error "║    $ cat $APP_DIR/.env"
            log_error "║    $ tail -100 /var/log/mysite/console.log"
            log_error "║    $ curl -s http://localhost:8081/actuator/health"
            ;;
        DEPLOY_FRONTEND)
            log_error "║  可能原因:"
            log_error "║    1. dist 目录为空或不存在"
            log_error "║    2. Nginx web 根目录权限不足"
            log_error "║    3. 磁盘空间不足"
            log_error "║  排查步骤:"
            log_error "║    $ ls -la $PROJECT_DIR/mysite-frontend/dist/"
            log_error "║    $ df -h"
            log_error "║    $ ls -la $NGINX_WEB_ROOT/"
            ;;
        NGINX)
            log_error "║  可能原因:"
            log_error "║    1. Nginx 配置语法错误"
            log_error "║    2. SSL 证书路径不正确"
            log_error "║    3. upstream 后端服务未启动"
            log_error "║  排查步骤:"
            log_error "║    $ sudo nginx -t"
            log_error "║    $ sudo systemctl status nginx"
            log_error "║    $ curl -s http://localhost:8081/actuator/health"
            ;;
        HEALTH_CHECK)
            log_error "║  可能原因:"
            log_error "║    1. 应用启动时间过长 (内存不足)"
            log_error "║    2. 数据库或 Redis 连接失败"
            log_error "║    3. 端口 8081 被其他进程占用"
            log_error "║  排查步骤:"
            log_error "║    $ tail -100 /var/log/mysite/application.log"
            log_error "║    $ tail -100 /var/log/mysite/console.log"
            log_error "║    $ curl -s http://localhost:8081/actuator/health"
            log_error "║    $ free -h"
            ;;
    esac
    log_error "╚══════════════════════════════════════════════════════════════╝"
}

die() {
    local error_type="$1"
    local detail="$2"
    print_error_guide "$error_type" "$detail"
    if [ "$ROLLBACK_NEEDED" = true ]; then
        log_warn "检测到部署中途失败，正在执行自动回滚..."
        do_rollback
    fi
    log_error "部署失败！详细日志: $DEPLOY_LOG"
    exit 1
}

init_deploy_log() {
    sudo mkdir -p "$DEPLOY_LOG_DIR"
    DEPLOY_START_TIME=$(date '+%Y%m%d_%H%M%S')
    DEPLOY_LOG="$DEPLOY_LOG_DIR/deploy_${DEPLOY_START_TIME}.log"
    sudo touch "$DEPLOY_LOG"
    sudo chown "$(whoami)" "$DEPLOY_LOG"
    log_info "部署日志: $DEPLOY_LOG"
}

save_checkpoint() {
    local step="$1"
    echo "$step" | sudo tee "$CHECKPOINT_FILE" > /dev/null
    log_info "检查点已保存: $step"
}

read_checkpoint() {
    if [ -f "$CHECKPOINT_FILE" ]; then
        cat "$CHECKPOINT_FILE" 2>/dev/null || echo ""
    else
        echo ""
    fi
}

clear_checkpoint() {
    sudo rm -f "$CHECKPOINT_FILE"
}

create_backup() {
    local timestamp="$DEPLOY_START_TIME"
    sudo mkdir -p "$BACKUP_DIR"

    if [ -f "$APP_JAR" ]; then
        ROLLBACK_BACKEND_JAR="$BACKUP_DIR/mysite_${timestamp}.jar"
        sudo cp "$APP_JAR" "$ROLLBACK_BACKEND_JAR"
        log_info "后端 JAR 已备份: $ROLLBACK_BACKEND_JAR"
    fi

    if [ -d "$NGINX_WEB_ROOT" ] && [ "$(ls -A "$NGINX_WEB_ROOT" 2>/dev/null)" ]; then
        ROLLBACK_FRONTEND_DIR="$BACKUP_DIR/frontend_${timestamp}"
        sudo mkdir -p "$ROLLBACK_FRONTEND_DIR"
        sudo cp -r "$NGINX_WEB_ROOT"/* "$ROLLBACK_FRONTEND_DIR/" 2>/dev/null || true
        log_info "前端文件已备份: $ROLLBACK_FRONTEND_DIR"
    fi

    local nginx_conf="/etc/nginx/sites-available/mysite"
    if [ -f "$nginx_conf" ]; then
        ROLLBACK_NGINX_CONF="$BACKUP_DIR/mysite_nginx_${timestamp}.conf"
        sudo cp "$nginx_conf" "$ROLLBACK_NGINX_CONF"
        log_info "Nginx 配置已备份: $ROLLBACK_NGINX_CONF"
    fi

    local count=$(ls -1 "$BACKUP_DIR" 2>/dev/null | wc -l)
    if [ "$count" -gt "$((MAX_BACKUPS * 4))" ]; then
        log_info "清理旧备份 (保留最近 $MAX_BACKUPS 组)..."
        ls -1t "$BACKUP_DIR"/mysite_*.jar 2>/dev/null | tail -n +"$((MAX_BACKUPS + 1))" | xargs -r sudo rm -f
        ls -1t "$BACKUP_DIR"/frontend_* 2>/dev/null | tail -n +"$((MAX_BACKUPS + 1))" | xargs -r sudo rm -rf
        ls -1t "$BACKUP_DIR"/mysite_nginx_*.conf 2>/dev/null | tail -n +"$((MAX_BACKUPS + 1))" | xargs -r sudo rm -f
    fi

    ROLLBACK_NEEDED=true
}

do_rollback() {
    log_warn "========== 开始回滚 =========="

    if [ -n "$ROLLBACK_BACKEND_JAR" ] && [ -f "$ROLLBACK_BACKEND_JAR" ]; then
        log_info "回滚后端 JAR..."
        sudo cp "$ROLLBACK_BACKEND_JAR" "$APP_JAR"
        if [ -f "$APP_START_SCRIPT" ]; then
            sudo "$APP_START_SCRIPT" restart || true
            sleep 5
        fi
    fi

    if [ -n "$ROLLBACK_FRONTEND_DIR" ] && [ -d "$ROLLBACK_FRONTEND_DIR" ]; then
        log_info "回滚前端文件..."
        sudo rm -rf "$NGINX_WEB_ROOT"/* 2>/dev/null || true
        sudo cp -r "$ROLLBACK_FRONTEND_DIR"/* "$NGINX_WEB_ROOT/" 2>/dev/null || true
    fi

    if [ -n "$ROLLBACK_NGINX_CONF" ] && [ -f "$ROLLBACK_NGINX_CONF" ]; then
        log_info "回滚 Nginx 配置..."
        sudo cp "$ROLLBACK_NGINX_CONF" /etc/nginx/sites-available/mysite
        sudo nginx -t && sudo systemctl reload nginx || true
    fi

    sudo systemctl reload nginx 2>/dev/null || true

    log_warn "========== 回滚完成 =========="
    log_warn "系统已恢复到部署前状态，请检查服务是否正常"
    ROLLBACK_NEEDED=false
}

step_pre_check() {
    log_step "═══ [1/7] 部署前检查 ═══"
    local failed=0

    log_info "检查 Git 项目..."
    if [ ! -d "$PROJECT_DIR/.git" ]; then
        die "DEPENDENCY" "$PROJECT_DIR 不是 Git 项目或不存在"
    fi
    log_success "Git 项目目录正常"

    log_info "检查必需命令..."
    local commands=("java" "node" "npm" "git")
    for cmd in "${commands[@]}"; do
        if command -v "$cmd" &> /dev/null; then
            local ver
            ver=$("$cmd" --version 2>&1 | head -1)
            log_success "$cmd: $ver"
        else
            log_error "$cmd 未安装"
            failed=1
        fi
    done

    if [ -f "$PROJECT_DIR/mvnw" ]; then
        log_success "Maven Wrapper 可用"
    elif command -v mvn &> /dev/null; then
        log_success "mvn: $(mvn -v 2>&1 | head -1)"
    else
        log_error "Maven 未安装且无 Maven Wrapper"
        failed=1
    fi

    [ "$failed" -eq 1 ] && die "DEPENDENCY" "必需命令缺失，请安装后重试"

    log_info "检查网络连通性..."
    if curl -s --connect-timeout 5 -o /dev/null https://github.com 2>/dev/null; then
        log_success "外网连通正常"
    else
        log_warn "无法直接访问 github.com，尝试检查代理..."
        local http_proxy=$(git config --global http.proxy 2>/dev/null || true)
        local https_proxy=$(git config --global https.proxy 2>/dev/null || true)
        if [ -n "$http_proxy" ] || [ -n "$https_proxy" ]; then
            log_info "检测到 Git 代理: http=$http_proxy https=$https_proxy"
        else
            log_warn "未检测到代理配置，git pull 可能失败"
        fi
    fi

    log_info "检查磁盘空间..."
    local disk_avail=$(df -h "$PROJECT_DIR" | awk 'NR==2{print $4}')
    local disk_usage=$(df -h "$PROJECT_DIR" | awk 'NR==2{print $5}' | sed 's/%//')
    if [ "$disk_usage" -gt 90 ]; then
        die "DEPENDENCY" "磁盘使用率 ${disk_usage}%，空间不足 (剩余: $disk_avail)"
    fi
    log_success "磁盘空间充足 (使用: ${disk_usage}%, 剩余: ${disk_avail})"

    log_info "检查内存..."
    local mem_total=$(free -m | awk '/Mem/{print $2}')
    local mem_avail=$(free -m | awk '/Mem/{print $7}')
    if [ "$mem_avail" -lt 256 ]; then
        log_warn "可用内存较低: ${mem_avail}MB / ${mem_total}MB"
    else
        log_success "内存充足 (可用: ${mem_avail}MB / 总计: ${mem_total}MB)"
    fi

    log_info "检查服务依赖..."
    if command -v mysql &> /dev/null; then
        if mysqladmin ping -h "${DB_HOST:-localhost}" -P "${DB_PORT:-3306}" -u "${DB_USER:-root}" --password="${DB_PASSWORD:-}" &>/dev/null 2>&1; then
            log_success "MySQL 连接正常"
        else
            log_warn "MySQL 连接测试失败 (可能密码未配置，应用启动时会重试)"
        fi
    else
        log_warn "mysql 客户端未安装，跳过数据库连接测试"
    fi

    if command -v redis-cli &> /dev/null; then
        if redis-cli -h "${REDIS_HOST:-localhost}" -p "${REDIS_PORT:-6379}" ping &>/dev/null 2>&1; then
            log_success "Redis 连接正常"
        else
            log_warn "Redis 连接测试失败 (可能需要密码，应用启动时会重试)"
        fi
    else
        log_warn "redis-cli 未安装，跳过 Redis 连接测试"
    fi

    log_info "检查 Nginx..."
    if command -v nginx &> /dev/null; then
        log_success "Nginx: $(nginx -v 2>&1)"
        if ! sudo nginx -t 2>/dev/null; then
            log_warn "Nginx 当前配置有误，部署后会更新配置"
        fi
    else
        log_warn "Nginx 未安装，前端部署步骤将跳过"
    fi

    log_info "检查当前运行状态..."
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            log_info "后端服务运行中 (PID: $pid)"
        else
            log_warn "PID 文件存在但进程未运行 (stale PID)"
        fi
    else
        log_info "后端服务未运行"
    fi

    log_success "部署前检查完成"
    save_checkpoint "pre_check"
}

step_git_pull() {
    log_step "═══ [2/7] 拉取最新代码 ═══"
    cd "$PROJECT_DIR"

    PREV_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
    log_info "当前版本: $(git log --oneline -1 2>/dev/null || echo 'unknown')"

    local has_changes=false
    if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
        log_warn "检测到本地有未提交的修改:"
        git status --short 2>/dev/null | head -10 | while read line; do log_warn "  $line"; done
        has_changes=true
    fi

    log_info "正在拉取代码..."
    if ! git pull origin main 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "NETWORK" "git pull 失败"
    fi

    local new_commit=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
    if [ "$PREV_COMMIT" = "$new_commit" ]; then
        log_info "代码无变更 (已是最新)"
    else
        log_success "代码已更新:"
        log_info "  旧: $PREV_COMMIT"
        log_info "  新: $new_commit"
        git log --oneline "$PREV_COMMIT..$new_commit" 2>/dev/null | while read line; do log_info "  $line"; done
    fi

    log_info "当前版本: $(git log --oneline -1)"
    save_checkpoint "git_pull"
}

step_build_backend() {
    log_step "═══ [3/7] 构建后端 ═══"
    cd "$PROJECT_DIR"

    log_info "开始 Maven 构建 (输出同时写入日志)..."
    local build_ok=false
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        if ./mvnw clean package -DskipTests 2>&1 | tee -a "$DEPLOY_LOG"; then
            build_ok=true
        fi
    else
        if mvn clean package -DskipTests 2>&1 | tee -a "$DEPLOY_LOG"; then
            build_ok=true
        fi
    fi

    if [ "$build_ok" = false ]; then
        die "BUILD_BACKEND" "Maven 构建失败，请查看上方日志"
    fi

    local jar_count=$(ls "$PROJECT_DIR/target"/*.jar 2>/dev/null | wc -l)
    if [ "$jar_count" -eq 0 ]; then
        die "BUILD_BACKEND" "构建成功但未找到 JAR 文件"
    fi

    log_info "构建产物:"
    ls -lh "$PROJECT_DIR/target"/*.jar 2>/dev/null | awk '{print "  " $NF ": " $5 " (" $6 " " $7 " " $8 ")"}' | tee -a "$DEPLOY_LOG"

    local fat_jar=""
    for jar in "$PROJECT_DIR/target"/*.jar; do
        if [[ ! "$jar" =~ \.original$ ]]; then
            local size=$(stat -c%s "$jar" 2>/dev/null || stat -f%z "$jar" 2>/dev/null || echo 0)
            if [ "$size" -gt 10000000 ]; then
                fat_jar="$jar"
                break
            fi
        fi
    done

    if [ -z "$fat_jar" ]; then
        die "BUILD_BACKEND" "未找到 fat JAR (含依赖的可执行 JAR)，可能构建不完整"
    fi

    log_success "后端构建成功: $(basename "$fat_jar") ($(du -h "$fat_jar" | awk '{print $1}'))"
    save_checkpoint "build_backend"
}

step_build_frontend() {
    log_step "═══ [4/7] 构建前端 ═══"
    cd "$PROJECT_DIR/mysite-frontend"

    local pkg_json="$PROJECT_DIR/mysite-frontend/package.json"
    local pkg_lock="$PROJECT_DIR/mysite-frontend/package-lock.json"
    local need_install=false

    if [ ! -d "node_modules" ]; then
        need_install=true
        log_info "node_modules 不存在，需要安装依赖"
    elif [ "$pkg_lock" -nt "node_modules" ] 2>/dev/null; then
        need_install=true
        log_info "package-lock.json 比 node_modules 更新，需要重新安装"
    elif [ "$pkg_json" -nt "node_modules" ] 2>/dev/null; then
        need_install=true
        log_info "package.json 比 node_modules 更新，需要重新安装"
    fi

    if [ "$need_install" = true ]; then
        log_info "安装前端依赖..."
        if ! npm install 2>&1 | tee -a "$DEPLOY_LOG"; then
            die "BUILD_FRONTEND" "npm install 失败"
        fi
        log_success "前端依赖安装完成"
    else
        log_info "前端依赖已是最新，跳过安装"
    fi

    log_info "开始前端构建..."
    if ! npm run build 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "BUILD_FRONTEND" "npm run build 失败"
    fi

    if [ ! -d "dist" ] || [ -z "$(ls -A dist 2>/dev/null)" ]; then
        die "BUILD_FRONTEND" "构建成功但 dist 目录为空"
    fi

    local file_count=$(find dist -type f | wc -l)
    log_info "前端构建产物: $file_count 个文件"
    ls -lh dist/index.html 2>/dev/null | awk '{print "  index.html: " $5 " (" $6 " " $7 " " $8 ")"}' | tee -a "$DEPLOY_LOG"

    log_success "前端构建成功"
    save_checkpoint "build_frontend"
}

step_deploy_backend() {
    log_step "═══ [5/7] 部署后端 ═══"

    sudo mkdir -p "$APP_DIR"

    local fat_jar=""
    for jar in "$PROJECT_DIR/target"/*.jar; do
        if [[ ! "$jar" =~ \.original$ ]]; then
            local size=$(stat -c%s "$jar" 2>/dev/null || stat -f%z "$jar" 2>/dev/null || echo 0)
            if [ "$size" -gt 10000000 ]; then
                fat_jar="$jar"
                break
            fi
        fi
    done

    if [ -z "$fat_jar" ]; then
        die "DEPLOY_BACKEND" "未找到 fat JAR 文件"
    fi

    log_info "部署 JAR: $(basename "$fat_jar") ($(du -h "$fat_jar" | awk '{print $1}'))"
    sudo cp "$fat_jar" "$APP_JAR"
    log_success "JAR 文件已复制到 $APP_JAR"

    if [ -f "$PROJECT_DIR/deploy/config/application-production.yml" ]; then
        sudo cp "$PROJECT_DIR/deploy/config/application-production.yml" "$APP_DIR/"
        log_success "Spring 配置已更新"
    fi

    if [ -f "$PROJECT_DIR/deploy/config/.env" ]; then
        sudo cp "$PROJECT_DIR/deploy/config/.env" "$APP_DIR/"
        log_success ".env 文件已更新"
    else
        if [ ! -f "$APP_DIR/.env" ]; then
            log_warn ".env 文件缺失！请手动创建: $APP_DIR/.env"
            log_warn "参考模板: deploy/config/.env.example"
        fi
    fi

    if [ -f "$PROJECT_DIR/deploy/scripts/start.sh" ]; then
        sudo cp "$PROJECT_DIR/deploy/scripts/start.sh" "$APP_START_SCRIPT"
        sudo chmod +x "$APP_START_SCRIPT"
        log_success "启动脚本已更新"
    fi

    log_info "停止旧的后端服务..."
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            log_info "正在停止进程 $pid..."
            sudo kill "$pid" 2>/dev/null || true
            for i in $(seq 1 15); do
                if ! ps -p "$pid" > /dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            if ps -p "$pid" > /dev/null 2>&1; then
                log_warn "进程未响应 SIGTERM，发送 SIGKILL..."
                sudo kill -9 "$pid" 2>/dev/null || true
                sleep 2
            fi
            log_info "旧进程已停止"
        else
            log_info "PID 文件中的进程不存在，清理 PID 文件"
        fi
        sudo rm -f "$PID_FILE"
    fi

    local port_pid=$(sudo lsof -t -i :8081 2>/dev/null || true)
    if [ -n "$port_pid" ]; then
        log_warn "端口 8081 仍被占用 (PID: $port_pid)，强制释放..."
        echo "$port_pid" | xargs sudo kill -9 2>/dev/null || true
        sleep 2
    fi

    log_info "启动新的后端服务..."
    if [ -f "$APP_START_SCRIPT" ]; then
        sudo "$APP_START_SCRIPT" start
    else
        die "DEPLOY_BACKEND" "启动脚本不存在: $APP_START_SCRIPT"
    fi

    log_info "等待服务启动..."
    local max_wait=60
    local waited=0
    local health_ok=false
    while [ "$waited" -lt "$max_wait" ]; do
        sleep 2
        waited=$((waited + 2))

        if [ -f "$PID_FILE" ]; then
            local pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
            if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
                if curl -s -o /dev/null -w "%{http_code}" "http://localhost:8081/actuator/health" 2>/dev/null | grep -q "200"; then
                    health_ok=true
                    break
                fi
            else
                die "DEPLOY_BACKEND" "进程已退出，请检查日志: /var/log/mysite/console.log"
            fi
        fi

        printf "\r  等待中... %ds/%ds" "$waited" "$max_wait"
    done
    echo ""

    if [ "$health_ok" = true ]; then
        local pid=$(cat "$PID_FILE" 2>/dev/null || echo "?")
        log_success "后端服务已启动 (PID: $pid, 启动耗时: ${waited}s)"
        log_info "JAR 时间: $(ls -lh "$APP_JAR" | awk '{print $6, $7, $8}')"
    else
        local pid=$(cat "$PID_FILE" 2>/dev/null || echo "?")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            log_warn "后端进程运行中 (PID: $pid) 但健康检查未在 ${max_wait}s 内通过"
            log_warn "应用可能仍在启动中，或健康检查端点需要认证"
            log_warn "请手动验证: curl -s http://localhost:8081/actuator/health"
            log_warn "查看日志: tail -100 /var/log/mysite/application.log"
            ROLLBACK_NEEDED=false
        else
            die "DEPLOY_BACKEND" "进程已退出，请检查日志: /var/log/mysite/console.log"
        fi
    fi

    save_checkpoint "deploy_backend"
}

step_deploy_frontend() {
    log_step "═══ [6/7] 部署前端 ═══"

    if [ ! -d "$PROJECT_DIR/mysite-frontend/dist" ]; then
        die "DEPLOY_FRONTEND" "前端构建产物 dist 目录不存在"
    fi

    sudo mkdir -p "$NGINX_WEB_ROOT"

    log_info "清理旧的前端文件..."
    sudo rm -rf "$NGINX_WEB_ROOT"/* "$NGINX_WEB_ROOT"/.[!.]* 2>/dev/null || true

    log_info "复制新的前端文件..."
    if ! sudo cp -r "$PROJECT_DIR/mysite-frontend/dist/"* "$NGINX_WEB_ROOT/"; then
        die "DEPLOY_FRONTEND" "前端文件复制失败"
    fi

    local file_count=$(find "$NGINX_WEB_ROOT" -type f | wc -l)
    log_success "前端部署成功 ($file_count 个文件)"
    log_info "index.html: $(ls -lh "$NGINX_WEB_ROOT/index.html" 2>/dev/null | awk '{print $5, $6, $7, $8}')"

    save_checkpoint "deploy_frontend"
}

step_reload_nginx() {
    log_step "═══ [7/7] 重载 Nginx ═══"

    if ! command -v nginx &> /dev/null; then
        log_warn "Nginx 未安装，跳过此步骤"
        return 0
    fi

    local project_nginx_conf="$PROJECT_DIR/deploy/nginx/mysite.conf"
    local sites_available="/etc/nginx/sites-available/mysite"
    local sites_enabled="/etc/nginx/sites-enabled/mysite"

    if [ -f "$project_nginx_conf" ]; then
        if [ -f "$sites_available" ]; then
            if ! diff -q "$project_nginx_conf" "$sites_available" > /dev/null 2>&1; then
                log_info "检测到 Nginx 配置有变更，正在更新..."
                sudo cp "$project_nginx_conf" "$sites_available"
                log_success "Nginx 配置已更新"
            else
                log_info "Nginx 配置无变更"
            fi
        else
            log_info "首次部署 Nginx 配置..."
            sudo cp "$project_nginx_conf" "$sites_available"
            sudo ln -sf "$sites_available" "$sites_enabled" 2>/dev/null || true
            log_success "Nginx 配置已安装"
        fi
    fi

    if ! sudo nginx -t 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "NGINX" "Nginx 配置测试失败"
    fi
    log_success "Nginx 配置测试通过"

    if ! sudo systemctl reload nginx; then
        log_warn "Nginx reload 失败，尝试 restart..."
        if sudo systemctl restart nginx; then
            log_success "Nginx 重启成功"
        else
            die "NGINX" "Nginx 重启失败"
        fi
    else
        log_success "Nginx 重载成功"
    fi

    save_checkpoint "reload_nginx"
}

print_summary() {
    local duration=$(($(date +%s) - $(date -d "${DEPLOY_START_TIME:0:8} ${DEPLOY_START_TIME:8:2}:${DEPLOY_START_TIME:10:2}:${DEPLOY_START_TIME:12:2}" +%s 2>/dev/null || echo 0)))
    if [ "$duration" -eq 0 ]; then
        duration="N/A"
    else
        duration="${duration}s"
    fi

    echo ""
    log_info "══════════════════════════════════════════════════════════"
    log_success "                  部署完成！"
    log_info "══════════════════════════════════════════════════════════"
    echo ""
    log_info "部署耗时: $duration"
    log_info "代码版本: $(cd "$PROJECT_DIR" && git log --oneline -1 2>/dev/null || echo 'unknown')"
    log_info "后端 JAR:  $(ls -lh "$APP_JAR" 2>/dev/null | awk '{print $5, $6, $7, $8}')"
    log_info "前端文件:  $(find "$NGINX_WEB_ROOT" -type f 2>/dev/null | wc -l) 个"
    echo ""
    log_info "服务地址:"
    log_info "  后端: http://localhost:8081"
    log_info "  前端: http://localhost"
    log_info "  健康检查: http://localhost:8081/actuator/health"
    echo ""
    log_info "常用命令:"
    log_info "  查看日志:   $APP_START_SCRIPT logs"
    log_info "  重启服务:   $APP_START_SCRIPT restart"
    log_info "  服务状态:   $APP_START_SCRIPT status"
    log_info "  部署日志:   $DEPLOY_LOG"
    echo ""
    log_info "如需回滚，执行:"
    log_info "  ls -lt $BACKUP_DIR/"
    log_info "  sudo cp $BACKUP_DIR/mysite_YYYYMMDD_HHMMSS.jar $APP_JAR"
    log_info "  sudo cp -r $BACKUP_DIR/frontend_YYYYMMDD_HHMMSS/* $NGINX_WEB_ROOT/"
    log_info "  sudo $APP_START_SCRIPT restart"
    echo ""
}

resume_deploy() {
    local checkpoint=$(read_checkpoint)
    if [ -z "$checkpoint" ]; then
        return 0
    fi

    log_warn "检测到未完成的部署 (检查点: $checkpoint)"
    log_warn "上次部署可能在以下步骤之后中断"

    local steps=("pre_check" "git_pull" "build_backend" "build_frontend" "deploy_backend" "deploy_frontend" "reload_nginx")
    local step_names=("部署前检查" "拉取代码" "构建后端" "构建前端" "部署后端" "部署前端" "重载Nginx")
    local found=false

    for i in "${!steps[@]}"; do
        if [ "${steps[$i]}" = "$checkpoint" ]; then
            log_info "将从步骤 [$((i + 2))/7] ${step_names[$i]} 之后继续"
            found=true
            break
        fi
    done

    if [ "$found" = false ]; then
        log_warn "无法识别检查点，将从头开始部署"
        return 0
    fi

    echo ""
    log_info "选择操作:"
    log_info "  1) 从断点继续 (跳过已完成的步骤)"
    log_info "  2) 从头开始完整部署"
    log_info "  3) 取消部署"
    echo ""
    read -p "请选择 [1/2/3]: " choice

    case "$choice" in
        1)
            log_info "从断点继续部署..."
            SKIP_STEPS=()
            for i in "${!steps[@]}"; do
                if [ "${steps[$i]}" = "$checkpoint" ]; then
                    break
                fi
                SKIP_STEPS+=("${steps[$i]}")
            done
            ;;
        2)
            log_info "从头开始完整部署..."
            clear_checkpoint
            SKIP_STEPS=()
            ;;
        3)
            log_info "部署已取消"
            exit 0
            ;;
        *)
            log_info "无效选择，从头开始部署..."
            clear_checkpoint
            SKIP_STEPS=()
            ;;
    esac
}

SKIP_STEPS=()

should_skip() {
    local step="$1"
    for s in "${SKIP_STEPS[@]}"; do
        if [ "$s" = "$step" ]; then
            return 0
        fi
    done
    return 1
}

main() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}           MySite 一键部署更新脚本 v2.0${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo ""

    init_deploy_log
    resume_deploy

    create_backup

    if should_skip "pre_check"; then
        log_info "[1/7] 部署前检查 - 跳过 (断点续传)"
    else
        step_pre_check
    fi

    if should_skip "git_pull"; then
        log_info "[2/7] 拉取最新代码 - 跳过 (断点续传)"
    else
        step_git_pull
    fi

    if should_skip "build_backend"; then
        log_info "[3/7] 构建后端 - 跳过 (断点续传)"
    else
        step_build_backend
    fi

    if should_skip "build_frontend"; then
        log_info "[4/7] 构建前端 - 跳过 (断点续传)"
    else
        step_build_frontend
    fi

    if should_skip "deploy_backend"; then
        log_info "[5/7] 部署后端 - 跳过 (断点续传)"
    else
        step_deploy_backend
    fi

    if should_skip "deploy_frontend"; then
        log_info "[6/7] 部署前端 - 跳过 (断点续传)"
    else
        step_deploy_frontend
    fi

    if should_skip "reload_nginx"; then
        log_info "[7/7] 重载 Nginx - 跳过 (断点续传)"
    else
        step_reload_nginx
    fi

    clear_checkpoint
    ROLLBACK_NEEDED=false
    print_summary
}

main "$@"
