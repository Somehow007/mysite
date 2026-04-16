#!/bin/bash

# MySite 快速启动脚本

set -e

echo "========================================="
echo "  MySite 开发环境快速启动脚本"
echo "========================================="

cd "$(dirname "$0")"

# 检查 .env 文件是否存在
if [ ! -f .env ]; then
    echo "正在创建 .env 文件..."
    cp .env.example .env
    echo "已创建 .env 文件，请根据需要修改配置"
fi

# 启动服务
echo ""
echo "正在启动 Docker 服务..."
docker-compose up -d

echo ""
echo "等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "服务状态:"
docker-compose ps

echo ""
echo "========================================="
echo "  服务已启动!"
echo "========================================="
echo ""
echo "服务地址:"
echo "  - MySQL:        localhost:3306"
echo "  - Redis:        localhost:6379"
echo "  - Elasticsearch: localhost:9200"
echo "  - Artalk:       localhost:23366"
echo ""
echo "默认账号:"
echo "  - MySQL root:   root / root123456"
echo "  - MySQL mysite: 自动创建"
echo "  - Elasticsearch: elastic / elastic123456"
echo ""
echo "停止服务: docker-compose down"
echo "查看日志: docker-compose logs -f [服务名]"
echo ""
