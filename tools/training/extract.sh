#!/bin/bash
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
COMICS=${KAPOW_COMICS:-$HERE/../../comics}
RAW=${KAPOW_TRAINING_WORK:-$HERE/work}/raw
slug() { basename "$1" | sed -E 's/\.[^.]+$//' | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9\n' '_'; }
extract() {
  local series=$1 src=$2
  mkdir -p "$RAW/$series"
  if [ -d "$src" ]; then
    find "$src" -type f \( -iname '*.cbr' -o -iname '*.cbz' \) | sort | while read -r f; do unar -q -o "$RAW/$series" "$f"; done
  else
    unar -q -o "$RAW/$series" "$src"
  fi
  echo "done $series $(find "$RAW/$series" -type f | wc -l)"
}
for entry in "$COMICS"/*; do
  case "$entry" in *.cbr|*.cbz|*.CBR|*.CBZ) extract "$(slug "$entry")" "$entry" ;; esac
  [ -d "$entry" ] && extract "$(slug "$entry")" "$entry"
done
echo ALLDONE
