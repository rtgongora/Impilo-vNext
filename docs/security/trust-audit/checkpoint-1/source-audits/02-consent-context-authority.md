# Source audit — Consent, Mvumo, work context & authority

Branch `claude/tshepo-trust-cp1-truth-audit`, commit `f190318e1`.
Source: [Audit consent, Mvumo, context, authority](91dc2e63-0f3a-4923-a026-d6b717bf979e).
Classification against **intended production design**.

## Headline

Consent *capture* (Mvumo) and the consent *engine* (`tshepo-consent-service`) are real and deep.
Work-context *minting* is genuinely proven against source-of-record.
**Enforcement is almost entirely dormant at runtime**: Envoy ext_authz off, duty-token binding in SHADOW, boundary rules seeded inactive, licence standing not checked at mint, and a **POST-vs-GET contract defect** in the PDP → consent client that would fail-closed-deny every consent-requiring clinical request if the PDP were enabled today.

## Confirmed high-risk defects

1. **PDP consent client contract broken** — `tshepo-authz` `ConsentClient` POSTs a JSON body; consent service only exposes `GET /v1/consent/evaluate`. Result: 405 → fail-closed `CONSENT_SERVICE_UNAVAILABLE`. Must be fixed before any OPA/ext_authz cutover.
2. **`/fhir` bypasses FHIR gateway consent** — Envoy (and live Traefik) routes SHR traffic to BUTANO; BUTANO has zero consent code. Gateway consent only covers the BFF interop lane.
3. **Experience BFF treats consent as display data** — clinical payload returned regardless of consent evaluation.
4. **Duty-token mint endpoint is mesh-reachable under permitAll** — `tshepo-identity-service` has `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` live; identity validates anchor *presence* only, not source proof.
5. **Direct-care and statutory lawful bases are ABSENT** as evaluation engines (purpose enums / UI only).

## Classification (vs intended production design)

| # | Capability | Classification |
|---|---|---|
| 1 | Mvumo consent capture / lifecycle / remote sessions | **PARTIAL** |
| 2 | Mvumo → tshepo-consent materialisation | **PARTIAL** (coded; live wiring needs proof) |
| 3 | tshepo-consent evaluation engine (in-service) | **ENFORCED** (where called) |
| 4 | PDP Step 5 consent check | **DISCONNECTED** + broken contract |
| 5 | FHIR-gateway consent | **BYPASSABLE** (code correct, path not on SHR) |
| 6 | BUTANO consent | **ABSENT** |
| 7 | BFF consent gating before clinical data | **ABSENT** |
| 8 | Citizen consent list/revoke | **PARTIAL** |
| 9 | Break-glass / emergency lawful basis | **ACTIVE_NOT_ENFORCED** |
| 10 | Direct-care relationship lawful basis | **ABSENT** |
| 11 | Statutory-authority lawful basis | **DOCUMENTED_ONLY / ABSENT** |
| 12 | Work-context mint (BFF proof against Vashandi/org-registry) | **ENFORCED (at mint)**; S2S mint endpoint **BYPASSABLE** |
| 13 | Duty-token binding at PDP | **SHADOW** + **DISCONNECTED** |
| 14 | WorkMode / clinical boundary rules (V055/V058) | **SHADOW** (seeded `active=false`) |
| 15 | Signed decision envelope | **ACTIVE_NOT_ENFORCED** |
| 16 | Work Home mint-only composition | **ENFORCED (BFF-level)** |
| 17 | Licence/council standing on protected actions | **PARTIAL** (advisory + event revocation; no mint-time licence check) |
| 18 | Work-token revalidation sweep | **ACTIVE_NOT_ENFORCED** |
| 19 | Delegated authority (Mvumo + PDP Step 4.5) | **ACTIVE_NOT_ENFORCED** |
| 20 | Delegation chains | **ABSENT** |
| 21 | Estate service-level OAuth backstop | **BYPASSABLE** (96/98 OAuth-disabled) |

## Runtime confirmations (2026-08-01)

From `../runtime-evidence/OPEN_QUESTION_ANSWERS.md`:

| Fact | Value |
|---|---|
| Deployed Envoy `ext_authz` count | **0** |
| `TSHEPO_WORK_CONTEXT_MODE` on tshepo-authz | **SHADOW** |
| `tshepo-identity-service` OAuth disabled | **true** |
| `fhir-gateway-service` deployed | **yes** (1/1), OAuth disabled |
| `mvumo-service` / `tshepo-consent-service` OAuth disabled | **true** |
| experience-bff anonymous / auth fallback | **false / false** (hardened) |

Still open (need DB/probe evidence, not done in this checkpoint pass):

- Whether Mvumo client-credentials are configured and produce `consent_directive` rows.
- Live DB state of V055/V058 `active` flags.
- Theatre/procedure hard workflow gate on granted consent.
