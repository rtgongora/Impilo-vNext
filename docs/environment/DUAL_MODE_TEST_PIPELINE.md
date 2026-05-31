# Dual-mode test pipeline

Impilo vNext uses **one canonical test package** executed in two places:

| Mode | Runner | When |
|------|--------|------|
| **VM / Cursor local** | `scripts/pipeline/run-local-quality-gates.sh` | Always available on the dev VM |
| **GitHub Actions** | Same script + sub-scripts from `.github/workflows/ci.yml` | When billing/runners are healthy |

## Principles

1. **Script source of truth** — Test logic lives under `scripts/pipeline/`, `scripts/test/`, and `scripts/guard/`. Workflow YAML only checks out deps and calls scripts.
2. **GitHub Actions is not exclusive** — Billing/runner locks produce **0-step jobs**; that is **infrastructure failure**, not code pass/fail.
3. **VM local pipeline is first-class** — Run before/after push from Cursor on the VM.
4. **Preview deploy stays manual** — User authorizes after reviewing VM results and/or GitHub CI.
5. **Deploy with VM gates** — If GitHub CI is infra-blocked but VM local pipeline passed for the commit, user may type `AUTHORIZE DEPLOY WITH VM GATES`.

## Workflow

```text
Cursor changes
  → bash scripts/pipeline/run-local-quality-gates.sh
  → fix blocking failures
  → commit / push
  → GitHub Actions (if available) calls same scripts
  → bash scripts/ci/collect-ci-feedback.sh
  → bash scripts/pipeline/cursor-local-feedback.sh
  → user authorizes deploy
  → bash scripts/deploy/manual-authorized-preview-deploy.sh
  → smoke + /health/version
```

## Reports

After each local run:

- `reports/pipeline/latest-summary.md`
- `reports/pipeline/latest-summary.json`
- `reports/pipeline/latest-failures.md`
- `reports/pipeline/latest-change-summary.md`

(gitignored except `.gitkeep`)

## Commands

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/pipeline/run-local-quality-gates.sh
bash scripts/pipeline/cursor-local-feedback.sh
bash scripts/ci/collect-ci-feedback.sh
bash scripts/deploy/manual-authorized-preview-deploy.sh
```

See also: [PREVIEW_PIPELINE.md](./PREVIEW_PIPELINE.md), [HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md](./HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md).
