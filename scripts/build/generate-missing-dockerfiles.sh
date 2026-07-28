#!/usr/bin/env bash
# Generate standard Dockerfiles for Maven services missing one (batch remediation).
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

TEMPLATE='FROM eclipse-temurin:21-jre
RUN groupadd -r impilo && useradd -r -g impilo impilo
WORKDIR /app
COPY target/__MODULE__-*.jar app.jar
RUN chown -R impilo:impilo /app
USER impilo
EXPOSE __PORT__
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
'

created=0
for pom in services/*/pom.xml; do
  mod="$(basename "$(dirname "$pom")")"
  [[ "$mod" == "shared-core" ]] && continue
  [[ -f "services/$mod/Dockerfile" ]] && continue
  port=""
  port="$(python3 -c "
import yaml, pathlib
r=yaml.safe_load(pathlib.Path('docs/registry/services-registry.yaml').read_text())
for s in r.get('services',[]):
    if s.get('maven_module')=='$mod':
        print(s.get('default_http_port') or 8080)
        break
else:
    print(8080)
" 2>/dev/null || echo 8080)"
  echo "$TEMPLATE" | sed "s/__MODULE__/$mod/g; s/__PORT__/$port/g" > "services/$mod/Dockerfile"
  echo "Created services/$mod/Dockerfile (port $port)"
  created=$((created + 1))
done
echo "Created $created Dockerfiles"
