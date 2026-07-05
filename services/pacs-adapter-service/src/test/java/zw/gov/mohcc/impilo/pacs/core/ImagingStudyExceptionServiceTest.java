package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyExceptionEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyExceptionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagingStudyExceptionServiceTest {

    @Mock private ImagingStudyExceptionRepository exceptionRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ImagingStudyExceptionService service;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ImagingStudyExceptionService(exceptionRepository, outboxRepository, new ObjectMapper());
    }

    private ImagingStudyEntity study(String orderId, String accession, String sourceType) {
        ImagingStudyEntity s = new ImagingStudyEntity();
        s.setId(7L);
        s.setTenantId(TENANT);
        s.setPatientCpid("CPID-1");
        s.setStudyUid("1.2.3.4");
        s.setOrosOrderId(orderId);
        s.setAccessionNumber(accession);
        s.setSourceType(sourceType);
        return s;
    }

    @Test
    @DisplayName("registered study without order and accession raises both exceptions")
    void raisesOrderAndAccessionExceptions() {
        when(exceptionRepository.existsByStudyIdAndExceptionTypeAndStatus(anyLong(), anyString(), anyString()))
                .thenReturn(false);
        when(exceptionRepository.save(any(ImagingStudyExceptionEntity.class))).thenAnswer(i -> {
            ImagingStudyExceptionEntity e = i.getArgument(0);
            e.setId(99L);
            return e;
        });

        service.raiseForRegisteredStudy(study(null, null, "DICOM"));

        ArgumentCaptor<ImagingStudyExceptionEntity> captor =
                ArgumentCaptor.forClass(ImagingStudyExceptionEntity.class);
        verify(exceptionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ImagingStudyExceptionEntity::getExceptionType)
                .containsExactlyInAnyOrder(
                        ImagingStudyExceptionService.TYPE_STUDY_WITHOUT_ORDER,
                        ImagingStudyExceptionService.TYPE_MISSING_ACCESSION);
        verify(outboxRepository, times(2)).save(any(EventOutboxEntity.class));
    }

    @Test
    @DisplayName("manual import always raises a MANUAL_IMPORT_REVIEW exception even when correlated")
    void manualImportRaisesReview() {
        when(exceptionRepository.existsByStudyIdAndExceptionTypeAndStatus(anyLong(), anyString(), anyString()))
                .thenReturn(false);
        when(exceptionRepository.save(any(ImagingStudyExceptionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.raiseForRegisteredStudy(study("ORD-1", "ACC-1", "MANUAL_IMPORT"));

        ArgumentCaptor<ImagingStudyExceptionEntity> captor =
                ArgumentCaptor.forClass(ImagingStudyExceptionEntity.class);
        verify(exceptionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getExceptionType())
                .isEqualTo(ImagingStudyExceptionService.TYPE_MANUAL_IMPORT_REVIEW);
    }

    @Test
    @DisplayName("network DICOM study with order and accession raises nothing")
    void cleanDicomStudyRaisesNothing() {
        service.raiseForRegisteredStudy(study("ORD-1", "ACC-1", "DICOM"));
        verify(exceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("duplicate OPEN exception of same type is not raised twice")
    void duplicateOpenExceptionSkipped() {
        when(exceptionRepository.existsByStudyIdAndExceptionTypeAndStatus(
                7L, ImagingStudyExceptionService.TYPE_STUDY_WITHOUT_ORDER,
                ImagingStudyExceptionEntity.STATUS_OPEN)).thenReturn(true);

        service.raise(study(null, "ACC-1", "DICOM"),
                ImagingStudyExceptionService.TYPE_STUDY_WITHOUT_ORDER, "detail");

        verify(exceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolve records actor, action, note, timestamp and emits event")
    void resolveIsAuditable() {
        ImagingStudyExceptionEntity open = new ImagingStudyExceptionEntity();
        open.setId(5L);
        open.setTenantId(TENANT);
        open.setStudyId(7L);
        open.setExceptionType(ImagingStudyExceptionService.TYPE_MANUAL_IMPORT_REVIEW);
        open.setStatus(ImagingStudyExceptionEntity.STATUS_OPEN);
        when(exceptionRepository.findById(5L)).thenReturn(Optional.of(open));
        when(exceptionRepository.save(any(ImagingStudyExceptionEntity.class))).thenAnswer(i -> i.getArgument(0));

        ImagingStudyExceptionEntity resolved =
                service.resolve(5L, "patient_confirmed", "verified against VITO", false, "radiographer-1");

        assertThat(resolved.getStatus()).isEqualTo(ImagingStudyExceptionEntity.STATUS_RESOLVED);
        assertThat(resolved.getResolutionAction()).isEqualTo("PATIENT_CONFIRMED");
        assertThat(resolved.getResolvedBy()).isEqualTo("radiographer-1");
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getResolutionNote()).isEqualTo("verified against VITO");
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    @DisplayName("resolving a non-open exception is rejected")
    void resolveNonOpenRejected() {
        ImagingStudyExceptionEntity done = new ImagingStudyExceptionEntity();
        done.setId(6L);
        done.setTenantId(TENANT);
        done.setStatus(ImagingStudyExceptionEntity.STATUS_RESOLVED);
        when(exceptionRepository.findById(6L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.resolve(6L, "X", null, false, "a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("correlation auto-resolves open STUDY_WITHOUT_ORDER exceptions only")
    void correlationAutoResolvesOrderExceptions() {
        ImagingStudyExceptionEntity orderEx = new ImagingStudyExceptionEntity();
        orderEx.setId(1L);
        orderEx.setTenantId(TENANT);
        orderEx.setStudyId(7L);
        orderEx.setExceptionType(ImagingStudyExceptionService.TYPE_STUDY_WITHOUT_ORDER);
        orderEx.setStatus(ImagingStudyExceptionEntity.STATUS_OPEN);

        ImagingStudyExceptionEntity manualEx = new ImagingStudyExceptionEntity();
        manualEx.setId(2L);
        manualEx.setTenantId(TENANT);
        manualEx.setStudyId(7L);
        manualEx.setExceptionType(ImagingStudyExceptionService.TYPE_MANUAL_IMPORT_REVIEW);
        manualEx.setStatus(ImagingStudyExceptionEntity.STATUS_OPEN);

        when(exceptionRepository.findByStudyIdOrderByRaisedAtDesc(7L))
                .thenReturn(List.of(orderEx, manualEx));
        when(exceptionRepository.save(any(ImagingStudyExceptionEntity.class))).thenAnswer(i -> i.getArgument(0));

        ImagingStudyEntity correlated = study("ORD-9", "ACC-1", "DICOM");
        service.resolveOrderExceptionsOnCorrelation(correlated, "clerk-1");

        assertThat(orderEx.getStatus()).isEqualTo(ImagingStudyExceptionEntity.STATUS_RESOLVED);
        assertThat(orderEx.getResolutionAction()).isEqualTo("CORRELATED_TO_ORDER");
        // manual-import review must remain open — correlation does not bypass human confirmation
        assertThat(manualEx.getStatus()).isEqualTo(ImagingStudyExceptionEntity.STATUS_OPEN);
    }
}
