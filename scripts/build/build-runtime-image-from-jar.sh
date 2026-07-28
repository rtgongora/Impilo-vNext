#!/usr/bin/env bash
# Build impilo/<service> from pre-built JAR using shared JRE template (no Maven-in-Docker).
set -euo pipefail
SERVICE_ID="${1:?service id}"
TAG_EXTRA="${2:-}"
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"

PORT="${APP_PORT:-8080}"
python3 -c "
import yaml, pathlib
for e in yaml.safe_load(pathlib.Path('config/full-boot-service-classification.yml').read_text())['classifications']:
    if e['id']=='$SERVICE_ID' and e.get('default_http_port'):
        print(e['default_http_port']); break
else:
    import re
    p=pathlib.Path('docs/registry/services-registry.yaml').read_text()
    m=re.search(r'id: $SERVICE_ID[\\s\\S]*?default_http_port: (\\d+)', p)
    print(m.group(1) if m else 8080)
" 2>/dev/null > /tmp/impilo-port-$$ || echo 8080 > /tmp/impilo-port-$$
PORT="$(cat /tmp/impilo-port-$$ 2>/dev/null || echo 8080)"
rm -f /tmp/impilo-port-$$

JAR="$(ls "services/${SERVICE_ID}"/target/*.jar 2>/dev/null | grep -v original | head -1)"
[[ -n "$JAR" ]] || { echo "[estate] FAIL no JAR in services/${SERVICE_ID}/target - run mvn package first"; exit 1; }

# Runtime image truth: refuse to package a stale JAR into an image. If any tracked source
# file under the module's src/ is newer than the built JAR, the JAR is stale and the image
# would silently ship old code (the COPY target/*.jar stale-JAR risk).
if [[ "${ALLOW_STALE_JAR:-0}" != "1" && -d "services/${SERVICE_ID}/src" ]]; then
  if find "services/${SERVICE_ID}/src" -type f -newer "$JAR" 2>/dev/null | grep -q .; then
    echo "[estate] FAIL stale JAR for ${SERVICE_ID}: source under services/${SERVICE_ID}/src is newer than ${JAR##*/}."
    echo "[estate]      Rebuild with: (cd services && ./mvnw -q package -pl ${SERVICE_ID} -am -DskipTests)"
    echo "[estate]      Override only for emergencies with ALLOW_STALE_JAR=1 (not valid for full estate)."
    exit 1
  fi
fi

SOURCE_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
SOURCE_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

# Git-commit stale-JAR guard (complements the mtime check above): the jar stamps its
# source commit via git-commit-id-maven-plugin (BOOT-INF/classes/git.properties). If
# that commit != HEAD, the jar is from a different checkout — refuse under strict mode.
if [[ "$SOURCE_COMMIT" != "unknown" ]] && command -v unzip >/dev/null 2>&1; then
  JAR_COMMIT="$(unzip -p "$JAR" BOOT-INF/classes/git.properties 2>/dev/null | sed -n 's/^git.commit.id.abbrev=//p' | tr -d '\r')"
  if [[ -n "$JAR_COMMIT" && "$SOURCE_COMMIT" != "$JAR_COMMIT"* ]]; then
    echo "[estate] WARN stale JAR for ${SERVICE_ID}: jar built from ${JAR_COMMIT} but HEAD is ${SOURCE_COMMIT:0:7}."
    [[ "${IMPILO_STRICT_JAR_FRESHNESS:-0}" == "1" ]] && { echo "[estate] ABORT (IMPILO_STRICT_JAR_FRESHNESS=1)"; exit 1; }
  fi
fi

TMP="$(mktemp -d)"
cp "$JAR" "$TMP/app.jar"
TAG_SHA="$(full_boot_image_tag)"
IMG="impilo/${SERVICE_ID}"
docker build -t "${IMG}:preview" -t "${IMG}:${TAG_SHA}" \
  --build-arg "APP_PORT=${PORT}" \
  --label "org.opencontainers.image.revision=${SOURCE_COMMIT}" \
  --label "org.opencontainers.image.version=${TAG_SHA}" \
  --label "org.opencontainers.image.created=${BUILD_DATE}" \
  --label "org.impilo.source.branch=${SOURCE_BRANCH}" \
  -f scripts/build/templates/impilo-jre-runtime.Dockerfile \
  "$TMP"
rm -rf "$TMP"
echo "PASS $IMG (shared-template)"
