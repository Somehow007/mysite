#!/bin/bash

set -euo pipefail

APP_NAME="mysite"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
LOG_DIR="/var/log/mysite"
PID_FILE="/var/run/mysite.pid"

load_env() {
    local env_file="$APP_DIR/.env"
    [ ! -f "$env_file" ] && return 0
    while IFS='=' read -r key value || [ -n "$key" ]; do
        key=$(echo "$key" | xargs)
        [ -z "$key" ] && continue
        [[ "$key" =~ ^# ]] && continue
        export "$key=$value"
    done < "$env_file"
}

load_env

JAVA_OPTS="-Xms512m -Xmx768m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -XX:InitiatingHeapOccupancyPercent=45"
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=$LOG_DIR/heapdump.hprof"
JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/./urandom"
JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=production"
JAVA_OPTS="$JAVA_OPTS -Dserver.port=8081"

if [ -f "$APP_DIR/application-production.yml" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dspring.config.additional-location=file:$APP_DIR/application-production.yml"
fi

sudo mkdir -p "$LOG_DIR"

find_process_on_port() {
    sudo lsof -t -i :8081 2>/dev/null || true
}

kill_process() {
    local pid="$1"
    kill "$pid" 2>/dev/null || return 1
    local i=0
    while [ $i -lt 15 ]; do
        ! ps -p "$pid" > /dev/null 2>&1 && return 0
        sleep 1
        i=$((i + 1))
    done
    kill -9 "$pid" 2>/dev/null || true
    sleep 1
    ! ps -p "$pid" > /dev/null 2>&1
}

start() {
    if [ ! -f "$APP_JAR" ]; then
        echo "ERROR: JAR file not found: $APP_JAR"
        return 1
    fi

    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            echo "$APP_NAME is already running (PID: $pid)"
            return 1
        fi
        rm -f "$PID_FILE"
    fi

    local port_pid
    port_pid=$(find_process_on_port)
    if [ -n "$port_pid" ]; then
        echo "WARNING: Port 8081 is occupied by PID $port_pid, stopping..."
        kill_process "$port_pid" || { echo "ERROR: Failed to free port 8081"; return 1; }
    fi

    echo "Starting $APP_NAME..."
    nohup java $JAVA_OPTS -jar "$APP_JAR" > "$LOG_DIR/console.log" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$PID_FILE"

    sleep 2

    if ps -p "$new_pid" > /dev/null 2>&1; then
        echo "$APP_NAME started (PID: $new_pid)"
    else
        echo "ERROR: Process exited unexpectedly. Check: tail -100 $LOG_DIR/console.log"
        rm -f "$PID_FILE"
        return 1
    fi
}

stop() {
    local pid=""
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
    fi
    if [ -z "$pid" ]; then
        pid=$(find_process_on_port)
    fi
    if [ -z "$pid" ]; then
        echo "$APP_NAME is not running"
        rm -f "$PID_FILE"
        return 0
    fi
    if ! ps -p "$pid" > /dev/null 2>&1; then
        echo "$APP_NAME is not running (stale PID: $pid)"
        rm -f "$PID_FILE"
        return 0
    fi
    echo "Stopping $APP_NAME (PID: $pid)..."
    if kill_process "$pid"; then
        rm -f "$PID_FILE"
        echo "$APP_NAME stopped"
    else
        echo "ERROR: Failed to stop $APP_NAME (PID: $pid)"
        return 1
    fi
}

restart() {
    stop
    sleep 2
    start
}

status() {
    local pid=""
    local running=false

    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            running=true
        fi
    fi

    if [ "$running" = false ]; then
        pid=$(find_process_on_port)
        if [ -n "$pid" ]; then
            running=true
            echo "WARNING: Process on port 8081 (PID: $pid) but PID file missing"
        fi
    fi

    if [ "$running" = true ]; then
        echo "$APP_NAME is running (PID: $pid)"
        ps -p "$pid" -o rss,vsz,pmem --no-headers 2>/dev/null | awk '{printf "  RSS: %d MB, VSZ: %d MB, MEM: %.1f%%\n", $1/1024, $2/1024, $3}'
    else
        echo "$APP_NAME is not running"
        [ -f "$PID_FILE" ] && echo "WARNING: Stale PID file: $PID_FILE"
    fi
}

logs() {
    case "${1:-}" in
        app)
            tail -f "$LOG_DIR/application.log" 2>/dev/null || echo "File not found: $LOG_DIR/application.log"
            ;;
        console)
            tail -f "$LOG_DIR/console.log" 2>/dev/null || echo "File not found: $LOG_DIR/console.log"
            ;;
        deploy)
            local latest
            latest=$(ls -t "$LOG_DIR/deploy/"deploy_*.log 2>/dev/null | head -1)
            if [ -n "$latest" ]; then
                tail -f "$latest"
            else
                echo "No deploy logs found"
            fi
            ;;
        *)
            tail -f "$LOG_DIR/console.log" 2>/dev/null || echo "File not found: $LOG_DIR/console.log"
            ;;
    esac
}

case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    logs)    logs "${2:-}" ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        echo ""
        echo "Commands:"
        echo "  start    Start the application"
        echo "  stop     Stop the application"
        echo "  restart  Restart the application"
        echo "  status   Show application status and memory usage"
        echo "  logs     Tail log files"
        echo "           logs         -> console.log (default)"
        echo "           logs app     -> application.log"
        echo "           logs console -> console.log"
        echo "           logs deploy  -> latest deploy log"
        exit 1
        ;;
esac
