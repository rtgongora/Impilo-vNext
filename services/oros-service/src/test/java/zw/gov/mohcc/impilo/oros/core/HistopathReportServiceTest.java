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
import zw.gov.mohcc.impilo.oros.api.dto.HistopathReportRequest;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.HistopathReportEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.HistopathReportRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HistopathReportService} — draft capture, and the pathologist sign-out gate
 * that routes a FINAL through {@link ReportService} (result + Butano + HL7 for free) and flags the
 * linked result critical when required.
 */
@ExtendWith(MockitoExtension.class)
class HistopathReportServiceTest {

    @Mock private HistopathReportRepository repository;
    @Mock private ReportService reportService;
    @Mock private EventOutboxRepository outboxRepository;

    private HistopathReportService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper om = OrosTestObjectMapper.create();
        service = new HistopathReportService(repository, reportService, outboxRepository, om);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "pathologist-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private void stubSaveReturnsWithId() {
        when(repository.save(any(HistopathReportEntity.class))).thenAnswer(i -> {
            HistopathReportEntity r = i.getArgument(0);
            if (r.getReportId() == null) r.setReportId(UUID.randomUUID());
            return r;
        });
    }

    private ResultEntity finalResult() {
        ResultEntity r = new ResultEntity();
        r.setResultId(UUID.randomUUID());
        r.setOrderId(ORDER_ID);
        return r;
    }

    @Test
    @DisplayName("draft then get returns the captured pathology sections")
    void draftThenGet() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(repository.findFirstByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Optional.empty());
            stubSaveReturnsWithId();

            HistopathReportRequest body = new HistopathReportRequest(
                    "SP-1", "gross text", "microscopic text", "adenocarcinoma",
                    Map.of("ihc", "ER+"), null, null, null, null, null, null);
            HistopathReportEntity drafted = service.draft(ORDER_ID, body);

            assertThat(drafted.getSignOutStatus()).isEqualTo("DRAFT");
            assertThat(drafted.getTenantId()).isEqualTo(TENANT_ID);

            // get() reads the latest report back as a flat map.
            when(repository.findFirstByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Optional.of(drafted));
            Map<String, Object> read = service.get(ORDER_ID);

            assertThat(read).isNotNull();
            assertThat(read.get("grossDescription")).isEqualTo("gross text");
            assertThat(read.get("microscopicDescription")).isEqualTo("microscopic text");
            assertThat(read.get("diagnosis")).isEqualTo("adenocarcinoma");
            assertThat(read.get("specimenRef")).isEqualTo("SP-1");
            assertThat(read.get("signOutStatus")).isEqualTo("DRAFT");
        }
    }

    @Test
    @DisplayName("signOut sets FINAL + pathologist and produces the canonical FINAL via ReportService")
    void signOutProducesFinal() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            HistopathReportEntity draft = new HistopathReportEntity();
            draft.setReportId(UUID.randomUUID());
            draft.setOrderId(ORDER_ID);
            draft.setTenantId(TENANT_ID);
            draft.setSignOutStatus("DRAFT");
            draft.setDiagnosis("adenocarcinoma");
            when(repository.findFirstByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Optional.of(draft));
            stubSaveReturnsWithId();

            ResultEntity result = finalResult();
            when(reportService.createFinal(eq(ORDER_ID), anyString(), eq("adenocarcinoma"), any(), any(), any()))
                    .thenReturn(result);

            HistopathReportRequest body = new HistopathReportRequest(
                    null, null, null, null, null, null, "clinical correlation", null, "Z-HISTO", null, null);
            HistopathReportEntity signed = service.signOut(ORDER_ID, body);

            assertThat(signed.getSignOutStatus()).isEqualTo("FINAL");
            assertThat(signed.getSignedOutAt()).isNotNull();
            assertThat(signed.getReportingPathologist()).isEqualTo("pathologist-1");
            assertThat(signed.getResultId()).isEqualTo(result.getResultId());
            assertThat(signed.getButanoReportRef()).contains(result.getResultId().toString());

            // Diagnosis is passed as the impression; recommendations/zibo flow through.
            verify(reportService).createFinal(eq(ORDER_ID), anyString(), eq("adenocarcinoma"),
                    eq("clinical correlation"), any(), eq("Z-HISTO"));
            verify(reportService, never()).flagCritical(any(), any());

            ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(ev.capture());
            assertThat(ev.getValue().getEventType()).isEqualTo("oros.histopath.signed_out");
            assertThat(ev.getValue().getTenantId()).isEqualTo(TENANT_ID);
        }
    }

    @Test
    @DisplayName("critical signOut flags the linked result critical and emits the critical event")
    void signOutCriticalFlagsResult() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            HistopathReportEntity draft = new HistopathReportEntity();
            draft.setReportId(UUID.randomUUID());
            draft.setOrderId(ORDER_ID);
            draft.setTenantId(TENANT_ID);
            draft.setSignOutStatus("DRAFT");
            draft.setDiagnosis("high-grade malignancy");
            when(repository.findFirstByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Optional.of(draft));
            stubSaveReturnsWithId();

            ResultEntity result = finalResult();
            when(reportService.createFinal(eq(ORDER_ID), anyString(), any(), any(), any(), any()))
                    .thenReturn(result);

            HistopathReportRequest body = new HistopathReportRequest(
                    null, null, null, null, null, null, null, null, null, true, "malignant — notify surgeon");
            HistopathReportEntity signed = service.signOut(ORDER_ID, body);

            assertThat(signed.isCritical()).isTrue();
            assertThat(signed.getCriticalReason()).isEqualTo("malignant — notify surgeon");
            verify(reportService).flagCritical(eq(result.getResultId()), eq("malignant — notify surgeon"));

            // Both signed_out and critical events are emitted.
            verify(outboxRepository, times(2)).save(any(EventOutboxEntity.class));
        }
    }
}
