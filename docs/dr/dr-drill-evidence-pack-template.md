# DR Drill Evidence Pack — Impilo vNext

> Template version: 1.0
> Date: 2026-03-15

---

## Instructions

Complete this template after each DR drill or game day exercise. All sections must be filled — no placeholders or "TBD." If a section is not applicable, mark it "N/A" with a one-sentence explanation.

This pack serves as auditable evidence that DR capabilities have been tested and validated. Retain completed packs for a minimum of 2 years per Zimbabwe MoHCC compliance requirements.

---

## 1. Drill Identification

| Field | Value |
|-------|-------|
| **Drill ID** | DR-DRILL-YYYY-MM-DD-NNN |
| **Date** | |
| **Drill type** | Single-service restore / Ring 0 core restore / Partial platform recovery / Game day |
| **Game day scenario** | GD-1 / GD-2 / GD-3 / GD-4 / GD-5 / GD-6 / N/A |
| **Environment** | Staging / DR site / Production (with approval) |
| **Duration** | Start: HH:MM UTC — End: HH:MM UTC |
| **Drill lead** | Name, role |
| **Announced/Surprise** | Announced / Surprise |

---

## 2. Participants

| Role | Name | Present | Notes |
|------|------|:-------:|-------|
| Drill lead | | ☐ | |
| SRE on-call | | ☐ | |
| Platform engineer | | ☐ | |
| Platform engineer | | ☐ | |
| Clinical systems representative | | ☐ | |
| Observer / auditor | | ☐ | |

---

## 3. Scope

### 3.1 Services Under Test

| Service | Ring | Database | In Scope |
|---------|:----:|----------|:--------:|
| TSHEPO | 0 | tshepo | ☐ |
| VITO | 0 | vito | ☐ |
| VARAPI | 0 | varapi | ☐ |
| TUSO | 0 | tuso | ☐ |
| ZIBO | 0 | zibo | ☐ |
| MSIKA | 0 ext | msika | ☐ |
| BUTANO | 0 ext | butano | ☐ |
| MUSHEX | 0 ext | mushex | ☐ |
| PCT | 1 | pct | ☐ |
| OROS | 1 | oros | ☐ |
| Keycloak | Infra | keycloak | ☐ |
| Kafka | Infra | N/A | ☐ |
| MinIO | Infra | N/A | ☐ |

### 3.2 Failure Mode Simulated

| Failure Mode | Description |
|-------------|-------------|
| **Type** | Database loss / Network partition / AZ failure / Backup corruption / Realm corruption / Other |
| **Injection method** | kubectl delete pod / docker pause / tc netem / DROP DATABASE / Other: _______ |
| **Blast radius** | Single service / Ring 0 / Ring 0 + Ring 1 / Full platform |

---

## 4. Pre-Drill Baseline

### 4.1 Service Health (Before Injection)

| Service | Port | Health Status | Response Time |
|---------|:----:|:------------:|:-------------:|
| TSHEPO | 8081 | | ms |
| VITO | 8082 | | ms |
| VARAPI | 8083 | | ms |
| TUSO | 8084 | | ms |
| ZIBO | 8085 | | ms |

### 4.2 Database State (Before Injection)

| Database | Flyway Version | Total Rows (approx) | Outbox Unpublished | Backup Available |
|----------|:--------------:|:-------------------:|:------------------:|:----------------:|
| | | | | ☐ |
| | | | | ☐ |
| | | | | ☐ |

### 4.3 Backup Verification

| Database | Latest Backup Timestamp | Backup Size | Checksum Verified |
|----------|:-----------------------:|:-----------:|:-----------------:|
| | | | ☐ |
| | | | ☐ |

---

## 5. Drill Timeline

| Time (UTC) | Elapsed | Event | Actor | Notes |
|:----------:|:-------:|-------|-------|-------|
| | T+0 | Failure injected | Drill lead | |
| | T+__m | Failure detected by monitoring | Automated / Manual | Alert name: |
| | T+__m | Incident declared | On-call SRE | Severity: SEV-__ |
| | T+__m | Root cause identified | | |
| | T+__m | Recovery procedure initiated | | Runbook used: |
| | T+__m | Backup located and verified | | File: |
| | T+__m | Restore started | | |
| | T+__m | Restore completed | | |
| | T+__m | Service health verified | | |
| | T+__m | Post-restore verification passed | | |
| | T+__m | Drill declared complete | Drill lead | |

