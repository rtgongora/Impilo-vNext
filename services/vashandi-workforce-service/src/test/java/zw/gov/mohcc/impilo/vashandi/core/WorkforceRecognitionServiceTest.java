package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Workforce recognition rules: active profile + current assignment required;
 * every negative (unknown / offboarded / no assignment / expired assignment)
 * yields the IDENTICAL generic body (anti-enumeration).
 */
@ExtendWith(MockitoExtension.class)
class WorkforceRecognitionServiceTest {

    @Mock private WorkforceProfileRepository profileRepository;
    @Mock private WorkforceAssignmentRepository assignmentRepository;

    private WorkforceRecognitionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();
    private WorkforceProfileEntity profile;

    @BeforeEach
    void setUp() {
        service = new WorkforceRecognitionService(profileRepository, assignmentRepository);
        profile = new WorkforceProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setTenantId(tenantId);
        profile.setHealthId(healthId);
        profile.setCurrentStatus("active");
        profile.setCadre("REGISTERED_GENERAL_NURSE");
        profile.setProfession("Nursing");
    }

    private WorkforceAssignmentEntity assignment(String status, LocalDate endDate) {
        WorkforceAssignmentEntity a = new WorkforceAssignmentEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setWorkforceProfileId(profile.getId());
        a.setAssignmentType("appointment");
        a.setStatus(status);
        a.setEndDate(endDate);
        return a;
    }

    @Test
    void activeProfileWithCurrentAssignmentIsRecognised() {
        when(profileRepository.findByTenantIdAndHealthId(tenantId, healthId))
                .thenReturn(Optional.of(profile));
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                eq(tenantId), eq(profile.getId()), anyList()))
                .thenReturn(List.of(assignment("active", null)));

        var r = service.recognitionByHealthId(tenantId, healthId);

        assertThat(r.recognised()).isTrue();
        assertThat(r.cadre()).isEqualTo("REGISTERED_GENERAL_NURSE");
        assertThat(r.profession()).isEqualTo("Nursing");
    }

    @Test
    void unknownHealthIdYieldsGenericBody() {
        when(profileRepository.findByTenantIdAndHealthId(eq(tenantId), any()))
                .thenReturn(Optional.empty());

        assertThat(service.recognitionByHealthId(tenantId, "no-such-person"))
                .isEqualTo(WorkforceRecognitionService.RecognitionResult.notRecognised());
    }

    @Test
    void offboardedProfileIsNotRecognised_andMatchesUnknownBody() {
        profile.setCurrentStatus("offboarded");
        when(profileRepository.findByTenantIdAndHealthId(tenantId, healthId))
                .thenReturn(Optional.of(profile));
        lenient().when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                eq(tenantId), eq(profile.getId()), anyList()))
                .thenReturn(List.of(assignment("active", null)));

        var offboarded = service.recognitionByHealthId(tenantId, healthId);

        assertThat(offboarded.recognised()).isFalse();
        assertThat(offboarded).isEqualTo(WorkforceRecognitionService.RecognitionResult.notRecognised());
    }

    @Test
    void profileWithoutCurrentAssignmentIsNotRecognised() {
        when(profileRepository.findByTenantIdAndHealthId(tenantId, healthId))
                .thenReturn(Optional.of(profile));
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                eq(tenantId), eq(profile.getId()), anyList()))
                .thenReturn(List.of());

        assertThat(service.recognitionByHealthId(tenantId, healthId))
                .isEqualTo(WorkforceRecognitionService.RecognitionResult.notRecognised());
    }

    @Test
    void endedAssignmentDoesNotCountAsCurrent() {
        when(profileRepository.findByTenantIdAndHealthId(tenantId, healthId))
                .thenReturn(Optional.of(profile));
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                eq(tenantId), eq(profile.getId()), anyList()))
                .thenReturn(List.of(assignment("active", LocalDate.now().minusDays(1))));

        assertThat(service.recognitionByHealthId(tenantId, healthId))
                .isEqualTo(WorkforceRecognitionService.RecognitionResult.notRecognised());
    }

    @Test
    void blankHealthIdYieldsGenericBody() {
        assertThat(service.recognitionByHealthId(tenantId, "  "))
                .isEqualTo(WorkforceRecognitionService.RecognitionResult.notRecognised());
    }
}
