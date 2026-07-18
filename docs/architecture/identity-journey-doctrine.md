# Impilo Identity Journey Doctrine

**Status:** RATIFIED (PO ruling, 2026-07-19). Governs the human identity **journey** layer that sits on the
identity **spine** ([`identity-trust-contract.md`](identity-trust-contract.md)). Where a journey and the spine
contract disagree, the spine contract wins on identifiers; this doctrine governs the *flow*, *assurance*, and
*safety* of the journeys that use them.

This is the canonical operating model: not "generate an Impilo ID", but making sure a client or provider can
**enter, identify, receive care, work, delegate, recover, replace credentials, handle exceptions and complete
every journey without creating parallel identities** — at national scale.

---

## 1. The one identity spine, many journeys

```text
CLIENT / PROVIDER CREDENTIALS
Impilo ID │ Digital/Physical/Smart card │ QR │ Biometrics │ National ID │ Passport │ Phone │ Passkey
                                        │
                                        ▼
                              TSHEPO TRUST CORE
        Identity resolution │ assurance │ consent │ authorisation │ tokens
                          Canonical Health ID (HID)
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
             VITO CLIENT REGISTRY                      BUTANO SHR
             CRID + demographics                       CPID + clinical record
             matching / deduplication                 no PII, no matching
```

At point of care TSHEPO resolves the presented credential to the internal HID, then **separately** obtains the
CRID (demographics, VITO) and CPID (clinical, BUTANO). The application receives a **short-lived patient context
token**, never permission to join the databases directly. VITO remains the PII + deduplication authority; the SHR
operates only on CPID.

Supporting services (SoR map): **VITO** demographics/identifiers/proofing/matching/lifecycle · **TSHEPO** HID/mapping/
trust/tokens/consent/assurance · **BUTANO** longitudinal clinical (CPID) · **PCT** visits/appointments/queues/
encounters/referrals · **VARAPI** provider identity/registration/licence/scope · **VASHANDI** employment/posting/
roster · **TUSO** facility/service-point/ward/workspace · **ABIS** protected biometric templates + matching ·
**Keycloak/eSignet** authn/MFA/passkeys/session · **UBOMI** CRVS / civil-identity gateway · **Card service** card
lifecycle · **mvumo** consent + **act-on-behalf relationships (delegation SoR — ratified)** · **Nompilo** guidance.

---

## 2. The load-bearing security boundary (PO-flagged risk)

> **Search before create — but never expose the Client Registry as a public people-search.**

A self-service user submits an identity **claim**; VITO performs **private, server-side** matching; TSHEPO requires
**sufficient proof** before any record, identifier, or personal detail is disclosed or bound. **Merely knowing
someone's details must never cross from *private match* to *account↔HID binding*.**

Structural rules (enforced, not advisory):
- **No public candidate list.** Discovery is separated from disclosure: VITO may internally find candidates; the
  public user never sees them. Even masked demographics are shown only *after* the claimant proves possession of
  something already linked to the record.
- **Name/DOB/address/phone alone grant nothing** — they may produce `MATCH_CANDIDATE_FOUND / STEP_UP_REQUIRED`,
  never `HID linked`. Binding requires a **second independent factor** (OTP-to-recorded-contact, card, QR, selfie+
  liveness, biometric, video, or in-person).
- **Generic responses everywhere** — never "already registered", "no record for this person", "attends the X
  clinic". The detailed reason lives only in the audit/identity-review workflow.
- **Constant-time + anti-enumeration** — uniform response shape and a timing floor on hit and miss alike (the
  existing `SilentIdentifierResolutionService` 120 ms floor is the pattern; extend it to contact matching).
- **Anti-abuse** — per-account and per-device throttles, login lockout, repeated-unrelated-lookup escalation, and
  **notification to the genuine account holder** on suspicious recovery/claim attempts.
- **Staff search is different** — an authenticated workforce identity, in a facility/workspace context, with a
  purpose-of-use (e.g. TREATMENT), a legitimate reason (patient presence), minimum-necessary masked results, and
  full audit. Even staff see only enough to confirm the correct person; no bulk browsing.

---

