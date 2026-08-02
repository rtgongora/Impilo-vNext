# tshepo.trust.v1 — Compatibility and Deprecation Rules

Version constant: `tshepo.trust.v1` (single source per representation, parity-checked).

## Canonical locations (the only permitted import points)

| Representation | Canonical location | Entry point |
|---|---|---|
| Java | `libs/tshepo-contracts` — `zw.gov.mohcc.impilo.tshepo.contracts.v1` | Maven artifact `zw.gov.mohcc.impilo:tshepo-contracts` (repository reactor module, `services/pom.xml` line 189) |
| TypeScript | `contracts/trust-decision/` | `contracts/trust-decision/index.ts` (re-exports `v1.ts` + `validate.ts`) |
| JSON Schema | `contracts/schemas/trust-decision-v1.schema.json` | `$defs/<TypeName>` |
| OpenAPI | `contracts/openapi/tshepo-trust-decision-v1.openapi.yaml` | `components.schemas.<TypeName>` |

Consumers must not re-declare these types. `scripts/guard/check-trust-decision-contracts.sh`
fails the contract CI gate (`scripts/test/run-api-contract-checks.sh`) if a TS/TSX source
outside `contracts/trust-decision/` declares any canonical trust type name or duplicates the
`"tshepo.trust.v1"` version constant.

## Compatibility rules for v1 (while v1 is current)

1. **Additive only.** New optional fields may be added to object types in all four
   representations in the same change; the parity gate rejects a partial addition.
2. **No repurposing.** A field's meaning, type, or nullability must not change within v1.
3. **Enums are closed.** Adding a `TrustChallengeDecision`, `RecoveryAuthenticationState`,
   `LawfulBasisType` or `OperatingMode` constant is a **breaking** change for consumers that
   exhaustively match; it requires a minor contract revision note in this file plus fixture
   coverage for the new constant before merge.
4. **Wire casing is fixed.** `TrustChallengeOutcome` is snake_case on the wire; all other
   types are camelCase. Do not add `@JsonProperty` renames to existing fields.
5. **Validation may only tighten toward the documented doctrine** (e.g. secret/token
   exclusion), never loosen. Any tightening needs a negative fixture in the shared manifest.
6. **Fixtures are part of the contract.** Every contract change updates
   `contracts/trust-decision/fixtures/trust-decision-v1.fixtures.json` and must pass the
   Java, TypeScript, JSON Schema and OpenAPI layers of the conformance suite.

## Deprecation rules

1. A future `tshepo.trust.v2` is introduced side-by-side (`contracts/v2` package,
   `v2.ts`, new schema/OpenAPI files) — v1 files are never edited into v2.
2. v1 enters deprecation only after every proven caller listed in the
   `@CompatibilityAdapterMeta` annotations consumes v2; deprecation is recorded here and in
   `docs/security/trust-audit/checkpoint-2/COMPATIBILITY_ADAPTERS.md`.
3. Compatibility adapters (`contracts/v1/adapter`) are removed only when their annotated
   `removalCondition` is met; removal is a separate reviewed change.
4. Legacy (pre-v1) DTOs (`AuthzResponse`, `dto.AuthenticationAssurance`,
   `dto.ConsentDecision`) remain frozen: no new consumers, no field additions. New code
   consumes v1 and bridges through the adapters.
5. Adapters must never broaden authority during any deprecation window: AAL, scope,
   context, consent and recovery constraints translate downward or fail closed
   (`AuthzResponseChallengeAdapter.toLegacySafe` denies unrepresentable outcomes).
