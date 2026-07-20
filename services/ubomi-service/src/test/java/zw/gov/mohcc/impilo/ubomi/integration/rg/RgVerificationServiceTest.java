package zw.gov.mohcc.impilo.ubomi.integration.rg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** W1b/W1c — fail-open posture, idempotency, and civil.* emission (no RG bean). */
@ExtendWith(MockitoExtension.class)
class RgVerificationServiceTest {

    @Mock RgVerificationRepository ledger;
    @Mock EventOutboxRepository outbox;
    @Mock ObjectProvider<RegistrarGeneralClient> clientProvider;

    RgVerificationService service;
    final UUID TENANT = UUID.randomUUID();
    final String SUBJECT = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new RgVerificationService(ledger, outbox, clientProvider, new ObjectMapper());
    }

    @Test
    void flag_off_parks_pending_and_never_throws() {
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledger.findByTenantIdAndBusinessKey(eq(TENANT), anyString())).thenReturn(Optional.empty());
        when(clientProvider.getIfAvailable()).thenReturn(null); // RG disabled — no client bean

        RgVerificationService.RgVerificationResult r =
                service.verify(TENANT, SUBJECT, "NATIONAL_ID", "63-1234567X08", null, null);

        assertThat(r.status()).isEqualTo(RgVerificationEntity.RG_PENDING);
        assertThat(r.matched()).isFalse();
        verify(outbox, never()).save(any()); // no civil event on a parked attempt
    }

    @Test
    void terminal_row_is_idempotent_and_short_circuits() {
        RgVerificationEntity existing = new RgVerificationEntity();
        existing.setStatus(RgVerificationEntity.RG_VERIFIED);
        existing.setCivilIdentityToken("opaque-tok");
        when(ledger.findByTenantIdAndBusinessKey(eq(TENANT), anyString())).thenReturn(Optional.of(existing));

        RgVerificationService.RgVerificationResult r =
                service.verify(TENANT, SUBJECT, "NATIONAL_ID", "63-1234567X08", null, null);

        assertThat(r.status()).isEqualTo(RgVerificationEntity.RG_VERIFIED);
        assertThat(r.civilIdentityToken()).isEqualTo("opaque-tok");
        verifyNoInteractions(clientProvider); // never re-hits RG for a terminal row
    }

    @Test
    void dispute_emits_civil_identity_disputed() {
        service.dispute(TENANT, SUBJECT, "name mismatch at RG");
        ArgumentCaptor<EventOutboxEntity> cap = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("CIVIL_IDENTITY_DISPUTED");
        assertThat(cap.getValue().getPayload()).contains("DISPUTED");
    }

    @Test
    void correct_emits_civil_identity_corrected() {
        service.correct(TENANT, SUBJECT, "opaque-tok");
        ArgumentCaptor<EventOutboxEntity> cap = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("CIVIL_IDENTITY_CORRECTED");
    }
}
