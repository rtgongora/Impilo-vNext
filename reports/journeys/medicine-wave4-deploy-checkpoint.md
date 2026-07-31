# Wave 4 — Unlock product proof (checkpoint)

**Date:** 2026-07-31  
**Branch:** `claude/adult-medicine-waves-0-2`  
**Workspace HEAD at checkpoint write:** see `git rev-parse HEAD` on the VM  
**Preview URL:** https://impilo.mohcc.gov.zw

## Done without deploy

- Isolated gate log dir pattern: `PIPELINE_LOG_DIR=/tmp/impilo-pipeline-gates-adult-medicine`
- Pack-owned FE/BE cleared earlier (`988322cd3`); Waves 5–7 landed subsequent commits
- Prior walkthrough evidence retained: `reports/journeys/medicine-clinician-walkthrough-20260731/summary.txt` — **INCOMPLETE** (preview was `fe0ba72d`, not adult-medicine HEAD; public HTTPS down; deploy not authorized)

## Blocked on product owner

Preview deploy of this branch requires the exact authorization phrase (CI infrastructure still unavailable):

```text
AUTHORIZE DEPLOY WITH VM GATES
```

Then:

```bash
cd /opt/impilo/repos/wt-adult-medicine
PIPELINE_LOG_DIR=/tmp/impilo-pipeline-gates-adult-medicine \
  bash scripts/pipeline/run-local-quality-gates.sh
bash scripts/pipeline/cursor-local-feedback.sh
# after authorization:
bash scripts/deploy/manual-authorized-preview-deploy.sh
# confirm /health/version == HEAD
# re-run docs/clinical/adult-medicine-domain-pack/clinician-walkthrough.md
# write reports/journeys/medicine-clinician-walkthrough-<date>/summary.txt → COMPLETE
```

Estate gate failures (product-truth ×6, phase6 ×3, unrelated FE/BE debt) may still yield `deploy_recommended: false`. Do **not** raise baselines; authorize with VM gates only if product owner accepts that residual.

## §23 / §25

Remain **PARTIAL / NOT MET** until the walkthrough summary is COMPLETE on HEAD-matched preview.
