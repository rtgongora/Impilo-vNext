# Rito Experience & Reputation — Capability Design

**Status:** Design (ratified doctrine, pre-build). **Owning service:** `rito-quality-safety-service`
(pkg `zw.gov.mohcc.impilo.rito`, schema `rito`, tables `rit_*`, port 8391). **Doctrine:**
[`provider-reputation-doctrine.md`](../doctrine/provider-reputation-doctrine.md) ·
[`service-relationship-doctrine.md`](../doctrine/service-relationship-doctrine.md).

This document specifies the "Rito Experience & Reputation" bounded capability. It is a design; the
build is the RW1–RW7 program (§9). No service code exists yet.

## 1. Why here (not a new service, not Varapi)

Ratings are client feedback, quality, safety and experience — Rito's domain. Rito already models
cases, complaints, compliments, safety incidents, quality signals, audits, corrective actions, QI
plans and surveys, and already consumes PCT signals. Reputation is **greenfield** in Rito (no
rating/score/reputation concept exists anywhere today) and slots beside CSAT/NPS surveys, reusing
`rit_case_party(PROVIDER)` and `rit_case_link(ENCOUNTER→PCT)`. It is a **bounded capability within
Rito**, not a new microservice. Varapi stays provider-truth SoR and composes a read-only summary.

## 2. Reused Rito substrate (do not re-invent)

| Existing construct | Reuse |
|---|---|
| `rit_case_party` (`party_role=PROVIDER`, `identity_protected`) | Attach the rated provider + shield respondent identity |
| `rit_case_link` (`link_type=ENCOUNTER`, `target_system=PCT`, `target_ref=encounterRef`) | Verified-interaction provenance |
| `rit_survey` / `rit_survey_response` (`score`, `nps_category`, `facility_id`, `visit_ref`) | Post-visit questionnaire delivery + response capture |
| `rit_case` (`pillar`, `satisfaction_score`, escalation) | Complaint/compliment linkage + governed referral pattern |
| `RitoSignalConsumer` (`@KafkaListener` on `rito.signals.topics.pct`) | Extend to consume `clinical.pct.encounter.completed` |
| `RitoOutboxPublisher` (`rito.*` topics) | New `rito.reputation` topic for summary-changed events |

## 3. Data model — migration `V005__experience_reputation.sql` (schema `rito`)

**`rit_provider_rating`** — the contextual rating record (a **Record ID** derived from a
**Transaction**; see Multi-Class Identifier Model). Columns (indicative):
`rating_id UUID PK`, `tenant_id`, `provider_id` (Varapi Provider ID), `facility_id`,
`service_point_id`, `encounter_ref` (PCT, nullable for unverified), `provider_role`,
`modality` (PHYSICAL|VIRTUAL|OUTREACH|EMERGENCY), `specialty`, `verified_interaction bool`,
`verification_source` (PCT|NONE|STAFF|CAREGIVER|REGULATORY), `respondent_class`
(CLIENT|CAREGIVER|GUARDIAN|STAFF|ANONYMOUS), `respondent_identity_protected bool`,
`respondent_actor_ref` (nullable, TSHEPO-gated), `reporting_period`, `submitted_at`,
`moderation_state`, `provider_response_id` (nullable), plus the **historical snapshot** fields
(§`service-relationship-doctrine.md`): `provider_status_at_time`, `assignment_ref_at_time`,
`facility_id_at_time`, `provider_role_at_time`. Free-text narrative in a linked
`rit_case`/`rit_case_message`, never inline PII on the rating.

**`rit_rating_domain_score`** — one row per **domain** per rating (the four domains are stored
separately, never blended): `id`, `rating_id FK`, `domain`
(CLIENT_EXPERIENCE|ACCESS_PROCESS|PROFESSIONAL_QUALITY|SAFETY_ACCOUNTABILITY), `measure`
(e.g. communication, respect_dignity, waiting_experience, explanation_of_care), `score NUMERIC`,
`scale`.

**`rit_provider_reputation_summary`** — **derived** aggregate (never a source of truth), keyed by
`(provider_id, facility_id, service_point_id, domain, reporting_period)`: `count`,
`verified_count`, `mean`, `verified_mean`, `distribution jsonb`, `last_recomputed_at`. Public read
uses `verified_*` only. Recomputed by a scheduled aggregator (mirror the existing QI/signal jobs).

**`rit_rating_moderation`** — moderation/dispute state machine on a rating:
`SUBMITTED → UNDER_REVIEW → PUBLISHED | WITHHELD | DISPUTED → RESOLVED`; manipulation flags
(coordinated/retaliation/review-bombing), reviewer, decision, dispute + appeal linkage.

**`rit_provider_response`** — a provider's response to feedback (part of the record).

All UUID identifiers; providers keyed by Provider ID, patients by CPID (no PII in the rating row).

## 4. Verified-interaction intake (PCT gate)

