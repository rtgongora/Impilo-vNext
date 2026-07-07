# IATG Real-Life Journeys Runbook (A–F)

**Purpose.** Prove the six IATG real-life browser journeys **A–F** as a real product journey on
the Web Preview VM — clean deploy at the RJ anchor, services up, the **21-scenario seed** + the
**runtime harness** returning real exit codes, then a **browser walkthrough A–F** where a real
person enters real data, hands the task to the correct next actor, and reaches a governed outcome
visible in the product. The verdict is whatever the evidence supports — nothing is assumed.

**Working principle (Section 12).** The standard is not "the code exists." It is: *a real person
can use the product, enter real information, hand the task to the correct next actor, and reach a
governed outcome visible in the product.* No data entry going nowhere, no dead-end forms, no
mocked proof, no green-gates-as-product-truth.

**Who runs this.** An operator on the preview VM / desktop with SSH+kubectl to the preview
cluster. The coordinating Claude session is **egress-blocked from the VM** (`41.57.127.235` →
403) and cannot run any of this itself. It authored this runbook and will **adjudicate the
evidence you paste back** (§8) into one verdict label (§9).

**Anchor under test.** The RJ stream tip on `claude/web-session-anchor-nnnkf6` after the
`fable/iatg-realjourney` merge (RJ-1…RJ-6). Confirm the running commit in §1 — do not proceed on
a different commit.

**Hard invariants.** No changes under `services/tshepo-service/**`. No org-registry phase-2c /
write-freeze / irreversible cutover flip. Live authorization must flow through the live PDP
(`tshepo-authz-service`), never the frozen `tshepo-service` monolith. Do **not** report
`IATG_REAL_LIFE_JOURNEYS_PROVEN` unless **every** Section-11 condition (§9 checklist) holds on
real evidence.

> This runbook is the **A–F superset** of `iatg-e2e-preview-journey-runbook.md` (which covered the
> E3 A–E slice). Use this one for the real-life-journey milestone; the deploy/health/realm steps
> are identical and cross-referenced rather than repeated where unchanged.

---

## 0. Prerequisites

Same as `iatg-e2e-preview-journey-runbook.md` §0 (SSH + kubectl + helm/curl/jq/python3/bash on the
VM; repo at `/opt/impilo/repos/Impilo-vNext`; host `http://41.57.127.235`; namespace
`impilo-full-preview`; realm `impilo`; browser login = password-grant `POST /internal/v1/auth/login`).

Seeded realm principals (scenarios 1–4):

| Purpose | username | password | role |
|---|---|---|---|
| Platform-Origin admin (initiator) | `origin.admin.one` | `Origin@Admin2024!` | `PLATFORM_ORIGIN_ADMINISTRATOR` |
| Second approver (two-person) | `origin.admin.two` | `Origin@Admin2024!` | `PLATFORM_ORIGIN_ADMINISTRATOR` |
| National administrator | `national.admin.one` | `National@Admin2024!` | `NATIONAL_ADMINISTRATOR` |
| Citizen (Health ID only) | `citizen.moyo` | `Vashandi@2024!` | `CITIZEN` (health_id `b0000000-0000-4000-8000-000000000001`) |
| Org-onboarding operator | `superadmin` | `Impilo@2024!` | all roles (satisfies `ORGANIZATION_ADMIN` gate) |

---

## 1. Clean deploy at the RJ anchor

```bash
export REPO_PATH=/opt/impilo/repos/Impilo-vNext
cd "$REPO_PATH"
git fetch --all --prune
git checkout claude/web-session-anchor-nnnkf6
git pull --ff-only
git rev-parse --short HEAD          # 1.1  RECORD — the commit under test
```

