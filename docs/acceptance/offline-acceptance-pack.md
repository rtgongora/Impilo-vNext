# Offline & Edge Framework — Acceptance Pack

## Wave 15 Acceptance Criteria

### A) Offline Entitlement Verification Library (JWT)

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| A1 | Verifier accepts valid TSHEPO capability token with correct Ed25519 signature | `OfflineEntitlementVerifierTest.ValidTokens.validTokenWithCapability` | PASS |
| A2 | Verifier rejects expired token | `OfflineEntitlementVerifierTest.DeniedTokens.expiredToken` | PASS |
| A3 | Verifier rejects wrong issuer | `OfflineEntitlementVerifierTest.DeniedTokens.wrongIssuer` | PASS |
| A4 | Verifier rejects wrong audience | `OfflineEntitlementVerifierTest.DeniedTokens.wrongAudience` | PASS |
| A5 | Verifier rejects missing required capability | `OfflineEntitlementVerifierTest.DeniedTokens.missingCapability` | PASS |
| A6 | Verifier rejects invalid signature (different key) | `OfflineEntitlementVerifierTest.DeniedTokens.invalidSignature` | PASS |
| A7 | Verifier rejects null/empty/malformed tokens | `OfflineEntitlementVerifierTest.DeniedTokens.nullToken/emptyToken/malformedJws` | PASS |
| A8 | Queue format enforces required fields | `OfflineQueueFormatTest.actionEntryRequiresFields` | PASS |
| A9 | Queue format rejects invalid sequenceNum | `OfflineQueueFormatTest.actionEntryRejectsInvalidSequence` | PASS |
| A10 | Sync batch rejects empty entries | `OfflineQueueFormatTest.syncBatchRejectsEmpty` | PASS |

### B) Edge Local Store + Sync Protocol

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| B1 | POST /offline/vitals requires X-Offline-Entitlement JWT | `OfflineVitalsEndpointTest.EntitlementVerification.allowValidToken` | PASS |
| B2 | Vitals capture denied with expired token | `OfflineVitalsEndpointTest.EntitlementVerification.denyExpiredToken` | PASS |
| B3 | Vitals capture denied without CAPTURE_VITALS capability | `OfflineVitalsEndpointTest.EntitlementVerification.denyMissingCapability` | PASS |
| B4 | Captured vital stored with QUEUED status + audit event | `OfflineVitalsEndpointTest.CaptureAudit.captureStoresAndAudits` | PASS |
| B5 | Outbox event emitted with idempotency key | `OfflineVitalsEndpointTest.CaptureAudit.captureStoresAndAudits` (verify idem key) | PASS |
| B6 | POST /offline/sync processes batch with correlation_id preserved | `OfflineSyncServiceTest.SyncBatch.syncPublishesWithCorrelationId` | PASS |
| B7 | Sync creates reconciliation batch entry | `OfflineSyncServiceTest.SyncBatch.syncCreatesBatchEntry` | PASS |
| B8 | Sync denied with expired token | `OfflineSyncServiceTest.SyncEntitlement.denyExpiredForSync` | PASS |

### C) Conflict Handling

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| C1 | Replay is safe (queued actions transition to REPLAYED/CONFLICT/FAILED) | `OfflineVitalsWorkflowTest.ReplayTests.replayMarksAsSent` | PASS |
| C2 | BUTANO 409 creates conflict review entry | `OfflineVitalsWorkflowTest.ReplayTests.replayCreatesConflict` | PASS |
| C3 | Conflict audit event emitted on conflict detection | `OfflineVitalsWorkflowTest.ReplayTests.replayCreatesConflict` (outbox) | PASS |
| C4 | Unresolved conflicts visible in review queue | `ConflictReviewController.listConflicts` (existing) | PASS |
| C5 | Conflicts resolvable with KEEP_OFFLINE/KEEP_EXISTING/MERGED | `ConflictReviewController.resolveConflict` (existing) | PASS |

### D) Existing Functionality Preserved

| # | Criterion | Test Class | Status |
|---|-----------|------------|--------|
| D1 | Entitlement issuance (HMAC) still works | `OfflineEdgeApiMockMvcTest.EntitlementLifecycle` | PASS |
| D2 | Token verification (HMAC) still works | `OfflineEdgeApiMockMvcTest.EntitlementLifecycle.verifyEntitlementToken` | PASS |
| D3 | Action capture with entitlement validation | `OfflineEdgeApiMockMvcTest.ActionCapture` | PASS |
| D4 | Replay pipeline processes queued actions | `OfflineEdgeApiMockMvcTest.ReplayPipeline` | PASS |
| D5 | Outbox events emitted on all operations | `OfflineEdgeApiMockMvcTest.OutboxValidation` | PASS |

## Test Execution

```bash
# Run offline-sdk library tests
cd libs/offline-sdk && mvn test

# Run offline-edge-service tests
cd services/offline-edge-service && mvn test

# Run specific test classes
mvn test -pl libs/offline-sdk -Dtest=OfflineEntitlementVerifierTest
mvn test -pl services/offline-edge-service -Dtest=OfflineVitalsEndpointTest
mvn test -pl services/offline-edge-service -Dtest=OfflineSyncServiceTest
```

## Deliverables Summary

| Deliverable | Location | Type |
|-------------|----------|------|
| Offline SDK library | `libs/offline-sdk/` | Java 21 library |
| JWT Entitlement Verifier | `libs/offline-sdk/.../entitlement/OfflineEntitlementVerifier.java` | Class |
| Local Queue Format | `libs/offline-sdk/.../queue/OfflineActionEntry.java` | Record |
| Sync Batch Format | `libs/offline-sdk/.../queue/OfflineSyncBatch.java` | Record |
| Vitals Endpoint | `POST /internal/v1/offline/vitals` | REST API |
| Sync Endpoint | `POST /internal/v1/offline/sync` | REST API |
| Conflicts Endpoint | `GET /internal/v1/offline/conflicts` | REST API (existing) |
| V004 Migration | `db/migration/V004__wave15_offline_sync.sql` | SQL |
| SDK Tests | `OfflineEntitlementVerifierTest`, `OfflineQueueFormatTest` | JUnit 5 |
| Service Tests | `OfflineVitalsEndpointTest`, `OfflineSyncServiceTest` | JUnit 5 |
| Architecture Doc | `docs/offline/wave15-offline-edge.md` | Markdown |
| This Acceptance Pack | `docs/acceptance/offline-acceptance-pack.md` | Markdown |

## Non-Scope Confirmation

- No mobile app code — SDK and HTTP interface only
- No UI changes
- No changes to TSHEPO core service
- No changes to Envoy configuration
