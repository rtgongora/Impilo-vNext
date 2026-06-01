# Full Boot Deployability Fix Plan

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Target namespace:** `impilo-full-preview`  
**Slice preserved:** `impilo-preview` (must not be modified by full-boot scripts)

## Current image readiness

| Metric | Status |
|--------|--------|
| Required runtime images | 22 |
| Required-only image build | 22 pass / 0 fail |
| Full image build | 94 pass / 0 fail / 23 legitimate skips |
| Missing required image strategies | 0 |

Images are **ready** for first full-boot attempt.

## Required runtime services

**Count:** 22 (`required_full_boot` in `config/full-boot-service-classification.yml`)

Experience: `one-ui-shell`, `experience-bff`  
Infrastructure: `postgres`, `redis`, `kafka`, `keycloak`, `envoy`, `minio`, `hapi-fhir`  
Trust: `tshepo-authz-service`, `tshepo-identity-service`, `tshepo-consent-service`, `tshepo-audit-service`, `tshepo-keys-service`  
Registry: `vito-service`, `varapi-service`, `tuso-service`, `ubomi-service`, `zibo-service`  
Clinical: `butano-service`, `fhir-gateway-service`, `pct-service`

## Helm deployability status

| Area | Status |
|------|--------|
| Chart | `deploy/helm/impilo-vnext` |
| Full-boot values | `values-full-preview.yaml` |
| Domain microservices | `templates/microservice.yaml` (loop) |
| Infra templates | postgres, redis, kafka, keycloak, envoy, minio, hapi-fhir |
| Audit script | `scripts/full-boot/audit-helm-deployability.py` |
| Matrix | `docs/environment/FULL_HELM_DEPLOYABILITY_MATRIX.md` |

After this batch, required services should classify as **helm_ready** in the deployability matrix (no placeholder Deployments).

## First full boot attempt strategy

1. Run `bash scripts/deploy/full-boot-preview-deploy.sh --preflight`
2. Run `bash scripts/deploy/full-boot-preview-deploy.sh --dry-run`
3. Build/import images: `build-full-vnext-images.sh --required-only` + `import-full-vnext-images-k3s.sh`
4. User authorizes with phrase `AUTHORIZE FULL BOOT PREVIEW DEPLOY`
5. Deploy only to `impilo-full-preview` via `values-full-preview.yaml`
6. Run `scripts/test/run-full-boot-smoke-tests.sh` and completeness gate

Deploy order (Helm applies all resources; startup order enforced by probes and dependencies):

1. postgres, redis  
2. kafka, keycloak, minio  
3. hapi-fhir  
4. trust + registry + clinical microservices  
5. experience-bff, one-ui-shell, envoy, ingress  

## Risks

- **Resource pressure:** 22+ workloads on single-node k3s may cause Pending/OOM — tune requests or staged rollout.
- **Keycloak/HAPI slow start:** long startup probes; first deploy may exceed timeout without `--timeout` bump.
- **Kafka KRaft single-node:** preview-only; not production topology.
- **Ingress host:** slice may still own public ingress at `41.57.127.235`; full-boot health may need port-forward until separate ingress host is configured.
- **Database schema:** services expect Flyway migrations; first boot may fail until DB init jobs or manual migration are added.

## Blockers (pre-deploy)

| ID | Blocker | Priority |
|----|---------|----------|
| B-DEP-001 | Full boot not deployed yet | P0 — expected |
| B-DEP-002 | Runtime completeness `FULL_BOOT_PARTIAL` until pods healthy in `impilo-full-preview` | P0 — expected |
| B-DEP-003 | Public `/health/version` may route to slice not full-boot | P1 — ingress routing |
| B-DEP-004 | HAPI DB `hapi` database may need postgres init | P1 — first boot |

See `docs/environment/FULL_BOOT_BLOCKER_TRIAGE.md` for classified deployability items.

## Commands

```bash
bash scripts/deploy/full-boot-preview-deploy.sh --preflight
bash scripts/deploy/full-boot-preview-deploy.sh --dry-run
bash scripts/guard/check-full-boot-runtime-completeness.sh
```

Real deploy (explicit approval only):

```bash
bash scripts/deploy/full-boot-preview-deploy.sh
```
