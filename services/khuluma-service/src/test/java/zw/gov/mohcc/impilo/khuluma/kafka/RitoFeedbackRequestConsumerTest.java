package zw.gov.mohcc.impilo.khuluma.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.core.DeliveryService;
import zw.gov.mohcc.impilo.khuluma.core.DeliveryService.DispatchContent;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RW11 acceptance coverage for the post-visit feedback-request orchestration seam
 * (RW9). Proves Khuluma consumes Rito's {@code rito.feedback.requested} signal and
 * dispatches across channels — carrying the patient CPID as the notification-service
 * recipient/patientRef (Khuluma holds no PII), the encounter context, and the
 * Rito-chosen template — and that a malformed / incomplete event is fail-soft
 * (logged, never dispatched, never poisons the listener).
 */
@ExtendWith(MockitoExtension.class)
class RitoFeedbackRequestConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private DeliveryService deliveryService;
    @Captor private ArgumentCaptor<DispatchContent> contentCaptor;
    @Captor private ArgumentCaptor<List<String>> channelsCaptor;

    private RitoFeedbackRequestConsumer consumer() {
        return new RitoFeedbackRequestConsumer(mapper, deliveryService);
    }

    @Test
    void dispatchesFeedbackRequestToAllChannelsWithPatientRefAndTemplate() {
        UUID tenant = UUID.randomUUID();
        String encounterRef = UUID.randomUUID().toString();
        String event = """
            {"tenantId":"%s","encounterRef":"%s","patientCpid":"CPID-42",
             "attendingProviderId":"PROV12345678901234567890AB",
             "facilityId":"%s","encounterType":"OUTPATIENT",
             "templateKey":"POST_VISIT_FEEDBACK_REQUEST","messageKind":"POST_VISIT_FEEDBACK"}
            """.formatted(tenant, encounterRef, UUID.randomUUID());

        consumer().onFeedbackRequested(event);

        verify(deliveryService).dispatch(
                eq(tenant), eq(encounterRef), eq("CPID-42"),
                channelsCaptor.capture(), contentCaptor.capture());

        // Cross-channel post-visit request: SMS + WhatsApp + in-app.
        assertThat(channelsCaptor.getValue()).containsExactly("SMS", "WHATSAPP", "IN_APP");
        DispatchContent content = contentCaptor.getValue();
        assertThat(content.templateKey()).isEqualTo("POST_VISIT_FEEDBACK_REQUEST");
        assertThat(content.messageKind()).isEqualTo("POST_VISIT_FEEDBACK");
        // Recipient/patientRef = the patient CPID — notification-service resolves the
        // real contact + comm-preferences; Khuluma never carries PII.
        assertThat(content.patientRef()).isEqualTo("CPID-42");
        assertThat(content.variables()).containsEntry("encounterRef", encounterRef);
        // The in-app deep link the notification template turns into the tap target (RW12).
        assertThat(content.variables()).containsEntry("feedbackPath", "/feedback/visit/" + encounterRef);
    }

    @Test
    void skipsEventMissingEncounterRef() {
        UUID tenant = UUID.randomUUID();
        consumer().onFeedbackRequested("{\"tenantId\":\"" + tenant + "\",\"patientCpid\":\"CPID-x\"}");
        verify(deliveryService, never()).dispatch(any(), any(), any(), any(), any());
    }

    @Test
    void malformedPayloadIsFailSoft() {
        consumer().onFeedbackRequested("not-json");
        verify(deliveryService, never()).dispatch(any(), any(), any(), any(), any());
    }
}
