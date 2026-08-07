package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderApplicationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderStatusHistoryRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderApplicationFeeWorkflowTest {

    @Mock private ProviderApplicationRepository applicationRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderStatusHistoryRepository statusHistoryRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private CouncilRepository councilRepository;

    private ProviderApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderApplicationService(
                applicationRepository,
                providerRepository,
                statusHistoryRepository,
                outboxRepository,
                councilRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "OPERATOR", "REGISTRY_ADMIN", "device",
                correlationId, facilityId, null, null, AccessMode.INTERNAL));
        lenient().when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void advanceAfterFeePayment_movesAwaitingPaymentToReadyForReview() {
        ProviderApplicationEntity app = new ProviderApplicationEntity();
        app.setId(9L);
        app.setTenantId(tenantId);
        app.setWorkflowState("AWAITING_PAYMENT");
        app.setVersion(2);
        when(applicationRepository.findByIdAndTenantId(9L, tenantId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(ProviderApplicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProviderApplicationEntity updated = service.advanceAfterFeePayment(9L);
        assertEquals("READY_FOR_REVIEW", updated.getWorkflowState());
        assertEquals("PAID", updated.getFeeState());
    }

    @Test
    void advanceToAwaitingPayment_setsUnpaidFeeState() {
        ProviderApplicationEntity app = new ProviderApplicationEntity();
        app.setId(10L);
        app.setTenantId(tenantId);
        app.setWorkflowState("UNDER_ADMIN_REVIEW");
        app.setVersion(1);
        when(applicationRepository.findByIdAndTenantId(10L, tenantId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(ProviderApplicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProviderApplicationEntity updated = service.advanceToAwaitingPayment(10L);
        assertEquals("AWAITING_PAYMENT", updated.getWorkflowState());
        assertEquals("UNPAID", updated.getFeeState());
    }
}
