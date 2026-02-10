package zw.gov.mohcc.impilo.vito.migration.v11.offline;

import java.util.List;

/**
 * JDK-only offline verification of v1.1 request-path enforcement.
 *
 * Reads VITO source files as text and asserts the v1.1 Manifest + Companion Spec
 * requirements using strict string/regex matching. No Spring, JUnit, or other
 * external dependencies.
 *
 * Verifies:
 *   1. V11PatientsController: @RequestMapping("/internal/v1/patients") + merge mapping
 *   2. V1_1HeaderFilter: required headers + HTTP 400 error envelope
 *   3. IdempotencyFilter: Idempotency-Key on POST/PUT/PATCH for /internal/v1/**
 *   4. FederationAuthorityGuard: X-Pod-ID == "national" for merge path
 *   5. V017 migration: outbox v1.1 context columns
 *   6. MergeService: sets outbox context fields from v1.1 headers
 *   7. VitoOutboxPublisher/VitoEventMapper: emits "impilo.vito.*.v1" event types
 */
public final class V11RequestPathOfflineVerificationTest {

    // Paths relative to vito-service root
    private static final String CONTROLLER = "src/main/java/zw/gov/mohcc/impilo/vito/api/v11/V11PatientsController.java";
    private static final String HEADER_FILTER = "src/main/java/zw/gov/mohcc/impilo/vito/config/V1_1HeaderFilter.java";
    private static final String IDEMPOTENCY_FILTER = "src/main/java/zw/gov/mohcc/impilo/vito/config/IdempotencyFilter.java";
    private static final String FEDERATION_GUARD = "src/main/java/zw/gov/mohcc/impilo/vito/core/FederationAuthorityGuard.java";
    private static final String V017_MIGRATION = "src/main/resources/db/migration/V017__outbox_v11_columns.sql";
    private static final String MERGE_SERVICE = "src/main/java/zw/gov/mohcc/impilo/vito/core/merge/MergeService.java";
    private static final String OUTBOX_PUBLISHER = "src/main/java/zw/gov/mohcc/impilo/vito/events/VitoOutboxPublisher.java";
    private static final String EVENT_MAPPER = "src/main/java/zw/gov/mohcc/impilo/vito/events/VitoEventMapper.java";
    private static final String ERROR_WRITER = "src/main/java/zw/gov/mohcc/impilo/vito/config/V1_1ErrorWriter.java";
    private static final String EXCEPTION_HANDLER = "src/main/java/zw/gov/mohcc/impilo/vito/config/V1_1ExceptionHandler.java";
    private static final String OUTBOX_ENTITY = "src/main/java/zw/gov/mohcc/impilo/vito/persistence/entity/EventOutboxEntity.java";

    private final OfflineVerificationRunner r;

    public V11RequestPathOfflineVerificationTest(OfflineVerificationRunner runner) {
        this.r = runner;
    }

    public void runAll() {
        verifyV11PatientsController();
        verifyV1_1HeaderFilter();
        verifyIdempotencyFilter();
        verifyFederationAuthorityGuard();
        verifyV017Migration();
        verifyMergeServiceContextFields();
        verifyVitoEventEmission();
    }

    // ============================================================
    // REQ 1: V11PatientsController
    // Must have @RequestMapping("/internal/v1/patients") and @PostMapping("/merge")
    // ============================================================
    private void verifyV11PatientsController() {
        System.out.println("--- REQ 1: V11PatientsController ---");

        String src = r.readSource(CONTROLLER, "R1.0 V11PatientsController exists");
        if (src == null) return;

        r.assertContains(src, "@RequestMapping(\"/internal/v1/patients\")",
                "R1.1 Controller mapped to /internal/v1/patients");

        r.assertContains(src, "@PostMapping(\"/merge\")",
                "R1.2 Merge endpoint mapped as POST /merge");

        r.assertContainsAll(src, List.of(
                "\"X-Tenant-ID\"",
                "\"X-Pod-ID\"",
                "\"X-Request-ID\"",
                "\"X-Correlation-ID\""
        ), "R1.3 All four v1.1 headers referenced in controller");

        r.assertContains(src, "@RequestHeader(\"X-Tenant-ID\")",
                "R1.4 X-Tenant-ID injected via @RequestHeader");

        r.assertContains(src, "@RequestHeader(\"X-Pod-ID\")",
                "R1.5 X-Pod-ID injected via @RequestHeader");

        r.assertContains(src, "MergeService",
                "R1.6 Controller delegates to MergeService");

        System.out.println();
    }