---

## 6. Measurements

### 6.1 RPO/RTO Achievement

| Metric | Target | Achieved | Verdict |
|--------|:------:|:--------:|:-------:|
| **RTO (Recovery Time)** | ≤ __ min | __ min | PASS / FAIL |
| **RPO (Data Loss)** | ≤ __ min | __ min | PASS / FAIL |
| **Detection time** | ≤ __ min | __ min | PASS / FAIL |
| **Total drill duration** | N/A | __ min | — |

### 6.2 Data Integrity Verification

| Check | Result | Details |
|-------|:------:|---------|
| Database connectivity | PASS / FAIL | |
| Flyway schema version matches | PASS / FAIL | Version: |
| Row counts within 1% of baseline | PASS / FAIL | Delta: __% |
| Outbox table intact | PASS / FAIL | Unpublished count: |
| No invalid indexes | PASS / FAIL | |
| Audit chain integrity (if applicable) | PASS / FAIL / N/A | |
| Service health endpoint | PASS / FAIL | Status: |
| Prometheus metrics available | PASS / FAIL | |

### 6.3 Outbox Recovery (if applicable)

| Service | Pre-Drill Unpublished | Peak During Drill | Post-Recovery Unpublished | Drain Time |
|---------|:---------------------:|:-----------------:|:-------------------------:|:----------:|
| | | | | min |
| | | | | min |

---

## 7. Runbook Adherence

| Question | Answer |
|----------|--------|
| Which runbook(s) were used? | |
| Were all runbook steps followed in order? | Yes / No — explain: |
| Were any steps skipped or improvised? | Yes / No — explain: |
| Were any steps unclear or incorrect? | Yes / No — explain: |
| Time spent searching for information (not in runbook)? | __ min |

### Runbook Corrections Needed

| Runbook | Section | Issue | Proposed Fix |
|---------|---------|-------|-------------|
| | | | |

---

## 8. Findings

### 8.1 What Went Well

1.
2.
3.

### 8.2 What Needs Improvement

1.
2.
3.

### 8.3 Surprises / Unexpected Behavior

1.
2.

---

## 9. Action Items

| # | Action | Priority | Owner | Due Date | Status |
|---|--------|:--------:|-------|:--------:|:------:|
| 1 | | HIGH/MED/LOW | | | ☐ |
| 2 | | HIGH/MED/LOW | | | ☐ |
| 3 | | HIGH/MED/LOW | | | ☐ |

---

## 10. Verdict

| Overall Verdict | |
|----------------|---|
| ☐ **PASS** | All RPO/RTO targets met. Runbooks followed successfully. No critical findings. |
| ☐ **CONDITIONAL PASS** | RPO/RTO met but process improvements needed. Action items must be completed before next drill. |
| ☐ **FAIL** | RPO/RTO breached or critical data integrity issue. Remediation required before production deployment proceeds. |

**Justification:**

(Write 2–3 sentences explaining the verdict.)

---

## 11. Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Drill Lead | | | |
| SRE Lead | | | |
| Platform Engineering Lead | | | |
| Clinical Systems Lead (if applicable) | | | |
| Compliance Officer (if applicable) | | | |

---

## 12. Attachments Checklist

| Attachment | Included |
|-----------|:--------:|
| Drill timeline log (automated, if run via `run-drill.sh`) | ☐ |
| `post-restore-verify.sh` output | ☐ |
| Prometheus/Grafana screenshots (error rate, latency during drill) | ☐ |
| Outbox lag graph during drill | ☐ |
| Backup file checksums | ☐ |
| Alert notification screenshots | ☐ |

---

## 13. Historical Drill Results

Track drill results over time to identify trends:

| Drill ID | Date | Scenario | RTO Target | RTO Achieved | RPO Target | RPO Achieved | Verdict |
|----------|------|----------|:----------:|:------------:|:----------:|:------------:|:-------:|
| | | | | | | | |
| | | | | | | | |
| | | | | | | | |

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial DR drill evidence pack template |
