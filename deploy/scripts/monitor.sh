#!/bin/bash

set -euo pipefail

LOG_FILE="/var/log/mysite/monitor.log"
ALERT_WEBHOOK=""

check_memory() {
    local mem_total mem_used mem_usage
    mem_total=$(free -m | awk '/Mem/{print $2}')
    mem_used=$(free -m | awk '/Mem/{print $3}')
    mem_usage=$(awk "BEGIN {printf \"%.0f\", ($mem_used/$mem_total)*100}")

    if [ "$mem_usage" -gt 90 ]; then
        local msg="[$(date)] CRITICAL: Memory ${mem_usage}% (${mem_used}MB/${mem_total}MB)"
        echo "$msg" >> "$LOG_FILE"
        send_alert "$msg"
    elif [ "$mem_usage" -gt 80 ]; then
        echo "[$(date)] WARNING: Memory ${mem_usage}% (${mem_used}MB/${mem_total}MB)" >> "$LOG_FILE"
    fi
}

check_disk() {
    local disk_usage
    disk_usage=$(df -h / | awk 'NR==2{print $5}' | sed 's/%//')

    if [ "$disk_usage" -gt 90 ]; then
        local msg="[$(date)] CRITICAL: Disk ${disk_usage}%"
        echo "$msg" >> "$LOG_FILE"
        send_alert "$msg"
    elif [ "$disk_usage" -gt 80 ]; then
        echo "[$(date)] WARNING: Disk ${disk_usage}%" >> "$LOG_FILE"
    fi
}

check_cpu() {
    local cpu_usage
    cpu_usage=$(top -bn2 -d1 | grep "Cpu(s)" | tail -1 | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1}')
    cpu_usage=${cpu_usage%.*}

    if [ "$cpu_usage" -gt 90 ]; then
        echo "[$(date)] WARNING: CPU ${cpu_usage}%" >> "$LOG_FILE"
    fi
}

check_services() {
    local services=("nginx" "mysql" "redis-server")

    for service in "${services[@]}"; do
        if ! systemctl is-active --quiet "$service" 2>/dev/null; then
            local msg="[$(date)] ERROR: $service is not running"
            echo "$msg" >> "$LOG_FILE"
            echo "[$(date)] Attempting to restart $service..." >> "$LOG_FILE"
            systemctl restart "$service" 2>> "$LOG_FILE"
            sleep 3
            if systemctl is-active --quiet "$service"; then
                echo "[$(date)] $service restarted successfully" >> "$LOG_FILE"
            else
                send_alert "$msg - Restart failed!"
            fi
        fi
    done

    if ! /opt/mysite/start.sh status > /dev/null 2>&1; then
        local msg="[$(date)] ERROR: mysite is not running"
        echo "$msg" >> "$LOG_FILE"
        echo "[$(date)] Attempting to restart mysite..." >> "$LOG_FILE"
        /opt/mysite/start.sh start >> "$LOG_FILE" 2>&1
        sleep 3
        if /opt/mysite/start.sh status > /dev/null 2>&1; then
            echo "[$(date)] mysite restarted successfully" >> "$LOG_FILE"
        else
            send_alert "$msg - Restart failed!"
        fi
    fi
}

send_alert() {
    [ -n "$ALERT_WEBHOOK" ] && curl -X POST -H 'Content-type: application/json' --data "{\"text\":\"$1\"}" "$ALERT_WEBHOOK" 2>/dev/null
}

cleanup_logs() {
    find /var/log/mysite -name "*.log" -mtime +7 -exec gzip {} \; 2>/dev/null
    find /var/log/mysite -name "*.gz" -mtime +30 -delete 2>/dev/null
}

mkdir -p "$(dirname "$LOG_FILE")"

check_memory
check_disk
check_cpu
check_services
cleanup_logs
