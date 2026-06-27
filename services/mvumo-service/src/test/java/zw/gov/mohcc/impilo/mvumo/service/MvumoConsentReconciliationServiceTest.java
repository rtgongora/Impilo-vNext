package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.mvumo.domain.ConsentRequestState;
import zw.gov.mohcc.impilo.mvumo.integration.TshepoConsentClient;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MvumoConsentReconciliationServiceTest {

    @Mock private ConsentRequestRepository requestRepository;
    @Mock private TshepoConsentClient tshepoConsentClient;
    @Mock private ConsentEventRepository consentEventRepository;
    @Mock private EventOutboxRepository eventOutboxRepository;

    private MvumoConsentReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new MvumoConsentReconciliationService(
                requestRepository, tshepoConsentClient, consentEventRepository, eventOutboxRepository, new ObjectMapper());
    }

    private ConsentRequestEntity granted(UUID directiveId) {
        ConsentRequestEntity e = new ConsentRequestEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(UUID.randomUUID());
        e.setState(ConsentRequestState.GRANTED.name());
        e.setTshepoConsentId(directiveId);
        return e;
    }

    @Test
    void activeDirective_noChange() {
        ConsentRequestEntity e = granted(UUID.randomUUID());
        when(requestRepository.findByStateInAndTshepoConsentIdIsNotNull(any())).thenReturn(List.of(e));
        when(tshepoConsentClient.getDirectiveStatus(any())).thenReturn("ACTIVE");

        assertEquals(0, service.reconcileOnce());
        assertEquals(ConsentRequestState.GRANTED.name(), e.getState());
        verify(eventOutboxRepository, never()).save(any());
    }

    @Test
    void missingDirective_invalidates() {
        ConsentRequestEntity e = granted(UUID.randomUUID());
        when(requestRepository.findByStateInAndTshepoConsentIdIsNotNull(any())).thenReturn(List.of(e));
        when(tshepoConsentClient.getDirectiveStatus(any())).thenReturn(null);

        assertEquals(1, service.reconcileOnce());
        assertEquals(ConsentRequestState.INVALIDATED.name(), e.getState());
        verify(consentEventRepository).save(any());
        verify(eventOutboxRepository).save(any());
    }

    @Test
    void revokedDirective_flagsSyncConflict() {
        ConsentRequestEntity e = granted(UUID.randomUUID());
        when(requestRepository.findByStateInAndTshepoConsentIdIsNotNull(any())).thenReturn(List.of(e));
        when(tshepoConsentClient.getDirectiveStatus(any())).thenReturn("REVOKED");

        assertEquals(1, service.reconcileOnce());
        assertEquals(ConsentRequestState.SYNC_CONFLICT.name(), e.getState());
        verify(eventOutboxRepository).save(any());
    }

    @Test
    void statusCheckFailure_neverInvalidates() {
        ConsentRequestEntity e = granted(UUID.randomUUID());
        when(requestRepository.findByStateInAndTshepoConsentIdIsNotNull(any())).thenReturn(List.of(e));
        when(tshepoConsentClient.getDirectiveStatus(any())).thenThrow(new IllegalStateException("tshepo down"));

        assertEquals(0, service.reconcileOnce());
        assertEquals(ConsentRequestState.GRANTED.name(), e.getState(), "transient errors must not invalidate a grant");
        verify(eventOutboxRepository, never()).save(any());
    }
}
