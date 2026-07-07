# IATG End-to-End Preview Journey Runbook

**Purpose.** Prove the IATG doctrine journey as a *real product journey* on the Web Preview
VM — clean deploy, services actually up, seed + runtime harness returning real VERDICT lines
and exit codes, then a **browser walkthrough (A–E)** in the experience shell — and capture
evidence that maps to one of five verdict labels. No optimistic language: the verdict is
whatever the evidence supports.

**Who runs this.** An operator on the preview VM / a desktop with SSH+kubectl to the preview
cluster. The coordinating Claude session is **egress-blocked from the VM** (`41.57.127.235`
returns 403) and cannot run any of this itself — it authored this runbook and will **adjudicate
the evidence you paste back** into the final verdict.

**Anchor under test.** `claude/web-session-anchor-nnnkf6` at its **post-E3 tip** (E3 =
journey-honesty fixes: EC-employment seed capability, `seed.env` bridge, facility-detail 502
fail-closed notice). Confirm the running commit in Step 1.4 — do not proceed on a different commit.

**Hard invariants.** No changes under `services/tshepo-service/**`. No org-registry phase-2c /
write-freeze / irreversible cutover flip. Do **not** relabel IATG "proven end-to-end" unless
**both** script proof (seed exit 0 **and** harness exit 0) **and** the browser walkthrough pass.

---

## 0. Prerequisites

- SSH to the Web Preview VM; `kubectl` context = the preview cluster; `helm`, `curl`, `jq`,
  `python3`, `bash` on the VM.
- Repo present at `/opt/impilo/repos/Impilo-vNext` (or export `REPO_PATH` to its location).
- Preview host is `http://41.57.127.235`; namespace `impilo-full-preview`.
- Realm `impilo`; browser login is **password-grant** through the shell (`POST /internal/v1/auth/login`),
  **not** a Keycloak PKCE redirect.

Seeded principals (all in the preview realm):

| Purpose | username | password | role |
|---|---|---|---|
| Platform-Origin admin (initiator) | `origin.admin.one` | `Origin@Admin2024!` | `PLATFORM_ORIGIN_ADMINISTRATOR` |
| Second approver (two-person) | `origin.admin.two` | `Origin@Admin2024!` | `PLATFORM_ORIGIN_ADMINISTRATOR` |
| National administrator | `national.admin.one` | `National@Admin2024!` | `NATIONAL_ADMINISTRATOR` |
| Citizen (trust/provider journey) | `citizen.moyo` | `Vashandi@2024!` | `CITIZEN` (health_id `b0000000-0000-4000-8000-000000000001`) |
| Org-onboarding operator | `superadmin` | `Impilo@2024!` | all roles (satisfies `ORGANIZATION_ADMIN` gate) |

---

## 1. Clean deploy at the post-E3 anchor

```bash
export REPO_PATH=/opt/impilo/repos/Impilo-vNext
cd "$REPO_PATH"
git fetch --all --prune
git checkout claude/web-session-anchor-nnnkf6
git pull --ff-only
git rev-parse --short HEAD          # 1.1  RECORD THIS — the commit under test
```

**1.2 (recommended) truly clean namespace** — the deploy script reuses the namespace if present:
```bash
kubectl delete namespace impilo-full-preview --wait=true   # skip for an in-place upgrade
```

