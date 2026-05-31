# AI Agent Workflow

This document defines how Cursor and AI agents work on Impilo vNext. It is the authoritative
workflow; the always-on rules in `.cursor/rules/` enforce it.

## Workspace (default for all work)

| Item | Value |
|------|-------|
| Remote SSH target | `robert@41.57.127.235:2276` |
| Repo path | `/opt/impilo/repos/Impilo-vNext` |
| Active working branch | `claude/staging-ux-orchestration-remediation-Yypyl` (unless explicitly changed) |
| Preview URL | `http://41.57.127.235` |
| Source of truth | **GitHub** `rtgongora/Impilo-vNext` |

1. **GitHub is the source of truth** — not the VM disk alone.
2. **The VM is the primary remote development workspace.** Connect Cursor via Remote SSH to
   `robert@41.57.127.235:2276` and open `/opt/impilo/repos/Impilo-vNext`.
3. **Do not use the local laptop clone for normal development.** The laptop is only the Cursor
   client and the browser-testing machine. All heavy work — dependency installation, builds,
   tests, Docker/image builds, k3s deployment, smoke tests, and logs — happens on the VM.

## 1. Start of every session

```bash
cd /opt/impilo/repos/Impilo-vNext
git status
git branch --show-current
git rev-parse --short HEAD
git remote -v
```

- Confirm the branch is `claude/staging-ux-orchestration-remediation-Yypyl` unless the user has
  explicitly instructed otherwise.
- **Pull latest** before starting new work.
- Create a feature/fix branch where appropriate.

## 2. Before making changes

- Inspect the existing implementation first.
- Read relevant docs, contracts, APIs, tests, services, frontend routes, backend endpoints,
  and deployment scripts.
- Do **not** duplicate existing functionality.
- Do **not** delete or replace existing functionality unless explicitly instructed.

## 3. While making changes

- Keep frontend and backend functionality aligned.
- If backend functionality exists but is not visible in the frontend, **surface it in the UI**
  rather than duplicating backend code.
- Never commit secrets. Never expose internal services publicly. Never use real patient data.

## 4. After making changes

- Run **`bash scripts/pipeline/run-local-quality-gates.sh`** (canonical VM pipeline; same scripts as GitHub Actions).
- **Push triggers CI only** — preview does **not** auto-deploy.
- If GitHub CI is infra-blocked (billing/0-step jobs), use VM local reports — do not treat as code pass/fail.
- After push: `bash scripts/ci/collect-ci-feedback.sh` and summarize per
  `docs/environment/CURSOR_CI_FEEDBACK_TEMPLATE.md`.
- **Deploy only after explicit user approval** via
  `bash scripts/deploy/manual-authorized-preview-deploy.sh` (or Actions Deploy Preview).
- Post-deploy: smoke tests, `/health/version` commit match, healthy pods.
- Update `docs/environment/OWNER_PREVIEW_TEST_CHECKLIST.md` when user-facing workflows change.

### Change-safety and inventories

- Before new services/features: read `docs/architecture/SERVICE_INVENTORY.md` and
  `FEATURE_INVENTORY.md`; extend existing code; do not duplicate.
- Run `bash scripts/guard/run-change-safety-gates.sh` before preview deploy when practical.
- Do not replace richer working code with thinner stubs without tests.

## 5. Report at the end of the task

- Preview URL (`http://41.57.127.235`)
- Branch and commit SHA
- Tests run, tests passed, tests failed
- Blockers
- Commit and push to GitHub from the VM.

## Notes

- The Dev Preview Sandbox is not production or formal staging — see
  `docs/environment/FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md`.
- Day-to-day commands and the Cursor startup checklist live in
  `docs/environment/REMOTE_DEV_WORKSPACE_USAGE.md`.
