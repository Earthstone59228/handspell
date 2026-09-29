#!/usr/bin/env bash
set -euo pipefail

recorder_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
caller_dir="$PWD"

if ! command -v uv >/dev/null 2>&1; then
    echo "uv is required: https://docs.astral.sh/uv/" >&2
    exit 1
fi

selftest=false
output=""
recorder_args=("$@")
while (($#)); do
    case "$1" in
        --selftest) selftest=true ;;
        --out)
            if (($# < 2)); then
                echo "--out requires a path" >&2
                exit 2
            fi
            output="$2"
            shift
            ;;
        --out=*) output="${1#--out=}" ;;
    esac
    shift
done

if [[ "$selftest" == false ]]; then
    if [[ -z "$output" ]]; then
        echo "Pass --out /path/to/a/new/capture.csv; the shipped references CSV is protected." >&2
        exit 2
    fi
    shipped="$(realpath -m -- "$recorder_dir/../android/app/src/main/assets/classifier/references-v1.csv")"
    requested="$(realpath -m -- "$caller_dir/$output")"
    if [[ "$requested" == "$shipped" ]]; then
        echo "Refusing to overwrite the shipped references CSV." >&2
        exit 2
    fi
    recorder_args+=(--out "$requested")
fi

cd "$recorder_dir"

if [[ ! -x .venv/bin/python ]]; then
    if ! uv python find 3.12 >/dev/null 2>&1; then
        uv python install 3.12
    fi
    uv venv .venv --python 3.12
fi
if [[ "$(.venv/bin/python -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')" != 3.12 ]]; then
    echo "recorder/.venv must use Python 3.12" >&2
    exit 1
fi
uv pip install --python .venv/bin/python -r requirements.txt

exec .venv/bin/python record_references.py "${recorder_args[@]}"
