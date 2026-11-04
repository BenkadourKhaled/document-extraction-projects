#!/usr/bin/env bash
set -euo pipefail

# Prevent Git Bash on Windows from rewriting container paths such as /bin/sh.
export MSYS_NO_PATHCONV=1

# Restore PostgreSQL and MinIO from a backup produced by scripts/backup.sh.
# Usage: ./scripts/restore.sh <stamp> [backup-directory]
STAMP="${1:?Usage: ./scripts/restore.sh <stamp> [backup-directory]}"
BACKUP_DIR="${2:-./backups}"
POSTGRES_DUMP="$BACKUP_DIR/postgres-$STAMP.dump"
MINIO_DIR="$BACKUP_DIR/minio-$STAMP"

if [[ ! -f "$POSTGRES_DUMP" ]]; then
  echo "PostgreSQL dump not found: $POSTGRES_DUMP" >&2
  exit 1
fi
if [[ ! -d "$MINIO_DIR" ]]; then
  echo "MinIO backup directory not found: $MINIO_DIR" >&2
  exit 1
fi

echo "Stopping the API to prevent writes during restore"
docker compose stop document-extraction-api

echo "Restoring PostgreSQL"
docker compose exec -T postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' \
  < "$POSTGRES_DUMP"

echo "Restoring MinIO objects"
docker compose run --rm -e BACKUP_STAMP="$STAMP" --entrypoint /bin/sh minio-init -c \
  'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" && mc mirror --overwrite "/backups/minio-$BACKUP_STAMP" "local/$MINIO_BUCKET"'

echo "Starting the API"
docker compose start document-extraction-api
echo "Restore complete: $STAMP"
