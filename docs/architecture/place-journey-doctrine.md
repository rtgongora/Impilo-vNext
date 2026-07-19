# Impilo Place Journey Doctrine (Tuso + Indawo)

**Status:** ADOPTED (PO-approved program plan, 2026-07-19). Governs the **place identity** layer — how
health facilities (TUSO) and regulated premises / public-health sites (INDAWO) are discovered, claimed,
registered, verified, linked, administered, inspected, and published. Sibling of the
[Identity Journey Doctrine](identity-journey-doctrine.md) (persons) and successor to the IATG registry-truth
work ([`identity-access-trust-governance.md`](../doctrine/identity-access-trust-governance.md) §5): IATG built
per-source facility legitimacy, the claim/appointment rail, and the adjudication contract; this doctrine adds
the journey, anti-enumeration, linkage, and verification layers on top — and hardens the enumeration leak the
IATG Wave-2 eligibility surface shipped with.

> **Governing rule:** *Search before create — but keep matching private. Verify both the claimant and their
> authority. Maintain separate Tuso and Indawo canonical records, link them through shared geography and
> explicit typed relationships, and expose only approved public-directory projections.*

---

## 1. Two registries, one trust model

```text
PUBLIC / OPERATOR / INSPECTOR PRESENTS
Facility code │ Site code │ Licence │ QR │ Map pin │ Legacy code │ Permit
                         │
                         ▼
              TSHEPO PLACE-TRUST WORKFLOW
   claimant identity • authority to represent • alias resolution
              disclosure control • audit
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
          TUSO                    INDAWO
   Health service facility     Regulated premises / PH site
   facility_uuid (canonical)   site_id (canonical)
   Clinical topology           Geospatial/jurisdiction
   Services/capabilities       Inspection/compliance
              │                     │
              └──────────┬──────────┘
                         ▼
           SHARED GEOSPATIAL ANCHOR (NDILA)
     coordinates • boundary • jurisdiction • campus
     explicit typed relationships — never silent merging
```

Unlike a person (one canonical identity), one physical location can legitimately host **multiple separately
governed entities**: a hospital (Tuso) with an incinerator (Indawo) and a water point (Indawo) on campus; a
school (Indawo) hosting a school clinic (Tuso); a port of entry (Indawo) with a port-health service point
(Tuso). Therefore: **never merge Tuso and Indawo records — link them** (§6).

**System-of-record boundaries (unchanged by this doctrine):** TUSO owns facility identity, topology,
capability, HPA facility regulation. INDAWO owns site identity, category regulation, site inspection,
surveillance sites. **NDILA** owns geography only (locations, anchors, proximity, tiles) — never names,
status, or authority. **org-registry** stays a relationship registry. **rito** owns complaint/quality cases.
PCT **materialises** queues from Tuso truth; it never defines facilities.

## 2. The load-bearing security boundary (places)

The person-identity rule (Identity Journey Doctrine §2) applies to places with one addition: a place claimant
must prove **two different things** — *who they are* (person identity, consumed from the person-proofing
framework) and *why they are authorised to represent this place*. **Knowing the facility name, address,
licence number, or code is never sufficient** — those identify a claim target; they prove nothing.

Structural rules (enforced, not advisory):
- **No candidate lists, no administrator disclosure.** Private server-side matching only. The claimant never
  sees which record matched, who administers it, or whether it exists, until authority is proven.
- **Generic responses everywhere** — never "this facility is already administered", "licence suspended",
  "no such facility". Eligibility endpoints return claim-submitted semantics; verdict detail lives in the
  steward/Trust-Console workflow. *(This retires the current `FacilityClaimController` eligibility behaviour,
  which discloses the platform-legitimacy verdict to unproven claimants — an enumeration leak.)*
- **Proof of authority** requires at least one independent factor: OTP to a contact **on record** for the
  place, an invitation token from an existing ACTIVE administrator, documentary evidence cross-checked
  against regulator (HPA/council) data, or a completed place-verification event — layered on verified person
  identity.
