# Preview deploy — sudo checkpoint

**Status:** Docker images built for commit `c0e65ddb`; k3s import blocked (sudo password required).

## Product owner — run once in VM terminal

```bash
sudo /opt/impilo/repos/Impilo-vNext/scripts/deploy/k3s-import-preview-images.sh
```

When import completes, reply **sudo checkpoint completed** (or re-authorize deploy) so the agent can run Helm deploy + smoke tests.

## After import (agent or you)

```bash
cd /opt/impilo/repos/Impilo-vNext
git checkout claude/staging-ux-orchestration-remediation-Yypyl
export DEPLOY_BRANCH=claude/staging-ux-orchestration-remediation-Yypyl
export DEPLOY_COMMIT_SHA=c0e65ddb1fc5854f2b9129b7289f0b83e8f6af87
bash scripts/deploy/preview-deploy.sh
bash scripts/deploy/preview-smoke-test.sh
curl -sf http://41.57.127.235/health/version
```

## Deploy context

| Field | Value |
|-------|-------|
| Branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Commit | `c0e65ddb` |
| Preview URL | http://41.57.127.235 |
| Auth mode | risk_override (user authorized; frontend gates pass) |
| Images | `impilo/one-ui-shell:preview`, `impilo/experience-bff:preview` |
