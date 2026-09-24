#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/ionic-app/dist   (e.g. web/dist after: cd web && npm ci && npm run build -- --base=./)" >&2
  exit 2
fi

source_dir=$1
if [[ ! -f "$source_dir/index.html" ]]; then
  echo "Missing built frontend: $source_dir/index.html" >&2
  exit 2
fi
if rg -q '(src|href)="/assets/' "$source_dir/index.html"; then
  echo "Build the frontend with relative assets: npm run build -- --base=./" >&2
  exit 2
fi

repo_dir=$(cd "$(dirname "$0")/.." && pwd)
destination="$repo_dir/android/app/src/main/assets/web"
mkdir -p "$destination"
rsync -a --delete "$source_dir/" "$destination/"
