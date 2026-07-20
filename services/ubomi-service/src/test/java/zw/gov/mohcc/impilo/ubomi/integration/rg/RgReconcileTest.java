package zw.gov.mohcc.impilo.ubomi.integration.rg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** W1c — reconcile loop: TTL expiry to RG_UNAVAILABLE and re-drive to RG_VERIFIED. */
@ExtendWith(MockitoExtension.class)
class RgReconcileTest {

    @Mock RgVerificationRepository ledger;
    @Mock EventOutboxRepository outbox;
    @Mock ObjectProvider<RegistrarGeneralClient> clientProvider;
    @Mock RegistrarGeneralClient client;

    RgVerificationService service;

    @BeforeEach
    void setUp() {
        service = new RgVerificationService(ledger, outbox, clientProvider, new ObjectMapper());
    }

    private RgVerificationEntity pending(OffsetDateTime createdAt) {
        RgVerificationEntity e = new RgVerificationEntity();
        e.setId(7L);
        e.setTenantId(UUID.randomUUID());
        e.setSubjectRef(UUID.randomUUID().toString());
        e.setVerificationType("NATIONAL_ID");
        e.setReferenceHash("abc123");
        e.setStatus(RgVerificationEntity.RG_PENDING);
        e.setCreatedAt(createdAt);
        return e;
    }

    @Test
    void expires_stale_pending_to_rg_unavailable_without_fabricating() {
        RgVerificationEntity row = pending(OffsetDateTime.now().minusHours(100));
        when(ledger.findById(7L)).thenReturn(Optional.of(row));
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean terminal = service.reDrive(7L, 72);

        assertThat(terminal).isTrue();
        assertThat(row.getStatus()).isEqualTo(RgVerificationEntity.RG_UNAVAILABLE);
        verify(outbox, never()).save(any());       // never fabricates a verification
        verifyNoInteractions(client);
    }

    @Test
    void redrive_matches_and_emits_civil_identity_verified() {
        RgVerificationEntity row = pending(OffsetDateTime.now().minusMinutes(30));
        when(ledger.findById(7L)).thenReturn(Optional.of(row));
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.confirmRegistration("NATIONAL_ID", "abc123"))
                .thenReturn(new RgVerificationOutcome(true, "opaque-tok-99", false, false));

        boolean terminal = service.reDrive(7L, 72);

        assertThat(terminal).isTrue();
        assertThat(row.getStatus()).isEqualTo(RgVerificationEntity.RG_VERIFIED);
        assertThat(row.getCivilIdentityToken()).isEqualTo("opaque-tok-99");
        verify(outbox).save(argThat((EventOutboxEntity e) ->
                "CIVIL_IDENTITY_VERIFIED".equals(e.getEventType())
                        && e.getPayload().contains("opaque-tok-99")));
    }

    @Test
    void redrive_stays_pending_on_outage() {
        RgVerificationEntity row = pending(OffsetDateTime.now().minusMinutes(30));
        when(ledger.findById(7L)).thenReturn(Optional.of(row));
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.confirmRegistration(anyString(), anyString()))
                .thenThrow(new RgUnavailableException("RG down"));

        boolean terminal = service.reDrive(7L, 72);

        assertThat(terminal).isFalse();
        assertThat(row.getStatus()).isEqualTo(RgVerificationEntity.RG_PENDING);
        verify(outbox, never()).save(any());
    }
}
