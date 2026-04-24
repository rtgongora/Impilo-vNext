package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ReconcileQueueEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ReconcileQueueRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReconciliationService}.
 *
 * <p>Validates match operation sets confidence to 1.0, resolve
 * operation requires proper auth context, and status constraints.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private ReconcileQueueRepository reconcileRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private ReconciliationService reconciliationService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "ops-user-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        reconciliationService = new ReconciliationService(
                reconcileRepository, outboxRepository, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "OPERATIONS",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private ReconcileQueueEntity createPendingEntry() {
        ReconcileQueueEntity entry = new ReconcileQueueEntity();
        entry.setRecId(UUID.randomUUID());
        entry.setTenantId(TENANT_ID);
        entry.setExternalKey("EXT-KEY-001");
        entry.setExternalSystem("LIMS");
        entry.setPatientCpid("CPID-001");
        entry.setConfidence(BigDecimal.ZERO);
        entry.setStatus(ReconcileStatus.PENDING);
        return entry;
    }

    @Test
    @DisplayName("match sets confidence to 1.0 and status to MATCHED")
    void matchSetsConfidenceAndStatus() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ReconcileQueueEntity entry = createPendingEntry();
            when(reconcileRepository.findById(entry.getRecId())).thenReturn(Optional.of(entry));
            when(reconcileRepository.save(any(ReconcileQueueEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ReconcileQueueEntity matched = reconciliationService.matchReconciliation(
                    entry.getRecId(), "ORD-MATCH-001");

            assertThat(matched.getConfidence()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(matched.getStatus()).isEqualTo(ReconcileStatus.MATCHED);
            assertThat(matched.getOrderId()).isEqualTo("ORD-MATCH-001");
            assertThat(matched.getMatchedAt()).isNotNull();
            assertThat(matched.getResolvedBy()).isEqualTo(ACTOR_ID);

            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("match rejects non-PENDING entries")
    void matchRejectsNonPending() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ReconcileQueueEntity entry = createPendingEntry();
            entry.setStatus(ReconcileStatus.MATCHED);
            when(reconcileRepository.findById(entry.getRecId())).thenReturn(Optional.of(entry));

            assertThatThrownBy(() -> reconciliationService.matchReconciliation(
                    entry.getRecId(), "ORD-001"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot match entry in status MATCHED");
        }
    }

    @Test
    @DisplayName("resolve requires proper auth context and records actor")
    void resolveRequiresAuthContext() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ReconcileQueueEntity entry = createPendingEntry();
            when(reconcileRepository.findById(entry.getRecId())).thenReturn(Optional.of(entry));
            when(reconcileRepository.save(any(ReconcileQueueEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ReconcileQueueEntity resolved = reconciliationService.resolveReconciliation(
                    entry.getRecId(), "Manual resolution: duplicate order");

            assertThat(resolved.getStatus()).isEqualTo(ReconcileStatus.RESOLVED);
            assertThat(resolved.getOpsNotes()).isEqualTo("Manual resolution: duplicate order");
            assertThat(resolved.getResolvedBy()).isEqualTo(ACTOR_ID);

            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("resolve fails when no auth context is available")
    void resolveFailsWithoutAuthContext() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require)
                    .thenThrow(new IllegalStateException("TrustContext not set"));

            UUID recId = UUID.randomUUID();

            assertThatThrownBy(() -> reconciliationService.resolveReconciliation(
                    recId, "notes"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TrustContext not set");
        }
    }

    @Test
    @DisplayName("match for non-existent entry throws IllegalArgumentException")
    void matchNonExistentEntryThrows() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            UUID nonExistentId = UUID.randomUUID();
            when(reconcileRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reconciliationService.matchReconciliation(
                    nonExistentId, "ORD-001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Reconcile entry not found");
        }
    }
}
