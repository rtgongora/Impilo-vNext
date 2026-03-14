# Developer & Governance Platform — Acceptance Pack

## Wave 16 Acceptance Criteria

### A) Contract Tests Library (`libs/contract-tests`)

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| A1 | Adding optional field is backward compatible | `SchemaCompatibilityValidatorTest.AllowedChanges.addOptionalField` | PASS |
| A2 | Widening int→number is backward compatible | `SchemaCompatibilityValidatorTest.AllowedChanges.widenIntegerToNumber` | PASS |
| A3 | Making required→optional is backward compatible | `SchemaCompatibilityValidatorTest.AllowedChanges.requiredToOptional` | PASS |
| A4 | Identical schemas are compatible | `SchemaCompatibilityValidatorTest.AllowedChanges.identicalSchemas` | PASS |
| A5 | Removing a field is a breaking change | `SchemaCompatibilityValidatorTest.BreakingChanges.removeField` | PASS |
| A6 | Changing field type is a breaking change | `SchemaCompatibilityValidatorTest.BreakingChanges.changeType` | PASS |
| A7 | Adding a required field is a breaking change | `SchemaCompatibilityValidatorTest.BreakingChanges.addRequiredField` | PASS |
| A8 | Making optional→required is a breaking change | `SchemaCompatibilityValidatorTest.BreakingChanges.optionalToRequired` | PASS |
| A9 | Adding enum values is allowed | `SchemaCompatibilityValidatorTest.EnumCompat.addEnumValue` | PASS |
| A10 | Removing enum values is a breaking change | `SchemaCompatibilityValidatorTest.EnumCompat.removeEnumValue` | PASS |
| A11 | Valid camelCase envelope passes validation | `EventEnvelopeValidatorTest.ValidEnvelopes.validCamelCase` | PASS |
| A12 | Valid snake_case envelope passes validation | `EventEnvelopeValidatorTest.ValidEnvelopes.validSnakeCase` | PASS |
| A13 | Missing required field detected | `EventEnvelopeValidatorTest.MissingFields.missingEventId/missingPayload` | PASS |
| A14 | Invalid event type naming detected | `EventEnvelopeValidatorTest.EventTypeNaming` (parameterized) | PASS |
| A15 | Invalid schemaVersion detected | `EventEnvelopeValidatorTest.SchemaVersion.zeroInvalid/negativeInvalid` | PASS |

### B) Developer Portal Service

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| B1 | Client registration creates entity and outbox event | `DeveloperPortalServiceTest.ClientRegistration.registersClientAndEmitsEvent` | PASS |
| B2 | API key issuance returns raw key with `imp_` prefix | `DeveloperPortalServiceTest.ApiKeyManagement.issueKeyReturnsRawKey` | PASS |
| B3 | API key rotation marks old key ROTATED and creates new | `DeveloperPortalServiceTest.ApiKeyManagement.rotateKeyRevokesOldAndCreatesNew` | PASS |
| B4 | Golden contract: trust headers enforced | `DeveloperPortalGoldenContractIT` (HeaderEnforcement suite) | PASS |
| B5 | Golden contract: error envelope format correct | `DeveloperPortalGoldenContractIT` (ErrorEnvelopeFormat suite) | PASS |
| B6 | Golden contract: idempotency supported | `DeveloperPortalGoldenContractIT` (Idempotency suite) | PASS |
| B7 | Golden contract: federation authority validated | `DeveloperPortalGoldenContractIT` (FederationAuthority suite) | PASS |
| B8 | POST /internal/v1/developer/clients creates client with sandbox config | Controller (manual) | PASS |
| B9 | POST /internal/v1/developer/clients/{id}/keys issues key with scopes | Controller (manual) | PASS |
| B10 | POST /internal/v1/developer/keys/{id}/rotate rotates key atomically | Controller (manual) | PASS |
| B11 | GET /internal/v1/developer/discovery returns service metadata | Controller (manual) | PASS |

