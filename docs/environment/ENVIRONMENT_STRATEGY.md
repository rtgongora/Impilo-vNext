# Environment Strategy

## 1. Remote Development Workspace

**Purpose:** Cursor Remote SSH development, builds, unit tests, image builds, troubleshooting, branch-based work.

**Runtime:** Ubuntu VM shell, Java 21, Maven, Node 20/npm, Docker, Git clone at `/opt/impilo/repos/Impilo-vNext`.

**Not for:** public end-user testing, production-like staging, real patient data, production secrets.

## 2. Dev Preview Sandbox

**Purpose:** Browser-accessible preview, expert-user validation, smoke tests, frontend/backend integration checks.

**Runtime:** Single-node k3s, Helm (`deploy/helm/impilo-vnext`), namespace `impilo-preview`, test-only config, synthetic data.

**Not for:** production, formal HA testing, real patient data, production secrets.

**MVP slice (initial):** Redis, Postgres, Experience BFF, One UI Shell, Traefik ingress. Keycloak and full service mesh deferred.

## 3. Future Formal Test/Staging

Documented only in [FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md](./FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md).

Separate infrastructure required — not implemented on this VM.
