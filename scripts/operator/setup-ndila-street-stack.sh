#!/usr/bin/env bash
# Provision Ndila Tier B street stack data (PMTiles + optional OSRM).
#
# Usage:
#   bash scripts/operator/setup-ndila-street-stack.sh [--compose-up]
#
# Environment:
#   NDILA_ZW_PMTILES_URL  — direct download URL for zimbabwe.pmtiles (required unless file exists)
#   REPO_PATH               — repo root (default: auto-detect)

set -euo pipefail

ROOT="${REPO_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
DATA_DIR="$ROOT/compose/ndila/data"
PMTILES="$DATA_DIR/zimbabwe.pmtiles"
COMPOSE_UP=false

for arg in "$@"; do
  case "$arg" in
    --compose-up) COMPOSE_UP=true ;;
    -h|--help)
      echo "Usage: bash scripts/operator/setup-ndila-street-stack.sh [--compose-up]"
      exit 0
      ;;
  esac
done

mkdir -p "$DATA_DIR"

if [[ ! -f "$PMTILES" ]]; then
  if [[ -z "${NDILA_ZW_PMTILES_URL:-}" ]]; then
    cat <<EOF
WARN: $PMTILES not found and NDILA_ZW_PMTILES_URL is unset.

Build or download a Zimbabwe PMTiles file (Protomaps, tilemaker, or OpenMapTiles pipeline)
and either:
  1. export NDILA_ZW_PMTILES_URL='https://…/zimbabwe.pmtiles' && bash $0
  2. copy the file to $PMTILES

Ndila will fall back to sovereign preview tiles until PMTiles are available.
EOF
  else
    echo "Downloading PMTiles to $PMTILES …"
    curl -fsSL "${NDILA_ZW_PMTILES_URL}" -o "$PMTILES"
    echo "Downloaded $(du -h "$PMTILES" | awk '{print $1}')"
  fi
else
  echo "PMTiles already present: $PMTILES ($(du -h "$PMTILES" | awk '{print $1}'))"
fi

if [[ "$COMPOSE_UP" == true ]]; then
  echo "Starting Ndila street stack (Martin) …"
  docker compose -f "$ROOT/compose/ndila/docker-compose.street-stack.yml" up -d ndila-martin
  echo "Martin health: http://localhost:3410/health"
  echo "Tile sample (when PMTiles loaded): http://localhost:3410/zimbabwe/6/32/35"
fi

cat <<EOF

Ndila service env (local / preview):
  NDILA_DEFAULT_TILES=OSM_OSRM
  NDILA_OSM_ENABLED=true
  NDILA_OSM_TILE_BASE_URL=http://ndila-martin:3000/zimbabwe/{z}/{x}/{y}
  NDILA_OSM_PROXY_THROUGH_NDILA=true

Optional routing (requires OSRM data + --profile routing):
  NDILA_OSRM_ENABLED=true
  NDILA_OSRM_BASE_URL=http://ndila-osrm:5000
  NDILA_DEFAULT_ROUTING=OSM_OSRM

Optional geocoding (Photon — separate import):
  NDILA_NOMINATIM_ENABLED=true
  NDILA_NOMINATIM_BASE_URL=http://ndila-photon:2322
  NDILA_NOMINATIM_ENGINE=photon
  NDILA_DEFAULT_GEOCODING=NOMINATIM
EOF
