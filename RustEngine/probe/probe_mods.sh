#!/usr/bin/env bash
# Which registry mods fit this engine: each mod of candidates.txt that falls to this shard is
# mixed alone into the patched engine source (as upstream's packaging/android/native_build.sh
# does it) and type-checked for Android with `cargo check`. Nothing is linked or packaged.
#
#   probe_mods.sh SHARD SHARDS RESULTS
#
# RESULTS gets a line a mod: "OK id" or "FAIL id: where it failed". The engine source must be
# fetched already (tools/build_engine.py --fetch-only).
set -uo pipefail
shard=$1
shards=$2
results=$3
here=$(cd "$(dirname "$0")" && pwd)
module=$(dirname "$here")
source=$module/.engine-src
abi=arm64-v8a

git -C "$source" add -A
git -C "$source" -c user.name="Rust Client" -c user.email=rust-client@users.noreply.github.com \
  commit -q --allow-empty -m "Rust Client"
commit=$(git -C "$source" rev-parse HEAD)

mixins_rev=$(grep -m1 '^MIXINS_REV=' "$source/packaging/android/native_build.sh" | cut -d= -f2)
min_sdk=$(grep -m1 '^MIN_SDK = ' "$source/packaging/android/build_apk.py" | sed -E 's/^MIN_SDK = ([0-9]+).*/\1/')
tools=$HOME/.cache/probe-mixins
cargo install --locked --git https://github.com/horizon-rust/horizon-mixins --rev "$mixins_rev" \
  --root "$tools" horizon-mixins-cli || { echo "FAIL (all): horizon-mixins did not install" > "$results"; exit 1; }

build=$HOME/probe-build
mkdir -p "$build"
manifest=crates/horizon-android/Cargo.toml
lib_attr='#![cfg_attr(not(target_os = "android"), forbid(unsafe_code))]'
: > "$results"

# One line of what a log says went wrong: the first errors, each with where.
errors() {
  sed -E 's/\x1b\[[0-9;]*m//g' "$1" | awk '
    /^error/ && !/^error: could not compile/ && !/^error: aborting/ { if (n < 3) { held = $0; n++ } next }
    held != "" && /-->/ { sub(/^ *--> */, ""); printf "%s (%s) || ", held, $0; held = ""; next }
    held != "" { printf "%s || ", held; held = "" }
    END { if (held != "") printf "%s", held }' | cut -c1-600
}

probe() {
  local mod=$1 name=${1%%@*} work
  work=$(mktemp -d)
  if ! python3 "$module/tools/native_mods.py" "$mod" "$work/bundles" > "$work/fetch.log" 2>&1; then
    echo "FAIL $mod: download: $(tail -1 "$work/fetch.log" | cut -c1-300)"; rm -rf "$work"; return
  fi
  mkdir "$work/source" "$work/mods"
  git -C "$source" archive --format=tar HEAD | tar -x -C "$work/source"
  find "$work/source" -type l -delete
  echo "$commit" > "$work/source/.horizon-revision"
  sed -i 's|^path = "../horizon-client/src/android_lib.rs"$|path = "plan-stub.rs"|' "$work/source/$manifest"
  : > "$work/source/crates/horizon-android/plan-stub.rs"
  grep -vxF "$lib_attr" "$work/source/crates/horizon-client/src/lib.rs" > "$work/lib.rs"
  cp "$work/lib.rs" "$work/source/crates/horizon-client/src/lib.rs"
  cp -r "$work/bundles/$name" "$work/mods/$name"
  sed -i -E "s|^horizon_revision = \"[0-9a-f]*\"|horizon_revision = \"$commit\"|" \
    "$work/mods/$name/horizon-mixin.toml"
  if ! python3 "$source/packaging/android/native_mod_adapt.py" "$work/source" "$work/mods" > "$work/adapt.log" 2>&1; then
    echo "FAIL $mod: adapt: $(tail -1 "$work/adapt.log" | cut -c1-300)"; rm -rf "$work"; return
  fi
  cargo fetch --locked --manifest-path "$work/source/Cargo.toml" > /dev/null 2>&1
  if ! "$tools/bin/horizon-mixins" apply --workspace "$work/source" --mods "$work/mods" \
      --out "$work/stage" > "$work/mix.log" 2>&1; then
    echo "FAIL $mod: mixin: $(sed -E 's/\x1b\[[0-9;]*m//g' "$work/mix.log" | tail -3 | tr '\n' ' ' | cut -c1-500)"
    rm -rf "$work"; return
  fi
  rsync -r --checksum --delete --exclude /target/ --exclude "/$manifest" \
    --exclude /crates/horizon-android/plan-stub.rs "$work/stage/" "$build/"
  git -C "$source" show "HEAD:$manifest" > "$work/manifest"
  cmp -s "$work/manifest" "$build/$manifest" || cp "$work/manifest" "$build/$manifest"
  if (cd "$build" && CARGO_TERM_COLOR=never cargo ndk -t "$abi" --platform "$min_sdk" \
      check -p horizon-android) > "$work/check.log" 2>&1; then
    echo "OK $mod"
  else
    local why
    why=$(errors "$work/check.log")
    [ -n "$why" ] || why=$(tail -3 "$work/check.log" | tr '\n' ' ' | cut -c1-400)
    echo "FAIL $mod: compile: $why"
  fi
  rm -rf "$work"
}

at=0
grep -v '^[[:space:]]*#' "$here/candidates.txt" | awk 'NF { print $1 }' | while read -r mod; do
  if [ $((at % shards)) -eq "$shard" ]; then
    echo "::group::$mod"
    began=$(date +%s)
    line=$(probe "$mod" < /dev/null)
    echo "$line ($(( $(date +%s) - began )) s)"
    echo "$line" >> "$results"
    echo "::endgroup::"
  fi
  at=$((at + 1))
done
echo "probe: shard $shard of $shards done"
cat "$results"
