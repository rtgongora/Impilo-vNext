# Current Slice Preview Baseline

> **This is a slice preview, not full vNext.** Full boot completeness requires
> `scripts/guard/check-full-boot-runtime-completeness.sh` to report `FULL_BOOT_PASS`.

## Summary

| Item | Value |
|------|-------|
| Namespace | `impilo-preview` |
| Preview URL | http://41.57.127.235 |
| Helm release | `impilo-preview` (chart `impilo-vnext-0.1.0`) |
| Branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Live commit (`/health/version`) | `5a58424d8c2621abbc589ca70e8f5f61c87527f2` |
| Workspace HEAD (snapshot time) | `2bed7ab6` |
| Environment | `preview` |
| Status | `ok` |

## Deployed workloads (slice)

| Workload | Image | Port | Ready |
|----------|-------|------|-------|
| `one-ui-shell` | `impilo/one-ui-shell:preview` | 3000 | 1/1 |
| `experience-bff` | `impilo/experience-bff:preview` | 8160 | 1/1 |
| `postgres` | `postgres:16-alpine` | 5432 | 1/1 |
| `redis` | `redis:7-alpine` | 6379 | 1/1 |

**Not deployed in slice:** identity (Keycloak), policy (OPA/TSHEPO ext_authz), Kafka, domain microservices, registries (VITO/VARAPI/TUSO/…), observability stack, mobile runtime config, etc.

## Evidence artifacts

- `reports/full-boot/health-version-snapshot.json`
- `reports/full-boot/current-preview-k8s-before-full-boot.txt`
- `reports/full-boot/current-preview-helm-list.txt`
- `reports/full-boot/current-preview-values-before-full-boot.yaml`
- `reports/full-boot/current-preview-manifest-before-full-boot.yaml`

## Rollback notes

- Do **not** replace or uninstall `impilo-preview` during full-boot preparation.
- Full-boot attempts use namespace **`impilo-full-preview`** with explicit authorization:
  `AUTHORIZE FULL BOOT PREVIEW DEPLOY`.
- To restore slice after accidental full-boot chart overlap: `helm upgrade impilo-preview deploy/helm/impilo-vnext -n impilo-preview -f deploy/helm/impilo-vnext/values-preview.yaml`.

## Smoke status

Slice smoke: experience shell + BFF `/health/version` OK at snapshot time. Full-boot smoke not run (deploy not authorized).
