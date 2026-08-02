# Deployed-vs-branch provenance audit — Checkpoint 4

**Namespace:** `impilo-full-preview` · **Reference:** `claude/tshepo-trust-completion-Yypyl` @ `72f1623fb`
**Captured:** 2026-08-02 · **Tool:** `scripts/security/audit-deployed-provenance.py`
**Data:** [`reports/estate/deployed-provenance.json`](../../../../reports/estate/deployed-provenance.json)

## Why this exists

Browser login was broken in preview because `experience-bff` ran an image built from a commit
that was **not an ancestor** of the integration branch — it predated the merge that brought the
authentication work in. Source truth said the work was merged; deployed truth said it had never
shipped. **Nothing in the estate compared the two.**

Provenance is read from each image's own **OCI config blob** (`org.opencontainers.image.revision`),
not from a tag or a pin file. A tag can be moved and a pin file goes stale; the config blob is
part of the content the digest addresses.

## Result

| Status | Count | Meaning |
|---|---|---|
| `IN_BRANCH` | **7** | ancestor **and** the service's `src/main` is unchanged since — genuinely current |
| `STALE` | **93** | ancestor, but the service's source **has moved** since the image was built |
| `UNSTAMPED` | **3** | Impilo image with no commit label — provenance cannot be established |
| `EXTERNAL` | 13 | postgres, redis, kafka, envoy, … — out of scope |
| `DIVERGENT` | **0** | running code the branch does not contain |

**93 of 100 Impilo workloads are running code older than the branch.** Unshipped commits per
service: min 1, **median 2**, **max 46**.

Current: `analytics-pipeline-service`, `experience-bff`, `keycloak`, `llm-orchestration-service`,
`tshepo-audit-service`, `tshepo-authz-service`, `wellness-service` — and three of those are
current only because this checkpoint redeployed them today.

### Worst offenders

| Workload | Unshipped commits |
|---|---|
| `pct-service` | **46** |
| `clinical-knowledge-platform-service` | 29 |
| `varapi-service` | 21 |
| `organization-registry-service` | 14 |
| `zibo-service` | 11 |
| `coverage-service` / `tshepo-identity-service` | 9 |

### `UNSTAMPED` — provenance unknowable

| Workload | Why |
|---|---|
| `inpatient-service` | digest-pinned image carrying no revision label |
| `public-website` | not chart-managed; pinned in `deploy/tls/mohcc-gov/public-website.yaml` |
| `matcher-engine` | renders a **mutable tag** `impilo/matcher-engine:preview`, not a digest |

`matcher-engine` is the worst of the three: a mutable tag means the running content can change
without any manifest change at all.

## Security work merged but not running

The point of the audit, not a side note:

| Unshipped commit | Services still without it |
|---|---|
| `fix(security): make the rate-limit bucket configurable in all 97 remaining services` | **88** |
| `fix(security): fail-closed cryptographic seeds and provision F6 secrets` | 5 |
| `feat(security): govern lost-device recovery` | 2 |
| `feat(abis): wire shared trust-context filter + security baseline config` | 1 |
| `feat(mvumo): consent is the record of a conversation, not a signature` | 1 |

A fail-closed cryptographic-seed fix that is merged and not deployed is not a fix.

## Why `IN_BRANCH` alone is not the test

The first version of this audit reported **100 `IN_BRANCH`, 0 problems** — and that was
misleading. The branch's history is a merge of several lanes, so almost any commit from any lane
is an ancestor. Ancestry is necessary and nowhere near sufficient.

The question that decides whether a running binary is current is narrower: *has this service's
own `src/main` changed since its image was built?* That is the `STALE` bucket, and it turned a
clean-looking 100/100 into 7/100.

`src/main` specifically — counting `src/test` would flag services whose shipped binary is
identical and overstate the finding.

## Anti-vacuous proof

A classifier that can only emit good news proves nothing. Re-running with `--branch 235db2ec3`
(the stale commit the broken BFF was built from) correctly emits **`DIVERGENT` for exactly the
two services redeployed today**, each *"9 commits behind"*, naming the right commit subjects. The
tool can produce every verdict it defines.

## Consequence for Checkpoint 7

**No cohort may enter enforcement on a `STALE` image.** Enforcing workload identity, audience
restriction or resource checks against a binary that does not contain the branch's current code
proves nothing about the branch — and a green result would be actively misleading.

Cohort selection therefore gains a precondition alongside caller enumeration: **the candidate
must be `IN_BRANCH` at enforcement time**, re-verified immediately before the flip rather than
inherited from this capture.

## Reproduce

```bash
python3 scripts/security/audit-deployed-provenance.py \
  --branch HEAD --namespace impilo-full-preview \
  --output reports/estate/deployed-provenance.json
```