## 3. When is a journey genuinely complete?

A registration, recovery, or care journey is **not** complete because a screen says "success". It is complete when
**all** of:
1. the correct HID was identified or created;
2. the HID is mapped to **one** CRID and **one active** CPID;
3. the presented credential is bound to that HID;
4. identity **and** session assurance were recorded (the 4-dimension vector, §5);
5. consent or an authorised exception was resolved;
6. the requested transaction completed (account link / appointment / visit / record retrieval / …);
7. audit and provenance were written;
8. the person receives a clear confirmation **and next step**.

---

## 4. Assurance is an **outcome** ladder feeding the 4-dimension vector

Do not reduce trust to verified/unverified. Every journey records an **assurance outcome**, which maps into the
existing decision vector (`PolicyEngine.deficientAssuranceDimension`: IAL / AAL / record-link confidence / session).

| Assurance outcome | Typical evidence | Feeds |
|---|---|---|
| SELF_ASSERTED | phone/email + demographics only | IAL=LOA1 |
| RECORD_LINKED | existing Impilo credential + OTP/corroboration | IAL≥LOA2, RLC↑ |
| DOCUMENT_VERIFIED | valid ID document reviewed (OCR + integrity) | IAL≥LOA2 |
| AUTHORITATIVELY_VERIFIED | CRVS / National-ID source match (via UBOMI) | IAL=LOA3 |
| BIOMETRICALLY_BOUND | verified biometric linked to HID (ABIS) | IAL=LOA3, AAL↑ |
| REMOTELY_WITNESSED | authorised video-verification decision | IAL≥LOA2 |
| IN_PERSON_WITNESSED | authorised officer physically inspected evidence | IAL=LOA3 |
| DISPUTED / REVIEW | conflicting identity evidence | blocks (RLC=DISPUTED) |

TSHEPO decides whether the achieved combination is adequate **for the requested action** — booking an appointment
needs less than viewing sensitive results or changing consent. The proofing orchestrator chooses the
**least-burdensome sufficient route**, and an **assisted + in-person pathway is always retained** for people who
cannot self-serve. **Care is never denied** for missing National ID, smartphone, or a failed biometric.

---

## 5. The 40-journey catalog

Legend — **Built**: works today · **Reuse**: substrate exists, journey needs wiring · **Gap**: net-new · wave in `[ ]`.

### Client journeys (CJ1–CJ20)

| # | Journey | State | Anchor / gap |
|---|---|---|---|
| CJ1 | Browse & obtain services without logging in | Built | public gateway; Nompilo explains when login is needed `[H]` |
| CJ2 | First-time self-registration (private dedup) | Reuse | `ProvisionalHealthIdController` + **private matching** `[F,G,H]` |
| CJ3 | Existing client claims their record | Gap | account-shell→private match→2nd factor→`client_authorization_link` `[F]` |
| CJ4 | Recover a forgotten Impilo ID | Reuse | `PortalController` recovery; **verify is a stub** `[F]` |
| CJ5 | Recover account after lost phone / SIM | Reuse | device/session revocation (tshepo-authz) + recovery `[F]` |
| CJ6 | Digital Impilo card issuance | Built | `PortalController /health-id/qr`, wallet UI |
| CJ7 | Request a physical card | Built | `IssuanceController`/`PrintJobController`/card-print-agent |
| CJ8 | Lost/stolen/damaged card → replace | Reuse | `/cards/{id}/revoke` exists; **citizen "report lost card" UI** gap `[H]` |
| CJ9 | Biometric self/assisted enrolment | Reuse | consent+capture; matcher via ABIS `[G,X1]` |
| CJ10 | Returning client identified by biometric | Reuse | search-first→1:1 verify; 1:N candidates `[G,X1]` |
| CJ11 | Book appointment + self-check-in | Reuse | booking-service + kiosk; **signed appointment token** gap `[H]` |
| CJ12 | View personal health information | Built | patient-context token; VITO banner + BUTANO record |
| CJ13 | Manage consent & privacy + access log | Reuse | `PortalConsentController` + `AccessHistoryController`; unify surface `[H]` |
| CJ14 | Add a child / dependant | Gap | own HID/CRID/CPID/Impilo ID + guardian relationship `[J]` |
| CJ15 | Authorise a caregiver / representative | Gap | act-on-behalf on mvumo `[J]` |
| CJ16 | Update demographic details (low/high-risk) | Reuse | VITO update + steward for high-risk; SHR unchanged `[H]` |
| CJ17 | Report duplicate / incorrect identity | Reuse | dedup/merge steward workflow (audited) `[H]` |
| CJ18 | Foreign national / person without National ID | Reuse | passport/programme-id; Impilo ID regardless of citizenship `[H]` |
| CJ19 | Emergency unidentified client | Built | `V11IdentitiesController` provisional → merge-reconcile `[K]` |
| CJ20 | Offline registration & reconciliation | Built | tshepo-offline O-CPID → relay `subject.reconciled` `[K]` |