    // ============================================================
    // REQ 2: V1_1HeaderFilter
    // Must enforce X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
    // Returns HTTP 400 + v1.1 error envelope with code MISSING_REQUIRED_HEADER
    // ============================================================
    private void verifyV1_1HeaderFilter() {
        System.out.println("--- REQ 2: V1_1HeaderFilter ---");

        String src = r.readSource(HEADER_FILTER, "R2.0 V1_1HeaderFilter exists");
        if (src == null) return;

        r.assertContains(src, "implements Filter",
                "R2.1 V1_1HeaderFilter implements Filter");

        r.assertContainsAll(src, List.of(
                "\"X-Tenant-ID\"",
                "\"X-Pod-ID\"",
                "\"X-Request-ID\"",
                "\"X-Correlation-ID\""
        ), "R2.2 All four required header names present");

        r.assertContains(src, "MISSING_REQUIRED_HEADER",
                "R2.3 Error code MISSING_REQUIRED_HEADER");

        r.assertContains(src, "400",
                "R2.4 HTTP 400 status referenced");

        r.assertContains(src, "V1_1ErrorWriter.writeError",
                "R2.5 Delegates to V1_1ErrorWriter for error envelope");

        // Verify the error writer produces the v1.1 envelope structure
        String errSrc = r.readSource(ERROR_WRITER, "R2.6 V1_1ErrorWriter exists");
        if (errSrc != null) {
            r.assertContainsAll(errSrc, List.of(
                    "\"error\"",
                    "\"code\"",
                    "\"message\"",
                    "\"details\"",
                    "\"request_id\"",
                    "\"correlation_id\""
            ), "R2.7 Error envelope has all v1.1 JSON keys");
        }

        r.assertContains(src, "/internal/v1/",
                "R2.8 Filter targets /internal/v1/ path prefix");

        r.assertContains(src, "/external/v1/",
                "R2.9 Filter also targets /external/v1/ path prefix");

        System.out.println();
    }

    // ============================================================
    // REQ 3: IdempotencyFilter
    // POST/PUT/PATCH on /internal/v1/** must carry Idempotency-Key
    // Missing key => 400 IDEMPOTENCY_KEY_REQUIRED
    // Same key + different hash => 409 IDENTITY_CONFLICT
    // ============================================================
    private void verifyIdempotencyFilter() {
        System.out.println("--- REQ 3: IdempotencyFilter ---");

        String src = r.readSource(IDEMPOTENCY_FILTER, "R3.0 IdempotencyFilter exists");
        if (src == null) return;

        r.assertContains(src, "implements Filter",
                "R3.1 IdempotencyFilter implements Filter");

        r.assertContains(src, "\"Idempotency-Key\"",
                "R3.2 Checks Idempotency-Key header");

        r.assertContainsAll(src, List.of("\"POST\"", "\"PUT\"", "\"PATCH\""),
                "R3.3 Enforces on POST, PUT, and PATCH methods");

        r.assertContains(src, "/internal/v1/",
                "R3.4 Scoped to /internal/v1/** paths");

        r.assertContains(src, "IDEMPOTENCY_KEY_REQUIRED",
                "R3.5 Error code IDEMPOTENCY_KEY_REQUIRED for missing key");

        r.assertContains(src, "IDENTITY_CONFLICT",
                "R3.6 Error code IDENTITY_CONFLICT for replay conflict (409)");

        r.assertContains(src, "409",
                "R3.7 HTTP 409 status for conflict");

        r.assertContains(src, "@Order(11)",
                "R3.8 IdempotencyFilter runs after V1_1HeaderFilter (@Order 11 > 10)");

        System.out.println();
    }

