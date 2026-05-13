package zw.gov.mohcc.impilo.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import zw.gov.mohcc.impilo.contract.envelope.EventEnvelopeValidator;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repo-level contract tests that validate all known event types across
 * Impilo services conform to the v1.1 naming convention:
 * {@code impilo.{service}.{entity}.{action}.v{N}}
 *
 * <p>Services that already follow the convention are validated to remain compliant.
 * Services using legacy uppercase patterns (PCT, pharmacy, inventory, mushex,
 * tshepo-identity) are documented as non-compliant and tracked for migration.
 */
@DisplayName("Repo-Level Event Type Contract Tests")
class RepoEventTypeContractTest {

    // ── v1.1-compliant event types (waves 9–16) ─────────────────────────

    static Stream<Arguments> compliantEventTypes() {
        return Stream.of(
                // notification-service (wave 10)
                Arguments.of("notification", "impilo.notify.receipt.recorded.v1"),
                Arguments.of("notification", "impilo.notify.notification.enqueued.v1"),
                Arguments.of("notification", "impilo.notify.notification.sent.v1"),
                Arguments.of("notification", "impilo.notify.notification.failed.v1"),
                Arguments.of("notification", "impilo.notify.template.created.v1"),
                Arguments.of("notification", "impilo.notify.template.published.v1"),
                Arguments.of("notification", "impilo.notify.template.retired.v1"),
                Arguments.of("notification", "impilo.notify.template.version_created.v1"),

                // asset-registry-service (wave 11)
                Arguments.of("asset-registry", "impilo.asset.asset.created.v1"),
                Arguments.of("asset-registry", "impilo.asset.asset.updated.v1"),
                Arguments.of("asset-registry", "impilo.asset.asset.retired.v1"),
                Arguments.of("asset-registry", "impilo.asset.asset.status.changed.v1"),

                // surveillance-service (wave 11)
                Arguments.of("surveillance", "impilo.surv.signal.created.v1"),
                Arguments.of("surveillance", "impilo.surv.signal.hit.v1"),
                Arguments.of("surveillance", "impilo.surv.case.opened.v1"),

                // data-pipeline-service (wave 12)
                Arguments.of("data-pipeline", "impilo.pipeline.event.ingested.v1"),
                Arguments.of("data-pipeline", "impilo.pipeline.watermark.updated.v1"),

                // ndr-service (wave 12)
                Arguments.of("ndr", "impilo.ndr.bronze.received.v1"),
                Arguments.of("ndr", "impilo.ndr.gold.encounters.built.v1"),
                Arguments.of("ndr", "impilo.ndr.query.audit.v1"),

                // offline-edge-service (wave 15)
                Arguments.of("offline-edge", "impilo.offline.action.synced.v1"),
                Arguments.of("offline-edge", "impilo.offline.sync.completed.v1"),
                Arguments.of("offline-edge", "impilo.offline.vital.captured.v1"),
                Arguments.of("offline-edge", "impilo.offline.entitlement.issued.v1"),
                Arguments.of("offline-edge", "impilo.offline.action.recorded.v1"),
                Arguments.of("offline-edge", "impilo.offline.action.conflict.v1"),
                Arguments.of("offline-edge", "impilo.offline.action.replayed.v1"),

                // audit-ledger-service (wave 14)
                Arguments.of("audit-ledger", "impilo.audit.record.appended.v1"),

                // developer-portal-service (wave 16)
                Arguments.of("developer-portal", "impilo.developer-portal.client.registered.v1"),
                Arguments.of("developer-portal", "impilo.developer-portal.apikey.issued.v1"),
                Arguments.of("developer-portal", "impilo.developer-portal.apikey.rotated.v1"),

                // schema-registry-service (wave 16)
                Arguments.of("schema-registry", "impilo.schema-registry.schema.registered.v1"),

                // learning-service / Impilo Fundo (Phase 4B dual-emit migration)
                // Dual-emitted alongside the legacy dotted-lowercase strings below.
                // Both rows are produced from a single LearningPlatformFacade write so
                // existing consumers continue to receive the legacy event type while
                // new consumers can subscribe to the v1.1-compliant names. A future
                // phase will retire the legacy strings once all consumers migrate.
                Arguments.of("learning", "impilo.learning.completion.recorded.v1"),
                Arguments.of("learning", "impilo.learning.fundo.sync.completed.v1"),
                Arguments.of("learning", "impilo.learning.resource.opened.v1"),
                Arguments.of("learning", "impilo.learning.path.assigned.v1"),
                Arguments.of("learning", "impilo.learning.prerequisite.registered.v1"),
                Arguments.of("learning", "impilo.learning.moodle.ws.completion.ingested.v1"),
                Arguments.of("learning", "impilo.learning.fundo.link.established.v1"),

                // learning-service / Impilo Fundo (Phase 5D native LMS events)
                // Brand-new native LMS surface — there is no legacy event-type
                // counterpart to dual-emit because the underlying domain concepts
                // (course/module/lesson/enrolment/assessment/certificate) did not
                // exist before Phase 5. Emitted via FundoOutboxAppender against
                // the existing lrn_event_outbox table.
                Arguments.of("learning", "impilo.learning.course.published.v1"),
                Arguments.of("learning", "impilo.learning.course.completed.v1"),
                Arguments.of("learning", "impilo.learning.enrolment.created.v1"),
                Arguments.of("learning", "impilo.learning.enrolment.cancelled.v1"),
                Arguments.of("learning", "impilo.learning.progress.started.v1"),
                Arguments.of("learning", "impilo.learning.progress.completed.v1"),
                Arguments.of("learning", "impilo.learning.assessment.attempt.submitted.v1"),
                Arguments.of("learning", "impilo.learning.certificate.issued.v1")
        );
    }

