#!/bin/bash

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()    { echo -e "${BLUE}[STEP]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }

find_fat_jar() {
    local project_dir="$1"
    for jar in "$project_dir"/target/*.jar; do
        [[ "$jar" =~ \.original$ ]] && continue
        local size
        size=$(stat -c%s "$jar" 2>/dev/null || stat -f%z "$jar" 2>/dev/null || echo 0)
        [ "$size" -gt 10000000 ] && echo "$jar" && return 0
    done
    return 1
}

find_maven() {
    local project_dir="$1"
    if [ -f "$project_dir/mvnw" ]; then
        echo "$project_dir/mvnw"
    else
        echo "mvn"
    fi
}
