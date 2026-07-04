package zw.gov.mohcc.impilo.pacs.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.pacs.api.dto.AnnotationRequest;
import zw.gov.mohcc.impilo.pacs.integration.PacsBackendClient;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingAnnotationEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAccessAuditRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAnnotationRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingInstanceRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingOrderLinkRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingReportLinkRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingSeriesRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingViewerSessionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagingStudyServiceAnnotationTest {

    @Mock private ImagingStudyRepository studyRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private PacsBackendClient pacsBackendClient;
    @Mock private ObjectProvider<zw.gov.mohcc.impilo.pacs.events.PacsOutboxPublisher> outboxPublisherProvider;
    @Mock private ImagingHierarchySyncService hierarchySyncService;
    @Mock private ImagingSeriesRepository seriesRepository;
    @Mock private ImagingInstanceRepository instanceRepository;
    @Mock private ImagingViewerSessionRepository viewerSessionRepository;
    @Mock private ImagingAccessAuditRepository accessAuditRepository;
    @Mock private ImagingReportLinkRepository reportLinkRepository;
    @Mock private ImagingOrderLinkRepository orderLinkRepository;
    @Mock private ImagingAnnotationRepository annotationRepository;
    @Mock private ImagingStudyExceptionService exceptionService;

    private ImagingStudyService service;

    @BeforeEach
    void setUp() {
        service = new ImagingStudyService(
                studyRepository,
                outboxRepository,
                new ObjectMapper(),
                pacsBackendClient,
                new ViewerEnginePolicy("DICOMWEB_STACK", "DICOMWEB_STACK,OHIF,CORNERSTONE"),
                outboxPublisherProvider,
                false,
                hierarchySyncService,
                seriesRepository,
                instanceRepository,
                viewerSessionRepository,
                accessAuditRepository,
                reportLinkRepository,
                orderLinkRepository,
                annotationRepository,
                exceptionService);
    }

    @Test
    void createAnnotation_persistsAndEmitsOutbox() {
        ImagingStudyEntity study = new ImagingStudyEntity();
        study.setId(42L);
        study.setTenantId(UUID.randomUUID());
        study.setPatientCpid("CPID-001");
        study.setFacilityId("fac-1");
        when(studyRepository.findById(42L)).thenReturn(Optional.of(study));

        AnnotationRequest request = new AnnotationRequest();
        request.setAnnotationType("REVIEW_NOTE");
        request.setNote("Suspicious opacity in RUL");
        request.setMeasurement(Map.of("unit", "mm", "value", 12.5));

        when(annotationRepository.save(any(ImagingAnnotationEntity.class))).thenAnswer(invocation -> {
            ImagingAnnotationEntity saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        ImagingAnnotationEntity result = service.createAnnotation(42L, request, "actor-1", "PROVIDER");

        assertEquals("Suspicious opacity in RUL", result.getNote());
        assertEquals("REVIEW_NOTE", result.getAnnotationType());
        verify(accessAuditRepository).save(any());
        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals(ImagingStudyService.EVENT_ANNOTATION_CREATED, outboxCaptor.getValue().getEventType());
    }

    @Test
    void listAnnotations_returnsStudyAnnotations() {
        ImagingStudyEntity study = new ImagingStudyEntity();
        study.setId(42L);
        when(studyRepository.findById(42L)).thenReturn(Optional.of(study));
        ImagingAnnotationEntity annotation = new ImagingAnnotationEntity();
        annotation.setId(7L);
        annotation.setNote("Follow-up recommended");
        when(annotationRepository.findByStudyIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(annotation));

        List<ImagingAnnotationEntity> results = service.listAnnotations(42L);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Follow-up recommended", results.get(0).getNote());
    }
}
