#!/usr/bin/env bash
# After new letters have been recorded into android/app/src/main/assets/classifier/references-v1.csv
# (desktop recorder: recorder/README.md, or training/scripts/build_references.py from capture CSVs), run this to
# push them through the rest of the app:
#   1. regenerate the alphabet menu's hand wireframes from the references,
#   2. rebuild the Ionic bundle and sync it into the Android assets.
# The native camera reference card and the classifier read the same CSV, so nothing else needs editing.
#
# Usage: scripts/refresh-letters.sh [path/to/web-project]   (default: <repo>/web, the Ionic alphabet menu)
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
ionic=${1:-$repo/web}
csv="$repo/android/app/src/main/assets/classifier/references-v1.csv"

"$repo/scripts/gen-web-wireframes.py" "$ionic/src/js/handshapes.js"
(cd "$ionic" && { [[ -d node_modules ]] || npm ci; } && npm run build -- --base=./)
"$repo/scripts/sync-ionic-frontend.sh" "$ionic/dist"

echo
echo "Exemplars per letter in references-v1.csv (a letter with none stays 'not ready' in the app):"
python3 - "$csv" <<'PY'
import csv, sys, collections
rows = [r for r in csv.reader(l for l in open(sys.argv[1]) if not l.startswith('#'))][1:]
counts = collections.Counter(r[0] for r in rows)
for letter in "ABCDEFGHIKLMNOPQRSTUVWXY":
    print(f"  {letter}: {counts.get(letter, 0):3d}" + ("" if counts.get(letter) else "   <- needs recording"))
print("  (J and Z need motion and are intentionally unsupported)")
PY
echo
echo "Now rebuild the app: cd android && ./gradlew :app:installDebug"
