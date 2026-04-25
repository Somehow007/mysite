#!/bin/bash

APP_NAME="mysite"
APP_DIR="/opt/mysite"
APP_JAR="$APP_DIR/mysite.jar"
LOG_DIR="/var/log/mysite"
PID_FILE="/var/run/mysite.pid"

if [ -f "$APP_DIR/.env" ]; then
    export $(cat "$APP_DIR/.env" | grep -v '^#' | xargs)
fi

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

start() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "$APP_NAME is already running (PID: $PID)"
            return 1
        fi
    fi
    
    echo "Starting $APP_NAME..."
    nohup java $JAVA_OPTS -jar "$APP_JAR" > "$LOG_DIR/console.log" 2>&1 &
    echo $! > "$PID_FILE"
    
    sleep 3
    
    if ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
        echo "$APP_NAME started successfully (PID: $(cat $PID_FILE))"
    else
        echo "Failed to start $APP_NAME. Check logs at $LOG_DIR/console.log"
        exit 1
    fi
}

stop() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "Stopping $APP_NAME (PID: $PID)..."
            kill $PID
            
            for i in {1..10}; do
                if ! ps -p $PID > /dev/null 2>&1; then
                    break
                fi
                sleep 1
            done
            
            if ps -p $PID > /dev/null 2>&1; then
                echo "Force killing $APP_NAME..."
                kill -9 $PID
            fi
            
            rm -f "$PID_FILE"
            echo "$APP_NAME stopped"
        else
            echo "$APP_NAME is not running"
            rm -f "$PID_FILE"
        fi
    else
        echo "$APP_NAME is not running"
    fi
}

restart() {
    stop
    sleep 2
    start
}

status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "$APP_NAME is running (PID: $PID)"
            echo "Memory usage:"
            ps -p $PID -o rss,vsz,pmem --no-headers | awk '{printf "  RSS: %d MB, VSZ: %d MB, MEM: %.1f%%\n", $1/1024, $2/1024, $3}'
        else
            echo "$APP_NAME is not running"
        fi
    else
        echo "$APP_NAME is not running"
    fi
}

logs() {
    if [ -f "$LOG_DIR/console.log" ]; then
        tail -f "$LOG_DIR/console.log"
    else
        echo "Log file not found: $LOG_DIR/console.log"
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
        logs
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs}"
        exit 1
        ;;
esac
