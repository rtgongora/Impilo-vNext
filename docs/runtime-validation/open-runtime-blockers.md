# Open Runtime Blockers

## Blocker 1: No Docker Daemon
- **Severity**: CRITICAL
- **Impact**: Blocks runtime boot, steel threads, eventing proof, and wire compliance — all phases that need live services
- **Evidence**: `docker ps` returns "failed to connect to the docker API at unix:///var/run/docker.sock"
- **Affects**: ALL runtime validation phases (2-4, 6)
- **Next Action**: Run validation suite in an environment with Docker daemon available. Use `./scripts/runtime-validation/run-all.sh`.

## Blocker 2: JVM Proxy/DNS Mismatch for Maven
- **Severity**: HIGH
- **Impact**: Blocks Maven reactor build of all 68 Java services + 11 libs
- **Evidence**: JVM resolves DNS directly (fails) while the container routes curl through an egress proxy (succeeds). Maven settings.xml with proxy auth gets 407 because the proxy uses JWT authentication that Maven's XML config cannot encode properly.
- **Affects**: Phase 1 (fleet build — Java components)
- **Next Action**: Build via `docker-compose.build.yml` which runs Maven in an eclipse-temurin:21 Docker container with its own network stack. For normal connected local builds, use Maven's default user cache (`~/.m2/repository`) rather than a repo-local cache on synced folders. For offline/runtime preparation, pre-warm the vendored cache on a machine with direct internet access: `cd services && mvn dependency:go-offline -Dmaven.repo.local=../vendor/m2/repository`

## Blocker 3: No pnpm Workspace Configuration
- **Severity**: MEDIUM
- **Impact**: All secondary UIs (20+) and mobile apps (2) fail type-check due to unresolved `workspace:*` dependencies
- **Evidence**: UIs declare `"shared-ui": "workspace:*"` in package.json but there's no root `pnpm-workspace.yaml`. npm install rejects the `workspace:*` protocol.
- **Affects**: Phase 1 (fleet build — web UIs), Phase 5 (app runtime checks)
- **Fixable**: YES — create root `pnpm-workspace.yaml` and use `pnpm install` instead of `npm install`
- **Next Action**: Create `/pnpm-workspace.yaml` with appropriate package patterns

## Blocker 4: No Expo/EAS Environment for Mobile Builds
- **Severity**: MEDIUM
- **Impact**: Cannot build native Android/iOS binaries
- **Evidence**: npm install fails on workspace:* protocol, expo tsconfig base not found
- **Affects**: Phase 5 (mobile app runtime checks)
- **Next Action**: Set up EAS Build pipeline or use local Expo development server with `expo start --web` for web preview

## Blocker 5: Support and Notification Services Missing from Runtime Compose
- **Severity**: LOW (for this validation wave)
- **Impact**: Steel threads 3 (support) and D (messaging) cannot execute against canonical runtime
- **Evidence**: docker-compose.runtime.yml includes 8 backend services but not support-service (8340) or notification-service (8111)
- **Affects**: Phase 3 (steel threads 3 and 4)
- **Next Action**: Add these services to docker-compose.runtime.yml or create integration compose profile

## Summary

| # | Blocker | Severity | Fixable in Repo | External Dependency |
|---|---------|----------|-----------------|---------------------|
| 1 | No Docker daemon | CRITICAL | No | Environment must provide Docker |
| 2 | JVM proxy/DNS | HIGH | No | Build via docker-compose.build.yml |
| 3 | No pnpm workspace | MEDIUM | **YES** | None |
| 4 | No Expo/EAS | MEDIUM | Partially | EAS account + SDK |
| 5 | Missing compose services | LOW | **YES** | None |
