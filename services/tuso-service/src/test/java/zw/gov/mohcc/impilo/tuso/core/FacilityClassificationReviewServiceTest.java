package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityClassificationDtos.*;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitCandidateRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityClassificationReviewServiceTest {
    // Actor types here were HPA_ADMIN / HPA_REGISTRAR / DEVELOPER / FACILITY_APPLICANT — role names
    // no client emits, so this suite was exercising the service with values no real request can
    // carry, and would have kept passing against a gate no real operator could satisfy. See
    // ActorTypeGuard. OPERATOR is what the admin consoles actually emit.

    @Mock private FacilityRegulatoryProfileRepository profileRepository;
    @Mock private FacilityRegulatoryProfileHistoryRepository historyRepository;
    @Mock private FacilityUnitCandidateRepository candidateRepository;
    @Mock private FacilityCertificateRepository certificateRepository;
    @Mock private FacilityUnitService unitService;
    @Mock private ClassificationRuleEvaluator evaluator;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityClassificationReviewService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private TrustContext ctx(String actorType) {
        return new TrustContext(tenantId, "tester", actorType, "REGULATION", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
    }

    @BeforeEach
    void setUp() {
        lenient().when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(historyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new FacilityClassificationReviewService(profileRepository, historyRepository, candidateRepository,
                certificateRepository, unitService, evaluator, outboxRepository);
    }

    private FacilityRegulatoryProfileEntity profile(String status, String label, String hpaClass) {
        FacilityRegulatoryProfileEntity p = new FacilityRegulatoryProfileEntity();
        p.setProfileId(UUID.randomUUID());
        p.setFacilityId(1L);
        p.setTenantId(tenantId);
        p.setClassificationStatus(status);
        p.setHpaClassificationLabel(label);
        p.setHpaClass(hpaClass);
        return p;
    }

    @Test
    void aNonAdminActorTypeCannotConfirm() {
        assertThatThrownBy(() -> service.confirm(ctx("FACILITY_ADMIN"), 1L, new ConfirmRequest("x")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void bulkConfirmRejectsNonDeterministicStatuses() {
        assertThatThrownBy(() -> service.bulkConfirm(ctx("OPERATOR"),
                new BulkConfirmRequest(List.of("AMBIGUOUS"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not bulk-confirmable");
        verify(profileRepository, never()).findByClassificationStatus(any());
    }

    @Test
    void bulkConfirmConfirmsOnlyLabelledDeterministicRows() {
        when(profileRepository.findByClassificationStatus("DETERMINISTIC_MULTI_FIELD_MATCH"))
                .thenReturn(List.of(profile("DETERMINISTIC_MULTI_FIELD_MATCH", "some label", "C"),
                                    profile("DETERMINISTIC_MULTI_FIELD_MATCH", null, "C")));   // unlabelled → skipped

        BulkConfirmResult r = service.bulkConfirm(ctx("OPERATOR"),
                new BulkConfirmRequest(List.of("DETERMINISTIC_MULTI_FIELD_MATCH")));

        assertThat(r.confirmed()).isEqualTo(1);
    }

    @Test
    void confirmRefusesAProfileWithoutAResolvedLabel() {
        when(profileRepository.findByFacilityId(1L))
                .thenReturn(Optional.of(profile("MISSING_REQUIRED_FACTS", null, "C")));
        assertThatThrownBy(() -> service.confirm(ctx("OPERATOR"), 1L, new ConfirmRequest(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without a resolved classification label");
    }

    @Test
    void confirmSucceedsForALabelledProfile() {
        when(profileRepository.findByFacilityId(1L))
                .thenReturn(Optional.of(profile("DETERMINISTIC_MULTI_FIELD_MATCH", "a label", "C")));
        FacilityRegulatoryProfileEntity p = service.confirm(ctx("OPERATOR"), 1L, new ConfirmRequest("looks right"));
        assertThat(p.getClassificationStatus()).isEqualTo("HUMAN_CONFIRMED");
        assertThat(p.getConfirmedBy()).isEqualTo("tester");
        verify(outboxRepository).save(any());
    }

    @Test
    void correctToNotApplicableRequiresAJustification() {
        when(profileRepository.findByFacilityId(1L))
                .thenReturn(Optional.of(profile("AMBIGUOUS", null, "NOT_YET_DETERMINED")));
        assertThatThrownBy(() -> service.correct(ctx("OPERATOR"), 1L,
                new CorrectRequest("NOT_APPLICABLE", null, null, "note")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justification");
    }

    @Test
    void supplyFactsRequiresAtLeastOneFact() {
        assertThatThrownBy(() -> service.supplyFacts(ctx("OPERATOR"), 1L,
                new FactsRequest(null, null, null, null, "note")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one fact");
    }

    @Test
    void unitCandidateConfirmCreatesARealUnitExactlyOnce() {
        UUID candidateId = UUID.randomUUID();
        var candidate = new zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitCandidateEntity();
        candidate.setCandidateId(candidateId);
        candidate.setFacilityId(1L);
        candidate.setTenantId(tenantId);
        candidate.setSuggestedUnitType("PHARMACY");
        candidate.setStatus("PROPOSED");
        when(candidateRepository.findByCandidateId(candidateId)).thenReturn(Optional.of(candidate));
        var unit = org.mockito.Mockito.mock(zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity.class);
        when(unit.getId()).thenReturn(99L);
        when(unitService.create(any(), anyLong(), any())).thenReturn(unit);
        when(candidateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.confirmCandidate(ctx("OPERATOR"), candidateId, new UnitCandidateConfirmRequest(null, "OUTPATIENT", true));

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getCreatedUnitId()).isEqualTo(99L);
        verify(unitService).create(any(), anyLong(), any());   // exactly one real unit created
    }
}
