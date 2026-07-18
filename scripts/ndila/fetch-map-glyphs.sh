#!/usr/bin/env bash
#
# Fetch self-hosted MapLibre glyph PBFs for the Impilo street map.
#
# Downloads the prebuilt OpenMapTiles font glyphs (SDF PBFs) and installs the
# Latin-script ranges for "Noto Sans Regular" and "Noto Sans Bold" into
# ui/one-ui-shell/public/map/glyphs/<fontstack>/<range>.pbf. The files are
# COMMITTED to the repo (self-hosted sovereignty — no CDN at runtime); this
# script exists to (re)generate them, e.g. to add ranges or fontstacks.
#
# Zimbabwe place/road names are Latin script (English/Shona/Ndebele), so only
# unicode ranges 0-1023 are needed: ~770 KB total across both fontstacks.
#
# Source: https://github.com/openmaptiles/fonts (OFL-licensed Noto Sans),
# release asset noto-sans.zip, laid out as "<fontstack>/<range>.pbf".

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEST_ROOT="${REPO_ROOT}/ui/one-ui-shell/public/map/glyphs"
RELEASE_URL="https://github.com/openmaptiles/fonts/releases/download/v2.0/noto-sans.zip"

FONTSTACKS=("Noto Sans Regular" "Noto Sans Bold")
RANGES=("0-255" "256-511" "512-767" "768-1023")

workdir="$(mktemp -d)"
trap 'rm -rf "${workdir}"' EXIT

echo "Downloading ${RELEASE_URL} ..."
curl -fsSL --retry 3 -o "${workdir}/noto-sans.zip" "${RELEASE_URL}"

total_bytes=0
for stack in "${FONTSTACKS[@]}"; do
  mkdir -p "${DEST_ROOT}/${stack}"
  for range in "${RANGES[@]}"; do
    unzip -oq "${workdir}/noto-sans.zip" "${stack}/${range}.pbf" -d "${workdir}/extract"
    src="${workdir}/extract/${stack}/${range}.pbf"
    if [[ ! -s "${src}" ]]; then
      echo "ERROR: extracted glyph file is missing or empty: ${stack}/${range}.pbf" >&2
      exit 1
    fi
    install -m 0644 "${src}" "${DEST_ROOT}/${stack}/${range}.pbf"
    size=$(stat -c%s "${DEST_ROOT}/${stack}/${range}.pbf")
    total_bytes=$((total_bytes + size))
    printf '  %-20s %-12s %6d bytes\n' "${stack}" "${range}.pbf" "${size}"
  done
done

echo "Installed $(( ${#FONTSTACKS[@]} * ${#RANGES[@]} )) glyph files into ${DEST_ROOT} ($(( total_bytes / 1024 )) KB total)."
