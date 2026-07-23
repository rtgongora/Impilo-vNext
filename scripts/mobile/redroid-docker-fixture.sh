#!/usr/bin/env bash
# redroid-docker-fixture.sh — the standing Android-in-container fixture,
# docker-managed on the preview LB host.
#
# Why docker and not the k8s chart: the same image boots Android 13 flawlessly
# under docker (proven repeatedly), but k3s/containerd kills init at or shortly
# after exec (pty-close SIGHUP with tty, exit 255 without) — findings recorded
# in docs/runbooks/redroid-android-sandbox-runbook.md. Docker's restart policy
# gives the same "runs like redis" permanence (daemon starts on boot, container
# auto-restarts); precedent: the local registry (127.0.0.1:5000) runs the same
# way on this host.
#
# usage: redroid-docker-fixture.sh <start|stop|status|recreate>
set -euo pipefail

NAME="${REDROID_NAME:-impilo-redroid}"
IMAGE="${REDROID_IMAGE:-127.0.0.1:5000/redroid/redroid:13.0.0-k3s}"
# Host-local only (loopback bind): registered in docs/runbooks/port-allocation.md
PORT="${REDROID_PORT:-15555}"
VOLUME="${REDROID_VOLUME:-impilo-redroid-data}"
WIDTH="${REDROID_WIDTH:-1080}"
HEIGHT="${REDROID_HEIGHT:-2340}"
DPI="${REDROID_DPI:-440}"

case "${1:-}" in
  start)
    if docker ps --format '{{.Names}}' | grep -qx "$NAME"; then
      echo "$NAME already running"; exit 0
    fi
    docker rm "$NAME" >/dev/null 2>&1 || true
    # -t is required: Android init needs a console; docker keeps the detached
    # pty open (unlike the k3s CRI shim). --restart unless-stopped = standing
    # fixture across daemon/host restarts.
    docker run -dt --privileged --restart unless-stopped \
      --name "$NAME" \
      -p "127.0.0.1:${PORT}:5555" \
      -v "${VOLUME}:/data" \
      --add-host impilo.mohcc.gov.zw:10.50.1.67 \
      "$IMAGE" \
      "androidboot.redroid_gpu_mode=guest" \
      "androidboot.redroid_width=${WIDTH}" \
      "androidboot.redroid_height=${HEIGHT}" \
      "androidboot.redroid_dpi=${DPI}"
    echo "started $NAME (adb: 127.0.0.1:${PORT}); Android cold boot takes 1-3 min"
    ;;
  stop)     docker stop "$NAME" ;;
  status)   docker ps -a --filter "name=^${NAME}$" --format '{{.Names}} {{.Status}}' ;;
  recreate) docker rm -f "$NAME" >/dev/null 2>&1 || true; exec "$0" start ;;
  *) echo "usage: $0 <start|stop|status|recreate>" >&2; exit 2 ;;
esac
