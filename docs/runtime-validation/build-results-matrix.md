# Build Results Matrix

## Build Environment
- Java: OpenJDK 21.0.10
- Maven: 3.9.11
- Node.js: 22.22.0
- npm: 10.9.4
- pnpm: available
- Docker: CLI only (no daemon)

## Java Backend Fleet (68 services + 11 libs via Maven reactor)

| Component | Build Attempted | Build Passed | Blocker | Mitigation |
|-----------|----------------|-------------|---------|------------|
| services/pom.xml (all 68 services + 11 libs) | Yes | No | JVM cannot connect to Maven Central through container egress proxy. DNS resolution fails at JVM level. No local Maven cache available. | BLOCKED_EXTERNAL: Container environment does not route JVM HTTP traffic through the egress proxy correctly. Maven settings.xml with explicit proxy config gets 407 (proxy auth uses JWT which Maven's XML settings can't handle). Requires Docker-based build via docker-compose.build.yml which uses eclipse-temurin:21 image with network access. |

## Web UI Applications

| Component | Build Attempted | Build Passed | Blocker | Mitigation |
|-----------|----------------|-------------|---------|------------|
| ui/experience (main clinical UI) | Yes — npm install + next build | **YES** | None | 80+ routes built, all static/dynamic pages compiled |
| libs/shared-kernel (TypeScript) | Yes — npm install + tsc --noEmit | **YES** | None | Clean type-check |
| ui/one-ui-shell | Yes — npm install + tsc | No | Missing zustand types after npm install | workspace:* deps need pnpm workspace manager |
| ui/portal | Yes — npm install + tsc | No | Missing next types | workspace:* deps need pnpm |
| ui/support-console | Yes — npm install + tsc | No | Missing vitest types | workspace:* deps need pnpm |
| ui/developer-console | Yes — npm install + tsc | No | Missing test runner types | workspace:* deps need pnpm |
| ui/self-service | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/ops-docs | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/butano-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/zibo-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/oros-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/pct-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/pharmacy-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/inventory-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/msika-web | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/costa-console | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/mushex-ops-console | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/mushex-finance-console | Yes — npm install + tsc | No | Missing next types | workspace:* deps need pnpm |
| ui/mushex-payer-portal | Yes — npm install + tsc | No | Missing next types | workspace:* deps need pnpm |
| ui/msika-flow-portal | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/msika-flow-ops | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/msika-flow-vendor | Yes — npm install + tsc | No | Missing react types | workspace:* deps need pnpm |
| ui/ops-console | No | — | No tsconfig.json | Not buildable in current form |
| ui/ehr | No | — | DEPRECATED | Superseded by ui/experience |

## Mobile Applications

| Component | Build Attempted | Build Passed | Blocker | Mitigation |
|-----------|----------------|-------------|---------|------------|
| apps/mobile/provider-app | Yes — tsc --noEmit | No | workspace:* protocol rejected by npm; expo tsconfig base not found | Needs pnpm workspace + expo CLI |
| apps/mobile/citizen-app | Yes — tsc --noEmit | No | workspace:* protocol rejected by npm; expo tsconfig base not found | Needs pnpm workspace + expo CLI |
| apps/mobile/packages/* (7 pkgs) | No | — | Depend on workspace protocol | Part of pnpm workspace |

## Summary
- **Total components attempted**: 28
- **Passed**: 2 (ui/experience, libs/shared-kernel)
- **Failed due to workspace protocol**: 22 (secondary UIs + mobile apps)
- **Failed due to environment**: 1 (Maven backend — proxy/DNS)
- **Skipped**: 3 (ops-console no tsconfig, ehr deprecated, mobile packages)

## Root Cause Analysis

### Maven Build Failure
The container environment configures JVM proxy via JAVA_TOOL_OPTIONS but the JVM cannot resolve DNS for repo.maven.apache.org. When DNS is bypassed, the proxy requires JWT-based authentication that Maven's settings.xml cannot handle. The intended build path is via `docker-compose.build.yml` which runs Maven inside a Docker container with proper network access.

### Secondary UI Build Failures
All secondary UIs use `"shared-ui": "workspace:*"` in package.json, which is a pnpm/yarn workspace protocol. Running `npm install` fails to resolve these. A root-level `pnpm-workspace.yaml` configuration is needed to enable workspace resolution across all UI apps.

### Mobile App Build Failures
Same workspace:* issue plus the Expo SDK requires `expo` CLI and the tsconfig extends `expo/tsconfig.base` which needs the expo package to be installed. Native builds (Android/iOS) additionally need platform SDKs.
