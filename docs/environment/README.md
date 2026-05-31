# Impilo vNext — Environment Documentation

Master entry point for the **Remote Development Workspace** and **Dev Preview Sandbox** on VM `41.57.127.235`.

> ## ⭐ Default Development Workflow: Cursor Remote SSH on VM
>
> **Do not use the local laptop clone for normal Impilo vNext development.** Use the VM Remote
> SSH workspace. The laptop is only the Cursor client and the browser-testing machine; all
> dependency installation, builds, tests, Docker/image builds, k3s deployment, smoke tests, and
> logs happen on the VM.
>
> | Item | Value |
> |------|-------|
> | Remote SSH target | `robert@41.57.127.235:2276` |
> | Repo path (open this folder) | `/opt/impilo/repos/Impilo-vNext` |
> | Active working branch | `claude/staging-ux-orchestration-remediation-Yypyl` (unless explicitly changed) |
> | Preview URL | `http://41.57.127.235` |
> | Source of truth | **GitHub** `rtgongora/Impilo-vNext` |
>
> Start every session with:
>
> ```bash
> cd /opt/impilo/repos/Impilo-vNext
> git status && git branch --show-current && git rev-parse --short HEAD && git remote -v
> git pull origin claude/staging-ux-orchestration-remediation-Yypyl
> bash scripts/dev/verify-remote-cursor-workspace.sh
> ```
>
> Full steps: [REMOTE_DEV_WORKSPACE_USAGE.md](./REMOTE_DEV_WORKSPACE_USAGE.md) ·
> Agent rules: [../AI_AGENT_WORKFLOW.md](../AI_AGENT_WORKFLOW.md)

### Quality gates (VM + GitHub — same scripts)

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/pipeline/run-local-quality-gates.sh      # full VM pipeline
bash scripts/pipeline/cursor-local-feedback.sh        # Cursor summary
bash scripts/ci/collect-ci-feedback.sh                # GitHub Actions status
bash scripts/deploy/manual-authorized-preview-deploy.sh  # after user approval only
```

See [DUAL_MODE_TEST_PIPELINE.md](./DUAL_MODE_TEST_PIPELINE.md).

## What This VM Is

| Environment | Purpose |
|-------------|---------|
| **Remote Development Workspace** | Primary Cursor Remote SSH workspace — builds, tests, image builds |
| **Dev Preview Sandbox** | Single-node k3s + Helm — browser preview for expert validation |

## What This VM Is Not

- Production deployment
- Formal production-grade Test/Staging
- HA / multi-node resilience testing
- Real patient data or production secrets

## Quick Start

### 1. Connect Cursor (primary workflow)

1. Open Cursor on your laptop.
2. **Remote SSH** → `robert@41.57.127.235` port `2276`.
3. Open folder: `/opt/impilo/repos/Impilo-vNext`.
4. Confirm branch: `claude/staging-ux-orchestration-remediation-Yypyl`.
5. Do **not** use the local laptop repo for normal development.

### 2. Verify workspace

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/dev/verify-remote-cursor-workspace.sh
```

### 3. Install / refresh dependencies

```bash
bash scripts/dev/install-dependencies.sh
```

### 4. Build and deploy preview

```bash
bash scripts/dev/build-all.sh
bash scripts/deploy/preview-build-images.sh
bash scripts/deploy/preview-deploy.sh
bash scripts/deploy/preview-smoke-test.sh
```

### 5. Open preview in laptop browser

`http://41.57.127.235/` (HTTP via k3s Traefik ingress)

## Document Index

| Document | Description |
|----------|-------------|
| [VM_BASELINE_AUDIT.md](./VM_BASELINE_AUDIT.md) | Pre-setup server snapshot |
| [REPO_ENVIRONMENT_AUDIT.md](./REPO_ENVIRONMENT_AUDIT.md) | Repo structure, services, tooling |
| [DEPENDENCY_INSTALLATION_REPORT.md](./DEPENDENCY_INSTALLATION_REPORT.md) | Install results |
| [ENVIRONMENT_STRATEGY.md](./ENVIRONMENT_STRATEGY.md) | Environment model |
| [K3S_PREVIEW_SETUP.md](./K3S_PREVIEW_SETUP.md) | k3s/Helm setup |
| [IMAGE_BUILD_STRATEGY.md](./IMAGE_BUILD_STRATEGY.md) | Container image workflow |
| [REMOTE_DEV_WORKSPACE_USAGE.md](./REMOTE_DEV_WORKSPACE_USAGE.md) | Cursor startup checklist + daily workflow |
| [PREVIEW_ENVIRONMENT_VARIABLES.md](./PREVIEW_ENVIRONMENT_VARIABLES.md) | Preview env vars |
| [OWNER_PREVIEW_TEST_CHECKLIST.md](./OWNER_PREVIEW_TEST_CHECKLIST.md) | Expert user testing |
| [DEV_PREVIEW_SECURITY_NOTES.md](./DEV_PREVIEW_SECURITY_NOTES.md) | Firewall / exposure |
| [DEV_PREVIEW_OPERATIONS.md](./DEV_PREVIEW_OPERATIONS.md) | Ops runbook |
| [DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md](./DEPLOYMENT_SUDO_AND_IMAGE_IMPORT_HARDENING.md) | k3s image import + limited sudo |
| [GITHUB_ACTIONS_PREVIEW_DEPLOYMENT.md](./GITHUB_ACTIONS_PREVIEW_DEPLOYMENT.md) | CI/CD preview (push → CI → auto deploy on active branch when secrets set) |
| [FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md](./FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md) | Future formal staging |
| [../AI_AGENT_WORKFLOW.md](../AI_AGENT_WORKFLOW.md) | AI agent rules |

## Repo Location

```
/opt/impilo/repos/Impilo-vNext
```

GitHub remains the source of truth. Commit and push from the VM workspace.
