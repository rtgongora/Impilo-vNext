package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vito.core.ProofingMethod;
import zw.gov.mohcc.impilo.vito.core.proofing.ProofingService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * W1d — consuming {@code civil.identity.verified} records the previously-dark
 * {@code AUTHORITATIVELY_VERIFIED} proofing outcome (LOA 3, MOSIP_INDIRECT),
 * storing only the opaque token.
 */
@ExtendWith(MockitoExtension.class)
class CivilIdentityEventConsumerTest {

    @Mock ProofingService proofingService;
    CivilIdentityEventConsumer consumer;

    final UUID TENANT = UUID.randomUUID();
    final UUID HEALTH = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new CivilIdentityEventConsumer(proofingService, new ObjectMapper());
    }

    @Test
    void verified_event_records_authoritatively_verified_outcome() {
        String msg = """
                {"tenantId":"%s","healthId":"%s","status":"VERIFIED",
                 "civilIdentityToken":"CIT-OPAQUE-777","verifiedAt":"2026-07-20T10:00:00Z"}"""
                .formatted(TENANT, HEALTH);

        consumer.onCivilIdentityVerified(msg);

        ArgumentCaptor<String> artifacts = ArgumentCaptor.forClass(String.class);
        verify(proofingService).recordEvent(
                eq(TENANT), eq(HEALTH),
                eq(ProofingMethod.MOSIP_INDIRECT), eq(3),
                artifacts.capture(),
                eq("ubomi-rg-relay"),
                anyString());
        assertThat(artifacts.getValue()).contains("CIT-OPAQUE-777");
    }

    @Test
    void tolerates_v11_envelope_wrapper() {
        String msg = """
                {"event_type":"impilo.ubomi.civil_identity.verified.v1",
                 "payload":{"tenantId":"%s","healthId":"%s","status":"VERIFIED",
                            "civilIdentityToken":"CIT-ENV-1"}}"""
                .formatted(TENANT, HEALTH);

        consumer.onCivilIdentityVerified(msg);

        verify(proofingService).recordEvent(eq(TENANT), eq(HEALTH),
                eq(ProofingMethod.MOSIP_INDIRECT), eq(3), anyString(), eq("ubomi-rg-relay"), anyString());
    }

    @Test
    void non_verified_status_is_ignored() {
        String msg = """
                {"tenantId":"%s","healthId":"%s","status":"DISPUTED"}""".formatted(TENANT, HEALTH);
        consumer.onCivilIdentityVerified(msg);
        verifyNoInteractions(proofingService);
    }

    @Test
    void missing_required_fields_are_ignored() {
        String msg = "{\"status\":\"VERIFIED\",\"civilIdentityToken\":\"\"}";
        consumer.onCivilIdentityVerified(msg);
        verifyNoInteractions(proofingService);
    }

    @Test
    void malformed_message_never_throws() {
        consumer.onCivilIdentityVerified("not-json");
        verifyNoInteractions(proofingService);
    }
}
