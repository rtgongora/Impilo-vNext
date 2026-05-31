# Full Boot Deployment Execution Plan

## Preconditions

1. Workspace on `claude/staging-ux-orchestration-remediation-Yypyl` (or approved branch).
2. `bash scripts/build/build-full-vnext.sh` — required targets pass.
3. `bash scripts/build/build-full-vnext-images.sh` — required images built.
4. `bash scripts/guard/check-full-boot-runtime-completeness.sh` — reviewed (may be PARTIAL).
5. User types exactly: **`AUTHORIZE FULL BOOT PREVIEW DEPLOY`**

## Namespace policy

| Namespace | Purpose |
|-----------|---------|
| `impilo-preview` | **Protected slice** — do not destroy |
| `impilo-full-preview` | First full-boot attempt |

## Execution order

1. infrastructure (postgres, redis, kafka, minio)
2. identity_trust_policy (keycloak, envoy, tshepo-authz)
3. data_layer / registries (vito, varapi, tuso, zibo, ubomi)
4. event_backbone (kafka consumers)
5. platform_services (integration hub, notification, workflow)
6. domain_services (clinical, enterprise, data)
7. experience_layer (experience-bff, one-ui-shell)
8. observability (observability-service)

## Commands

```bash
# After authorization only:
bash scripts/deploy/full-boot-preview-deploy.sh
bash scripts/test/run-full-boot-smoke-tests.sh
bash scripts/guard/check-full-boot-runtime-completeness.sh
```

## Rollback

- Uninstall full boot only: `helm uninstall impilo-full-preview -n impilo-full-preview`
- Slice remains: `kubectl get pods -n impilo-preview`
