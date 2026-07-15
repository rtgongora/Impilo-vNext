package zw.gov.mohcc.impilo.daidzai.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.daidzai.api.controller.PublicEmergencyStatusController;
import zw.gov.mohcc.impilo.daidzai.api.controller.PublicEmergencyStatusController.PublicEmergencyStatus;
import zw.gov.mohcc.impilo.daidzai.core.EmergencyService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyRequestEntity;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the anonymous public emergency status lane (gateway public-lane ADR
 * {@code Public*Controller} rule): allow-listed PII-free disclosure and an honest 404 on a miss.
 */
@ExtendWith(MockitoExtension.class)
class PublicEmergencyStatusControllerTest {

    @Mock private EmergencyService service;

    private static final UUID TENANT = UUID.randomUUID();

    /** An AWAITING_CALLBACK request carrying PII in the entity (callback / description / location / subject). */
    private static EmergencyRequestEntity awaitingCallbackRequest() {
        EmergencyRequestEntity r = new EmergencyRequestEntity();
        r.setRequestReference("SOS-20260715-AB12");
        r.setStatus(EmergencyService.STATUS_AWAITING_CALLBACK);
        r.setCallbackVerified(Boolean.FALSE);
        r.setCallbackNumber("+263771234567");
        r.setDescription("Man collapsed at the market");
        r.setLocationDescription("Mbare market");
        r.setSubjectHealthId("HID-SECRET-1");
        r.setSubjectLabel("John Doe");
        return r;
    }

    @Test
    void statusReturnsAllowListedPiiFreeView() {
        EmergencyRequestEntity r = awaitingCallbackRequest();
        when(service.findRequestByReference(TENANT, "SOS-20260715-AB12")).thenReturn(Optional.of(r));

        PublicEmergencyStatusController controller = new PublicEmergencyStatusController(service);
        var response = controller.status(TENANT, "SOS-20260715-AB12");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        PublicEmergencyStatus body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.requestReference()).isEqualTo("SOS-20260715-AB12");
        assertThat(body.status()).isEqualTo("AWAITING_CALLBACK");
        assertThat(body.stage()).isEqualTo("Awaiting callback confirmation");
        assertThat(body.callbackPending()).isTrue();

        // PII-free by construction: the disclosure record carries exactly the allow-listed components
        // and none of the sensitive entity fields, even though the source entity holds them.
        Set<String> components = Arrays.stream(PublicEmergencyStatus.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(components).containsExactlyInAnyOrder(
                "requestReference", "status", "stage", "callbackPending", "createdAt");
        assertThat(components).doesNotContain(
                "callbackNumber", "description", "locationDescription",
                "subjectHealthId", "subjectLabel", "locationLat", "locationLng");
    }

    @Test
    void callbackPendingFalseOnceVerified() {
        EmergencyRequestEntity r = awaitingCallbackRequest();
        r.setStatus("RECEIVED");
        r.setCallbackVerified(Boolean.TRUE);
        when(service.findRequestByReference(TENANT, "SOS-1")).thenReturn(Optional.of(r));

        PublicEmergencyStatusController controller = new PublicEmergencyStatusController(service);
        PublicEmergencyStatus body = controller.status(TENANT, "SOS-1").getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("RECEIVED");
        assertThat(body.stage()).isEqualTo("Received — being reviewed");
        assertThat(body.callbackPending()).isFalse();
    }

    @Test
    void unknownReferenceIsAnHonest404() {
        when(service.findRequestByReference(TENANT, "SOS-NONE")).thenReturn(Optional.empty());

        PublicEmergencyStatusController controller = new PublicEmergencyStatusController(service);
        var response = controller.status(TENANT, "SOS-NONE");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void createdAtTimestampIsDisclosed() {
        EmergencyRequestEntity r = awaitingCallbackRequest();
        // createdAt is normally stamped by @PrePersist; set it here for the unit test.
        OffsetDateTime created = OffsetDateTime.parse("2026-07-15T08:30:00Z");
        setCreatedAt(r, created);
        when(service.findRequestByReference(TENANT, "SOS-20260715-AB12")).thenReturn(Optional.of(r));

        PublicEmergencyStatusController controller = new PublicEmergencyStatusController(service);
        PublicEmergencyStatus body = controller.status(TENANT, "SOS-20260715-AB12").getBody();

        assertThat(body).isNotNull();
        assertThat(body.createdAt()).isEqualTo(created);
    }

    /** createdAt has no setter on the entity (it is persistence-managed); set it reflectively. */
    private static void setCreatedAt(EmergencyRequestEntity r, OffsetDateTime value) {
        try {
            var f = EmergencyRequestEntity.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(r, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void recordComponentsNeverGrowToIncludePii() {
        // Guard against a future field being mapped onto the public status view.
        List<String> names = Arrays.stream(PublicEmergencyStatus.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(names).hasSize(5);
    }
}
