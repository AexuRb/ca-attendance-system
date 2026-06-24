#!/usr/bin/env bash
set -euo pipefail

DB_NAME="${DB_NAME:-ca_attendance}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/ca-attendance/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUTPUT_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

mysqldump \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  "${DB_NAME}" | gzip > "${OUTPUT_FILE}"

find "${BACKUP_DIR}" \
  -type f \
  -name "${DB_NAME}_*.sql.gz" \
  -mtime +"${RETENTION_DAYS}" \
  -delete

echo "Backup created: ${OUTPUT_FILE}"