    // ============================================================
    // REQ 4: FederationAuthorityGuard
    // X-Pod-ID must == "national" for merge operations
    // Non-national => 403 FEDERATION_NOT_AUTHORIZED (via exception handler)
    // ============================================================
    private void verifyFederationAuthorityGuard() {
        System.out.println("--- REQ 4: FederationAuthorityGuard ---");

        String src = r.readSource(FEDERATION_GUARD, "R4.0 FederationAuthorityGuard exists");
        if (src == null) return;

        r.assertContains(src, "\"national\"",
                "R4.1 Guard references \"national\" pod constant");

        r.assertContains(src, "requireNationalPodForMerge",
                "R4.2 Guard has requireNationalPodForMerge method");

        r.assertContains(src, "FEDERATION_NOT_AUTHORIZED",
                "R4.3 Exception message contains FEDERATION_NOT_AUTHORIZED");

        r.assertContains(src, "FederationNotAuthorizedException",
                "R4.4 Custom exception class exists");

        // Check exception handler returns 403
        String handlerSrc = r.readSource(EXCEPTION_HANDLER, "R4.5 V1_1ExceptionHandler exists");
        if (handlerSrc != null) {
            r.assertContains(handlerSrc, "403",
                    "R4.6 Exception handler returns HTTP 403");

            r.assertContains(handlerSrc, "FEDERATION_NOT_AUTHORIZED",
                    "R4.7 Exception handler error code is FEDERATION_NOT_AUTHORIZED");

            r.assertContainsAll(handlerSrc, List.of("\"error\"", "\"code\"", "\"message\""),
                    "R4.8 Exception handler uses v1.1 error envelope structure");

            r.assertContains(handlerSrc, "FederationNotAuthorizedException",
                    "R4.9 Exception handler catches FederationNotAuthorizedException");
        }

        System.out.println();
    }

    // ============================================================
    // REQ 5: V017 migration
    // Adds v1.1 context columns to event_outbox:
    // tenant_id, pod_id, correlation_id, idempotency_key
    // ============================================================
    private void verifyV017Migration() {
        System.out.println("--- REQ 5: V017 outbox migration ---");

        String src = r.readSource(V017_MIGRATION, "R5.0 V017 migration file exists");
        if (src == null) return;

        r.assertContains(src, "event_outbox",
                "R5.1 Migration targets event_outbox table");

        r.assertContainsAll(src, List.of(
                "tenant_id",
                "pod_id",
                "correlation_id",
                "idempotency_key"
        ), "R5.2 All four v1.1 context columns present");

        r.assertContains(src, "ADD COLUMN",
                "R5.3 Uses ADD COLUMN (ALTER TABLE)");

        r.assertContains(src, "NULL",
                "R5.4 Columns are nullable for backward compatibility");

        // Cross-check: entity has matching fields
        String entitySrc = r.readSource(OUTBOX_ENTITY, "R5.5 EventOutboxEntity exists");
        if (entitySrc != null) {
            r.assertContainsAll(entitySrc, List.of(
                    "tenantId",
                    "podId",
                    "correlationId",
                    "idempotencyKey"
            ), "R5.6 Entity has matching Java fields for v1.1 columns");
        }

        System.out.println();
    }

    // ============================================================
    // REQ 6: MergeService sets outbox context fields from v1.1 headers
    // Must read X-Tenant-ID, X-Pod-ID, X-Correlation-ID, Idempotency-Key
    // from request context and set them on the outbox event entity.
    // ============================================================
    private void verifyMergeServiceContextFields() {
        System.out.println("--- REQ 6: MergeService outbox context population ---");

        String src = r.readSource(MERGE_SERVICE, "R6.0 MergeService exists");
        if (src == null) return;

        r.assertContains(src, "setTenantId",
                "R6.1 MergeService sets tenantId on outbox event");

        r.assertContains(src, "setPodId",
                "R6.2 MergeService sets podId on outbox event");

        r.assertContains(src, "setCorrelationId",
                "R6.3 MergeService sets correlationId on outbox event");

        r.assertContains(src, "setIdempotencyKey",
                "R6.4 MergeService sets idempotencyKey on outbox event");

        r.assertContains(src, "X-Tenant-ID",
                "R6.5 Reads X-Tenant-ID from request");

        r.assertContains(src, "X-Correlation-ID",
                "R6.6 Reads X-Correlation-ID from request");

        r.assertContains(src, "Idempotency-Key",
                "R6.7 Reads Idempotency-Key from request");

        r.assertContains(src, "X-Pod-ID",
                "R6.8 Reads X-Pod-ID from request");

        r.assertContains(src, "FederationAuthorityGuard",
                "R6.9 MergeService injects FederationAuthorityGuard");

        r.assertContains(src, "enforceFederationAuthority",
                "R6.10 MergeService calls enforceFederationAuthority before merge");

        r.assertContains(src, "RequestContextHolder",
                "R6.11 Uses RequestContextHolder to extract current headers");

        System.out.println();
    }

