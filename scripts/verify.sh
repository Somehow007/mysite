#!/usr/bin/env bash
# verify.sh — 一键验证（接管安全网，阶段 2 产出）
#
#   ./scripts/verify.sh          # quick：不依赖 docker。后端 hermetic 测试 + 前端 type-check
#   ./scripts/verify.sh full     # full：需 docker 全量（MySQL/Redis/PG）。全量测试 + 前端 lint/build
#
# 约定：
#   - 后端 @SpringBootTest 类打 @Tag("needs-infra")，quick 模式用 -DexcludedGroups 排除；
#     新增需要真实基础设施的测试必须打同 tag，否则会让 quick 变成依赖 docker。
#   - full 模式不自动 up -d 之外的操作；ES 相关测试本身 @Disabled。
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-quick}"
COMPOSE="docker/docker-compose.yml"

step() { printf '\n\033[1;34m== %s ==\033[0m\n' "$*"; }
fail() { printf '\033[1;31mFAIL: %s\033[0m\n' "$*" >&2; exit 1; }

case "$MODE" in
  quick)
    step "backend: hermetic tests (exclude needs-infra)"
    ./mvnw -q test -DexcludedGroups=needs-infra || fail "后端测试失败（hermetic 集合）"
    step "frontend: type-check"
    (cd mysite-frontend && npm run type-check) || fail "前端类型检查失败"
    ;;

  full)
    step "infra: docker daemon"
    docker info >/dev/null 2>&1 || fail "Docker daemon 未运行。先启动 Docker Desktop，再执行: docker compose -f $COMPOSE up -d"
    if ! docker compose -f "$COMPOSE" ps --status running --services 2>/dev/null | grep -q .; then
      step "infra: docker compose up -d"
      docker compose -f "$COMPOSE" up -d || fail "docker compose up 失败"
    fi
    step "backend: full test suite (含 needs-infra)"
    ./mvnw test || fail "后端全量测试失败"
    step "frontend: type-check"
    (cd mysite-frontend && npm run type-check) || fail "前端类型检查失败"
    step "frontend: lint (no-fix)"
    (cd mysite-frontend && npx oxlint . -D correctness --ignore-path .gitignore) || fail "oxlint 失败"
    (cd mysite-frontend && npx eslint .) || fail "eslint 失败"
    step "frontend: build"
    (cd mysite-frontend && npm run build) || fail "前端构建失败"
    ;;

  *)
    echo "usage: $0 [quick|full]" >&2
    exit 2
    ;;
esac

printf '\n\033[1;32mVERIFY OK (%s)\033[0m\n' "$MODE"
