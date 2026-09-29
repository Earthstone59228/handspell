#!/usr/bin/env bash
# Read-only Android APK compatibility checks. Usage: check-apk-compat.sh <apk>
set -euo pipefail

if (($# != 1)) || [[ ! -f "$1" ]]; then
    echo "Usage: $0 <existing.apk>" >&2
    exit 2
fi
apk="$(realpath -- "$1")"

sdk_tool() {
    local name="$1" sdk_root candidate
    if command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
        return
    fi
    for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [[ -n "$sdk_root" ]] || continue
        for candidate in "$sdk_root"/build-tools/*/"$name"; do
            [[ -x "$candidate" ]] && printf '%s\n' "$candidate"
        done
    done | sort -V | tail -1
}

aapt2="$(sdk_tool aapt2)"
zipalign="$(sdk_tool zipalign)"
apksigner="$(sdk_tool apksigner)"
for tool in aapt2 zipalign apksigner; do
    if [[ -z "${!tool}" ]]; then
        echo "Missing $tool; set ANDROID_HOME or ANDROID_SDK_ROOT to an installed Android SDK." >&2
        exit 2
    fi
done
command -v unzip >/dev/null || { echo "Missing unzip" >&2; exit 2; }
command -v readelf >/dev/null || { echo "Missing readelf" >&2; exit 2; }

scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
failed=0

size="$(stat -c %s -- "$apk")"
printf 'APK: %s\nSize: %s bytes (%.1f MiB)\n' "$apk" "$size" "$(awk -v n="$size" 'BEGIN {print n/1048576}')"

badging="$("$aapt2" dump badging "$apk")"
printf '%s\n' "$badging" | awk '/^package:|^minSdkVersion:|^targetSdkVersion:|^native-code:/ {print}'

if "$zipalign" -c -P 16 -v 4 "$apk" >"$scratch/zipalign.txt" 2>&1; then
    echo "ZIP 16 KB page alignment: PASS"
else
    echo "ZIP 16 KB page alignment: FAIL"
    tail -n 5 "$scratch/zipalign.txt"
    failed=1
fi

mapfile -t libraries < <(unzip -Z1 "$apk" | awk '/^lib\/.+\.so$/ {print}')
printf 'Native libraries: %s\n' "${#libraries[@]}"
for entry in "${libraries[@]}"; do
    unzip -p "$apk" "$entry" >"$scratch/library.so"
    if ! readelf -lW "$scratch/library.so" >"$scratch/segments.txt"; then
        printf 'ELF FAIL %s: readelf could not parse it\n' "$entry"
        failed=1
        continue
    fi
    mapfile -t loads < <(awk '$1 == "LOAD" {print $2, $3, $NF}' "$scratch/segments.txt")
    if ((${#loads[@]} == 0)); then
        printf 'ELF FAIL %s: no LOAD segments\n' "$entry"
        failed=1
        continue
    fi
    entry_failed=0
    alignments=()
    for load in "${loads[@]}"; do
        read -r offset vaddr alignment <<<"$load"
        alignments+=("$alignment")
        if ((alignment < 16384 || (offset - vaddr) % 16384 != 0)); then
            entry_failed=1
        fi
    done
    if ((entry_failed)); then
        printf 'ELF FAIL %s LOAD alignments: %s\n' "$entry" "${alignments[*]}"
        failed=1
    else
        printf 'ELF PASS %s LOAD alignments: %s\n' "$entry" "${alignments[*]}"
    fi
done

if ! "$apksigner" verify --verbose --print-certs "$apk"; then
    echo "APK signature: FAIL"
    failed=1
fi

exit "$failed"
