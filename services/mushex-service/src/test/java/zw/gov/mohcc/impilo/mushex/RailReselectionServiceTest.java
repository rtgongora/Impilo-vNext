package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailSelectionReason;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.service.IntentNotFoundException;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.RailReselectionService;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionPolicy;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionRequest;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionResult;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RailReselectionServiceTest {

    @Mock private PaymentIntentService paymentIntentService;
    @Mock private PaymentIntentRepository intentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private RailSelectionPolicy railSelectionPolicy;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private RailReselectionService build() {
        return new RailReselectionService(paymentIntentService, intentRepository,
                outboxRepository, railSelectionPolicy, objectMapper);
    }

    private PaymentIntentEntity intent(String metadata) {
        PaymentIntentEntity i = new PaymentIntentEntity();
        i.setIntentId("01INTENT");
        i.setSourceType(SourceType.COSTA_BILL);
        i.setCurrency("USD");
        i.setAmountTotal(new BigDecimal("12.34"));
        i.setFacilityId(facilityId);
        i.setStatus(IntentStatus.PENDING);
        i.setMetadata(metadata);
        return i;
    }

    @Test
    void reselect_replacesEffectiveRailAndPushesPreviousToHistory() throws Exception {
        PaymentIntentEntity i = intent(
                "{\"rail_selection\":{\"effective_rail\":\"MOBILE_MONEY\",\"preferred_rail\":\"MOBILE_MONEY\","
                        + "\"fallback_applied\":false,\"reason\":\"EXPLICIT_PREFERRED\"}}");
        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(railSelectionPolicy.select(any(RailSelectionRequest.class))).thenReturn(
                new RailSelectionResult(AdapterType.SANDBOX, null, false, false,
                        RailSelectionReason.DEFAULT_NO_PREFERENCE, "deployment default"));
        when(intentRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentEntity result = build().reselect("01INTENT", "mobile money provider outage");

        JsonNode meta = objectMapper.readTree(result.getMetadata());
        JsonNode rs = meta.get("rail_selection");
        assertEquals("SANDBOX", rs.get("effective_rail").asText());
        assertEquals(RailSelectionReason.DEFAULT_NO_PREFERENCE.name(), rs.get("reason").asText());
        assertTrue(rs.get("reason_detail").asText().contains("mobile money provider outage"));
        assertTrue(rs.get("reselected").asBoolean());
        assertEquals(1, rs.get("history").size());
        assertEquals("MOBILE_MONEY", rs.get("history").get(0).get("effective_rail").asText());
        assertTrue(rs.get("history").get(0).has("superseded_at"));

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, times(1)).save(outboxCaptor.capture());
        EventOutboxEntity event = outboxCaptor.getValue();
        assertEquals("ATTEMPT_RESELECTED", event.getEventType());
        assertEquals("01INTENT", event.getAggregateId());
        assertEquals("PAYMENT_INTENT", event.getAggregateType());
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertEquals("MOBILE_MONEY", payload.get("previousRail").asText());
        assertEquals("SANDBOX", payload.get("effectiveRail").asText());
        assertEquals("mobile money provider outage", payload.get("retryReason").asText());
    }

    @Test
    void reselect_appendsToExistingHistoryArray() throws Exception {
        PaymentIntentEntity i = intent(
                "{\"rail_selection\":{\"effective_rail\":\"BANK_TRANSFER\",\"reason\":\"EXPLICIT_PREFERRED\","
                        + "\"history\":[{\"effective_rail\":\"MOBILE_MONEY\",\"superseded_at\":\"2026-01-01T00:00:00Z\"}]}}");
        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(railSelectionPolicy.select(any(RailSelectionRequest.class))).thenReturn(
                new RailSelectionResult(AdapterType.SANDBOX, null, false, false,
                        RailSelectionReason.DEFAULT_NO_PREFERENCE, ""));
        when(intentRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentEntity result = build().reselect("01INTENT", null);
        JsonNode rs = objectMapper.readTree(result.getMetadata()).get("rail_selection");
        assertEquals(2, rs.get("history").size());
        // Existing entry preserved
        assertEquals("MOBILE_MONEY", rs.get("history").get(0).get("effective_rail").asText());
        // Newly-appended snapshot of the just-replaced selection
        assertEquals("BANK_TRANSFER", rs.get("history").get(1).get("effective_rail").asText());
    }

    @Test
    void reselect_unknownIntent_throwsIntentNotFound() {
        when(paymentIntentService.getIntent("01MISSING"))
                .thenThrow(new IntentNotFoundException("01MISSING"));
        IntentNotFoundException ex = assertThrows(IntentNotFoundException.class,
                () -> build().reselect("01MISSING", null));
        assertEquals("01MISSING", ex.getIntentId());
        verify(outboxRepository, times(0)).save(any());
        verify(intentRepository, times(0)).save(any());
    }

    @Test
    void reselect_emptyMetadataIsSafe() throws Exception {
        PaymentIntentEntity i = intent(null);
        when(paymentIntentService.getIntent("01INTENT")).thenReturn(i);
        when(railSelectionPolicy.select(any(RailSelectionRequest.class))).thenReturn(
                new RailSelectionResult(AdapterType.SANDBOX, null, false, false,
                        RailSelectionReason.DEFAULT_NO_PREFERENCE, ""));
        when(intentRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentEntity result = build().reselect("01INTENT", null);
        assertNotNull(result.getMetadata());
        JsonNode rs = objectMapper.readTree(result.getMetadata()).get("rail_selection");
        assertEquals("SANDBOX", rs.get("effective_rail").asText());
        assertEquals(0, rs.get("history").size());
    }
}
