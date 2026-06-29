package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.ExternalSystem;
import zw.gov.mohcc.impilo.inventory.domain.SyncEntityType;
import zw.gov.mohcc.impilo.inventory.domain.SyncStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncErrorEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncStateEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ExternalSyncErrorRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ExternalSyncStateRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalSyncServiceTest {

    @Mock private ExternalSyncStateRepository stateRepository;
    @Mock private ExternalSyncErrorRepository errorRepository;
    @InjectMocks private ExternalSyncServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "sync-agent", "SYSTEM", "INTEGRATION", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ExternalSyncStateEntity state(int attempts, SyncStatus status) {
        ExternalSyncStateEntity s = new ExternalSyncStateEntity();
        s.setSyncId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setExternalSystem(ExternalSystem.ELMIS);
        s.setEntityType(SyncEntityType.CONSUMPTION);
        s.setEntityRef("led-1");
        s.setAttempts(attempts);
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("enqueue creates a PENDING record")
    void enqueue_pending() {
        when(stateRepository.save(any(ExternalSyncStateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ExternalSyncStateEntity s = service.enqueue(ExternalSystem.NATPHARM, SyncEntityType.ORDER, "ord-9", "{}");
        assertThat(s.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(s.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("markFailed sets RETRY before the cap and logs an error")
    void markFailed_retry() {
        ExternalSyncStateEntity s = state(0, SyncStatus.PENDING);
        when(stateRepository.findById(s.getSyncId())).thenReturn(Optional.of(s));
        when(stateRepository.save(any(ExternalSyncStateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(errorRepository.save(any(ExternalSyncErrorEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ExternalSyncStateEntity result = service.markFailed(s.getSyncId(), "timeout");

        assertThat(result.getStatus()).isEqualTo(SyncStatus.RETRY);
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getLastError()).isEqualTo("timeout");
        verify(errorRepository).save(any(ExternalSyncErrorEntity.class));
    }

    @Test
    @DisplayName("markFailed sets FAILED once the retry cap is reached")
    void markFailed_failedAtCap() {
        // attempts already at MAX-1; this failure becomes attempt MAX
        ExternalSyncStateEntity s = state(ExternalSyncService.MAX_AUTO_RETRIES - 1, SyncStatus.RETRY);
        when(stateRepository.findById(s.getSyncId())).thenReturn(Optional.of(s));
        when(stateRepository.save(any(ExternalSyncStateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(errorRepository.save(any(ExternalSyncErrorEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ExternalSyncStateEntity result = service.markFailed(s.getSyncId(), "still failing");

        assertThat(result.getAttempts()).isEqualTo(ExternalSyncService.MAX_AUTO_RETRIES);
        assertThat(result.getStatus()).isEqualTo(SyncStatus.FAILED);
    }

    @Test
    @DisplayName("markSynced marks SYNCED and never fails afterwards")
    void markSynced_thenFailRejected() {
        ExternalSyncStateEntity s = state(1, SyncStatus.RETRY);
        when(stateRepository.findById(s.getSyncId())).thenReturn(Optional.of(s));
        when(stateRepository.save(any(ExternalSyncStateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ExternalSyncStateEntity synced = service.markSynced(s.getSyncId(), "ELMIS-REF-1");
        assertThat(synced.getStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(synced.getExternalRef()).isEqualTo("ELMIS-REF-1");
        assertThat(synced.getSyncedAt()).isNotNull();

        assertThatThrownBy(() -> service.markFailed(s.getSyncId(), "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already-synced");
    }

    @Test
    @DisplayName("retry replays a FAILED record back to PENDING")
    void retry_replays() {
        ExternalSyncStateEntity s = state(ExternalSyncService.MAX_AUTO_RETRIES, SyncStatus.FAILED);
        when(stateRepository.findById(s.getSyncId())).thenReturn(Optional.of(s));
        when(stateRepository.save(any(ExternalSyncStateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ExternalSyncStateEntity result = service.retry(s.getSyncId());
        assertThat(result.getStatus()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    @DisplayName("retry rejects an already-synced record")
    void retry_syncedRejected() {
        ExternalSyncStateEntity s = state(1, SyncStatus.SYNCED);
        when(stateRepository.findById(s.getSyncId())).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.retry(s.getSyncId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already-synced");
    }
}
