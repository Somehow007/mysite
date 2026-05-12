#!/bin/bash

APP_NAME="mysite"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
LOG_DIR="/var/log/mysite"
PID_FILE="/var/run/mysite.pid"
HEALTH_URL="http://localhost:8081/actuator/health"
HEALTH_TIMEOUT=60

load_env() {
    local env_file="$APP_DIR/.env"
    if [ ! -f "$env_file" ]; then
        return 0
    fi

    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        line="${line%%=*}"
        line=$(echo "$line" | xargs)
        [ -z "$line" ] && continue

        local key="$line"
        local value
        value=$(grep "^${key}=" "$env_file" | head -1 | cut -d'=' -f2-)
        value=$(echo "$value" | xargs)

        if [ -n "$key" ] && [ -n "$value" ]; then
            export "$key=$value"
        fi
    done < <(grep -v '^\s*#' "$env_file" | grep -v '^\s*$')
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

wait_for_health() {
    local max_wait="${1:-$HEALTH_TIMEOUT}"
    local waited=0
    while [ "$waited" -lt "$max_wait" ]; do
        if curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null | grep -q "200"; then
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

find_process_on_port() {
    sudo lsof -t -i :8081 2>/dev/null || true
}

kill_process() {
    local pid="$1"
    local timeout="${2:-15}"

    kill "$pid" 2>/dev/null || return 1

    local i=0
    while [ $i -lt "$timeout" ]; do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            return 0
        fi
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
        local pid=$(cat "$PID_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
            echo "$APP_NAME is already running (PID: $pid)"
            return 1
        fi
        rm -f "$PID_FILE"
    fi

    local port_pid=$(find_process_on_port)
    if [ -n "$port_pid" ]; then
        echo "WARNING: Port 8081 is occupied by PID $port_pid, attempting to stop..."
        if ! kill_process "$port_pid"; then
            echo "ERROR: Failed to free port 8081"
            return 1
        fi
        echo "Port 8081 freed"
    fi

    echo "Starting $APP_NAME..."
    nohup java $JAVA_OPTS -jar "$APP_JAR" > "$LOG_DIR/console.log" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$PID_FILE"

    echo "Waiting for application to start (PID: $new_pid)..."
    if ! wait_for_health "$HEALTH_TIMEOUT"; then
        if ps -p "$new_pid" > /dev/null 2>&1; then
            echo "$APP_NAME started (PID: $new_pid) - health check pending"
            echo "  Process is running but health check did not pass within ${HEALTH_TIMEOUT}s"
            echo "  This may be normal if the application requires more startup time"
            echo "  Check health: curl -s $HEALTH_URL"
            echo "  Check logs:   tail -100 $LOG_DIR/application.log"
        else
            echo "ERROR: Process exited unexpectedly. Check logs:"
            echo "  tail -100 $LOG_DIR/console.log"
            rm -f "$PID_FILE"
            return 1
        fi
    else
        echo "$APP_NAME started successfully (PID: $new_pid)"
        echo "  Health: $HEALTH_URL -> OK"
    fi
    echo "  Memory: $(ps -p $new_pid -o rss --no-headers | awk '{printf \"%.0f MB\n\", $1/1024}')"
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
    if kill_process "$pid" 15; then
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
            echo "WARNING: Process found on port 8081 (PID: $pid) but PID file is missing"
        fi
    fi

    if [ "$running" = true ]; then
        echo "$APP_NAME is running (PID: $pid)"
        echo "Memory usage:"
        ps -p "$pid" -o rss,vsz,pmem --no-headers | awk '{printf "  RSS: %d MB, VSZ: %d MB, MEM: %.1f%%\n", $1/1024, $2/1024, $3}'

        if command -v curl &> /dev/null; then
            local health_code=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
            if [ "$health_code" = "200" ]; then
                echo "Health check: OK"
            else
                echo "Health check: FAILED (HTTP $health_code)"
            fi
        fi
    else
        echo "$APP_NAME is not running"
        if [ -f "$PID_FILE" ]; then
            echo "WARNING: Stale PID file exists: $PID_FILE"
        fi
    fi
}

logs() {
    local log_file="$LOG_DIR/console.log"

    if [ -z "${1:-}" ]; then
        if [ -f "$log_file" ]; then
            tail -f "$log_file"
        else
            echo "Log file not found: $log_file"
            return 1
        fi
    else
        case "$1" in
            app)
                tail -f "$LOG_DIR/application.log" 2>/dev/null || echo "File not found: $LOG_DIR/application.log"
                ;;
            console)
                tail -f "$LOG_DIR/console.log" 2>/dev/null || echo "File not found: $LOG_DIR/console.log"
                ;;
            deploy)
                local latest=$(ls -t "$LOG_DIR/deploy/"deploy_*.log 2>/dev/null | head -1)
                if [ -n "$latest" ]; then
                    tail -f "$latest"
                else
                    echo "No deploy logs found"
                fi
                ;;
            *)
                echo "Usage: $0 logs [app|console|deploy]"
                return 1
                ;;
        esac
    fi
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    logs)
        logs "${2:-}"
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        echo ""
        echo "Commands:"
        echo "  start    Start the application"
        echo "  stop     Stop the application"
        echo "  restart  Restart the application"
        echo "  status   Show application status and health"
        echo "  logs     Tail log files"
        echo "           logs         -> console.log (default)"
        echo "           logs app     -> application.log"
        echo "           logs console -> console.log"
        echo "           logs deploy  -> latest deploy log"
        exit 1
        ;;
esac