- **Silent duplicate detection** at registration: a credible match **blocks creation silently** ("registration
  received, under review") and opens a steward case; the matched candidate is never disclosed.
- **Registry administration is a third interface.** Public directory search ≠ operator claim/self-service ≠
  authorised registry administration. Stewards merge; operators respond; the public reads projections.
- **Anti-abuse:** per-account/per-device claim throttles; repeated probing of identifiers escalates;
  notification to existing administrators on claim attempts where appropriate.

## 3. Identifier model

| Identifier | Purpose | Exposure |
|---|---|---|
| Tuso `facility_uuid` | Canonical facility identity (the seam PCT/Ndila/org-registry/Vashandi key off) | Internal/platform |
| Public facility code (`facility_code`) | Human-readable lookup credential | Public: signage, QR, referrals |
| Indawo `site_id` | Canonical site/premises identity | Internal/platform |
| Public site code (`site_code`) | Premises/site lookup credential | Public/operator per category |
| Ndila anchor id | The physical location/parcel/campus | Internal, shared |
| External aliases | HPA number, DHIS2 UID, GOFR id, eLMIS/LIMS codes, council permits, school codes, legacy MFL codes | Resolved per policy via `tuso.facility_identifier` / Indawo alias records |

Tuso and Indawo identifiers are **never derived from one another**; each registry owns its identifier and
lifecycle. Legacy/external codes are **governed aliases**, never primary identity.

## 4. Trust is multidimensional — no `VERIFIED = TRUE`

Tracked per record in dedicated dimension tables (`facility_trust_dimension` / `site_trust_dimension`),
materialised from underlying signals; `facility_source_legitimacy` (per-source allow/deny, "no source denies
AND at least one allows; silence never grants") remains the platform-access **gate** and becomes one input:

| Dimension | Question | Primary signal |
|---|---|---|
| EXISTENCE | Is the place proven to exist? | verification cases (photo/GPS/video/inspection) |
| GEOSPATIAL | Point, boundary, jurisdiction accurate? | Ndila anchor + geocode review |
| AUTHORITY | Is this user entitled to manage it? | role-scoped appointments/assignments |
| LEGAL_IDENTITY | Owner/operator correctly identified? | source legitimacy + documents |
| REGULATORY | Licence/permit valid? | HPA profile (Tuso) / licences (Indawo) |
| CAPABILITY | Claimed services actually available? | validated capabilities, inspection |
| OPERATIONAL | Open, closed, suspended? | operational status axes |
| COMPLIANCE | Compliant / remediation / enforcement? | inspection + compliance actions |
| PUBLIC_DISCLOSURE | What may be published? | projection/disclosure policy |

A hospital can be real, accurately mapped, legally recognised — and temporarily closed, or prohibited from a
service. A food premises can exist and be mapped while its permit is suspended. **Status changes never delete
identity.**

## 5. Journey catalogs

Legend — **Built**: works today · **Reuse**: substrate exists, journey needs wiring · **Gap**: net-new ·
program wave in `[ ]` (see the program plan / decision record).

### Facility journeys (FJ1–FJ9, Tuso)

| # | Journey | State | Anchor / gap |
|---|---|---|---|
| FJ1 | Claim an existing facility (role-scoped, proof-of-authority) | Reuse | V017 `facility_admin_appointment` rail + `FacilityClaimController`; **anti-enum + roles + expiry gap** `[W4]` |
| FJ2 | Register a new facility (silent duplicate detection) | Gap | import-time matcher exists (`FacilityMasterImportService`); live `FacilityMatchService` + registration cases net-new `[W6]` |
| FJ3 | Verify the physical facility (guided geotagged photos, live GPS, documents) | Gap | document-service evidence + `facility_verification_case` net-new; GPS plausibility vs Ndila anchor `[W6]` |
| FJ4 | Remote video facility verification (decision retained, not recording) | Gap | rtc-gateway media session + decision record in verification case `[W6]` |
| FJ5 | Configure departments & service points (governed catalogues → PCT queues) | Reuse | `FacilityUnitEntity`/`ServicePointEntity`/virtual-service registry live; `FacilitySetupService` per ownership split; PCT materialisation seam untouched `[W8]` |
| FJ6 | Update facility (low-risk self-service vs high-risk governed) | Reuse | update controllers exist; field-classification map + steward routing net-new `[W8]` |
| FJ7 | Transfer ownership/management (both parties verified, identity retained) | Gap | transfer cases net-new; appointments close/open append-only `[W8]` |
| FJ8 | Recover facility-administration access (no new facility record) | Reuse | FJ1 with stronger-assurance claim-type flag `[W4]` |
| FJ9 | Report duplicate/incorrect/fake facilities (steward-only merges) | Reuse | import row-review + `merged_into_id` substrate; report intake + `FacilityMergeService` net-new `[W8]` |

### Site journeys (SJ1–SJ6, Indawo)

| # | Journey | State | Anchor / gap |
|---|---|---|---|
| SJ1 | Claim an existing premises/site (operator role, limited scope) | Reuse | `SiteAssignmentEntity` + `SiteOperatorEntity`; **roles + expiry + anti-enum gap** `[W4]` |
| SJ2 | Register a new premises/site (category forms, silent dup + proximity match) | Reuse | `SiteApplication` rail (V003); category-specific config + `SiteMatchService` net-new `[W6]` |
| SJ3 | Request inspection / licence verification (competent authority, verified inspector) | Reuse | `SiteInspection` + checklist templates live; intake + inspector-jurisdiction policy `[W8]` |
| SJ4 | Respond to remediation (operator can never alter the original finding) | Reuse | `SiteComplianceAction` live; finding immutability enforcement `[W8]` |
| SJ5 | Citizen complaint / hazard report (complainant identity protected) | Reuse | rito `PublicCaseIntakeService` claim-codes; rito→Indawo **private** site matching net-new `[W8]` |
| SJ6 | Closure / demolition / change of use (status change, never delete) | Reuse | three-axis status live; `REPLACED_BY` place link + lifecycle episodes `[W8]` |

The operator may respond to findings but must never be able to alter the original inspection record (append
corrections only). Inspectors authenticate with their **own** workforce/provider credential; the badge selects
identity, TSHEPO verifies current role, jurisdiction, and assignment.

## 6. Typed relationships + the shared geospatial anchor

Typed Tuso↔Indawo links live in **Indawo** (`ind_place_links`, single writer, served both directions,
event-published). Vocabulary (enum, closed):

| Relationship | Example |
|---|---|
| LOCATED_WITHIN | Port-health clinic in a port of entry |
| SAME_CAMPUS_AS | Hospital and its regulated waste-treatment site |
| CONTAINS | Hospital campus containing a public water point |
| HOSTS_SERVICE_POINT | School hosting a school-health clinic |
| TEMPORARY_SERVICE_AT | Mobile clinic operating at a market |
| REGULATED_COMPONENT_OF | Food premises within a hospital |
| REPLACED_BY | Old premises replaced by a new location |
| SERVES | Health facility responsible for a defined public-health site |

The **shared geospatial anchor** is Ndila's job: `ndila_place_anchor` (centroid, optional boundary,
jurisdiction, campus label) referenced by owner-keyed `ndila_locations` rows for both `TUSO/FACILITY` and
`INDAWO/SITE`. Anchor assignment is a **stewarded** operation. Linked records share geography but retain
separate authorities, licences, inspections, and lifecycles.

**Premises boundary (both models stay):** `tuso.facility_premises` is the HPA occupancy/licensing axis
*internal to clinical facilities* (shared-campus occupancy, RFI, council review). `ind_sites` is the SoR for
*category-regulated public-health sites*. Where one building carries both meanings, express it as
`LOCATED_WITHIN` / `REGULATED_COMPONENT_OF` links. **Never auto-create a site from a premises or vice versa.**

## 7. Proof of place (verification)

A place has no biometrics. Its proofing equivalents: live GPS position, guided geotagged photo capture
(entrance → signage → service areas, captured *now*, not uploaded stock photos), licensing/utility/property
documents, live remote video walkthrough, authorised district/regulator verification, physical inspection.
Evidence lives in **document-service** (refs only in registries); the **decision** (who reviewed, what was
observed, verdict, timestamp) persists in `facility_verification_case` / `site_verification_case`; video
recordings are not retained by default. Static uploaded photographs alone are weak evidence and never
sufficient. Verification updates the trust dimensions (§4) **separately** — it never flips one global flag.
Biometrics may verify the *person* claiming authority, the PIC, the inspector, or the approving official —
via the person-proofing framework, never a place-local mechanism.

## 8. Place credentials (QR)

An approved facility/site may receive a certificate, printable card, window sticker, and **signed QR
credential** (`facility_credential` / `site_credential`, locally-held Ed25519 keys, revocable, expiring).
Scanning opens a **policy-filtered public verification page**: official name, type, public identifier,
verified location, current public status, permitted services, certificate validity, directions/contacts as
approved. It must never expose internal registry IDs, confidential findings, complainant identities, or
administrators' personal details. Certificate QR (HPA `PublicCertificateVerificationController`) and the
facility/site credential QR are distinct artifacts. A suspended permit revokes/annotates the credential —
it never deletes the identity.

## 9. Public projections — three interfaces, one truth

Public directory search reads **projections** (redaction-at-read, declaratively defined), never the master
registry. Tuso's public projection (name, code, location, hours, public contacts, level/ownership category,
verified services, accessibility, referral info, public operational status) already nulls internal fields —
formalised and kept. Indawo disclosure is **per site category** (`ind_site_disclosure_policy` allowlists):
a water point's safe-water status is public; a food premises' permit status is public; complainant
identities, inspection notes, investigation evidence, owner PII, and draft enforcement are **never** public.
Public lanes run anonymous on the public tenant (`PublicGatewayAnonymousDefaultsFilter`; Envoy strips trust
headers) — caller tenant is never authoritative there.

