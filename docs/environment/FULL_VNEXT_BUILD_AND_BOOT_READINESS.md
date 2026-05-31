# Full vNext Build and Boot Readiness

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

## Source of truth

- Registry: `docs/registry/services-registry.yaml`
- Classification: `config/full-boot-service-classification.yml`
- Catalog: `docs/architecture/FULL_VNEXT_SERVICE_CATALOG.md`

## Preview types

| Type | Namespace | Meaning |
|------|-----------|---------|
| Slice | `impilo-preview` | Thin shell + BFF + postgres + redis |
| Full boot | `impilo-full-preview` | Authorized full architecture attempt |

Do not call slice preview "full vNext" until `FULL_BOOT_PASS`.