**1.3 preflight, then deploy** (full estate; `FULLBOOT_SKIP_GATES=1` bypasses only the CI
evidence gate — CI is billing-dead, local gates already ran):
```bash
bash scripts/deploy/full-boot-preview-deploy.sh --preflight
FULLBOOT_SKIP_GATES=1 bash scripts/deploy/full-boot-preview-deploy.sh
# When prompted "Type authorization phrase:" enter exactly:
#   AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

**1.4 verify the deployed commit** (must equal 1.1):
```bash
curl -s http://41.57.127.235/health/version | jq '{environment, fullEstateCommit}'
# expect: environment == "full-preview"  AND  fullEstateCommit starts with the 1.1 short SHA
```
> If `fullEstateCommit` ≠ 1.1, STOP — you are testing the wrong artifact.

---

## 2. Confirm required services are actually running

```bash
kubectl get deploy -n impilo-full-preview
bash scripts/test/run-full-boot-smoke-tests.sh    # readyReplicas >= replicas for required_full_boot
```

Per-service health (from inside the cluster, or via the exposed IP where routed). Fill in ACTUAL:

| Service | k8s name | Port | Health path | Expected | Actual |
|---|---|---|---|---|---|
| Organization registry | `organization-registry-service` | 8153 | `/actuator/health` | UP | |
| Experience BFF | `experience-bff` | 8160 | `/actuator/health` | UP | |
| Workforce governance | `workforce-governance-service` | 8165 | `/actuator/health` | UP | |
| Varapi (provider trust) | `varapi-service` | 8083 | `/actuator/health` | UP | |
| Tuso (facility legitimacy) | `tuso-service` | 8084 | `/actuator/health` | UP | |
| Vashandi (work context) | `vashandi-workforce-service` | 8167 | `/actuator/health` | UP | |
| Tshepo authz PDP | `tshepo-authz-service` | 8081 | `/actuator/health` | UP | |
| Keycloak | `keycloak` | 8080 | `/health/ready` | UP | |
| Kafka | `kafka` | 9092 | (pod Ready) | Ready | |

Example (in-cluster):
```bash
for s in organization-registry-service:8153 experience-bff:8160 workforce-governance-service:8165 \
         varapi-service:8083 tuso-service:8084 vashandi-workforce-service:8167 tshepo-authz-service:8081; do
  echo -n "$s => "
  kubectl run curl-$RANDOM --rm -i --restart=Never --image=curlimages/curl -n impilo-full-preview -- \
    -s -o /dev/null -w '%{http_code}\n' "http://${s}/actuator/health" 2>/dev/null || echo FAIL
done
```
> Any service not UP is a **defect** for the report (name it + its journey letter). Journey
> flags are already baked into the generated preview values at this anchor (EC matching,
> org-registry adjudication + Kafka, governance Kafka, listener auto-startup) — no manual flip.

---

## 3. Confirm realm seed (users/roles resolve)

Log into the shell UI at `http://41.57.127.235` once as **each** principal in §0 and confirm the
login succeeds and lands on `/home` (this proves the realm seed + role mapping). Record pass/fail
per user. A failed login here is a blocking defect for that journey.

---

## 4. Seed the GIVEN fixtures + bridge `seed.env`

Get an admin bearer for the internal/governance plane (password-grant against the shell login,
as `origin.admin.one`), then run the seed pointed at the exposed service ports (override the
in-mesh defaults with the external IP, or run from inside the cluster with the default DNS names).

```bash
cd "$REPO_PATH"
mkdir -p reports/iatg-e2e

# ADMIN_TOKEN = a bearer accepted on the internal plane (origin.admin.one).
# From the VM host, point at the routed IP:port for each service.
ADMIN_TOKEN="<bearer>" \
VARAPI_BASE=http://41.57.127.235:8083 \
TUSO_BASE=http://41.57.127.235:8084 \
WGV_BASE=http://41.57.127.235:8165 \
bash scripts/seed/iatg-e2e-seed.sh | tee reports/iatg-e2e/seed.stdout.txt
echo "SEED_EXIT=${PIPESTATUS[0]}"        # 4.1  RECORD — must be 0

# Bridge the printed IATG_E2E_SEED_OUTPUT block into a sourceable env file:
bash scripts/seed/iatg-seed-to-env.sh reports/iatg-e2e/seed.stdout.txt reports/iatg-e2e/seed.env
cat reports/iatg-e2e/seed.env           # 4.2  RECORD — must contain CLAIM_TOKEN, PROVIDER_PUBLIC_ID,
                                         #       FACILITY_ID, CLAIMANT_HEALTH_ID, EC_NUMBER
```
> The seed now also creates the citizen's EC-bearing HSC employment (E3) so Step 6 can match.
> It is HONEST BY CONSTRUCTION — any HTTP mismatch prints the body and exits non-zero. If
> `SEED_EXIT` ≠ 0, capture the failing call; that is a defect, not something to work around.