## 10. Inspection engines stay separate

Tuso's HPA engine (V018–V021: 38 checksum-provenance modules, premises, RFI, council review, certificates)
serves **health-facility regulation** under HPA. Indawo's site engine (applications, licences, inspections,
checklist templates, compliance actions, enforcement cases) serves **category regulation** under councils /
environmental health / port authorities. Different competent authorities, different lifecycles — two engines,
one doctrine: findings signed and immutable, remediation tracked to authorised closure, public status per
disclosure policy. Adopting the V019 checksum content-catalogue format for Indawo checklists is a noted
later consolidation opportunity, not a merge.

## 11. Relation to IATG

IATG (2026-07) established: honest per-source facility legitimacy (V016), the append-only claim/appointment
rail (V017), org-registry relationships, the adjudication contract (fixed workflow definition ids; append-only
`wgv_adjudication_decision`; TSHEPO consumes decisions as policy inputs), and the Trust Console — under a
deliberate freeze of the enforcement PDP. This doctrine + program is the successor: it **extends** the claim
rail with role scopes, expiry, and proof-of-authority; **consumes** the adjudication contract for disputed
claims and duplicate stewardship; **fixes** the eligibility enumeration leak; and lands the enforcement rules
through the sanctioned channels (tshepo-authz `policy_rule` seeds + `impilo.authz` rego, SHADOW→ENFORCE) that
IATG left open. `services/tshepo-service` remains NO-TOUCH.

## 12. Verdict model & acceptance

The 3-tier verdict of the Identity Journey Doctrine §8 applies unchanged:

| Tier | Place meaning |
|---|---|
| **SOFTWARE_CONTRACT_GREEN** | FJ/SJ journeys + anti-enum + projections + immutability pass the gateway-authenticated pack |
| **EXTERNAL_INTEGRATION_GREEN** | real HPA/council/DHIS2/CRVS-adjacent links live |
| **NATIONAL_PRODUCTION_GREEN** | authority sign-off, performance/DR, SOPs |

Acceptance pack: `tests/place-contract/` mirroring [`tests/identity-contract/`](../../tests/identity-contract/)
(gateway-authenticated personas — operator, steward, inspector, regulator, anonymous citizen;
SKIP-never-false-PASS). Mandatory adversarial cases: **claim-by-knowledge-only fails** (name/address/licence
number → generic response, no binding, no enumeration), silent duplicate block, projection redaction
(forbidden fields *absent*, not nulled), finding immutability, complaint identity isolation, recovery creates
no new record, transfer retains identity, merge re-points aliases/links/anchors with events.
