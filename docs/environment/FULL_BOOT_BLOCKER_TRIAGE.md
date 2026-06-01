# Full Boot Blocker Triage

> Updated: 2026-06-01T03:36:42.766537+00:00

> Image doctrine: missing Dockerfile ≠ failure. See `RUNTIME_IMAGE_STRATEGY_DOCTRINE.md`.

## Former missing-Dockerfile reclassification (UI / optional)

| Service | Plane | Reclass | Strategy | Runtime required |
|---------|-------|---------|----------|------------------|
| `butano-web` | experience | buildpack-candidate | buildpacks | False |
| `costa-console` | experience | buildpack-candidate | buildpacks | False |
| `developer-console` | experience | buildpack-candidate | buildpacks | False |
| `ehr` | experience | buildpack-candidate | buildpacks | False |
| `inventory-web` | experience | buildpack-candidate | buildpacks | False |
| `knowledge-admin` | experience | buildpack-candidate | buildpacks | False |
| `msika-flow-ops` | experience | buildpack-candidate | buildpacks | False |
| `msika-flow-portal` | experience | buildpack-candidate | buildpacks | False |
| `msika-flow-vendor` | experience | buildpack-candidate | buildpacks | False |
| `msika-web` | experience | buildpack-candidate | buildpacks | False |
| `mushex-finance-console` | experience | buildpack-candidate | buildpacks | False |
| `mushex-ops-console` | experience | buildpack-candidate | buildpacks | False |
| `mushex-payer-portal` | experience | buildpack-candidate | buildpacks | False |
| `ops-console` | experience | buildpack-candidate | buildpacks | False |
| `ops-docs` | experience | buildpack-candidate | buildpacks | False |
| `oros-web` | experience | buildpack-candidate | buildpacks | False |
| `pct-web` | experience | buildpack-candidate | buildpacks | False |
| `pharmacy-web` | experience | buildpack-candidate | buildpacks | False |
| `portal` | experience | buildpack-candidate | buildpacks | False |
| `self-service` | experience | buildpack-candidate | buildpacks | False |
| `shared-ui` | experience | buildpack-candidate | buildpacks | False |
| `support-console` | experience | buildpack-candidate | buildpacks | False |
| `zibo-web` | experience | buildpack-candidate | buildpacks | False |

## Helm deployability (batch 2026-05-31)

| Plane | Service | Type | Evidence | Fix | Priority | Status |
|-------|---------|------|----------|-----|----------|--------|
| *all required* | 22 services | helm_ready | `values-full-preview.yaml` + templates | Preflight/dry-run then authorized deploy | P0 | **fixed** (templates) |
| integration | `kafka` | infrastructure | `templates/kafka.yaml` | Deploy with full boot | P0 | fixed |
| trust | `keycloak` | safe preview secret | `keycloak-preview-credentials` | Placeholder only | P1 | fixed |
| clinical | `hapi-fhir` | missing config | HAPI DB `hapi` | `postgres.initDatabases` + `postgres-init-databases` Job (idempotent) | P1 | **fixed** |
| experience | ingress | dependency order | Public host routes to slice | `ingress.enabled: false` in full boot; port-forward first | P1 | **fixed** (strategy) |

## Pre-deploy preparation (2026-06-01)

| Type | Evidence | Fix | Priority | Status |
|------|----------|-----|----------|--------|
| k3s image import | `sudo -n` not available in non-interactive agent shell | Run `import-full-vnext-images-k3s.sh` in VM terminal before authorized deploy | P0 | open (operator) |
| official infra images | All 6 pulled to Docker on VM | Import into k3s via script above | P1 | open (import) |

## Active blockers

| Plane | Service | Type | Evidence | Log | Fix | Priority | Status |
|-------|---------|------|----------|-----|-----|----------|--------|
| clinical | `butano-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `envoy` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| clinical | `fhir-gateway-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| clinical | `hapi-fhir` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| integration | `kafka` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `keycloak` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| integration | `minio` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| clinical | `pct-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `tshepo-audit-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `tshepo-authz-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `tshepo-consent-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `tshepo-identity-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| trust | `tshepo-keys-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| registry | `tuso-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| registry | `ubomi-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| registry | `varapi-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| registry | `vito-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
| registry | `zibo-service` | not_deployed | not in impilo-preview slice | `—` | Deploy in impilo-full-preview after authorization | P0 | open |
