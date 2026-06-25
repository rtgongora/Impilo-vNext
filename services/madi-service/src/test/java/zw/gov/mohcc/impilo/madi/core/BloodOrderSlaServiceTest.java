package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderSlaEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodOrderSlaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodOrderSlaServiceTest {

    @Mock private BloodOrderSlaRepository repository;
    @Mock private MadiEventEmitter eventEmitter;

    private BloodOrderSlaService service;
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BloodOrderSlaService(repository, eventEmitter, 120, 1440);
    }

    @Test
    void start_createsAnOpenTimerWithTarget() {
        when(repository.findFirstByOrderIdAndStageAndStatus(ORDER, "CROSSMATCH", "OPEN"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        BloodOrderSlaEntity sla = service.start(TENANT, ORDER, BloodOrderSlaService.STAGE_CROSSMATCH);

        assertThat(sla.getStatus()).isEqualTo("OPEN");
        assertThat(sla.getTargetAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void complete_marksOpenTimerMet() {
        BloodOrderSlaEntity open = new BloodOrderSlaEntity();
        open.setStatus("OPEN");
        open.setStage("CROSSMATCH");
        when(repository.findFirstByOrderIdAndStageAndStatus(ORDER, "CROSSMATCH", "OPEN"))
                .thenReturn(Optional.of(open));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.complete(ORDER, BloodOrderSlaService.STAGE_CROSSMATCH);

        assertThat(open.getStatus()).isEqualTo("MET");
        assertThat(open.getCompletedAt()).isNotNull();
    }

    @Test
    void scanForBreaches_marksOverdueAndEmitsEvent() {
        BloodOrderSlaEntity overdue = new BloodOrderSlaEntity();
        overdue.setTenantId(TENANT);
        overdue.setOrderId(ORDER);
        overdue.setStage("ISSUE");
        overdue.setStatus("OPEN");
        overdue.setTargetAt(OffsetDateTime.now().minusMinutes(5));
        when(repository.findByStatusAndTargetAtBefore(eq("OPEN"), any())).thenReturn(List.of(overdue));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        int breached = service.scanForBreaches();

        assertThat(breached).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo("BREACHED");
        verify(eventEmitter).emit(eq("BLOOD_ORDER"), eq(ORDER.toString()), eq("SLA_BREACHED"),
                eq("BLOOD_ORDER"), eq(ORDER.toString()), anyMap(), eq(TENANT));
    }
}
