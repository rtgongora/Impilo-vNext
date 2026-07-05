# IATG Wave 1 — Lease Record

Delivery-boundary record for the **Identity, Access and Trust Governance (IATG) Wave 1**
program (opened 2026-07-05, base anchor `74c22f480`). This file is the single source of
truth for what each workstream may and may not touch while Wave 1 branches are in flight.

Doctrine being implemented:
[`docs/doctrine/identity-access-trust-governance.md`](../doctrine/identity-access-trust-governance.md).
Demo proof path: [`docs/demo/iatg-wave1-demo-script.md`](../demo/iatg-wave1-demo-script.md).

---

## 1. Program-wide invariant

> **`services/tshepo-service/**` is NO-TOUCH for every IATG workstream.**

The ext_authz path is frozen for the whole program. Pre-existing fail-open defects in
tshepo-service are *flagged for a future RED wave* — they are not fixed, patched, or
"drive-by improved" in any Wave 1 branch. Any change under `services/tshepo-service/`
in an IATG branch fails coordinator review automatically.

## 2. Per-workstream FORBIDDEN lists

| Workstream | Scope (summary) | FORBIDDEN in this workstream |
|---|---|---|
| **WS-A** — Platform origin + country operation + two-person approval | workforce-governance origin/country surfaces | `GovernanceInternalController` beyond its own additive endpoints is the *only* governance internal surface — no other WS may add to it; `wgv_bootstrap_*` schema objects are WS-A-owned and frozen to others; BFF `admingovernance` surface (see §3: owner NOBODY — WS-A holds the production realm file only) |
| **WS-B** — organization-registry-service (new, port 8153) | new service scaffold + org/rep/affiliation/claim model | No governance code or schema changes (workforce-governance untouched); any governance-data consumption is **mirror, one-way only** — org-registry never writes back |
| **WS-C** — varapi trust + channel typing | provider trust blocks, channel typing, registry statuses | No deletion of the **4 legacy status axes** in varapi — new vocabulary is additive alongside them |
| **WS-D** — workforce-governance HSC employment + BFF trust composition | employment records, trust profile composition | `AuthSessionController` EC alias untouched; **vashandi migrations frozen** (no new vashandi Flyway files) |
| **WS-E** — tuso facility source legitimacy | per-source legitimacy model | `FacilityEntity` and existing enums **frozen** (additive tables/columns only); **no claim flows** (claims are Channel C / org-registry territory) |
| **WS-F** — varapi verification attempts + BFF client | verification attempt recording | **No edits to existing varapi files** — new files only (WS-C owns the existing varapi surface until merged) |
| **WS-G** — adjudication state machines | workflow-service adjudication definitions | Workflow **engine code frozen** (definitions/config only); `AccessRequest` untouched |
| **WS-H** — doctrine + lease docs (this workstream) | GREEN, documentation only | All code; `docs/registry/services-registry.yaml`; `docs/runbooks/port-allocation.md`; `docs/registry/system-of-record-map.md` (owned by another workstream) |

## 3. Experience BFF single-owner file map

Exactly one workstream may modify each BFF file/surface below. Anything not listed
follows normal lease rules (first-claimer registers here before touching).

| BFF file / surface | Single owner |
|---|---|
| `WorkforceGovernanceClient` | **WS-D** |
| `VarapiServiceClient` | **WS-F** |
| `application.yml` + `pom.xml` + registry docs (`services-registry.yaml`, `port-allocation.md`, `system-of-record-map.md`) | **WS-B** |
| Ownership docs (`service-ownership-matrix.md`, `forbidden-responsibilities-map.md`, this lease file, doctrine + demo docs) | **WS-H** |
| Production Keycloak realm | **WS-A** |
| `AuthSessionController` + BFF `admingovernance` surface | **NOBODY** (frozen for all of Wave 1) |

## 4. Governance Flyway merge order

Governance-schema migrations are pre-assigned and the merge order is **mandatory**:

> **A (V005/V006) → D (V007) → G (V008)**

WS-D must not merge before WS-A; WS-G must not merge before WS-D. A branch that renumbers
to "get ahead" fails review.

## 5. Pre-assigned migration numbers

| Schema / service | Migration(s) | Workstream |
|---|---|---|
| workforce-governance | `V005`, `V006` | WS-A |
| workforce-governance | `V007` | WS-D |
| workforce-governance | `V008` | WS-G |
| tshepo-authz-service | `V031` | WS-A |
| varapi-service | `V017`, `V018` | WS-C |
| tuso-service | `V016` | WS-E |
| workflow-service | `V003` | WS-G |
| organization-registry-service | `V001`–`V003` | WS-B |
| vashandi-workforce-service | *(none — frozen)* | — |

No workstream may create a migration number outside its pre-assignment. Gaps are filled
by coordinator re-assignment only, never by self-service renumbering.
