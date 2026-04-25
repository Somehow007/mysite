#!/bin/bash

set -e

GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
BLUE="\033[0;34m"
NC="\033[0m"

PROJECT_DIR="$HOME/project/mysite"
NGINX_WEB_ROOT="/var/www/mysite"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
APP_START_SCRIPT="$APP_DIR/start.sh"
PID_FILE="/var/run/mysite.pid"
LOG_DIR="/var/log/mysite"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    MySite 部署更新脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo

git_pull() {
    echo -e "${YELLOW}[1/6] 拉取最新代码...${NC}"
    cd "$PROJECT_DIR"
    git pull origin main
    if [ $? -ne 0 ]; then
        echo -e "${RED}错误：拉取代码失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ 代码拉取成功${NC}"
    echo
}

build_backend() {
    echo -e "${YELLOW}[2/6] 构建后端...${NC}"
    cd "$PROJECT_DIR"

    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        ./mvnw clean package -DskipTests -q
    else
        mvn clean package -DskipTests -q
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 后端构建成功${NC}"
    else
        echo -e "${RED}✗ 后端构建失败${NC}"
        exit 1
    fi
    echo
}

build_frontend() {
    echo -e "${YELLOW}[3/6] 构建前端...${NC}"
    cd "$PROJECT_DIR/mysite-frontend"

    if [ ! -d "node_modules" ]; then
        echo "安装前端依赖..."
        npm install --silent
    fi

    npm run build

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 前端构建成功${NC}"
    else
        echo -e "${RED}✗ 前端构建失败${NC}"
        exit 1
    fi
    echo
}

deploy_backend() {
    echo -e "${YELLOW}[4/6] 部署后端...${NC}"

    mkdir -p "$APP_DIR"

    cp "$PROJECT_DIR/target"/*.jar "$APP_JAR"

    if [ -f "$APP_START_SCRIPT" ]; then
        if [ -f "$PID_FILE" ]; then
            PID=$(cat "$PID_FILE")
            if ps -p $PID > /dev/null 2>&1; then
                echo "重启后端服务..."
                kill $PID 2>/dev/null || true
                sleep 2
            fi
        fi

        chmod +x "$APP_START_SCRIPT"
        nohup "$APP_START_SCRIPT" start > /dev/null 2>&1 &

        sleep 3

        if [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
            echo -e "${GREEN}✓ 后端部署成功 (PID: $(cat $PID_FILE))${NC}"
        else
            echo -e "${YELLOW}⚠ 后端已启动，请检查日志${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ 未找到启动脚本，后端 JAR 已复制到 $APP_DIR${NC}"
    fi
    echo
}

deploy_frontend() {
    echo -e "${YELLOW}[5/6] 部署前端...${NC}"

    sudo mkdir -p "$NGINX_WEB_ROOT"
    sudo cp -r "$PROJECT_DIR/mysite-frontend/dist/"* "$NGINX_WEB_ROOT/"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 前端部署成功${NC}"
    else
        echo -e "${RED}✗ 前端部署失败${NC}"
        exit 1
    fi
    echo
}

reload_nginx() {
    echo -e "${YELLOW}[6/6] 重启 Nginx...${NC}"
    sudo systemctl reload nginx
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Nginx 重载成功${NC}"
    else
        echo -e "${YELLOW}⚠ Nginx 重载失败，请手动检查${NC}"
    fi
    echo
}

print_summary() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${GREEN}部署完成！${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo
    echo -e "后端地址: ${GREEN}http://localhost:8081${NC}"
    echo -e "前端地址: ${GREEN}http://localhost${NC}"
    echo
    echo -e "${YELLOW}常用命令:${NC}"
    echo "  查看后端日志: $APP_START_SCRIPT logs"
    echo "  重启后端:     $APP_START_SCRIPT restart"
    echo "  查看后端状态: $APP_START_SCRIPT status"
    echo
}

main() {
    if [ ! -d "$PROJECT_DIR/.git" ]; then
        echo -e "${RED}错误：$PROJECT_DIR 不是 Git 项目或不存在${NC}"
        echo "请先克隆项目："
        echo "  git clone https://github.com/your-repo/mysite.git $PROJECT_DIR"
        exit 1
    fi

    git_pull
    build_backend
    build_frontend
    deploy_backend
    deploy_frontend
    reload_nginx
    print_summary
}

main "$@"