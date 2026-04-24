#!/bin/bash

LOG_FILE="/var/log/mysite/monitor.log"
ALERT_WEBHOOK=""

check_memory() {
    local mem_total=$(free -m | awk '/Mem/{print $2}')
    local mem_used=$(free -m | awk '/Mem/{print $3}')
    local mem_usage=$(awk "BEGIN {printf \"%.0f\", ($mem_used/$mem_total)*100}")
    
    if [ $mem_usage -gt 90 ]; then
        local msg="[$(date)] CRITICAL: Memory usage is ${mem_usage}% (${mem_used}MB/${mem_total}MB)"
        echo "$msg" >> "$LOG_FILE"
        send_alert "$msg"
    elif [ $mem_usage -gt 80 ]; then
        echo "[$(date)] WARNING: Memory usage is ${mem_usage}% (${mem_used}MB/${mem_total}MB)" >> "$LOG_FILE"
    fi
}

check_disk() {
    local disk_usage=$(df -h / | awk 'NR==2{print $5}' | sed 's/%//')
    
    if [ $disk_usage -gt 90 ]; then
        local msg="[$(date)] CRITICAL: Disk usage is ${disk_usage}%"
        echo "$msg" >> "$LOG_FILE"
        send_alert "$msg"
    elif [ $disk_usage -gt 80 ]; then
        echo "[$(date)] WARNING: Disk usage is ${disk_usage}%" >> "$LOG_FILE"
    fi
}

check_cpu() {
    local cpu_usage=$(top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1}')
    cpu_usage=${cpu_usage%.*}
    
    if [ $cpu_usage -gt 90 ]; then
        echo "[$(date)] WARNING: CPU usage is ${cpu_usage}%" >> "$LOG_FILE"
    fi
}

check_services() {
    local services=("mysite" "nginx" "mysql" "redis-server")
    
    for service in "${services[@]}"; do
        if ! systemctl is-active --quiet $service 2>/dev/null; then
            local msg="[$(date)] ERROR: $service is not running"
            echo "$msg" >> "$LOG_FILE"
            
            echo "[$(date)] Attempting to restart $service..." >> "$LOG_FILE"
            systemctl restart $service 2>> "$LOG_FILE"
            
            sleep 3
            if systemctl is-active --quiet $service; then
                echo "[$(date)] $service restarted successfully" >> "$LOG_FILE"
            else
                send_alert "$msg - Restart failed!"
            fi
        fi
    done
}

check_application() {
    local health_url="http://localhost:8081/actuator/health"
    
    if command -v curl &> /dev/null; then
        local response=$(curl -s -o /dev/null -w "%{http_code}" "$health_url" 2>/dev/null)
        
        if [ "$response" != "200" ]; then
            local msg="[$(date)] WARNING: Application health check failed (HTTP $response)"
            echo "$msg" >> "$LOG_FILE"
        fi
    fi
}

send_alert() {
    local message="$1"
    
    if [ -n "$ALERT_WEBHOOK" ]; then
        curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\"$message\"}" \
            "$ALERT_WEBHOOK" 2>/dev/null
    fi
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
check_application
cleanup_logs