- Extend `RitoSignalConsumer` to subscribe to **`clinical.pct.encounter.completed`** (already the
  `rito.signals.topics.pct` lane). Persist each completed encounter as an *eligible-for-feedback*
  interaction; a rating that references its `encounterRef` is stamped `verified_interaction=true`
  and linked via `rit_case_link(ENCOUNTER/PCT)`.
- **PCT dependency (flagged):** the current `ENCOUNTER_COMPLETED` payload
  (`EncounterService.completeEncounter` → `OutboxPublisher`) carries `encounterRef` + `patientCpid`
  + `encounterType` + `endedAt` but **no provider id**. For provably provider-attributed verified
  ratings, PCT SHALL add `attendingProviderId` (and participating providers) to the payload.
  **Recommended: enrich the PCT event** (RW2). Interim fallback: Rito reads the encounter's
  provider(s) via a PCT internal read at feedback time.
- One verified rating per (encounter, provider). Unverified public feedback is accepted but labelled
  and excluded from `verified_*` summaries.

## 5. Feedback request orchestration

Khuluma sends the post-visit request (SMS/WhatsApp/app/USSD/email) triggered off the completed
encounter; Nompilo guides the respondent through the domain questions and explains what happens with
the feedback; Rito owns the case/record and moderation; TSHEPO gates who may see identifiable
feedback. This composes existing services — Rito does not build a comms channel or a guidance engine.

## 6. Read surfaces & governance

- **Public experience summary** (internal `rito` endpoint, consumed via BFF public lane): **verified
  experience domains only** (client experience; selected access & process). No professional-quality
  or safety/accountability detail.
- **Authorised management view** (TSHEPO-gated): all four domains + moderation + trends + complaint
  themes + peer/service-point comparison + open investigations + provider responses.
- **`rito.reputation`** outbox topic on summary change, for cache-eviction/read-model consumers.
- **Governance firewall (tested invariant):** a rating write path SHALL emit **no** Varapi / TSHEPO /
  Vashandi authority mutation. A detected pattern creates a `rit_case` **referral** (reusing the
  existing escalation), never a direct sanction. This is asserted by test (RW7).

## 7. Varapi read-only summary surface

`PublicPractitionerVerificationResponse`
(`varapi …/api/dto/`, served by `PublicPractitionerVerificationController`
`/v1/public/practitioners/verify/{registrationNumber}`) — and the provider-profile BFF composition —
gain an **optional `experienceSummary` block fetched from Rito**, source-tagged `"Rito"`, allow-listed
to verified experience measures (overall + per-domain means + verified interaction count). Varapi
composes and displays; it never stores. This mirrors the existing "Varapi is SoR for X, not Y"
pattern and respects the enumeration-resistant public-lane ADR
([`gateway-public-lane-security-adr.md`](gateway-public-lane-security-adr.md)).

Composed public provider card (target):

```text
Dr Tariro Moyo · Registered Medical Practitioner · Licence: Active   (Varapi)
Current locations: Chinhoyi Provincial Hospital, Impilo Virtual Clinic (Vashandi + Tuso)
Patient experience: 4.6 / 5 from 184 verified interactions            (Rito)
  Communication 4.7 · Respect & dignity 4.8 · Waiting 4.1 · Explanation 4.6
```

## 8. Anti-manipulation

Verified-interaction gating (public weight requires a PCT-verified interaction); one-rating-per-
encounter; rate limiting; coordinated-manipulation / retaliation / review-bombing detection in
`rit_rating_moderation` before a rating reaches a summary; TSHEPO shields client identity from
provider-level access; anonymous and identified pathways both supported.

## 9. Build program (RW1–RW7, one commit-set per wave)

- **RW1** V005 migration + entities/repositories.
- **RW2** verified-interaction intake (consume `clinical.pct.encounter.completed`) + PCT
  `attendingProviderId` payload enrichment.
- **RW3** rating submission + moderation state machine + anti-manipulation + provider response.
- **RW4** aggregation job + `rit_provider_reputation_summary` + `rito.reputation` events.
- **RW5** governed read APIs (public verified-experience + authorised management view) + TSHEPO
  visibility policy seeds (identifiable-feedback protection; SHADOW→ENFORCE).
- **RW6** Varapi/Tuso/Ndila/Ruvimbo read-only summary surfacing (source-tagged) + shell provider-
  profile "Patient experience" card.
- **RW7** tests (`*Test` surefire + golden-thread) + acceptance pack: regulation-firewall,
  verified-gating, review-bombing, identity-protection, domain-separation.

## 10. Verification

Per wave: `cd services/rito-quality-safety-service && mvn -o test`; migration validated on a
runtime-proof Postgres rig (schema `rito`); the governance-firewall assertion (no authority mutation
on rating write) is a required test; PCT event contract change (RW2) covered by a contract test on
both `clinical.pct.encounter.completed` producer and the Rito consumer.
