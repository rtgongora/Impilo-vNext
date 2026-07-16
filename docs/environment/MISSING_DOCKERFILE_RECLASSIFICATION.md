# Missing Dockerfile Reclassification

> Former **~23 missing Dockerfile** findings from the legacy image build — reclassified below.

These are primarily **frontend workspaces** without `ui/<name>/Dockerfile`. They are **not** blocking full boot.

| Item | Path | Plane | Type | Previous finding | New strategy | Blocking | Reason | Next action |
|---|---|---|---|---|---|---|---|---|
| butano-web | ui/butano-web | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| costa-console | ui/costa-console | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| developer-console | ui/developer-console | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| inventory-web | ui/inventory-web | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| knowledge-admin | ui/knowledge-admin | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| ops-console | ui/ops-console | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| ops-docs | ui/ops-docs | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| oros-web | ui/oros-web | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| pct-web | ui/pct-web | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| pharmacy-web | ui/pharmacy-web | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| portal | ui/portal | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| self-service | ui/self-service | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| shared-ui | ui/shared-ui | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| support-console | ui/support-console | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |
| zibo-web | ui/zibo-web | experience | frontend_app | MISSING Dockerfile (legacy) | buildpacks | no | UI workspace not independently deployed — not a missing Dockerfile failure | No Dockerfile unless independent deploy required |

**Count:** 15 UI/buildpack workspaces reclassified as non-blocking.
