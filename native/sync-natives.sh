#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: native/sync-natives.sh [--verify|--sync]

Initialize the native source submodules (HarfBuzz, libpng, zlib) and check they sit on their
pinned release tags. --sync checks the pinned tags out.

Environment overrides:
  HARFBUZZ_TAG  HarfBuzz git tag (default: 15.0.0)
  LIBPNG_TAG    libpng git tag (default: v1.6.59)
  ZLIB_TAG      zlib git tag (default: v1.3.2)
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HARFBUZZ_TAG="${HARFBUZZ_TAG:-15.0.0}"
LIBPNG_TAG="${LIBPNG_TAG:-v1.6.59}"
ZLIB_TAG="${ZLIB_TAG:-v1.3.2}"

# name|submodule path|tag|file proving the checkout is populated
SOURCES=(
    "HarfBuzz|native/harfbuzz|$HARFBUZZ_TAG|src/harfbuzz.cc"
    "libpng|native/libpng|$LIBPNG_TAG|png.h"
    "zlib|native/zlib|$ZLIB_TAG|zlib.h"
)

ensure_registered() {
    local path="$1"
    if ! git config -f "$ROOT_DIR/.gitmodules" --get-regexp '^submodule\..*\.path$' | grep -q " $path\$"; then
        echo "Submodule is not registered at $path." >&2
        exit 1
    fi
}

init_submodule() {
    local path="$1" marker="$2"
    if [[ ! -f "$ROOT_DIR/$path/$marker" ]]; then
        git -C "$ROOT_DIR" submodule update --init --depth 1 "$path"
    fi
}

expected_commit() {
    local path="$1" tag="$2"
    git -C "$ROOT_DIR/$path" rev-parse -q --verify "refs/tags/$tag^{commit}" 2>/dev/null || true
}

fetch_tag() {
    local path="$1" tag="$2"
    git -C "$ROOT_DIR/$path" fetch --depth 1 --force origin "refs/tags/$tag:refs/tags/$tag"
}

verify_source() {
    local name="$1" path="$2" tag="$3" marker="$4"
    if [[ ! -f "$ROOT_DIR/$path/$marker" ]]; then
        echo "$name source is missing under $path. Run ./gradlew syncNativeSubmodules." >&2
        exit 1
    fi

    if [[ -z "$(expected_commit "$path" "$tag")" ]]; then
        echo "$name tag $tag is not available locally; fetching it." >&2
        fetch_tag "$path" "$tag"
    fi

    local expected actual
    expected="$(expected_commit "$path" "$tag")"
    actual="$(git -C "$ROOT_DIR/$path" rev-parse HEAD)"
    if [[ -z "$expected" ]]; then
        echo "$name tag $tag could not be resolved." >&2
        exit 1
    fi
    if [[ "$actual" != "$expected" ]]; then
        echo "$name submodule is at ${actual:0:12}, expected $tag." >&2
        echo "Run ./gradlew syncNativeSubmodules and commit the updated submodule pointer." >&2
        exit 1
    fi
}

sync_source() {
    local name="$1" path="$2" tag="$3" marker="$4"
    if ! git -C "$ROOT_DIR/$path" diff --quiet || ! git -C "$ROOT_DIR/$path" diff --cached --quiet; then
        echo "$name submodule has local changes; commit or discard them before switching tags." >&2
        exit 1
    fi
    fetch_tag "$path" "$tag"
    git -C "$ROOT_DIR/$path" -c advice.detachedHead=false checkout --detach "refs/tags/$tag"
    verify_source "$name" "$path" "$tag" "$marker"
    echo "$name submodule synced to $tag" >&2
}

main() {
    local mode="${1:---verify}"
    case "$mode" in
        -h|--help)
            usage
            exit 0
            ;;
        --verify|--sync) ;;
        *)
            usage >&2
            exit 1
            ;;
    esac

    if ! command -v git >/dev/null 2>&1; then
        echo "Missing required tool: git" >&2
        exit 1
    fi

    local entry name path tag marker
    for entry in "${SOURCES[@]}"; do
        IFS='|' read -r name path tag marker <<<"$entry"
        ensure_registered "$path"
        init_submodule "$path" "$marker"
        if [[ "$mode" == "--sync" ]]; then
            sync_source "$name" "$path" "$tag" "$marker"
        else
            verify_source "$name" "$path" "$tag" "$marker"
        fi
    done
}

main "$@"
