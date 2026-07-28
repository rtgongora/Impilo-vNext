package zw.gov.mohcc.impilo.surgery.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalAssessmentEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalAssessmentRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * S2 — the general surgical assessment. Proves: new-content fields apply cleanly, a
 * reference-without-review is refused for every referenced registry (functional status,
 * pregnancy, imaging, pathology — the same CHECK V003 enforces at the schema layer), and the
 * assessment is refined in place (one row per episode) rather than versioned.
 */
@ExtendWith(MockitoExtension.class)
class SurgicalAssessmentServiceTest {

    @Mock SurgicalAssessmentRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;

    private SurgicalAssessmentService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurgicalAssessmentService(repository, episodeRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(new SurgicalEpisodeEntity()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalAssessmentEntity e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void recordingNewContentFieldsSucceeds() {
        Map<String, Object> result = service.recordAssessment(episodeId, Map.of(
                "presentingProblem", "Right upper quadrant pain, worse after fatty meals",
                "symptomTimeline", "3 months, escalating frequency",
                "patientGoals", "Return to manual work within 4 weeks"));

        assertEquals("Right upper quadrant pain, worse after fatty meals", result.get("presentingProblem"));
        assertEquals("3 months, escalating frequency", result.get("symptomTimeline"));
        assertEquals("Return to manual work within 4 weeks", result.get("patientGoals"));
    }

    @Test
    void recordingForAnUnknownEpisodeIsNotFound() {
        when(episodeRepository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordAssessment(episodeId, Map.of("presentingProblem", "x")));
        assertEquals("SURGICAL_EPISODE_NOT_FOUND", ex.getCode());
    }

    @Test
    void reviewedFlagWithNotesButNoReferenceIsFine() {
        Map<String, Object> result = service.recordAssessment(episodeId, Map.of(
                "allergiesReviewed", true, "allergyReviewNotes", "No known drug allergies per pct_allergies"));

        assertEquals(true, result.get("allergiesReviewed"));
        assertEquals("No known drug allergies per pct_allergies", result.get("allergyReviewNotes"));
    }

    /** The application-layer mirror of V003's CHECK: a reference cannot exist without review. */
    @Test
    void functionalAssessmentRefWithoutReviewedIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordAssessment(episodeId, Map.of(
                        "functionalAssessmentRef", UUID.randomUUID().toString())));
        assertEquals("REFERENCE_REVIEW_REQUIRED", ex.getCode());
    }

    @Test
    void functionalAssessmentRefWithReviewedTrueIsAccepted() {
        UUID ref = UUID.randomUUID();
        Map<String, Object> result = service.recordAssessment(episodeId, Map.of(
                "functionalStatusReviewed", true, "functionalAssessmentRef", ref.toString()));

        assertEquals(true, result.get("functionalStatusReviewed"));
        assertEquals(ref.toString(), result.get("functionalAssessmentRef"));
    }

    @Test
    void pregnancyEpisodeRefWithoutConfirmedIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordAssessment(episodeId, Map.of(
                        "pregnancyEpisodeRef", UUID.randomUUID().toString())));
        assertEquals("REFERENCE_REVIEW_REQUIRED", ex.getCode());
    }

    @Test
    void imagingRefWithoutReviewedIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordAssessment(episodeId, Map.of("imagingRef", "pacs-study-1")));
        assertEquals("IMAGING_REVIEW_REQUIRED", ex.getCode());
    }

    @Test
    void pathologyRefWithoutReviewedIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordAssessment(episodeId, Map.of("pathologyRef", "oros-histo-1")));
        assertEquals("PATHOLOGY_REVIEW_REQUIRED", ex.getCode());
    }

    @Test
    void imagingRefWithReviewedTrueIsAccepted() {
        Map<String, Object> result = service.recordAssessment(episodeId, Map.of(
                "imagingReviewed", true, "imagingRef", "pacs-study-1"));

        assertEquals(true, result.get("imagingReviewed"));
        assertEquals("pacs-study-1", result.get("imagingRef"));
    }

    /** Refinement, not versioning: a second call on the same episode updates the one row. */
    @Test
    void assessingTheSameEpisodeTwiceRefinesTheSameRowRatherThanCreatingASecondOne() {
        SurgicalAssessmentEntity existing = new SurgicalAssessmentEntity();
        existing.setId(UUID.randomUUID());
        existing.setSurgicalEpisodeId(episodeId);
        existing.setTenantId(tenant);
        existing.setPresentingProblem("Initial complaint");
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(existing));

        Map<String, Object> result = service.recordAssessment(episodeId,
                Map.of("symptomTimeline", "Now 3 months and worsening"));

        assertEquals("Initial complaint", result.get("presentingProblem"), "prior content is preserved");
        assertEquals("Now 3 months and worsening", result.get("symptomTimeline"));
    }

    @Test
    void getAssessmentForAnUnassessedEpisodeIsNotFound() {
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.getAssessment(episodeId));
        assertEquals("SURGICAL_ASSESSMENT_NOT_FOUND", ex.getCode());
    }
}
