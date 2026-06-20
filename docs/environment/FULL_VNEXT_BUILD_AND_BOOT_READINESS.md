# Full vNext Build and Boot Readiness

## Runtime image strategy (canonical)

Every required runtime component must have a **repeatable image strategy** — not necessarily a Dockerfile.

See **[RUNTIME_IMAGE_STRATEGY_DOCTRINE.md](RUNTIME_IMAGE_STRATEGY_DOCTRINE.md)**.

| Strategy | Build command |
|----------|----------------|
| `dockerfile` | `docker build` via `bash scripts/build/build-full-vnext-images.sh` |
| `shared-dockerfile-template` / `jib` | `bash scripts/build/build-full-vnext.sh` then `build-runtime-image-from-jar.sh <service>` or Jib |
| `official-upstream-image` / `official-helm-chart` | Validate ref; no local `impilo/` build |
| `not-required-*` | Skipped with documented reason |

**Doctrine:** Dockerfile is not the doctrine. Repeatable runtime image strategy is the doctrine.

**Failure rule:** blocking only when a **required** runtime service lacks a valid strategy or its strategy build fails.

## Commands

```bash
node scripts/full-boot/generate-full-boot-artifacts.mjs
bash scripts/build/discover-build-targets.sh
bash scripts/build/build-full-vnext.sh
bash scripts/build/build-full-vnext-images.sh
bash scripts/guard/check-full-boot-runtime-completeness.sh
bash scripts/guard/check-doctrine-compliance.sh
bash scripts/full-boot/generate-blocker-triage.sh
```

### Preview deploy entrypoints (operator)

See **[PREVIEW_DEPLOY_OPERATOR_GUIDE.md](PREVIEW_DEPLOY_OPERATOR_GUIDE.md)** for targeted vs full-boot workflows.

```bash
bash scripts/preview/explain-blast-radius.sh      # dry-run: what would rebuild
bash scripts/preview/targeted-deploy.sh --dry-run # affected services only
bash scripts/preview/full-boot.sh                 # release-quality full estate
```

Audit references: `docs/audits/preview-full-boot-pipeline-truth.md`, `preview-blast-radius-strategy.md`, `preview-deploy-speedup-plan.md`.

## Source of truth

- Registry: `docs/registry/services-registry.yaml`
- Classification: `config/full-boot-service-classification.yml`
- Container matrix: `docs/environment/FULL_CONTAINERIZATION_MATRIX.md`

## Preview types

| Type | Namespace | Meaning |
|------|-----------|---------|
| Slice | `impilo-preview` | Thin shell + BFF + postgres + redis |
| Full boot | `impilo-full-preview` | Authorized full architecture attempt |

Do not call slice preview "full vNext" until `FULL_BOOT_PASS`.