### Provider journeys (PJ1–PJ18)

A provider is **first a person, then a regulated professional, then a worker in a facility context.**

| # | Journey | State | Anchor / gap |
|---|---|---|---|
| PJ1 | Create a personal account | Reuse | citizen account + personal proofing `[I,G]` |
| PJ2 | Request Provider Access | Built | `ProviderClaimController` eligibility/preview/claim |
| PJ3 | No provider record found | Built | council resolver + provider-verification case |
| PJ4 | Request facility access | Reuse | vashandi + varapi affiliations; facility approval `[I]` |
| PJ5 | Provider daily sign-in + workspace select | Reuse | session assurance; **duty-scoped workforce token** gap `[I]` |
| PJ6 | Provider card / staff badge | Reuse | card-print-agent `PROVIDER_CARD`; **sign-in resolve path** gap `[I]` |
| PJ7 | Identify a returning client at point of care | Built | masked search + QR resolve + patient context |
| PJ8 | Register a new client (search→create→visit) | Reuse | one continuous journey to PCT visit `[I,H]` |
| PJ9 | Use biometric identification | Reuse | 1:1 verify / controlled 1:N `[G,X1]` |
| PJ10 | Document clinical care | Built | CPID context + provenance; no PII to SHR |
| PJ11 | Sensitive / high-risk action (step-up) | Built | `@StepUpRequired` + scope + consent |
| PJ12 | Telemedicine encounter | Built | PCT telehealth + rtc-gateway; join by session |
| PJ13 | Offline care | Built | offline capability tokens + reconcile `[K]` |
| PJ14 | Provider self-service profile management | Built | varapi profile; regulator-gated authoritative fields |
| PJ15 | Licence expiry / suspension / restriction | **Broken** | `LicenceRenewalSweep` lapses but `PrivilegeRevocationConsumer` misses LAPSED → **no revocation** `[I]` |
| PJ16 | Transfer to another facility | Reuse | vashandi posting end/start; context swap `[I]` |
| PJ17 | Provider account recovery | Reuse | stronger assurance than citizen; relationships intact `[I,F]` |
| PJ18 | Provider identity/professional-record dispute | Built | governed case; no self-edit of authoritative fields |

### Remote proofing journeys (shared by clients & providers) — `[G]` + external tracks

Common sequence: enter National ID/passport → verify with authoritative source (UBOMI→CRVS) → if unavailable/no
match, upload document images + live selfie + liveness → automated document + face comparison → result assessment
(pass / inconclusive→assisted video or in-person / fail→review) → account↔HID (or Provider ID) binding approved.

- **PR1 National-ID/CRVS verification** — via UBOMI; minimum verified response / opaque token; failure changes the
  *proofing level*, never the entitlement to a health identity `[X2]`.
- **PR2 Document + selfie + liveness** — front/back ID + **live** selfie (not a stored photo) + liveness action;
  document quality/tamper check, field extraction, doc-photo↔selfie compare, minimum-retention evidence, score +
  algorithm version recorded, borderline→review `[G]`.
- **PR3 Assisted video verification** — authorised registrar session (reuse rtc-gateway lobby/admit/token); records
  a **decision** (verified / provisionally verified / more evidence / refer in-person / suspected impersonation),
  not a raw recording by default `[G]`.
