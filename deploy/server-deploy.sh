#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/lib.sh"

PROJECT_DIR="$HOME/project/mysite"
JOURNAL_DIR="$HOME/project/study-journey"
JOURNAL_REPO="git@github.com:Somehow007/study-journel.git"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
APP_START_SCRIPT="$APP_DIR/start.sh"
NGINX_WEB_ROOT="/var/www/mysite"
JOURNAL_WEB_ROOT="/var/www/journal"
UPLOAD_DIR="/data/mysite/uploads"
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

load_node() {
    export NVM_DIR="$HOME/.nvm"
    if [ -s "$NVM_DIR/nvm.sh" ]; then
        set +e
        . "$NVM_DIR/nvm.sh"
        nvm use 22 >/dev/null
        set -e
    else
        log_warn "nvm 未找到，使用系统默认 Node: $(node -v 2>/dev/null || echo '未安装')"
    fi
    command -v node >/dev/null || die "未找到 node，无法构建前端"
    command -v npm >/dev/null || die "未找到 npm，无法构建前端"
    log_info "当前 Node 版本: $(node -v)  npm: $(npm -v)"
}

wait_http() {
    local url="$1"
    local expect="${2:-200}"
    local tries="${3:-30}"
    local delay="${4:-3}"
    local i code
    for i in $(seq 1 "$tries"); do
        code=$(curl -sf -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
        if [ "$code" = "$expect" ]; then
            return 0
        fi
        sleep "$delay"
    done
    log_warn "等待 $url 返回 $expect 超时（最后 HTTP $code）"
    return 1
}

step_git_pull() {
    log_step "拉取博客仓库"
    cd "$PROJECT_DIR"
    if ! git pull origin main 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "mysite git pull 失败，请检查网络连接"
    fi
    log_success "mysite: $(git log --oneline -1)"
}

step_git_pull_journal() {
    log_step "拉取手帐仓库"
    if [ ! -d "$JOURNAL_DIR/.git" ]; then
        log_info "首次克隆 $JOURNAL_REPO → $JOURNAL_DIR"
        mkdir -p "$(dirname "$JOURNAL_DIR")"
        if ! git clone --branch main "$JOURNAL_REPO" "$JOURNAL_DIR" 2>&1 | tee -a "$DEPLOY_LOG"; then
            die "克隆手帐仓库失败（服务器需能 SSH 访问 GitHub）"
        fi
    else
        cd "$JOURNAL_DIR"
        if ! git fetch origin 2>&1 | tee -a "$DEPLOY_LOG"; then
            die "手帐 git fetch 失败"
        fi
        git checkout main 2>&1 | tee -a "$DEPLOY_LOG" || die "手帐 checkout main 失败"
        if ! git pull --ff-only origin main 2>&1 | tee -a "$DEPLOY_LOG"; then
            die "手帐 git pull 失败"
        fi
    fi
    cd "$JOURNAL_DIR"
    log_success "study-journey: $(git log --oneline -1)"
}

step_build_backend() {
    log_step "构建后端"
    cd "$PROJECT_DIR"
    local mvn_cmd
    mvn_cmd=$(find_maven "$PROJECT_DIR")
    [ "$mvn_cmd" = "$PROJECT_DIR/mvnw" ] && chmod +x "$mvn_cmd"
    # 用 -Dmaven.test.skip=true 而非 -DskipTests：仓库里存在签名漂移的 RAG 测试源码
    # （干净构建下编译不过，见 docs/study-journey-integration.md §10），
    # -DskipTests 只跳过执行、仍会编译测试，故必须跳过测试编译
    if ! "$mvn_cmd" clean package -Dmaven.test.skip=true 2>&1 | tee -a "$DEPLOY_LOG"; then
        die "后端构建失败，请查看日志: $DEPLOY_LOG"
    fi
    local fat_jar
    fat_jar=$(find_fat_jar "$PROJECT_DIR") || die "未找到 fat JAR 文件"
    log_success "后端构建成功: $(basename "$fat_jar") ($(du -h "$fat_jar" | awk '{print $1}'))"
}

step_build_frontend() {
    log_step "构建博客前端"
    load_node
    cd "$PROJECT_DIR/mysite-frontend"
    if [ ! -d "node_modules" ] || [ "package-lock.json" -nt "node_modules" ] 2>/dev/null; then
        log_info "安装博客前端依赖..."
        npm install 2>&1 | tee -a "$DEPLOY_LOG" || die "博客前端依赖安装失败"
    fi
    npm run build 2>&1 | tee -a "$DEPLOY_LOG" || die "博客前端构建失败"
    log_success "博客前端构建成功 ($(find dist -type f | wc -l) 个文件)"
}

step_build_journal() {
    log_step "构建手帐前端"
    load_node
    cd "$JOURNAL_DIR"
    if [ ! -d "node_modules" ] || [ "package-lock.json" -nt "node_modules" ] 2>/dev/null; then
        log_info "安装手帐依赖..."
        npm install 2>&1 | tee -a "$DEPLOY_LOG" || die "手帐依赖安装失败"
    fi
    npm run build 2>&1 | tee -a "$DEPLOY_LOG" || die "手帐构建失败"
    [ -f "$JOURNAL_DIR/dist/index.html" ] || die "手帐 dist/index.html 不存在"
    log_success "手帐构建成功 ($(find dist -type f | wc -l) 个文件)"
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
    ensure_crypto_key
}

ensure_crypto_key() {
    local env_file="$APP_DIR/.env"
    if [ ! -f "$env_file" ]; then
        log_warn "跳过 MYSITE_CRYPTO_KEY：没有 $env_file"
        return 0
    fi
    if sudo grep -qE '^MYSITE_CRYPTO_KEY=.+' "$env_file"; then
        log_info "MYSITE_CRYPTO_KEY 已存在"
        return 0
    fi
    local key
    key=$(openssl rand -base64 32)
    printf '\n# LLM API Key 入库加密主密钥（部署脚本首次自动写入）\nMYSITE_CRYPTO_KEY=%s\n' "$key" \
        | sudo tee -a "$env_file" >/dev/null
    log_success "已写入 MYSITE_CRYPTO_KEY（请自行备份 /opt/mysite/.env，丢失则密文无法解密）"
}

step_start_service() {
    log_step "启动新服务"
    sudo "$APP_START_SCRIPT" start 2>&1 | tee -a "$DEPLOY_LOG" || die "服务启动失败"
    sleep 2
    if [ -f /var/run/mysite.pid ]; then
        local pid
        pid=$(cat /var/run/mysite.pid 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            log_success "服务进程已起来 (PID: $pid)"
        else
            die "服务启动后进程不存在，请查看日志: tail -100 /var/log/mysite/console.log"
        fi
    else
        die "服务启动后未找到 PID 文件"
    fi
    log_info "等待后端就绪（最多约 90s）"
    if wait_http "http://localhost:8081/v1/site/config" 200 30 3; then
        log_success "后端已响应 /v1/site/config"
    else
        die "后端启动超时，请查看: tail -100 /var/log/mysite/console.log"
    fi
}

step_deploy_frontend() {
    log_step "部署博客前端"
    sudo mkdir -p "$NGINX_WEB_ROOT"
    sudo rm -rf "$NGINX_WEB_ROOT"/* "$NGINX_WEB_ROOT"/.[!.]* 2>/dev/null || true
    sudo cp -r "$PROJECT_DIR/mysite-frontend/dist/"* "$NGINX_WEB_ROOT/"
    log_success "博客前端部署成功 ($(find "$NGINX_WEB_ROOT" -type f | wc -l) 个文件)"
}

step_deploy_journal() {
    log_step "部署手帐前端"
    [ -f "$JOURNAL_DIR/dist/index.html" ] || die "手帐 dist 不存在，请先构建"
    sudo mkdir -p "$JOURNAL_WEB_ROOT"
    sudo rsync -a --delete "$JOURNAL_DIR/dist/" "$JOURNAL_WEB_ROOT/"
    sudo chown -R www-data:www-data "$JOURNAL_WEB_ROOT"
    log_success "手帐部署成功 ($(find "$JOURNAL_WEB_ROOT" -type f | wc -l) 个文件 → $JOURNAL_WEB_ROOT)"
}

step_setup_upload_dir() {
    log_step "设置上传目录"

    log_info "创建上传目录: $UPLOAD_DIR/images"
    sudo mkdir -p "$UPLOAD_DIR/images"

    log_info "修复已有上传文件的所有者（root -> www-data）"
    sudo chown -R www-data:www-data "$UPLOAD_DIR"

    log_info "设置目录权限: 755"
    sudo chmod -R 755 "$UPLOAD_DIR"

    log_info "验证目录结构:"
    ls -la "$UPLOAD_DIR/" 2>&1 | tee -a "$DEPLOY_LOG"
    if [ -d "$UPLOAD_DIR/images" ]; then
        ls -la "$UPLOAD_DIR/images/" 2>&1 | tee -a "$DEPLOY_LOG"
    fi

    log_success "上传目录设置完成: $UPLOAD_DIR"
}

step_sync_nginx() {
    log_step "同步 Nginx 配置"

    log_info "清理冲突配置（默认站点 + 旧 mysite 配置）"
    sudo rm -f /etc/nginx/sites-enabled/default
    sudo rm -f /etc/nginx/sites-enabled/mysite
    sudo rm -f /etc/nginx/sites-available/mysite
    log_success "已清理冲突配置"

    log_info "同步 mysite.conf"
    local project_conf="$PROJECT_DIR/deploy/nginx/mysite.conf"
    local sites_available="/etc/nginx/sites-available/mysite.conf"
    local sites_enabled="/etc/nginx/sites-enabled/mysite.conf"

    if [ ! -f "$project_conf" ]; then
        die "未找到项目 Nginx 配置文件: $project_conf"
    fi

    sudo cp "$project_conf" "$sites_available"
    sudo ln -sf "$sites_available" "$sites_enabled"
    log_success "Nginx 配置已同步到 $sites_available"

    log_info "验证 /uploads/ location 配置:"
    if grep -q "location.*uploads" "$sites_available"; then
        grep -A 5 "location.*uploads" "$sites_available" | tee -a "$DEPLOY_LOG"
        log_success "/uploads/ location 配置存在"
    else
        die "Nginx 配置中缺少 /uploads/ location 块"
    fi

    log_info "验证 sites-enabled 目录:"
    ls -la /etc/nginx/sites-enabled/ 2>&1 | tee -a "$DEPLOY_LOG"

    log_info "测试 Nginx 配置"
    sudo nginx -t 2>&1 | tee -a "$DEPLOY_LOG" || die "Nginx 配置测试失败"

    log_info "重启 Nginx"
    sudo systemctl restart nginx || die "Nginx 重启失败"
    log_success "Nginx 已重启"

    log_info "验证 Nginx 实际加载的配置:"
    # 必须 sudo（nginx -T 要读 error.log 等 root 权限文件）；|| true 保证该行
    # 的任何失败都不会在 set -euo pipefail 下中断脚本，使 step_verify 总能执行
    sudo nginx -T 2>&1 | grep -A 5 "location.*uploads" | tee -a "$DEPLOY_LOG" || true
}

step_verify() {
    log_step "部署验证"

    log_info "1. 检查后端服务"
    if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
        log_success "后端服务健康"
    else
        log_warn "后端健康检查未通过（可能还在启动中）"
    fi

    log_info "2. 检查 Nginx 代理"
    local http_code
    http_code=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/ 2>/dev/null || echo "000")
    if [ "$http_code" = "200" ]; then
        log_success "Nginx 前端代理正常 (HTTP $http_code)"
    else
        log_warn "Nginx 前端代理返回 HTTP $http_code"
    fi

    log_info "3. 检查上传目录可访问性"
    local upload_code
    upload_code=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/uploads/images/ 2>/dev/null || echo "000")
    if [ "$upload_code" = "403" ] || [ "$upload_code" = "404" ]; then
        log_success "上传目录 Nginx 路由正常 (HTTP $upload_code，目录不可列举是预期行为)"
    else
        log_warn "上传目录 Nginx 路由返回 HTTP $upload_code（期望 403/404）"
    fi

    log_info "4. 检查手帐静态页"
    local journal_code
    journal_code=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/journal/ 2>/dev/null || echo "000")
    if [ "$journal_code" = "200" ]; then
        log_success "手帐首页 HTTP $journal_code"
    else
        die "手帐首页异常 HTTP $journal_code（期望 200）"
    fi
    local journal_spa
    journal_spa=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/journal/stats 2>/dev/null || echo "000")
    if [ "$journal_spa" = "200" ]; then
        log_success "手帐 SPA fallback HTTP $journal_spa"
    else
        die "手帐深链异常 HTTP $journal_spa（期望 200）"
    fi

    log_info "5. 检查手帐 / AI 管理 API（未登录应为 401）"
    local journal_api ai_api
    journal_api=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/journal/export 2>/dev/null || echo "000")
    ai_api=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/v1/admin/ai/providers 2>/dev/null || echo "000")
    if [ "$journal_api" = "401" ]; then
        log_success "手帐 API 已挂载 (HTTP 401)"
    else
        die "手帐 API 异常 HTTP $journal_api（期望 401）"
    fi
    if [ "$ai_api" = "401" ]; then
        log_success "AI 管理 API 已挂载 (HTTP 401)"
    else
        die "AI 管理 API 异常 HTTP $ai_api（期望 401）"
    fi

    log_info "6. 检查已上传文件"
    local uploaded_files
    uploaded_files=$(find "$UPLOAD_DIR/images" -type f 2>/dev/null | head -3)
    if [ -n "$uploaded_files" ]; then
        log_info "已上传文件示例:"
        echo "$uploaded_files" | tee -a "$DEPLOY_LOG"
        local sample_file
        sample_file=$(echo "$uploaded_files" | head -1)
        local sample_url="/uploads/images/$(echo "$sample_file" | sed "s|$UPLOAD_DIR/images/||")"
        local file_code
        file_code=$(curl -sf -o /dev/null -w "%{http_code}" "http://localhost:8080$sample_url" 2>/dev/null || echo "000")
        if [ "$file_code" = "200" ]; then
            log_success "图片文件可访问: $sample_url (HTTP $file_code)"
        else
            log_error "图片文件不可访问: $sample_url (HTTP $file_code)"
            log_error "请检查 Nginx 配置和文件权限"
        fi
    else
        log_info "暂无已上传文件，跳过文件访问验证"
    fi
}

main() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}    MySite 服务器部署脚本${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo ""

    init_log

    step_git_pull
    step_git_pull_journal
    step_build_backend
    step_build_frontend
    step_build_journal
    step_stop_service
    step_deploy_backend
    step_start_service
    step_deploy_frontend
    step_deploy_journal
    step_setup_upload_dir
    step_sync_nginx
    step_verify

    echo ""
    log_success "部署完成！"
    log_info "博客版本: $(cd "$PROJECT_DIR" && git log --oneline -1)"
    log_info "手帐版本: $(cd "$JOURNAL_DIR" && git log --oneline -1)"
    log_info "后端 JAR:  $(ls -lh "$APP_JAR" | awk '{print $5}')"
    log_info "博客前端:  $(find "$NGINX_WEB_ROOT" -type f | wc -l) 个文件"
    log_info "手帐前端:  $(find "$JOURNAL_WEB_ROOT" -type f | wc -l) 个文件"
    log_info "部署日志:  $DEPLOY_LOG"
}

main "$@"
