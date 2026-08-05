# ADR-0055 — Trust-domain membership bootstrap and unresolved controller representation

**Status:** Accepted · **Date:** 2026-08-05 · **Decision by:** Product Owner
**Amends:** frozen architecture v1.3.8 (ADR-0054) → **v1.3.10**
**Implementation note:** v1.3.9 was the first attempt to carry these decisions and was refused freeze — it left `controller_type` NOT NULL against decision 1, and guarded none of decisions 1, 3, 5 and 6. v1.3.10 completes them. This ADR's decisions are unchanged; only their implementation was incomplete.

## Why this is a version and not an erratum

ADR-0054 defines a substantive change as any change to "doctrine, technical design, operating model, schemas, contracts, gates, journey behaviour or acceptance meaning", and requires a new architecture version for one. This amendment changes **schemas** and **technical design**. An additive ADR alone would have breached ADR-0054, so the correction lands as a narrowly scoped **v1.3.9**. Nothing else in v1.3.8 is reopened.

## The inconsistency

v1.3.8 held three positions that cannot all be true:

1. **§3.1** — a trust domain is a governed data-control and disclosure boundary, and **is not itself necessarily a legal person or a controller**.
2. **§3A** — legal controllership resolves through `processing_role_assignment` per (scope, data domain, purpose), and an unresolved controller must be **refused and flagged**, never defaulted.
3. **§3.2** — nevertheless placed **mandatory** controller identity on `trust_domain`, required `organisation.trust_domain_id NOT NULL`, required **every existing organisation to be backfilled to `MOHCC-ZW`**, and required every facility to carry an immediate non-null trust domain.

Position 3 manufactures governance facts before anyone determines them. The repository's closed `org_type` vocabulary already contains `PRIVATE_PROVIDER_GROUP` and `LOCAL_AUTHORITY`; a blanket backfill would record those as MoHCC and then enforce it with `NOT NULL`. **Organisation type is not authoritative evidence of trust-domain membership.**

The blanket backfill is rejected. **This decides no `[L]` matter.** It corrects how an undetermined fact is represented, which is the opposite of deciding it.

## Decisions

**1. Trust-domain identity is not controller identity.** `trust_domain` must not require a fabricated legal controller in order to exist. `controller_type`, `data_controller_legal_name` and `data_controller_contact` become **nullable and non-authoritative** — descriptive metadata only. **No service may read them as a fallback when controller resolution fails.** §3A.4 remains the only authority.

**2. Membership is an explicit governed relationship.** `trust_domain_membership` carries membership id, trust domain, subject type (`ORGANISATION` | `FACILITY`), subject id, status, effective period, source authority, provenance, created by/at, reviewed by/at and supersession. Statuses: **`UNMAPPED` · `PENDING_REVIEW` · `ACTIVE` · `SUSPENDED` · `ENDED`**.

**3. No blanket backfill.** Membership is never inferred from organisation type, hosting location, platform operator, name prefix, tenant default, `national-spine`, facility ownership or regulator status. Organisations without authoritative evidence remain **`UNMAPPED`**.

**4. MoHCC membership requires evidence** — the Ministry facility or organisation registry, a governed organisational hierarchy, an approved accreditation or onboarding decision, or another source the architecture designates. Private-provider, local-authority, mission, security-sector, university and regulator categories are **not** assumed MoHCC merely by existing in the national registry.

**5. Facility membership follows governed organisation and facility authority.** A facility may remain unmapped until its governing organisation is resolved, its membership approved, and the required legitimacy and affiliation evidence exists. A denormalised `trust_domain_id` on organisation or facility is a **query projection**, maintained only after an ACTIVE membership exists, and **never the source of truth**.

**6. `UNMAPPED` is visible and fail-closed.** It is not blank and not a default. Operations requiring an active trust domain refuse with `TRUST_DOMAIN_UNMAPPED`, `TRUST_DOMAIN_MEMBERSHIP_PENDING` or `TRUST_DOMAIN_MEMBERSHIP_INACTIVE`. Each refusal names the governance action required, fabricates no substitute, writes an audit event, grants no cross-domain access — and **does not prevent safe registry review or draft creation**. Refusal is operation-specific; making the open question unrecordable would defeat the model.

**7. MoHCC family identity does not settle controllership.** The `MOHCC-ZW` family may exist as a governance identity without declaring whether MoHCC or a hospital controls a given record estate. Controller-dependent operations still resolve through `processing_role_assignment`. **The 18 `[L]` decisions remain open.**

## Acceptance conditions

| # | Condition |
|---|---|
| **B1** | A `PRIVATE_PROVIDER_GROUP` organisation is not automatically assigned to `MOHCC-ZW` |
| **B2** | A `LOCAL_AUTHORITY` organisation is not automatically assigned to `MOHCC-ZW` |
| **B3** | An unmapped organisation is represented explicitly as `UNMAPPED`, never as blank or absent |
| **B4** | A trust domain can exist and be accredited while controller resolution remains `UNDETERMINED` |
| **B5** | An unmapped organisation cannot perform a trust-domain-dependent operation, and the refusal names the governance action |
| **B6** | Draft governance work remains possible while membership is `UNMAPPED` |
| **B7** | Later approved membership requires no destructive remodelling — one membership row, no schema change |
| **B8** | No controller is inferred from membership, at any status |

## Consequences

Bootstrap becomes slower and more honest: every organisation must be mapped deliberately, with evidence, instead of arriving pre-mapped by a migration. That cost is the point. The alternative was a database in which 100% of organisations claimed MoHCC membership and none of those claims could be traced to a decision.

v1.3.8 remains the frozen baseline for the period 2026-08-05 up to this amendment, archived with a banner recording that it **was** genuinely frozen — unlike v1.3.1–v1.3.7, which were refused. v1.3.9 is frozen on the same terms and inherits every control ADR-0054 established.
