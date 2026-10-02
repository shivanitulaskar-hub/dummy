#!/usr/bin/env sh
# Creates a compressed, consistent MySQL dump of the Console Adda database.
# Usage: scripts/backup-db.sh [output-directory]      (default: ./backups)
# Run from the repository root while `docker compose` is up.
set -eu

OUT_DIR="${1:-backups}"
mkdir -p "$OUT_DIR"
FILE="$OUT_DIR/console_adda_$(date +%Y-%m-%d_%H%M%S).sql.gz"

# --single-transaction gives a consistent snapshot without locking InnoDB tables (safe during business hours).
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --quick --routines --triggers \
  --no-tablespaces --set-gtid-purged=OFF -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip > "$FILE"

# Fail loudly if the dump is empty or truncated.
if ! gzip -t "$FILE" || ! gzip -dc "$FILE" | tail -n 1 | grep -q "Dump completed"; then
  echo "Backup FAILED: $FILE is incomplete" >&2
  exit 1
fi
echo "Backup written: $FILE ($(du -h "$FILE" | cut -f1))"
