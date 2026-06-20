# Preview Deploy Report — targeted

- **Generated:** 2026-06-20T09:51:51Z
- **Status:** PASS
- **Branch:** claude/staging-ux-orchestration-remediation-Yypyl
- **HEAD commit:** `4a3c525e0461730c32f6f1649bf193fd2713133e` (4a3c525e)
- **Preview URL:** http://41.57.127.235
- **Namespace:** impilo-full-preview
- **Helm release:** impilo-full-preview (revision 86)
- **Runtime readiness:** 99/99 deployments ready
- **Public /health/version environment:** full-preview
- **Public /health/version commit:** `4a3c525e0461730c32f6f1649bf193fd2713133e`
- **Commit alignment:** OK

## Blast radius

- **change_class:** B
- **change_classes:** ['B']
- **full_boot_required:** False
- **targeted_deploy_allowed:** True
- **direct_services:** ['one-ui-shell']
- **expanded_services:** ['one-ui-shell']
- **images_to_build:** ['one-ui-shell']
- **pipeline_only:** change-safety,frontend,parity-web,security,static

**Note:** Targeted deploy does not assert FULL_ESTATE_PASS. Run `scripts/preview/full-boot.sh` before release promotion.

## health/version raw

```json
{"service":"experience-bff","environment":"full-preview","branch":"claude/staging-ux-orchestration-remediation-Yypyl","commit":"4a3c525e0461730c32f6f1649bf193fd2713133e","buildDate":"2026-06-20T09:24:28Z","status":"ok"}
```
