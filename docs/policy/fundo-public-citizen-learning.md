# Fundo Public / Citizen Learning (B5)

Status: privacy guard REAL; public-tenant surface = design + PARTIAL (2026-06-26)

## What landed (real)
- **Privacy guard**: citizen learning (subjectType `USER_HEALTH_ID` / `CITIZEN_ANON`) never
  enters the provider CPD path. `FundoWorkforceReadinessService` only emits `cpdCandidates`
  when `subjectType == PROVIDER`; the CPD egress signal (certificate.issued.v1) carries
  subjectType so the varapi consumer filters to PROVIDER. Citizen completions stay citizen.
- Catalogue + enrolment are already subject-agnostic; the citizen mobile app
  (`FundoLearningScreen`) consumes them as `USER_HEALTH_ID`.
- Multilingual public content reuses `FundoLanguageOptionEntity` + the catalogue `language`
  filter + library `access_level=PUBLIC` (B1).

## Public-tenant surface (recommended approach — PARTIAL)
Do **not** weaken the v1.1 trust-header filter. Instead, a reserved public-health tenant is
resolved at the edge so the service still receives a full `RequestContext`; a dedicated BFF
`/public/v1/learning/...` surface injects that tenant + `X-Actor-Type: CITIZEN_ANON` and
omits provider headers. Catalogue browse = zero friction; enrolment/progress = graduated
friction (device-anchored pseudonymous subject, optional Health-ID upgrade).

**Honest partials**: the `/public/v1/learning` BFF surface + cross-device anonymous progress
+ offline public catalogue are not yet wired (device-anchored only). The privacy boundary
and subject-agnostic catalogue/enrolment that make them safe are in place.