Clean namespace (recommended), preflight, deploy — identical to the E3 runbook §1.2–1.3:
```bash
kubectl delete namespace impilo-full-preview --wait=true   # skip for an in-place upgrade
bash scripts/deploy/full-boot-preview-deploy.sh --preflight
FULLBOOT_SKIP_GATES=1 bash scripts/deploy/full-boot-preview-deploy.sh
#   authorization phrase:  AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

Verify the deployed commit equals 1.1:
```bash
curl -s http://41.57.127.235/health/version | jq '{environment, fullEstateCommit}'
# expect: environment == "full-preview" AND fullEstateCommit starts with the 1.1 short SHA
```
> If it differs, STOP — wrong artifact.

---

## 2. Services up (incl. workflow-service for Journey F)

Run the E3 runbook §2 health table **plus** the two services the RJ journeys additionally need:

| Service | k8s name | Port | Health | Needed by |
|---|---|---|---|---|
| Workflow (adjudication) | `workflow-service` | 8250 | `/actuator/health` | Journey F, seed scenario 20 |
| Kafka | `kafka` | 9092 | pod Ready | Journey F resolution |

Also re-confirm §2.1 of the E3 runbook — the `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true`
full-preview flag on `varapi-service` (prevents the uniform `/v1/**` 403). Any required service
not UP is a **defect** (name it + its journey letter); a missing workflow-service downgrades
Journey F / scenario 20 to a preview-dependency block, not a silent pass.

---

## 3. Realm seed (users/roles resolve)

Log into the shell once as **each** principal in §0; confirm login succeeds and lands on `/home`.
A failed login is a blocking defect for that principal's journey.

---

## 4. Seed the 21 real-life scenarios (idempotent)

Get an admin bearer (password-grant as `origin.admin.one`), then run the **new 21-scenario seed**.
It is idempotent and honest-by-construction: every call checks its HTTP status; an unmeetable
precondition is recorded `SKIPPED` with a reason; any hard failure exits non-zero.

```bash
cd "$REPO_PATH"
mkdir -p reports/iatg-rj

ADMIN_TOKEN="<bearer>" \
VARAPI_BASE=http://41.57.127.235:8083 \
TUSO_BASE=http://41.57.127.235:8084 \
WGV_BASE=http://41.57.127.235:8165 \
ORG_BASE=http://41.57.127.235:8153 \
WORKFLOW_BASE=http://41.57.127.235:8250 \
bash scripts/seed/iatg-realjourney-seed.sh | tee reports/iatg-rj/seed.stdout.txt
echo "SEED_EXIT=${PIPESTATUS[0]}"        # 4.1  RECORD — must be 0 (SKIPPED is allowed; FAILED is not)

# Bridge the IATG_E2E_SEED_OUTPUT block for the harness:
bash scripts/seed/iatg-seed-to-env.sh reports/iatg-rj/seed.stdout.txt reports/iatg-rj/seed.env
cat reports/iatg-rj/seed.env             # 4.2  RECORD — FACILITY_ID, PROVIDER_PUBLIC_ID, CLAIM_TOKEN,
                                         #      CLAIMANT_HEALTH_ID, EC_NUMBER
```

Capture the printed **21-SCENARIO SEED MANIFEST** (4.3): each scenario's state
(`SEEDED`/`SKIPPED`/`PRINCIPAL`) + detail, and the summary line
`principals=4 seeded=N skipped=M failed=0 / 21`. A `SKIPPED` line names the missing precondition
(e.g. a facility code that didn't resolve) — record it; it is honest, not a pass.

> Pin `FACILITY_CODE_COMPLIANT` / `_EXPIRED` / `_PRIVATE` to codes that exist in the preview DB if
> the defaults (`ZW-HCH-001` / `ZW-HCH-002` / `ZW-PVT-001`) do not resolve — otherwise those
> facility scenarios (13–16, and admin-claim 11/12) SKIP.

---

## 5. Runtime harness — read-back proof (incl. adjudication)

```bash
cd "$REPO_PATH"
IATG_SEED_ENV="$REPO_PATH/reports/iatg-rj/seed.env" \
PREVIEW_HOST=41.57.127.235 \
CLAIMANT_HEALTH_ID="$(. reports/iatg-rj/seed.env; echo "$CLAIMANT_HEALTH_ID")" \
RUN_ADJUDICATION=1 \
bash test/integration/iatg-end-to-end-runtime.sh | tee reports/iatg-rj/harness.stdout.txt
echo "HARNESS_EXIT=${PIPESTATUS[0]}"     # 5.1  RECORD — 0 iff every executed step passed
```

Steps proven (each asserts HTTP status **and** a named field; read-back steps confirm the record
survives a fresh GET):
1 country-op two-person → **EXECUTED** + **durable GET /actions/{id} == EXECUTED** (Journey A) ·
2 appoint national admin · 3 org onboard DRAFT→VERIFIED/ACTIVE (Journey B) · 4 provider preload ·
5 citizen claims provider · 6 EC → EMPLOYMENT_MATCHED · 7 four-block trust profile · 8 facility
per-source composite (Journey C) · **10 provider access-request submit → read-back by publicId
(Journey D)** · **11 facility-mode appointments read (Journey E eligibility)** · 9 Channel-C claim
→ ACCEPTED via Kafka (Journey F).

Capture: every `[PASS]/[FAIL] step N` line (5.2), the `IATG E2E results:` summary (5.3),
`HARNESS_EXIT` (5.1), and `reports/iatg-e2e/<label>/summary.json` (5.4). A single `[FAIL]` aborts
with exit 1 — record which step + its response body.

---

## 6. Browser walkthrough A–F — the product-journey proof

Open `http://41.57.127.235`. For each journey log in as the named principal, enter **real data**,
drive to a governed outcome, and capture a screenshot + the observed terminal state. Fill §8.

### Journey A — Platform-Origin governance + two-person
Entry `/platform-origin` as `origin.admin.one`.
1. Console loads **real** country-operations/actions (or an honest empty state), not placeholders.
2. Initiate a country operation (country code, name, governing authority, requested national-admin
   identity, justification, effective date). Confirm it **persists** as a PENDING action with an
   `accessRequestId` and appears in the two-person approval panel.
3. Approve as `origin.admin.one` → **1 of 2**, Execute **disabled** (initiator cannot self-approve
   as the distinct approver).
4. Login `origin.admin.two`; approve → **APPROVED** (2 of 2); Execute → **EXECUTED**
   (`data-testid=approval-progress`).
5. **Refresh the page** — the action must still read **EXECUTED** (durable state, not optimistic
   in-memory). Re-login as `origin.admin.one` and confirm the terminal state is visible to the
   initiator. **Record terminal state:** EXECUTED / REJECTED / FAILED_WITH_REASON.

### Journey B — Organization onboarding (all org types)
Entry org-onboarding wizard as `superadmin` (or `national.admin.one`).
1. Create/invite an organization with realistic data (legal name, type, country, authority basis,
   registration reference, representative Health ID, service scope).
2. Submit → confirm it **persists** and appears in the operator projection at
   `/organization-admin/governance` with a status (DRAFT/SUBMITTED/PENDING_APPROVAL/…).
3. Complete approval as the correct actor → VERIFIED/ACTIVE; confirm the Organization ID is
   reserved/created **only after** the approval threshold.
4. Repeat (or confirm via seed manifest scenarios 17/18/19) for **government**, **private/
   multi-facility**, and **NGO/implementing-partner** types. Confirm a private/NGO org's submitted
   claims do **not** carry government-level trust by default (doctrine).
   **Record:** each org's terminal status + projection-visible: yes/no.

### Journey C — Facility legitimacy + claim
Entry facility legitimacy panel + `/facility/claim`.
1. Open `/facility/{FACILITY_ID}` (seeded compliant facility). Confirm per-source verdicts:
   `HPA_LEGAL`, `MINISTRY_OPERATIONAL`, `PLATFORM_OPERATIONAL=GOVERNMENT_OPERATIONAL_EXCEPTION`
   with an **amber mandatory-reason** callout
   (`data-testid=legitimacy-exception-reason-PLATFORM_OPERATIONAL`) + a platform-access badge.
2. Open the **expired** facility (`FACILITY_CODE_EXPIRED`) → confirm it renders EXPIRED /
   non-compliant honestly (legal registration ≠ operational recognition).
3. **Fail-closed check:** `kubectl scale deploy/tuso-service --replicas=0 -n impilo-full-preview`,
   reload → explicit `data-testid=facility-legitimacy-unavailable` notice ("Nothing was assumed;
   verdict withheld"), **not** a silent empty panel. Scale back to 1.
4. Submit a facility claim on the **private** facility (`/facility/claim?facilityUuid=…`): realistic
   name/location/type/owner-org/authority-basis/representative. Confirm it **persists** as PENDING,
   is visible to the reviewer, and can be accepted / rejected / escalated / needs-more-info. Confirm
   no private facility receives a government exception without explicit national-admin reason.
   **Record:** verdicts shown, 502 notice yes/no, claim terminal state.

### Journey D — Provider trust + Request Provider Access + status
Entry post-login landing + `/citizen/wallet/trust` + `/citizen/provider-claim` as `citizen.moyo`.
1. **Request Provider Access entry point** is visible after Health-ID login for a user with no
   provider context (life zone rail + `/citizen/provider-claim`). Confirm the **7 landing choices**
   render: (1) I already have a Provider ID, (2) Recover my Provider ID, (3) new provider /
   request a Provider ID, (4) I have a council registration number, (5) I am a public-sector
   employee with an EC number, (6) I was invited by an organization/facility, (7) Check status of
   my request. Not a dead link, not admin-only, not route-only.
2. **D1 have-ID / D2 recover / D3 new** — drive at least the **council-number** and **new-provider**
   lanes: submit realistic evidence → confirm a **durable request** is created (a `publicId`, not
   local-only state) with a terminal/pending status + next actor. Recovery must **never issue a
   duplicate** Provider ID (record MATCH_NOT_FOUND / RECOVERED_AND_LINKED / DUPLICATE_SUSPECTED).
3. **Status page** — open "Check status of my request" (`/citizen/provider-claim/status`) and the
   per-request view: request ID, current status, submitted evidence, **next actor**, next action,
   reason, date submitted, last updated, terminal decision if any. **Refresh** — the record must
   persist.
4. **Four-block trust** at `/citizen/wallet/trust`: identity / professional / employment /
   operational each render with a `sourceOfRecord`; a block may read UNAVAILABLE ("nothing
   fabricated") but all four render. **EC masked** (`maskedEcNumber`); **no other person's Health
   ID leaks** (including on the EC-conflict scenario). Confirm the profile reflects pending/ matched
   state honestly.
   **Record:** 7 choices present, each driven lane's terminal state, status-page fields, EC masked.

### Journey E — Facility Mode after provider login
Entry shell work/professional context.
1. As a provider with **no** facility admin appointment (or `citizen.moyo` pre-link) → Facility
   Mode is **hidden/disabled with an explanation** (no facility assignment). Confirm the harness
   step-11 read backs an empty appointments array for that facility.
2. As the **ACTIVE** facility-admin from seed scenario 12 (health id
   `b0000000-0000-4000-8000-000000000012` on `FACILITY_CODE_COMPLIANT`) → the **Facility Mode**
   option appears; enter it and confirm the dashboard shows real facility-context cards (identity,
   legitimacy/compliance, organization affiliation, assignments, **pending claims/requests**,
   trust warnings). Perform **one real facility-mode action** (e.g. review a pending provider
   assignment / open legitimacy review) → confirm it **persists** and is visible to the next actor.
3. Confirm switching facility context changes **backend** calls (facility UUID in the request), not
   frontend-only state, and that a provider cannot administer a facility with no appointment.
   **Record:** Facility Mode hidden-when-ineligible, appears-when-ACTIVE, one persisted action.

> NB (fixed this stream): Facility-Mode eligibility gates on the **ACTIVE** appointment state
> (tuso's canonical literal), not the non-existent "APPROVED" — an approved admin now lights up.

### Journey F — Adjudication + decision completion
Entry: the pending cases seeded (scenario 20) + the harness Channel-C step 9 + the WS-D/WS-F
producers.
1. Confirm a **workflow instance is created** for a claim needing adjudication (harness step 9
   SUBMITTED→UNDER_REVIEW; and/or seed scenario 20 pending instance visible via
   `GET /internal/v1/workflows/instances`).
2. Record an **append-only** decision (harness step 9 records APPROVED; seed scenario 21 records a
   DECIDED_DENIED). Confirm the decision is queryable
   (`GET …/adjudications/decisions?subjectType=&subjectRef=`) and that a second decision **appends**
   (does not overwrite).
3. Confirm the consumer/listener **resolves the source claim** (harness step 9 polls the org-claim
   to **ACCEPTED** via Kafka) and that the original user-facing state updates after the decision.
4. Confirm **rejected / needs-more-information** paths exist, not only the approved golden path
   (scenario 21 DENIED; provider access-request DUPLICATE_SUSPECTED / needs-info routing).
   **Record:** instance created, decision append-only, claim resolved, rejected path shown.

### Failure honesty (spot-check across A–F)
Every unavailable dependency shows an explicit fail-closed message identifying the path ("nothing
was changed/fabricated"). **No** 501s surfaced as success, **no** silent empty panels, **no**
endless spinners, **no** fake success, **no** preview permitAll hiding missing policy without a
warning.

---

## 7. Contract verification (C/D/E/F)

Before the browser run, confirm the static contract audit at
`docs/coordination/iatg-realjourney-contract-verification.md` still holds (it maps each browser
form → BFF DTO → downstream DTO for journeys C/D/E/F, with the failure-code handling matrix). If a
form 400/422s in the browser on a well-formed submit, cross-check that doc for a field-name drift.

---

## 8. Evidence to paste back (for adjudication)

```
ANCHOR COMMIT TESTED (1.1 / verify):     __________  (fullEstateCommit match: yes/no)
DEPLOY STATUS (§2 incl. workflow-service):all UP? ____  (list any not-UP)
SEED_EXIT (4.1):                          ____
  21-scenario manifest (4.3):             <paste: state+detail per scenario, summary line>
  seed.env keys (4.2):                    ____
HARNESS_EXIT (5.1):                       ____
  step PASS/FAIL lines (5.2):             <paste>
  results line (5.3):                     <paste>
  summary.json (5.4):                     <paste>
BROWSER A governance:  terminal ____  refresh-durable EXECUTED ____  visible to initiator ____  shot ____
BROWSER B onboarding:  gov/private/NGO terminal ____  projection visible ____  shot ____
BROWSER C facility:    verdicts+exception ____  502 notice ____  claim terminal ____  shots ____
BROWSER D provider:    7 choices ____  lane terminals ____  status-page fields ____  EC masked ____  no HID leak ____  shot ____
BROWSER E facility-mode: hidden-when-ineligible ____  appears-when-ACTIVE ____  one persisted action ____  shot ____
BROWSER F adjudication: instance ____  append-only ____  claim resolved ____  rejected path ____  shot ____
FAILURE-HONESTY spot-checks:              <notes>
DEFECTS (route · user · expected · actual · backend endpoint · logs · proposed fix):
  - ...
```

---

## 9. Verdict rubric (the coordinating session applies this)

Report **`IATG_REAL_LIFE_JOURNEYS_PROVEN`** only if ALL Section-11 conditions hold on real evidence:

1. Anchor merged + pushed. 2. Preview deployed from that anchor. 3. Seed exit 0. 4. Harness exit 0.
5. Browser A–F pass. 6. Request Provider Access supports existing/recovery/new. 7. Facility Mode
appears only for eligible provider context. 8. Every form's data persists. 9. Every workflow has a
next actor or clear terminal state. 10. Refresh loses no submitted record. 11. Re-login as next
actor shows pending work. 12. Adjudication decisions resolve source claims. 13. Trust profile
updates honestly. 14. EC masked. 15. No other Health ID leaks. 16. No duplicate Provider ID on
recovery. 17. No private/NGO facility gets a government exception by default. 18.
`services/tshepo-service/**` untouched. 19. No irreversible org-registry cutover. 20. Residual
defects listed honestly.

Otherwise use exactly one lesser label:

| Label | Condition |
|---|---|
| `IATG_BROWSER_JOURNEY_FAILED` | scripts green (seed+harness exit 0) but one or more browser journeys A–F fail |
| `IATG_BLOCKED_BY_PREVIEW_DEPENDENCY` | a required service (§2) never comes up / deploy cannot complete |
| `IATG_PARTIAL_WITH_DEFECTS` | mostly passes with a bounded, enumerated defect list not cleanly fitting above |
| `IATG_NOT_READY` | seed or harness non-zero, or the milestone otherwise not demonstrably met |

No label is assigned on "should work" — only on the evidence in §8.