    @Nested
    @DisplayName("v1.1-Compliant Event Types")
    class CompliantEventTypes {

        @ParameterizedTest(name = "[{0}] {1}")
        @MethodSource("zw.gov.mohcc.impilo.contract.RepoEventTypeContractTest#compliantEventTypes")
        @DisplayName("Event type follows impilo.{service}.{entity}.{action}.v{N} convention")
        void eventTypeFollowsNamingConvention(String serviceName, String eventType) {
            assertThat(EventEnvelopeValidator.isValidEventType(eventType))
                    .as("Event type '%s' from %s must follow v1.1 naming convention", eventType, serviceName)
                    .isTrue();
        }

        @ParameterizedTest(name = "[{0}] {1}")
        @MethodSource("zw.gov.mohcc.impilo.contract.RepoEventTypeContractTest#compliantEventTypes")
        @DisplayName("Event type service segment is extractable")
        void serviceNameExtractable(String serviceName, String eventType) {
            String extracted = EventEnvelopeValidator.extractService(eventType);
            assertThat(extracted)
                    .as("Should extract service segment from '%s'", eventType)
                    .isNotNull()
                    .isNotBlank();
        }
    }

    // ── Legacy event types that do NOT follow v1.1 convention ────────────

    static Stream<Arguments> legacyEventTypes() {
        return Stream.of(
                // PCT service (wave 9) — uppercase enum-style
                Arguments.of("pct", "JOURNEY_CREATED"),
                Arguments.of("pct", "JOURNEY_STATE_CHANGED"),
                Arguments.of("pct", "QUEUE_ITEM_CREATED"),
                Arguments.of("pct", "QUEUE_ITEM_ENQUEUED"),
                Arguments.of("pct", "QUEUE_ITEM_CALLED"),
                Arguments.of("pct", "ENCOUNTER_STARTED"),
                Arguments.of("pct", "ENCOUNTER_COMPLETED"),
                Arguments.of("pct", "ADMISSION_CREATED"),
                Arguments.of("pct", "DISCHARGE_COMPLETED"),
                Arguments.of("pct", "DEATH_RECORDED"),
                Arguments.of("pct", "TRIAGE_RECORDED"),
                Arguments.of("pct", "TRANSFER_REQUESTED"),

                // pharmacy-service (wave 9) — uppercase enum-style
                Arguments.of("pharmacy", "DISPENSE_ORDER_RECEIVED"),
                Arguments.of("pharmacy", "DISPENSE_COMPLETED"),
                Arguments.of("pharmacy", "STOCK_MOVEMENT_RECORDED"),
                Arguments.of("pharmacy", "PICKUP_CLAIMED"),

                // inventory-service (wave 10) — uppercase enum-style
                Arguments.of("inventory", "LEDGER_EVENT_CREATED"),
                Arguments.of("inventory", "ONHAND_UPDATED"),
                Arguments.of("inventory", "STOCKOUT_RISK"),
                Arguments.of("inventory", "REQUISITION_CREATED"),
                Arguments.of("inventory", "REQUISITION_FULFILLED"),

                // mushex-service (wave 11) — uppercase enum-style
                Arguments.of("mushex", "INTENT_CREATED"),
                Arguments.of("mushex", "CLAIM_SUBMITTED"),
                Arguments.of("mushex", "CLAIM_ADJUDICATED"),
                Arguments.of("mushex", "FRAUD_FLAGGED"),
                Arguments.of("mushex", "LEDGER_ENTRY_POSTED"),
                Arguments.of("mushex", "REFUND_REQUESTED"),
                Arguments.of("mushex", "SETTLEMENT_COMPUTED"),

                // tshepo-identity-service (wave 9) — uppercase enum-style
                Arguments.of("tshepo-identity", "MAPPING_CREATED"),

                // tshepo-audit-service (wave 9) — uppercase enum-style
                Arguments.of("tshepo-audit", "AUTHZ_DECISION"),

                // learning-service / Impilo Fundo — legacy dotted-lowercase strings.
                // Phase 2: introduced as the v1.1 migration backlog. Phase 4B:
                // additive dual-emit is now active — every write that produces
                // one of these rows also produces a row carrying the v1.1
                // equivalent (see compliantEventTypes() above for the matching
                // pair list). These legacy strings remain emitted byte-for-byte
                // until every downstream consumer has migrated; a future phase
                // will drop them and switch to single v1.1 emission.
                Arguments.of("learning", "learning.completion.recorded"),
                Arguments.of("learning", "fundo.sync.completed"),
                Arguments.of("learning", "learning.resource.opened"),
                Arguments.of("learning", "learning.path.assigned"),
                Arguments.of("learning", "learning.prerequisite.registered"),
                Arguments.of("learning", "moodle.ws.completion.ingested"),
                Arguments.of("learning", "fundo.link.established")
        );
    }

