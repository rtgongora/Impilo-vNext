#!/usr/bin/env bash
# Trap-cleanup for the gateway UI catch-up rig. Removes ONLY gw-e-rig-* containers and the java
# service processes this rig started (by port). Never touches other rigs' containers/ports.
set -u
docker rm -f gw-e-rig-pg gw-e-rig-redis >/dev/null 2>&1 || true
for port in 28692 28660 28760; do
  pid=$(lsof -ti tcp:"$port" 2>/dev/null || true)
  [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
done
echo "[rig] cleanup done (gw-e-rig-* containers + service ports 28692/28660/28760)."
