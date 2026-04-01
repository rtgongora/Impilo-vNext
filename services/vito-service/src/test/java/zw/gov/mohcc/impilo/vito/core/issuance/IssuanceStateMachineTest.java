package zw.gov.mohcc.impilo.vito.core.issuance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import zw.gov.mohcc.impilo.vito.core.*;
import zw.gov.mohcc.impilo.vito.core.card.CardLifecycleService;
import zw.gov.mohcc.impilo.vito.core.id.ImpiloIdFormat;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IssuanceStateMachineTest {

    @Mock private IssuanceRequestRepository issuanceRepo;
    @Mock private ClientRepository clientRepo;
    @Mock private ImpiloIdFormat impiloIdFormat;
    @Mock private ImpiloIdAliasService aliasService;
    @Mock private CardLifecycleService cardService;
    @Mock private EventOutboxRepository outboxRepo;

    @InjectMocks private IssuanceStateMachineService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void submitCreatesRequestInSubmittedState() {
        ClientEntity client = new ClientEntity();
        client.setHealthId(healthId);
        client.setTenantId(tenantId);
        when(clientRepo.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(client));
        when(issuanceRepo.findByTenantIdAndHealthIdAndStateNotIn(any(), any(), any())).thenReturn(Optional.empty());
        when(issuanceRepo.save(any())).thenAnswer(inv -> {
            IssuanceRequestEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IssuanceRequestEntity result = service.submit(tenantId, healthId, IssuanceType.NEW, IssuanceChannel.ASSISTED, "actor1");

        assertEquals(IssuanceState.SUBMITTED, result.getState());
    }

    @Test
    void portalSubmitAutoTransitionsToProofing() {
        ClientEntity client = new ClientEntity();
        client.setHealthId(healthId);
        when(clientRepo.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(client));
        when(issuanceRepo.findByTenantIdAndHealthIdAndStateNotIn(any(), any(), any())).thenReturn(Optional.empty());
        when(issuanceRepo.save(any())).thenAnswer(inv -> {
            IssuanceRequestEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IssuanceRequestEntity result = service.submit(tenantId, healthId, IssuanceType.NEW, IssuanceChannel.PORTAL, "actor1");

        assertEquals(IssuanceState.PROOFING, result.getState());
    }

    @Test
    void cannotApproveFromSubmittedState() {
        IssuanceRequestEntity req = new IssuanceRequestEntity();
        req.setId(1L);
        req.setTenantId(tenantId);
        req.setState(IssuanceState.SUBMITTED);
        when(issuanceRepo.findById(1L)).thenReturn(Optional.of(req));

        assertThrows(IllegalStateException.class, () ->
                service.approve(tenantId, 1L, "actor1"));
    }

    @Test
    void cannotDeliverFromProofingState() {
        IssuanceRequestEntity req = new IssuanceRequestEntity();
        req.setId(1L);
        req.setTenantId(tenantId);
        req.setState(IssuanceState.PROOFING);
        when(issuanceRepo.findById(1L)).thenReturn(Optional.of(req));

        assertThrows(IllegalStateException.class, () ->
                service.deliver(tenantId, 1L, null));
    }

    @Test
    void rejectFromAnyNonTerminalState() {
        IssuanceRequestEntity req = new IssuanceRequestEntity();
        req.setId(1L);
        req.setTenantId(tenantId);
        req.setState(IssuanceState.PROOFING);
        when(issuanceRepo.findById(1L)).thenReturn(Optional.of(req));
        when(issuanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IssuanceRequestEntity result = service.reject(tenantId, 1L, "Failed proofing");

        assertEquals(IssuanceState.REJECTED, result.getState());
        assertEquals("Failed proofing", result.getRejectionReason());
    }

    @Test
    void cannotRejectAlreadyDelivered() {
        IssuanceRequestEntity req = new IssuanceRequestEntity();
        req.setId(1L);
        req.setTenantId(tenantId);
        req.setState(IssuanceState.DELIVERED);
        when(issuanceRepo.findById(1L)).thenReturn(Optional.of(req));

        assertThrows(IllegalStateException.class, () ->
                service.reject(tenantId, 1L, "Too late"));
    }

    @Test
    void duplicateActiveIssuanceThrows() {
        ClientEntity client = new ClientEntity();
        client.setHealthId(healthId);
        when(clientRepo.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(client));

        IssuanceRequestEntity existing = new IssuanceRequestEntity();
        existing.setState(IssuanceState.PROOFING);
        when(issuanceRepo.findByTenantIdAndHealthIdAndStateNotIn(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () ->
                service.submit(tenantId, healthId, IssuanceType.NEW, IssuanceChannel.ASSISTED, "actor1"));
    }
}
