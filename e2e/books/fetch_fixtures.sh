#!/usr/bin/env bash
# Downloads the real (non-synthetic) Gutenberg fixture books into e2e/books/fetched/ (gitignored —
# never committed) and verifies each one's sha256 against the value recorded in SOURCES.md on the
# day it was fetched. Refuses (deletes the bad file, exits non-zero) on a mismatch. Skips a file
# already present with the right hash, so re-runs are cheap and idempotent.
#
# Usage: e2e/books/fetch_fixtures.sh
#
# If Gutenberg is unreachable, each failing download is reported and the script exits non-zero
# after attempting every entry (it does not stop at the first failure).
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/fetched"
mkdir -p "$OUT"

# name | url | sha256 (as observed 2026-11-07; see SOURCES.md)
FIXTURES="
alice-illustrated.epub|https://www.gutenberg.org/ebooks/28885.epub3.images|69c757719102f175303e7e8b8e508f1466e9aa78ddd9afb05609d00150699b3d
rur-cs.epub|https://www.gutenberg.org/ebooks/13083.epub3.images|4ee88d1ee003543e89c6054c5953b70b16516144f79210bd5ecd60b453910663
iliad-el.epub|https://www.gutenberg.org/ebooks/36248.epub3.images|3ad87d8902119d6c27e3a1ed0755bfaa858700eb566402a61a5507fe87355957
don-quixote-es.epub|https://www.gutenberg.org/ebooks/2000.epub3.images|9a40acbbc8e640fa0fdc51a057ab3905676d404c4ba44090184098d4d8244fc0
dream-of-red-chamber-zh.epub|https://www.gutenberg.org/ebooks/24264.epub3.images|b387660f0a27441ab6f724b0e3a0da30d4776e90158b9aad9e1f36b518ef3d21
rashomon-ja.epub|https://www.gutenberg.org/ebooks/1982.epub3.images|090f520a4178b3a5575952728e5da718cc5e631de5255130d5ccd6608d11ed94
gutenberg-classic.mobi|https://www.gutenberg.org/ebooks/11.kindle.noimages|c6de8f83459904177eac2623aa653ef57483ed6968078c8fc3d3171f20e06408
"

sha256_of() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
  else sha256sum "$1" | awk '{print $1}'
  fi
}

fail=0
for line in $FIXTURES; do
  name="${line%%|*}"; rest="${line#*|}"
  url="${rest%%|*}"; expected="${rest#*|}"
  dest="$OUT/$name"

  if [ -f "$dest" ]; then
    have="$(sha256_of "$dest")"
    if [ "$have" = "$expected" ]; then
      echo "SKIP  $name (already present, sha256 matches)"
      continue
    fi
    echo "STALE $name (hash changed on disk, re-downloading)"
    rm -f "$dest"
  fi

  echo "FETCH $name <- $url"
  if ! curl -sL --fail --max-time 120 -o "$dest.tmp" "$url"; then
    echo "FAIL  $name: download failed (network/Gutenberg unreachable?)"
    rm -f "$dest.tmp"
    fail=1
    continue
  fi

  got="$(sha256_of "$dest.tmp")"
  if [ "$got" != "$expected" ]; then
    echo "FAIL  $name: sha256 mismatch (got $got, expected $expected) — refusing, deleting"
    rm -f "$dest.tmp"
    fail=1
    continue
  fi
  mv "$dest.tmp" "$dest"
  echo "OK    $name  sha256=$got"
done

exit $fail
