#!/usr/bin/env bash
# Trap-cleanup for the gateway BE-additions rig. Removes ONLY gw-be-rig-* containers and the
# java service processes this rig started (by port). Never touches other rigs' containers/ports.
set -u
docker rm -f gw-be-rig-pg gw-be-rig-redis >/dev/null 2>&1 || true
for port in 28492 28560 28562; do
  pid=$(lsof -ti tcp:"$port" 2>/dev/null || true)
  [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
done
echo "[rig] cleanup done (gw-be-rig-* containers + service ports 28492/28560/28562)."
