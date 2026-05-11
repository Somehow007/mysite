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

    sudo mkdir -p "$APP_DIR"

    # 清理旧的JAR文件，只保留最新的一个
    JAR_FILES=("$PROJECT_DIR/target"/*.jar)
    if [ ${#JAR_FILES[@]} -eq 0 ]; then
        echo -e "${RED}错误：没有找到 JAR 文件${NC}"
        exit 1
    fi
    
    # 获取最新的JAR文件（按修改时间排序）
    LATEST_JAR=$(ls -t "$PROJECT_DIR/target"/*.jar | head -n 1)
    echo "使用最新JAR: $(basename "$LATEST_JAR")"
    
    # 先备份旧的JAR
    if [ -f "$APP_JAR" ]; then
        sudo cp "$APP_JAR" "$APP_JAR.bak.$(date +%Y%m%d%H%M%S)"
    fi
    
    sudo cp "$LATEST_JAR" "$APP_JAR"

    if [ -f "$PROJECT_DIR/deploy/config/application-production.yml" ]; then
        sudo cp "$PROJECT_DIR/deploy/config/application-production.yml" "$APP_DIR/"
        echo -e "${GREEN}✓ 配置文件已更新${NC}"
    fi

    if [ -f "$PROJECT_DIR/deploy/config/.env" ]; then
        sudo cp "$PROJECT_DIR/deploy/config/.env" "$APP_DIR/"
        echo -e "${GREEN}✓ 环境变量文件已更新${NC}"
    else
        if [ ! -f "$APP_DIR/.env" ]; then
            echo -e "${YELLOW}⚠ 未找到 .env 文件，请手动创建: $APP_DIR/.env${NC}"
            echo -e "${YELLOW}  参考: deploy/config/.env.example${NC}"
        fi
    fi

    if [ -f "$PROJECT_DIR/deploy/scripts/start.sh" ]; then
        sudo cp "$PROJECT_DIR/deploy/scripts/start.sh" "$APP_START_SCRIPT"
        echo -e "${GREEN}✓ 启动脚本已更新${NC}"
    fi

    # 确保使用 start.sh 重启服务
    if [ -f "$APP_START_SCRIPT" ]; then
        sudo chmod +x "$APP_START_SCRIPT"
        echo "重启后端服务..."
        sudo "$APP_START_SCRIPT" restart

        sleep 5

        if [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
            echo -e "${GREEN}✓ 后端部署成功 (PID: $(cat $PID_FILE))${NC}"
            # 显示JAR文件修改时间
            echo -e "${GREEN}  JAR文件时间: $(ls -lh "$APP_JAR" | awk '{print $6, $7, $8}')${NC}"
        else
            echo -e "${YELLOW}⚠ 后端可能未正确启动，请检查日志: $APP_START_SCRIPT logs${NC}"
        fi
    else
        echo -e "${YELLOW}⚠ 未找到启动脚本，后端 JAR 已复制到 $APP_DIR${NC}"
    fi
    echo
}

deploy_frontend() {
    echo -e "${YELLOW}[5/6] 部署前端...${NC}"

    sudo mkdir -p "$NGINX_WEB_ROOT"
    
    # 先清理旧的前端文件，防止旧代码残留
    echo "清理旧的前端文件..."
    sudo rm -rf "$NGINX_WEB_ROOT"/* "$NGINX_WEB_ROOT"/.??* 2>/dev/null || true
    
    sudo cp -r "$PROJECT_DIR/mysite-frontend/dist/"* "$NGINX_WEB_ROOT/"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 前端部署成功${NC}"
        # 显示前端文件修改时间
        echo -e "${GREEN}  前端文件时间: $(ls -lh "$NGINX_WEB_ROOT"/index.html 2>/dev/null | awk '{print $6, $7, $8}')${NC}"
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