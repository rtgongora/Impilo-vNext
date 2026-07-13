package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.DisciplinaryActionRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderDisciplinaryCaseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisciplinaryCaseServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private ProviderDisciplinaryCaseRepository caseRepository;
    @Mock private DisciplinaryActionRepository actionRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private DisciplinaryCaseService service;

    @BeforeEach
    void setUp() {
        service = new DisciplinaryCaseService(caseRepository, actionRepository, providerRepository, outboxRepository);
        TrustContextHolder.set(new TrustContext(TENANT, "registrar-1", "PROVIDER", "REGISTRY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    private ProviderEntity provider() {
        ProviderEntity p = new ProviderEntity();
        p.setId(11L);
        p.setProviderPublicId("PRV-1");
        return p;
    }

    private ProviderDisciplinaryCaseEntity openCase() {
        ProviderDisciplinaryCaseEntity c = new ProviderDisciplinaryCaseEntity();
        c.setId(7L);
        c.setProvider(provider());
        c.setTenantId(TENANT);
        c.setStatus("OPEN");
        return c;
    }

    @Test
    void openCase_persistsAndEmits() {
        when(providerRepository.findByIdAndTenantId(11L, TENANT)).thenReturn(Optional.of(provider()));
        when(caseRepository.save(any())).thenAnswer(i -> { var c = (ProviderDisciplinaryCaseEntity) i.getArgument(0); c.setId(7L); return c; });

        var c = service.openCase(11L, "complaint", "Dispensing complaint", "COUNCIL");

        assertEquals("OPEN", c.getStatus());
        assertEquals("COMPLAINT", c.getTriggerType());
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void recordAction_suspension_writesActiveBlockingActionForPic() {
        when(caseRepository.findByIdAndTenantId(7L, TENANT)).thenReturn(Optional.of(openCase()));
        when(actionRepository.save(any())).thenAnswer(i -> { var a = (DisciplinaryActionEntity) i.getArgument(0); a.setId(99L); return a; });

        service.recordAction(7L, "suspension", "HIGH", "Interim suspension", null);

        ArgumentCaptor<DisciplinaryActionEntity> captor = ArgumentCaptor.forClass(DisciplinaryActionEntity.class);
        verify(actionRepository).save(captor.capture());
        DisciplinaryActionEntity saved = captor.getValue();
        assertEquals("SUSPENSION", saved.getActionType());
        assertEquals("ACTIVE", saved.getStatus()); // PIC eligibility reads ACTIVE SUSPENSION as blocking
        assertEquals(11L, saved.getProvider().getId());
    }

    @Test
    void recordAction_onClosedCase_isRejected() {
        ProviderDisciplinaryCaseEntity closed = openCase();
        closed.setStatus("CLOSED");
        when(caseRepository.findByIdAndTenantId(7L, TENANT)).thenReturn(Optional.of(closed));
        assertThrows(IllegalStateException.class, () -> service.recordAction(7L, "SUSPENSION", "HIGH", "x", null));
    }

    @Test
    void closeCase_requiresDecision() {
        when(caseRepository.findByIdAndTenantId(7L, TENANT)).thenReturn(Optional.of(openCase()));
        assertThrows(IllegalArgumentException.class, () -> service.closeCase(7L, "  ", "rec"));
    }
}
