# Human-Authorized Preview Deployment

## Workflow

1. **Cursor** makes changes on VM (`/opt/impilo/repos/Impilo-vNext`).
2. **Cursor** runs local gates when practical: `bash scripts/test/run-preview-gates.sh`.
3. **Cursor** commits and pushes (user-requested).
4. **GitHub Actions** runs `ci.yml` automatically — **no preview deploy**.
5. **Cursor** runs `bash scripts/ci/collect-ci-feedback.sh` and fills [CURSOR_CI_FEEDBACK_TEMPLATE.md](./CURSOR_CI_FEEDBACK_TEMPLATE.md).
6. **User** decides: APPROVE DEPLOY | FIX FIRST | APPROVE WITH RISKS | REJECT.
7. If approved, **Cursor** runs manual deploy (interactive or Actions workflow).
8. **Post-deploy:** `preview-smoke-test.sh`, `/health/version` commit match.
9. **User** opens `http://41.57.127.235` for manual UX verification.

## Deploy methods

### VM (interactive)

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/ci/collect-ci-feedback.sh
bash scripts/deploy/manual-authorized-preview-deploy.sh
# Type: AUTHORIZE DEPLOY
```

### GitHub Actions

**Actions → Deploy Preview → Run workflow**

Inputs: branch, optional `commit_sha`, `deploy_reason`, `bypass_ci_check` (default false).

### Risk override

```bash
BYPASS_CI=1 BYPASS_REASON='user approved hotfix' bash scripts/deploy/manual-authorized-preview-deploy.sh
```

## Refusal rules

Manual deploy **refuses** when:

- `deploy_blocked: yes` from CI feedback (unless `BYPASS_CI=1`)
- CI status unknown (unless bypass)
- Post-deploy SHA ≠ expected SHA
- Smoke tests fail

## Decision categories

| Code | Meaning |
|------|---------|
| APPROVE DEPLOY | Required gates passed; deploy |
| FIX TEST FAILURES FIRST | Do not deploy |
| APPROVE WITH KNOWN RISKS | Deploy with `BYPASS_CI` + documented reason |
| REJECT DEPLOY | Stop |
