#!/usr/bin/env bash
# Dump the mysite MySQL database (blog t_* tables + journal sj_* tables).
#
# Run on the host that already has the `mysite-mysql` container.
# Do not SSH to production from CI; copy this script to the server and cron it there.
#
# Cron example (daily 03:15, retain 14 days via RETAIN_DAYS below):
#   15 3 * * * /opt/mysite/deploy/backup-mysql.sh >> /var/log/mysite/backup-mysql.log 2>&1
#
# Env overrides:
#   MYSQL_CONTAINER  default: mysite-mysql
#   MYSQL_DATABASE   default: mysite
#   BACKUP_DIR       default: /var/backups/mysite-mysql
#   RETAIN_DAYS      default: 14

set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-mysite-mysql}"
DATABASE="${MYSQL_DATABASE:-mysite}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/mysite-mysql}"
RETAIN_DAYS="${RETAIN_DAYS:-14}"

if ! command -v docker >/dev/null 2>&1; then
    echo "[backup-mysql] docker not found" >&2
    exit 1
fi

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
    echo "[backup-mysql] container ${CONTAINER} is not running" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${BACKUP_DIR}/${DATABASE}-${STAMP}.sql.gz"

# Password comes from the container env (MYSQL_ROOT_PASSWORD); nothing is hardcoded here.
docker exec "$CONTAINER" sh -c \
    'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --default-character-set=utf8mb4 --databases "'"$DATABASE"'"' \
    | gzip -c > "$OUT"

if [ ! -s "$OUT" ]; then
    echo "[backup-mysql] dump is empty: ${OUT}" >&2
    rm -f "$OUT"
    exit 1
fi

echo "[backup-mysql] wrote ${OUT} ($(wc -c < "$OUT") bytes)"

find "$BACKUP_DIR" -type f -name "${DATABASE}-*.sql.gz" -mtime "+${RETAIN_DAYS}" -delete

echo "[backup-mysql] retention: deleted dumps older than ${RETAIN_DAYS} days"
