# Cursor CI Feedback Template

After every push, the agent runs `bash scripts/ci/collect-ci-feedback.sh` and fills this template for the user.

---

## CI summary

| Field | Value |
|-------|-------|
| **Branch** | |
| **Commit** | |
| **GitHub Actions run** | (ID + URL) |
| **Overall result** | success / failure / unknown |

## Gates

| Passed gates | |
|--------------|---|
| **Failed gates** | |
| **Advisory warnings** | |

## Change impact

| Files changed | |
|---------------|---|
| **Files added** | |
| **Files deleted** | |
| **Services affected** | |
| **Routes affected** | |
| **APIs affected** | |
| **Mobile affected** | yes/no |

## Risk

| **Regression risk** | low / medium / high + why |
| **Change-safety risk** | deletions, duplicates, inventory drift |
| **Secrets scan** | pass / fail |

## Recommendation

| **Recommendation** | Deploy / Fix first / Deploy with risks |
| **Suggested next step** | |

## User decision (required before deploy)

Choose one:

1. **APPROVE DEPLOY** — run `bash scripts/deploy/manual-authorized-preview-deploy.sh` (type `AUTHORIZE DEPLOY`)
2. **FIX TEST FAILURES FIRST**
3. **APPROVE WITH KNOWN RISKS** — `BYPASS_CI=1 BYPASS_REASON='...'`
4. **REJECT DEPLOY**

---

Do not deploy preview until the user selects option 1 or 3.
