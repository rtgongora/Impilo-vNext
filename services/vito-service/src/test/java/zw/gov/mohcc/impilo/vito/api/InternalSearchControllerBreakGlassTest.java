package zw.gov.mohcc.impilo.vito.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.core.IdentityService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("InternalSearchController — break-glass unmasked open")
class InternalSearchControllerBreakGlassTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID HID = UUID.randomUUID();

    private ClientRepository clientRepository;
    private EventOutboxRepository outboxRepository;
    private InternalSearchController controller;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        outboxRepository = mock(EventOutboxRepository.class);
        controller = new InternalSearchController(
                clientRepository, mock(IdentityService.class), outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(TENANT, "staff-9", "STAFF", "CARE_DELIVERY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("opening without a reason is rejected and reads nothing")
    void missingReasonRejected() {
        ResponseEntity<?> r = controller.getFull(HID, "  ");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(clientRepository, never()).findByTenantIdAndHealthId(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("opening with a reason returns the record AND writes a break-glass audit event")
    void withReasonAuditsAndReturns() {
        ClientEntity client = new ClientEntity();
        client.setTenantId(TENANT);
        client.setHealthId(HID);
        when(clientRepository.findByTenantIdAndHealthId(TENANT, HID)).thenReturn(Optional.of(client));

        ResponseEntity<?> r = controller.getFull(HID, "Patient collapsed in ED, verifying identity");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        org.mockito.ArgumentCaptor<EventOutboxEntity> cap =
                org.mockito.ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("CLIENT_RECORD_BREAK_GLASS_ACCESS");
        assertThat(cap.getValue().getPayload()).contains("staff-9").contains("Patient collapsed");
    }

    @Test
    @DisplayName("a non-existent record is not audited as accessed")
    void notFoundNotAudited() {
        when(clientRepository.findByTenantIdAndHealthId(TENANT, HID)).thenReturn(Optional.empty());
        ResponseEntity<?> r = controller.getFull(HID, "valid reason here");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(outboxRepository, never()).save(any());
    }
}