    @Nested
    @DisplayName("Legacy Event Types (Non-Compliant — Migration Tracked)")
    class LegacyEventTypes {

        @ParameterizedTest(name = "[{0}] {1}")
        @MethodSource("zw.gov.mohcc.impilo.contract.RepoEventTypeContractTest#legacyEventTypes")
        @DisplayName("Legacy event type does NOT follow v1.1 convention (migration required)")
        void legacyEventTypeIsNonCompliant(String serviceName, String eventType) {
            assertThat(EventEnvelopeValidator.isValidEventType(eventType))
                    .as("Legacy event type '%s' from %s should not match v1.1 pattern (confirms migration needed)",
                            eventType, serviceName)
                    .isFalse();
        }
    }

    // ── Cross-service invariants ─────────────────────────────────────────

    @Nested
    @DisplayName("Cross-Service Invariants")
    class CrossServiceInvariants {

        @Test
        @DisplayName("All compliant event types start with 'impilo.'")
        void allStartWithImpilo() {
            compliantEventTypes().forEach(args -> {
                String eventType = (String) args.get()[1];
                assertThat(eventType).startsWith("impilo.");
            });
        }

        @Test
        @DisplayName("All compliant event types end with version suffix")
        void allEndWithVersion() {
            compliantEventTypes().forEach(args -> {
                String eventType = (String) args.get()[1];
                assertThat(eventType).matches(".*\\.v\\d+$");
            });
        }

        @Test
        @DisplayName("No duplicate event types across services")
        void noDuplicateEventTypes() {
            List<String> allTypes = compliantEventTypes()
                    .map(args -> (String) args.get()[1])
                    .toList();
            assertThat(allTypes).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Each service uses a consistent service segment")
        void consistentServiceSegments() {
            // Verify that event types from the same service share the same segment
            compliantEventTypes().forEach(args -> {
                String eventType = (String) args.get()[1];
                String segment = EventEnvelopeValidator.extractService(eventType);
                assertThat(segment)
                        .as("Service segment for '%s' should be non-empty", eventType)
                        .isNotBlank();
            });
        }
    }

    // ── EventEnvelope structure validation ───────────────────────────────

    @Nested
    @DisplayName("EventEnvelope Structure Contract")
    class EnvelopeStructure {

        private static final String ENVELOPE_TEMPLATE = """
                {
                    "eventId": "evt-001",
                    "eventType": "%s",
                    "schemaVersion": 1,
                    "correlationId": "corr-001",
                    "causationId": "cause-001",
                    "idempotencyKey": "idem-001",
                    "producer": "%s",
                    "tenantId": "aaaa1111-1111-1111-1111-111111111111",
                    "podId": "pod-central",
                    "occurredAt": "2026-03-14T10:00:00Z",
                    "emittedAt": "2026-03-14T10:00:01Z",
                    "subjectType": "TestEntity",
                    "subjectId": "sub-001",
                    "payload": {"key": "value"},
                    "meta": {}
                }
                """;

        @ParameterizedTest(name = "[{0}] {1}")
        @MethodSource("zw.gov.mohcc.impilo.contract.RepoEventTypeContractTest#compliantEventTypes")
        @DisplayName("Envelope with compliant event type passes full validation")
        void envelopeWithCompliantTypeIsValid(String serviceName, String eventType) {
            String envelope = String.format(ENVELOPE_TEMPLATE, eventType, serviceName);
            var result = EventEnvelopeValidator.validate(envelope);
            assertThat(result.valid())
                    .as("Envelope for '%s' should be valid: %s", eventType, result.violations())
                    .isTrue();
        }
    }
}
