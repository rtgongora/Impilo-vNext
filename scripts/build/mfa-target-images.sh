#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

[[ -z "$(git status --porcelain --untracked-files=no)" ]] || {
  echo "REFUSING: tracked worktree changes must be committed before an immutable build" >&2; exit 65;
}
for command in docker mvn git jq curl; do
  command -v "$command" >/dev/null || { echo "missing command: $command" >&2; exit 69; }
done

COMMIT="$(git rev-parse HEAD)"
SHORT="$(git rev-parse --short=12 HEAD)"
TREE="$(git rev-parse HEAD^{tree})"
BRANCH="$(git branch --show-current)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
TAG="mfa-$SHORT"
REGISTRY="${REGISTRY:-127.0.0.1:5000}"
OUT="${MFA_IMAGE_MANIFEST:-$ROOT/.artifacts/mfa-images-$SHORT.json}"
LOG_DIR="${MFA_BUILD_LOG_DIR:-$ROOT/.artifacts/mfa-build-$SHORT}"
mkdir -p "$(dirname "$OUT")" "$LOG_DIR"

bash scripts/operator/registry-up.sh
ready=false
for _ in $(seq 1 30); do
  if curl -fsS "http://$REGISTRY/v2/" >/dev/null; then ready=true; break; fi
  sleep 2
done
[[ "$ready" == true ]] || { echo "registry did not become ready: $REGISTRY" >&2; exit 70; }

common_args=(
  --build-arg "SOURCE_COMMIT=$COMMIT"
  --build-arg "SOURCE_BRANCH=$BRANCH"
  --build-arg "SOURCE_TREE=$TREE"
  --build-arg SOURCE_DIRTY=false
  --build-arg "BUILD_DATE=$BUILD_DATE"
)

echo "BUILD source=$COMMIT tree=$TREE tag=$TAG"
mvn -q -f services/pom.xml -pl experience-bff,tshepo-authz-service,tshepo-audit-service \
  -am package -DskipTests >"$LOG_DIR/maven.log" 2>&1 &
maven_pid=$!
docker build "${common_args[@]}" -t "$REGISTRY/impilo/keycloak:$TAG" infra/keycloak \
  >"$LOG_DIR/keycloak.log" 2>&1 &
keycloak_pid=$!
wait "$maven_pid" || { tail -80 "$LOG_DIR/maven.log"; exit 1; }

docker build "${common_args[@]}" --build-arg "CACHE_BUST=$COMMIT" \
  -t "$REGISTRY/impilo/experience-bff:$TAG" services/experience-bff >"$LOG_DIR/experience-bff.log" 2>&1 &
p1=$!
docker build "${common_args[@]}" -t "$REGISTRY/impilo/tshepo-authz-service:$TAG" \
  services/tshepo-authz-service >"$LOG_DIR/tshepo-authz-service.log" 2>&1 &
p2=$!
docker build "${common_args[@]}" -t "$REGISTRY/impilo/tshepo-audit-service:$TAG" \
  services/tshepo-audit-service >"$LOG_DIR/tshepo-audit-service.log" 2>&1 &
p3=$!
docker build "${common_args[@]}" --build-arg "CACHE_BUST=$COMMIT" \
  --build-arg NEXT_PUBLIC_BFF_URL= --build-arg NEXT_PUBLIC_API_GATEWAY_URL= \
  -f ui/one-ui-shell/Dockerfile -t "$REGISTRY/impilo/one-ui-shell:$TAG" . \
  >"$LOG_DIR/one-ui-shell.log" 2>&1 &
p4=$!

wait "$keycloak_pid" || { tail -80 "$LOG_DIR/keycloak.log"; exit 1; }
for entry in "$p1:experience-bff" "$p2:tshepo-authz-service" "$p3:tshepo-audit-service" "$p4:one-ui-shell"; do
  pid="${entry%%:*}"; name="${entry#*:}"
  wait "$pid" || { tail -100 "$LOG_DIR/$name.log"; exit 1; }
done

names=(keycloak experience-bff tshepo-authz-service tshepo-audit-service one-ui-shell)
printf '{}\n' >"$OUT"
for name in "${names[@]}"; do
  image="$REGISTRY/impilo/$name:$TAG"
  docker push "$image" >"$LOG_DIR/push-$name.log" 2>&1
  digest="$(docker image inspect "$image" --format '{{json .RepoDigests}}' |
    jq -er --arg repository "$REGISTRY/impilo/$name" '.[] | select(startswith($repository + "@")) | split("@")[1]')"
  jq --arg name "$name" --arg repository "$REGISTRY/impilo/$name" --arg digest "$digest" \
    '.images[$name] = {repository: $repository, digest: $digest}' "$OUT" >"$OUT.next"
  mv "$OUT.next" "$OUT"
done
jq --arg commit "$COMMIT" --arg tree "$TREE" --arg branch "$BRANCH" --arg buildDate "$BUILD_DATE" '
  .source = {commit: $commit, tree: $tree, branch: $branch, dirty: false, buildDate: $buildDate}
' "$OUT" >"$OUT.next"
mv "$OUT.next" "$OUT"
echo "MFA_IMAGES_OK manifest=$OUT"
cat "$OUT"
