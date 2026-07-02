package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.core.DischargeWorkflow;
import zw.gov.mohcc.impilo.pct.core.QueueMaterializationService;
import zw.gov.mohcc.impilo.pct.core.TaskService;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DischargeCaseRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PctEventConsumer}.
 *
 * <p>Validates the Kafka event consumer behavior for MUSHEX payment
 * status events (auto-clearing PAYMENT blockers) and idempotency
 * guarantees.</p>
 */
@ExtendWith(MockitoExtension.class)
class PctEventConsumerTest {

    @Mock
    private DischargeWorkflow dischargeWorkflow;

    @Mock
    private TaskService taskService;

    @Mock
    private DischargeCaseRepository dischargeCaseRepository;

    @Mock
    private zw.gov.mohcc.impilo.pct.core.QueueMaterializationService queueMaterializationService;

    private PctEventConsumer consumer;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "system";
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new PctEventConsumer(
                dischargeWorkflow, taskService, dischargeCaseRepository,
                queueMaterializationService, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "SYSTEM", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    @Nested
    @DisplayName("MUSHEX Payment Status Consumer")
    class MushexPaymentStatusTests {

        @Test
        @DisplayName("auto-clears PAYMENT blocker when payment status is CLEARED")
        void autoClears_PaymentBlocker_WhenCleared() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setJourneyId("J-TEST-001");
            dischargeCase.setStatus("INITIATED");
            dischargeCase.setPaymentCleared(false);
            dischargeCase.setBlockers("[\"PAYMENT\",\"SUMMARY\"]");

            when(dischargeCaseRepository.findByJourneyIdAndStatusIn("J-TEST-001", List.of("INITIATED")))
                    .thenReturn(Optional.of(dischargeCase));
            when(dischargeWorkflow.clearBlocker(caseId, "PAYMENT")).thenReturn(dischargeCase);

            String event = "{\"journeyId\":\"J-TEST-001\",\"status\":\"CLEARED\"}";
            consumer.consumeMushexPaymentStatus(event);

            verify(dischargeWorkflow).clearBlocker(caseId, "PAYMENT");
        }

        @Test
        @DisplayName("auto-clears PAYMENT blocker when payment status is EXEMPTED")
        void autoClears_PaymentBlocker_WhenExempted() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setJourneyId("J-TEST-002");
            dischargeCase.setStatus("INITIATED");
            dischargeCase.setPaymentCleared(false);
            dischargeCase.setBlockers("[\"PAYMENT\"]");

            when(dischargeCaseRepository.findByJourneyIdAndStatusIn("J-TEST-002", List.of("INITIATED")))
                    .thenReturn(Optional.of(dischargeCase));
            when(dischargeWorkflow.clearBlocker(caseId, "PAYMENT")).thenReturn(dischargeCase);

            String event = "{\"journeyId\":\"J-TEST-002\",\"status\":\"EXEMPTED\"}";
            consumer.consumeMushexPaymentStatus(event);

            verify(dischargeWorkflow).clearBlocker(caseId, "PAYMENT");
        }

        @Test
        @DisplayName("does not clear PAYMENT blocker when status is PENDING")
        void doesNotClear_WhenPending() {
            String event = "{\"journeyId\":\"J-TEST-003\",\"status\":\"PENDING\"}";
            consumer.consumeMushexPaymentStatus(event);

            verify(dischargeWorkflow, never()).clearBlocker(any(), any());
            verify(dischargeCaseRepository, never()).findByJourneyIdAndStatusIn(any(), any());
        }

