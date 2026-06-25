package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReportService} — the versioned reporting lifecycle (never overwrite a
 * final), critical flagging, release, acknowledgement, and best-effort imaging-state sync.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ResultRepository resultRepository;
    @Mock private OrderStateMachine stateMachine;
    @Mock private ImagingWorkflowService imagingWorkflowService;
    @Mock private EventOutboxRepository outboxRepository;

    private ReportService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper om = OrosTestObjectMapper.create();
        service = new ReportService(resultRepository, stateMachine, imagingWorkflowService, outboxRepository, om);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "radiologist-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private OrderEntity order(OrderType type, ImagingWorkflowState imagingState) {
        OrderEntity o = new OrderEntity();
        o.setOrderId(ORDER_ID);
        o.setTenantId(TENANT_ID);
        o.setOrderType(type);
        o.setImagingState(imagingState);
        return o;
    }

    private void stubSaveReturnsWithId() {
        when(resultRepository.save(any(ResultEntity.class))).thenAnswer(i -> {
            ResultEntity r = i.getArgument(0);
            if (r.getResultId() == null) r.setResultId(UUID.randomUUID());
            return r;
        });
    }

    @Test
    @DisplayName("createPreliminary sets PRELIMINARY status at version 1")
    void preliminary() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.LAB, null));
            when(resultRepository.findFirstByOrderIdOrderByVersionDesc(ORDER_ID)).thenReturn(Optional.empty());
            stubSaveReturnsWithId();

            ResultEntity r = service.createPreliminary(ORDER_ID, "{\"k\":1}", "impression", "rec", null, "Z1");

            assertThat(r.getReportStatus()).isEqualTo(ResultStatus.PRELIMINARY);
            assertThat(r.getVersion()).isEqualTo(1);
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("createFinal records the validating provider")
    void finalReport() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.LAB, null));
            when(resultRepository.findFirstByOrderIdOrderByVersionDesc(ORDER_ID)).thenReturn(Optional.empty());
            stubSaveReturnsWithId();

            ResultEntity r = service.createFinal(ORDER_ID, "{}", "imp", "rec", null, null);

            assertThat(r.getReportStatus()).isEqualTo(ResultStatus.FINAL);
            assertThat(r.getValidatedBy()).isEqualTo("radiologist-1");
        }
    }

    @Test
    @DisplayName("amend creates a new AMENDED version superseding the final, never overwriting it")
    void amendCreatesNewVersion() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.LAB, null));

            ResultEntity head = new ResultEntity();
            head.setResultId(UUID.randomUUID());
            head.setOrderId(ORDER_ID);
            head.setReportStatus(ResultStatus.FINAL);
            head.setVersion(1);
            head.setSummary("{\"orig\":true}");
            when(resultRepository.findFirstByOrderIdAndReportStatusOrderByVersionDesc(ORDER_ID, ResultStatus.FINAL))
                    .thenReturn(Optional.of(head));
            stubSaveReturnsWithId();

            ResultEntity amended = service.amend(ORDER_ID, "typo fix", "{\"orig\":false}", "new imp", null);

            assertThat(amended.getReportStatus()).isEqualTo(ResultStatus.AMENDED);
            assertThat(amended.getVersion()).isEqualTo(2);
            assertThat(amended.getSupersedesResultId()).isEqualTo(head.getResultId());
            assertThat(amended.getCriticalReason()).isEqualTo("typo fix");
            // The original final is never mutated/overwritten.
            assertThat(head.getReportStatus()).isEqualTo(ResultStatus.FINAL);
            assertThat(head.getVersion()).isEqualTo(1);
            ArgumentCaptor<ResultEntity> captor = ArgumentCaptor.forClass(ResultEntity.class);
            verify(resultRepository).save(captor.capture());
            assertThat(captor.getValue()).isNotSameAs(head);
        }
    }

    @Test
    @DisplayName("amend without any prior report is rejected")
    void amendWithoutFinalThrows() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.LAB, null));
            when(resultRepository.findFirstByOrderIdAndReportStatusOrderByVersionDesc(ORDER_ID, ResultStatus.FINAL))
                    .thenReturn(Optional.empty());
            when(resultRepository.findFirstByOrderIdOrderByVersionDesc(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.amend(ORDER_ID, "x", null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No finalized report");
        }
    }

    @Test
    @DisplayName("flagCritical sets the critical flag and reason and emits RESULT_CRITICAL")
    void flagCritical() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            ResultEntity r = new ResultEntity();
            r.setResultId(UUID.randomUUID());
            r.setOrderId(ORDER_ID);
            when(resultRepository.findById(r.getResultId())).thenReturn(Optional.of(r));
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.LAB, null));
            stubSaveReturnsWithId();

            ResultEntity flagged = service.flagCritical(r.getResultId(), "K+ 7.1");

            assertThat(flagged.isCritical()).isTrue();
            assertThat(flagged.getCriticalReason()).isEqualTo("K+ 7.1");
            ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(ev.capture());
            assertThat(ev.getValue().getEventType()).isEqualTo("RESULT_CRITICAL");
        }
    }

    @Test
    @DisplayName("acknowledge stamps the acknowledger and chooses the critical-ack event type")
    void acknowledgeCritical() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            ResultEntity r = new ResultEntity();
            r.setResultId(UUID.randomUUID());
            r.setOrderId(ORDER_ID);
            r.setCritical(true);
            when(resultRepository.findById(r.getResultId())).thenReturn(Optional.of(r));
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.LAB, null));
            stubSaveReturnsWithId();

            ResultEntity acked = service.acknowledge(r.getResultId(), "seen");

            assertThat(acked.getAcknowledgedAt()).isNotNull();
            assertThat(acked.getAcknowledgedBy()).isEqualTo("radiologist-1");
            ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(ev.capture());
            assertThat(ev.getValue().getEventType()).isEqualTo("RESULT_CRITICAL_ACKNOWLEDGED");
        }
    }

    @Test
    @DisplayName("versions returns the full chain newest-first")
    void versionsReturnsChain() {
        ResultEntity v2 = new ResultEntity(); v2.setVersion(2); v2.setOrderId(ORDER_ID);
        ResultEntity v1 = new ResultEntity(); v1.setVersion(1); v1.setOrderId(ORDER_ID);
        when(resultRepository.findByOrderIdOrderByVersionDescCreatedAtDesc(ORDER_ID))
                .thenReturn(java.util.List.of(v2, v1));

        assertThat(service.versions(ORDER_ID)).containsExactly(v2, v1);
    }

    @Test
    @DisplayName("imaging final report best-effort syncs imaging state when the guard permits")
    void imagingSyncOnFinal() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order(OrderType.IMAGING, ImagingWorkflowState.IMAGES_LINKED));
            when(resultRepository.findFirstByOrderIdOrderByVersionDesc(ORDER_ID)).thenReturn(Optional.empty());
            stubSaveReturnsWithId();

            service.createFinal(ORDER_ID, "{}", null, null, null, null);

            verify(imagingWorkflowService).transition(eq(ORDER_ID), eq(ImagingWorkflowState.FINAL_REPORT), any());
        }
    }
}