---

## 5. Run the runtime harness (incl. adjudication)

```bash
cd "$REPO_PATH"
IATG_SEED_ENV="$REPO_ROOT/reports/iatg-e2e/seed.env" \
PREVIEW_HOST=41.57.127.235 \
CLAIMANT_HEALTH_ID="$(. reports/iatg-e2e/seed.env; echo "$CLAIMANT_HEALTH_ID")" \
RUN_ADJUDICATION=1 \
bash test/integration/iatg-end-to-end-runtime.sh | tee reports/iatg-e2e/harness.stdout.txt
echo "HARNESS_EXIT=${PIPESTATUS[0]}"     # 5.1  RECORD — 0 iff every executed step passed
```

Capture ALL of:
- every `[PASS]`/`[FAIL] step N: …` line (5.2),
- the final `IATG E2E results: P passed, F failed / T` line (5.3),
- `HARNESS_EXIT` (5.1),
- the JSON at `reports/iatg-e2e/<label>/summary.json` (5.4).

Steps proven: 1 country-op (two-person → EXECUTED) · 2 appoint national admin · 3 org onboard
(DRAFT→VERIFIED/ACTIVE) · 4 provider preload (Channel A) · 5 citizen claims provider · **6 EC →
EMPLOYMENT_MATCHED** · 7 four-block trust profile · 8 facility per-source composite · **9
Channel-C claim → ACCEPTED (Kafka)**.
> A single `[FAIL]` aborts the harness with exit 1 — record which step and its response body.

---

## 6. Browser walkthrough (A–E) — the product-journey proof

Open `http://41.57.127.235`. For each journey, log in as the named principal, drive the steps,
and capture a screenshot + the observed terminal state. Fill the evidence table in §7.

### A. Platform-Origin governance + two-person approval
1. Login `origin.admin.one`. Go to **`/platform-origin`**. Confirm country-operations / pending
   actions are **real data** (not placeholder) — or an honest empty state ("No country operations yet").
2. Initiate a country operation (ISO code + display name). Confirm an emerald "Pending action
   created — {accessRequestId}. 2 approvals required" banner and the action shows **PENDING**.
3. Open the action; approve as `origin.admin.one` → progress shows **1 of 2**; the Execute
   button is **disabled** ("Execution unlocks once two distinct approvals…"). (The panel blocks
   the initiator from self-approving as a distinct approver — expected.)
4. Login `origin.admin.two`; approve → **APPROVED** (progress 2 of 2). Execute → **EXECUTED**
   (`data-testid=approval-progress`, button reads "Executed").
   **Terminal state to record:** EXECUTED (or REJECTED / FAILED with a visible reason).

### B. Organization onboarding
1. Login `superadmin`. Go to **`/organization-admin/onboarding`**.
2. Create (DRAFT) → add a representative → submit-verification → verify. Confirm each step
   advances only on real success and ends at **VERIFIED / ACTIVE**.
3. Follow "View in governance registry" → **`/organization-admin/governance`** and confirm the
   org appears in the projection (real backend record, not a dead end).

### C. Facility legitimacy + claim
1. Login any principal. Open **`/facility/{FACILITY_ID}`** (use the seeded `FACILITY_ID`).
2. Confirm the **Legitimacy & platform access** panel shows per-source verdicts:
   `HPA_LEGAL=EXPIRED`, `MINISTRY_OPERATIONAL=REGISTERED_CURRENT`,
   `PLATFORM_OPERATIONAL=GOVERNMENT_OPERATIONAL_EXCEPTION` with an **amber mandatory-reason**
   callout (`data-testid=legitimacy-exception-reason-PLATFORM_OPERATIONAL`) and a platform-access verdict badge.
