#!/usr/bin/env bash
set -euo pipefail

# Prevent Git Bash on Windows from rewriting container paths such as /bin/sh.
export MSYS_NO_PATHCONV=1

# Back up PostgreSQL and MinIO together. Run from the backend repository root.
# The API is stopped during the dump so the database and objects are consistent.
BACKUP_DIR="${BACKUP_DIR:-./backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"

API_WAS_RUNNING=false
if docker compose ps --status running --services 2>/dev/null \
  | grep -qx 'document-extraction-api'; then
  API_WAS_RUNNING=true
fi

restart_api() {
  if [[ "$API_WAS_RUNNING" == true ]]; then
    echo "Restarting the API"
    docker compose start document-extraction-api >/dev/null
  fi
}
trap restart_api EXIT

if [[ "$API_WAS_RUNNING" == true ]]; then
  echo "Stopping the API to obtain a consistent backup"
  docker compose stop document-extraction-api >/dev/null
fi

echo "Backing up PostgreSQL to $BACKUP_DIR/postgres-$STAMP.dump"
docker compose exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "$BACKUP_DIR/postgres-$STAMP.dump"

echo "Backing up MinIO objects to $BACKUP_DIR/minio-$STAMP"
docker compose run --rm -e BACKUP_STAMP="$STAMP" --entrypoint /bin/sh minio-init -c \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" && mc mirror --overwrite "local/$MINIO_BUCKET" "/backups/minio-$BACKUP_STAMP"'

echo "Backup complete: $STAMP"