- **PR4 In-person verification** — highest-availability fallback; officer records method, masked document ref,
  source result, biometric result, officer/facility/workstation, decision, confidence, discrepancies `[G]`.

---

## 6. Card & biometric role matrices

**Cards are credentials and convenience, never the master identity.**

| Credential | Primary role | Does NOT replace |
|---|---|---|
| Client digital card | present Impilo ID + signed QR | authentication or consent |
| Client physical card | assisted/offline lookup | HID or clinical record |
| Smart/NFC client card | signed token + controlled offline verify | central resolution |
| Provider badge/card | select workforce identity / facility entry / tap-login | professional verification |
| Appointment QR | locate one appointment/visit token | patient identity proofing |
| Referral token | link an authorised referral | full record access |
| Emergency token | temporary episode identification | permanent client identity |

A QR carries a **signed, expiring, opaque** token — never a readable HID/CPID or clinical payload. A card never
exposes HID/CRID/CPID.

**Biometric capabilities are distinct** (one vendor may supply several; keep them separate):

| Capability | Role |
|---|---|
| ABIS | fingerprint/face/iris template matching against enrolled **health** identities |
| Document verification | ID/passport image + document integrity |
| Facial comparison | live selfie ↔ authoritative/document photo |
| Liveness detection | a live person is present (not a replay) |
| Device biometric / passkey | authenticate the account holder locally |

Biometric rules: **1:1 verification routine** (after a candidate is identified); **1:N restricted** to enrolment/
recovery/deduplication and yields **candidates for adjudication, never an auto-merge**; per-modality approved
thresholds; consent/device/operator/quality/algorithm-version recorded; **fail-closed** (no matcher ⇒ UNAVAILABLE);
biometrics are never the clinical identifier, never stored in BUTANO, never the only route to care.

---

## 7. Delegation SoR (ratified)

Act-on-behalf relationships live in **mvumo** (consent/relationship plane). The relationship model:
`Relationship{ subjectHealthId, delegateActorId, relationshipType, legalBasis, scope[], minDelegateLoa,
effectiveFrom/To, status }`. PolicyEngine gains an **act-on-behalf dimension** keyed on `X-Subject-ID`. A child is a
**distinct person** (own CRID/HID/CPID/Impilo ID) with a **governed, time-bound guardian relationship** — never
stored inside the parent's record; they may claim their own account at the approved age. Caregiver delegation grants
**named actions with an expiry**; the representative acts **under their own identity**, fully audited (dual-identity
provenance). The client never shares a password or card. The orphaned `vito V020 caregiver_linkages` table is
retired into mvumo.

---

## 8. Verdict model (replaces the single AMBER→GREEN)

A journey can pass a *boundary* without a real engine, so a single GREEN is dishonest. Three tiers:

| Tier | Meaning | Proven by |
|---|---|---|
| **SOFTWARE_CONTRACT_GREEN** | journeys + interfaces work against controlled adapters / native fallbacks; separation, tokens, anti-enum, review-queues all pass | the 40-journey software pack (gateway-authenticated) |
| **EXTERNAL_INTEGRATION_GREEN** | real ABIS matcher, real CRVS/National-ID link, real KMS/HSM, running de-identification pipeline all connected | external-integration acceptance packs (X1–X4) |
| **NATIONAL_PRODUCTION_GREEN** | integrations approved; performance/DR proven; key ceremony done; SOPs + privacy/security assessment accepted | national-production readiness review |

Journey 7 (biometric) reaching a *boundary* is SOFTWARE_CONTRACT only; a real matcher is EXTERNAL_INTEGRATION.

## 9. Minimum journey acceptance pack

Before identity is declared complete at each tier, the journeys in §5 must pass automated + supervised end-to-end
tests, extending [`tests/identity-contract/`](../../tests/identity-contract/) with **gateway-authenticated** persona
runs (`scripts/integration-closure/bootstrap-auth.sh` + `docs/demo/persona-truth-pack.md`, e.g. `citizen.moyo`). Each
journey's acceptance asserts the §3 completion criteria and the §2 security boundary (no candidate leak, generic
responses, second-factor before binding).
