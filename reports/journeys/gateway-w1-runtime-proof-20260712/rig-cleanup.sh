#!/usr/bin/env bash
# Tear down the gateway W1 rig: gw-rig-* containers + the five rig jars + shell.
# NEVER touches msika-rig-* / vito-probe-* or any non-gw-rig process.
set -u
for c in gw-rig-keycloak gw-rig-redis gw-rig-pg gw-rig-kafka; do
  docker rm -f "$c" >/dev/null 2>&1 && echo "removed $c"
done
# Kill only java jars booted from THIS worktree's target dirs on the rig ports.
for port in 28084 28260 28201 28200 28460; do
  pid=$(ss -ltnp 2>/dev/null | grep ":$port " | grep -oP 'pid=\K[0-9]+' | head -1)
  if [ -n "${pid:-}" ] && ps -o args= -p "$pid" | grep -q "health-services-gateway-doctrine"; then
    kill "$pid" && echo "killed rig jar on :$port (pid $pid)"
  fi
done
# Shell smoke server (next start/dev on :3010) if it is ours.
pid=$(ss -ltnp 2>/dev/null | grep ":3010 " | grep -oP 'pid=\K[0-9]+' | head -1)
if [ -n "${pid:-}" ] && ps -o args= -p "$pid" | grep -qE "next (start|dev)"; then
  kill "$pid" && echo "killed shell smoke server on :3010 (pid $pid)"
fi
echo "cleanup done"
