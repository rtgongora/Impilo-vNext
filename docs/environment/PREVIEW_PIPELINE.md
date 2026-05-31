# Preview Pipeline (Human-Authorized)

## Intended workflow

```text
commit → push → GitHub Actions CI (automatic) → regression/change-safety gates
  → Cursor collects CI feedback (scripts/ci/collect-ci-feedback.sh)
  → user authorizes VM preview deploy
  → manual deploy (scripts/deploy/manual-authorized-preview-deploy.sh or Actions workflow)
  → post-deploy smoke tests → /health/version confirms deployed commit
```

## Rules

| Step | Automatic? | Notes |
|------|--------------|-------|
| CI on push | **Yes** | `.github/workflows/ci.yml` — no preview deploy |
| Preview deploy | **No** | User must authorize explicitly |
| CI feedback in Cursor | **Agent duty** | Run `collect-ci-feedback.sh` after push |
| Post-deploy smoke | **Mandatory** | `scripts/deploy/preview-smoke-test.sh` |
| Commit verification | **Mandatory** | `/health/version` must match expected SHA |

## Deployment decision

- **Default:** Do not deploy if required blocking gates failed.
- **Override:** User may authorize deploy with known risks (`BYPASS_CI=1` + documented reason).
- **VM preview** (`impilo-preview` namespace, `http://41.57.127.235`) updates only after approval.

## Related docs

- [PREVIEW_TEST_GATES.md](./PREVIEW_TEST_GATES.md) — blocking vs advisory gates
- [HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md](./HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md) — step-by-step for humans and agents
- [CURSOR_CI_FEEDBACK_TEMPLATE.md](./CURSOR_CI_FEEDBACK_TEMPLATE.md) — feedback format after push
- [PIPELINE_IMPLEMENTATION_AUDIT.md](./PIPELINE_IMPLEMENTATION_AUDIT.md) — what exists today
- [COMPREHENSIVE_PIPELINE_CRITERIA.md](./COMPREHENSIVE_PIPELINE_CRITERIA.md) — definition of “comprehensive”

## Commands (VM)

```bash
bash scripts/test/run-preview-gates.sh
bash scripts/ci/collect-ci-feedback.sh
bash scripts/deploy/manual-authorized-preview-deploy.sh
```