        @Test
        @DisplayName("does nothing when no active discharge case exists")
        void doesNothing_WhenNoActiveDischargeCase() {
            when(dischargeCaseRepository.findByJourneyIdAndStatusIn("J-TEST-004", List.of("INITIATED")))
                    .thenReturn(Optional.empty());

            String event = "{\"journeyId\":\"J-TEST-004\",\"status\":\"CLEARED\"}";
            consumer.consumeMushexPaymentStatus(event);

            verify(dischargeWorkflow, never()).clearBlocker(any(), any());
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("processing same payment event twice does not fail when blocker already cleared")
        void sameEventTwiceDoesNotFail() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setJourneyId("J-TEST-005");
            dischargeCase.setStatus("INITIATED");
            dischargeCase.setPaymentCleared(true); // Already cleared
            dischargeCase.setBlockers("[\"SUMMARY\"]");

            when(dischargeCaseRepository.findByJourneyIdAndStatusIn("J-TEST-005", List.of("INITIATED")))
                    .thenReturn(Optional.of(dischargeCase));

            String event = "{\"journeyId\":\"J-TEST-005\",\"status\":\"CLEARED\"}";

            // First call
            assertThatCode(() -> consumer.consumeMushexPaymentStatus(event))
                    .doesNotThrowAnyException();

            // Second call with same event — should be idempotent
            assertThatCode(() -> consumer.consumeMushexPaymentStatus(event))
                    .doesNotThrowAnyException();

            // clearBlocker should never be called because paymentCleared is already true
            verify(dischargeWorkflow, never()).clearBlocker(any(), any());
        }

        @Test
        @DisplayName("gracefully handles malformed JSON event")
        void handlersMalformedJson() {
            String malformedEvent = "NOT-VALID-JSON";

            assertThatCode(() -> consumer.consumeMushexPaymentStatus(malformedEvent))
                    .doesNotThrowAnyException();

            verify(dischargeWorkflow, never()).clearBlocker(any(), any());
        }

        @Test
        @DisplayName("gracefully handles event with missing fields")
        void handlesMissingFields() {
            String eventMissingFields = "{\"someField\":\"value\"}";

            assertThatCode(() -> consumer.consumeMushexPaymentStatus(eventMissingFields))
                    .doesNotThrowAnyException();

            verify(dischargeWorkflow, never()).clearBlocker(any(), any());
        }
    }

    @Nested
    @DisplayName("OROS Result Available Consumer")
    class OrosResultAvailableTests {

        @Test
        @DisplayName("creates a review task when result is available")
        void createsReviewTask() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(taskService.createTask(any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(null); // Return value not needed for this test

                String event = "{\"orderId\":\"ORD-001\",\"journeyId\":\"J-TEST-006\"," +
                        "\"resultType\":\"LAB\",\"orderingProvider\":\"dr-jones\"}";

                consumer.consumeOrosResultAvailable(event);

                verify(taskService).createTask(
                        eq("J-TEST-006"),
                        isNull(),
                        eq("LAB_REVIEW"),
                        eq("dr-jones"),
                        isNull(),
                        isNull(),
                        isNull(),
                        contains("LAB"));
            }
        }
    }

    @Nested
    @DisplayName("TUSO Workspace/Service-Point Update Consumer")
    class TusoWorkspaceConsumer {

        @Test
        @DisplayName("triggers PCT queue materialisation for the facility")
        void triggersMaterialisation() {
            UUID facility = UUID.randomUUID();
            when(queueMaterializationService.reconcileFacility(eq(TENANT_ID), eq(facility)))
                    .thenReturn(new QueueMaterializationService.MaterializationResult(
                            facility, "OK", 2, 0, 0, 0, 2, null));

            String event = "{\"facilityId\":\"" + facility + "\",\"tenantId\":\"" + TENANT_ID
                    + "\",\"changeType\":\"SERVICE_POINT_UPDATED\"}";
            consumer.consumeTusoWorkspaceUpdated(event);

            verify(queueMaterializationService).reconcileFacility(TENANT_ID, facility);
        }

        @Test
        @DisplayName("skips materialisation when facilityId is missing")
        void skipsWhenNoFacility() {
            consumer.consumeTusoWorkspaceUpdated("{\"tenantId\":\"" + TENANT_ID + "\"}");
            verify(queueMaterializationService, never()).reconcileFacility(any(), any());
        }

        @Test
        @DisplayName("skips materialisation when tenantId is missing (tenant-scoped)")
        void skipsWhenNoTenant() {
            consumer.consumeTusoWorkspaceUpdated("{\"facilityId\":\"" + UUID.randomUUID() + "\"}");
            verify(queueMaterializationService, never()).reconcileFacility(any(), any());
        }
    }
}