    // ============================================================
    // REQ 7: VitoOutboxPublisher/VitoEventMapper emit v1.1 event types
    // Event type must match "impilo.vito.*.v1" pattern
    // EventEnvelope must have schema_version >= 1 and meta.partition_key
    // Emits when mode is V1_1_ONLY or DUAL
    // ============================================================
    private void verifyVitoEventEmission() {
        System.out.println("--- REQ 7: V1.1 event emission ---");

        // Check VitoOutboxPublisher
        String pubSrc = r.readSource(OUTBOX_PUBLISHER, "R7.0 VitoOutboxPublisher exists");
        if (pubSrc != null) {
            r.assertContains(pubSrc, "EventEnvelope",
                    "R7.1 Publisher references EventEnvelope");

            r.assertContains(pubSrc, "EmitMode",
                    "R7.2 Publisher references EmitMode");

            r.assertContains(pubSrc, "emitV11",
                    "R7.3 Publisher calls emitV11 for v1.1 emission");

            r.assertContains(pubSrc, "emitLegacy",
                    "R7.4 Publisher calls emitLegacy for legacy emission");

            r.assertContains(pubSrc, "schema_version",
                    "R7.5 Serialized envelope includes schema_version key");

            r.assertContains(pubSrc, "event_type",
                    "R7.6 Serialized envelope includes event_type key");

            r.assertContainsAll(pubSrc, List.of(
                    "getTenantId", "getPodId", "getCorrelationId", "getIdempotencyKey"
            ), "R7.7 Publisher reads v1.1 context from outbox entity");
        }

        // Check VitoEventMapper
        String mapSrc = r.readSource(EVENT_MAPPER, "R7.8 VitoEventMapper exists");
        if (mapSrc != null) {
            // Check v1.1 event type pattern
            r.assertMatches(mapSrc, "\"impilo\\.vito\\.\".*\"\\.*\\.v1\"",
                    "R7.9 Event type builds \"impilo.vito.<agg>.<suffix>.v1\" pattern");

            // Alternative: check the concrete method that constructs the type
            r.assertContains(mapSrc, ".v1",
                    "R7.10 Event type string ends with .v1");

            r.assertContains(mapSrc, "impilo.vito.",
                    "R7.11 Event type starts with impilo.vito. prefix");

            // Verify V1.1 topic routing
            r.assertContainsAll(mapSrc, List.of(
                    "\"impilo.vito.identity\"",
                    "\"impilo.vito.dedup\""
            ), "R7.12 V1.1 topics use impilo.vito.* namespace");

            // Verify emit mode gating
            r.assertContains(mapSrc, "V1_1_ONLY",
                    "R7.13 EmitMode.V1_1_ONLY recognized");

            r.assertContains(mapSrc, "DUAL",
                    "R7.14 EmitMode.DUAL recognized");

            r.assertContains(mapSrc, "LEGACY_ONLY",
                    "R7.15 EmitMode.LEGACY_ONLY recognized");

            // Verify schema_version = 1 in envelope builder
            r.assertContains(mapSrc, "schemaVersion(1)",
                    "R7.16 EventEnvelope schema_version set to 1");

            // Verify meta.partition_key
            r.assertContains(mapSrc, "partition_key",
                    "R7.17 EventEnvelope meta contains partition_key");

            // Verify producer = "vito"
            r.assertContains(mapSrc, "\"vito\"",
                    "R7.18 EventEnvelope producer is \"vito\"");
        }

        System.out.println();
    }
}
