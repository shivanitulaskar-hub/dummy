#!/usr/bin/env sh
# Restores a backup created by backup-db.sh. THIS REPLACES ALL CURRENT DATA.
# Usage: scripts/restore-db.sh backups/console_adda_YYYY-MM-DD_HHMMSS.sql.gz
set -eu

FILE="${1:?Usage: scripts/restore-db.sh <backup.sql.gz>}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 1; }
gzip -t "$FILE"

printf 'This will REPLACE the current database with %s. Type RESTORE to continue: ' "$FILE"
read -r answer
[ "$answer" = "RESTORE" ] || { echo "Cancelled."; exit 1; }

# Stop the backend so nothing writes during the restore.
docker compose stop backend
gzip -dc "$FILE" | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
docker compose start backend
echo "Restore complete from $FILE"