### C) Schema Registry Service

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| C1 | First schema version registers as v1 | `SchemaRegistryServiceTest.Registration.firstVersionRegisters` | PASS |
| C2 | Backward-compatible change registers as v2 | `SchemaRegistryServiceTest.Registration.backwardCompatibleChange` | PASS |
| C3 | Breaking change is rejected (no version saved) | `SchemaRegistryServiceTest.Registration.breakingChangeRejected` | PASS |
| C4 | New subject is always compatible | `SchemaRegistryServiceTest.CompatibilityCheck.newSubjectIsCompatible` | PASS |
| C5 | Golden contract: trust headers enforced | `SchemaRegistryGoldenContractIT` (HeaderEnforcement suite) | PASS |
| C6 | Golden contract: error envelope format correct | `SchemaRegistryGoldenContractIT` (ErrorEnvelopeFormat suite) | PASS |
| C7 | Golden contract: idempotency supported | `SchemaRegistryGoldenContractIT` (Idempotency suite) | PASS |
| C8 | Golden contract: federation authority validated | `SchemaRegistryGoldenContractIT` (FederationAuthority suite) | PASS |
| C9 | Incompatible schema returns HTTP 409 with violations | Controller logic (conflict path) | PASS |
| C10 | Schema fingerprint computed as SHA-256 | `SchemaRegistryService.sha256()` | PASS |

### D) Repo-Level Contract Tests

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| D1 | All v1.1-compliant event types follow naming convention | `RepoEventTypeContractTest.CompliantEventTypes.eventTypeFollowsNamingConvention` | PASS |
| D2 | Service segments extractable from all compliant types | `RepoEventTypeContractTest.CompliantEventTypes.serviceNameExtractable` | PASS |
| D3 | Legacy event types confirmed as non-compliant | `RepoEventTypeContractTest.LegacyEventTypes.legacyEventTypeIsNonCompliant` | PASS |
| D4 | All compliant types start with 'impilo.' | `RepoEventTypeContractTest.CrossServiceInvariants.allStartWithImpilo` | PASS |
| D5 | All compliant types end with version suffix | `RepoEventTypeContractTest.CrossServiceInvariants.allEndWithVersion` | PASS |
| D6 | No duplicate event types across services | `RepoEventTypeContractTest.CrossServiceInvariants.noDuplicateEventTypes` | PASS |
| D7 | Envelope with compliant type passes full validation | `RepoEventTypeContractTest.EnvelopeStructure.envelopeWithCompliantTypeIsValid` | PASS |

### E) Golden Contract Tests

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| E1 | Developer Portal extends GoldenContractSuite | `DeveloperPortalGoldenContractIT` | PASS |
| E2 | Schema Registry extends GoldenContractSuite | `SchemaRegistryGoldenContractIT` | PASS |

### F) Documentation

| # | Criterion | Location | Status |
|---|-----------|----------|--------|
| F1 | Developer platform README | `docs/platforms/developer/README.md` | DONE |
| F2 | Acceptance pack | `docs/acceptance/developer-platform-acceptance-pack.md` | DONE |

## Legacy Event Type Migration Tracking

The following services use non-v1.1 event type naming (uppercase enum-style) and are tracked for future migration:

| Service | Example Event Type | Target Pattern |
|---------|-------------------|----------------|
| pct | `JOURNEY_CREATED` | `impilo.pct.journey.created.v1` |
| pharmacy | `DISPENSE_COMPLETED` | `impilo.pharmacy.dispense.completed.v1` |
| inventory | `LEDGER_EVENT_CREATED` | `impilo.inventory.ledger-event.created.v1` |
| mushex | `CLAIM_SUBMITTED` | `impilo.mushex.claim.submitted.v1` |
| tshepo-identity | `MAPPING_CREATED` | `impilo.tshepo-identity.mapping.created.v1` |
| tshepo-audit | `AUTHZ_DECISION` | `impilo.tshepo-audit.authz.decided.v1` |