3. **E3 fail-closed check:** temporarily make Tuso unreachable (e.g. `kubectl scale deploy/tuso-service
   --replicas=0 -n impilo-full-preview`), reload the facility page, and confirm the **explicit**
   notice `data-testid=facility-legitimacy-unavailable` ("…TUSO source-legitimacy service is
   unavailable. Nothing was assumed; the platform-access verdict is withheld…") — **not** a
   silently-missing panel. Scale Tuso back to 1 afterwards.
4. Open **`/facility/claim?facilityUuid={FACILITY_ID}`**, run the appointment-letter path →
   confirm "Administrator request recorded — pending approval". (document / org-invitation lanes
   are honest ComingSoon, not fake success.)

### D. Provider trust + claim
1. Login `citizen.moyo`. Open **`/citizen/wallet/trust`**. Confirm the **four blocks** render,
   each citing a `sourceOfRecord`: identity, professional, employment, operational — a block may
   read UNAVAILABLE ("Source unreachable — nothing fabricated"), but all four must render. EC
   numbers must be **masked** (`maskedEcNumber`); no other person's Health ID appears.
2. Open **`/citizen/provider-claim`**, EC lane: enter the seeded `EC_NUMBER`. Confirm
   `data-testid=provider-claim-ec-asserted` **EMPLOYMENT_MATCHED** (masked EC + asserted provider
   id), or an honest `provider-claim-ec-provider-required` / `provider-claim-ec-unavailable` state.
   **Terminal state to record.**

### E. Failure honesty (spot-check across A–D)
Confirm every unavailable dependency shows an explicit fail-closed message identifying the path
("nothing was changed/fabricated"), and there are **no** 501s surfaced as success, **no** silent
empty panels, **no** endless spinners, **no** fake success. Any 502/failure names the dependency
and the required action.

---

## 7. Evidence to paste back (for adjudication)

Fill and return this. The coordinating session maps it to a verdict.

```
ANCHOR COMMIT TESTED (1.1 / 1.4):        __________  (fullEstateCommit match: yes/no)
DEPLOY STATUS (§2 table):                 all UP? ____  (list any not-UP)
SEED_EXIT (4.1):                          ____   seed.env keys present (4.2): ____
HARNESS_EXIT (5.1):                       ____
  step PASS/FAIL lines (5.2):             <paste>
  results line (5.3):                     <paste>
  summary.json (5.4):                     <paste>
BROWSER A governance terminal state:      ____   screenshot: ____
BROWSER B onboarding terminal state:      ____   projection visible: ____   screenshot: ____
BROWSER C legitimacy verdicts + exception:____   E3 502 notice shown: ____   claim state: ____   screenshots: ____
BROWSER D four blocks + EC masked + EMPLOYMENT_MATCHED: ____   screenshot: ____
BROWSER E failure-honesty spot-checks:    <notes>
DEFECTS (route · user · expected · actual · backend endpoint · logs · proposed fix):
  - ...
```

### Verdict rubric (the coordinating session applies this)

| Label | Condition |
|---|---|
| `IATG_PROVEN_END_TO_END` | commit matches; SEED_EXIT 0; HARNESS_EXIT 0 (incl. step 9); browser A–E all pass; failure-honesty holds |
| `IATG_UI_MERGED_RUNTIME_FAILED` | deploy ok but SEED or HARNESS non-zero (scripts fail before browser proof is meaningful) |
| `IATG_UI_MERGED_BROWSER_JOURNEY_FAILED` | scripts green (exit 0) but one or more browser journeys A–E fail |
| `IATG_BLOCKED_BY_PREVIEW_DEPENDENCY` | a required service (§2) never comes up / deploy cannot complete |
| `IATG_PARTIAL_WITH_DEFECTS` | mixed: most passes with a bounded, enumerated defect list that isn't a clean fit above |

No label is assigned on "should work" — only on the evidence above.
