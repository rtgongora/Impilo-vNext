#!/usr/bin/env bash
# Tear down all gw2c-rig-* containers and any rig-launched jars. Safe to run repeatedly.
set -u
echo "[cleanup] removing gw2c-rig containers…"
docker rm -f gw2c-rig-pg gw2c-rig-redis gw2c-rig-kafka >/dev/null 2>&1 || true
echo "[cleanup] stopping rig jars…"
pkill -f 'daidzai-service-0.1.0-SNAPSHOT.jar --server.port=28392' >/dev/null 2>&1 || true
pkill -f 'experience-bff-0.1.0-SNAPSHOT.jar --server.port=28462' >/dev/null 2>&1 || true
echo "[cleanup] done."
