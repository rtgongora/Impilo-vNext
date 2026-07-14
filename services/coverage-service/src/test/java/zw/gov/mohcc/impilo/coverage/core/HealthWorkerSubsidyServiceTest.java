package zw.gov.mohcc.impilo.coverage.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentResponse;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.integration.HealthWorkerRecognitionClient;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opt-in requires a POSITIVE server-side recognition check (fail-closed),
 * records the recognition class + HEALTH_WORKER category, and the
 * suspension sweep suspends definite lapses but never on registry outage.
 */
@ExtendWith(MockitoExtension.class)
class HealthWorkerSubsidyServiceTest {

    @Mock private SubsidyProgramRepository programRepository;
    @Mock private SubsidyEnrolmentRepository enrolmentRepository;
    @Mock private SubsidyEnrolmentService enrolmentService;
    @Mock private HealthWorkerRecognitionClient recognitionClient;

    private HealthWorkerSubsidyService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new HealthWorkerSubsidyService(
                programRepository, enrolmentRepository, enrolmentService, recognitionClient);
    }

    @Test
    void optInEnrolsWithRecognitionClassAndHealthWorkerCategory() {
        when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(new HealthWorkerRecognitionClient.Recognition(true, "LICENSED_PROVIDER", true));
        SubsidyEnrolmentResponse expected = new SubsidyEnrolmentResponse(
                UUID.randomUUID(), programId, healthId, "ACTIVE",
                null, null, null, null, "USD", LocalDate.now(), null, "HEALTH_WORKER");
        when(enrolmentService.enrol(eq(tenantId), any())).thenReturn(expected);

        SubsidyEnrolmentResponse actual = service.optIn(tenantId, programId, healthId);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<SubsidyEnrolmentRequest> captor =
                ArgumentCaptor.forClass(SubsidyEnrolmentRequest.class);
        verify(enrolmentService).enrol(eq(tenantId), captor.capture());
        SubsidyEnrolmentRequest req = captor.getValue();
        assertThat(req.subsidyProgramId()).isEqualTo(programId);
        assertThat(req.memberCpid()).isEqualTo(healthId);
        assertThat(req.enrolledBy()).isEqualTo("SELF_OPT_IN:RECOGNITION:LICENSED_PROVIDER");
        assertThat(req.exemptionCategory()).isEqualTo("HEALTH_WORKER");
    }

    @Test
    void optInRejectedWhenNotRecognised() {
        when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(HealthWorkerRecognitionClient.Recognition.negative());

        assertThatThrownBy(() -> service.optIn(tenantId, programId, healthId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not be confirmed");
        verify(enrolmentService, never()).enrol(any(), any());
    }

    @Test
    void optInRejectedWhenRegistriesUnreachable_failClosed() {
        when(recognitionClient.resolve(tenantId, healthId))
                .thenReturn(HealthWorkerRecognitionClient.Recognition.unreachable());

        assertThatThrownBy(() -> service.optIn(tenantId, programId, healthId))
                .isInstanceOf(ResponseStatusException.class);
        verify(enrolmentService, never()).enrol(any(), any());
    }

    private SubsidyEnrolmentEntity activeOptIn(String memberHealthId) {
        SubsidyEnrolmentEntity e = new SubsidyEnrolmentEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setSubsidyProgramId(programId);
        e.setMemberCpid(memberHealthId);
        e.setStatus("ACTIVE");
        e.setEnrolledBy("SELF_OPT_IN:RECOGNITION:LICENSED_PROVIDER");
        e.setExemptionCategory("HEALTH_WORKER");
        e.setEffectiveFrom(LocalDate.now().minusMonths(1));
        return e;
    }

    @Test
    void sweepSuspendsDefiniteLapses() {
        SubsidyEnrolmentEntity lapsed = activeOptIn("hw-lapsed");
        when(enrolmentRepository.findByStatusAndEnrolledByStartingWith("ACTIVE", "SELF_OPT_IN:RECOGNITION"))
                .thenReturn(List.of(lapsed));
        when(recognitionClient.resolve(tenantId, "hw-lapsed"))
                .thenReturn(HealthWorkerRecognitionClient.Recognition.negative());

        int suspended = service.sweepOnce();

        assertThat(suspended).isEqualTo(1);
        assertThat(lapsed.getStatus()).isEqualTo("SUSPENDED");
        verify(enrolmentRepository).save(lapsed);
    }

    @Test
    void sweepKeepsRecognisedMembersActive() {
        SubsidyEnrolmentEntity ok = activeOptIn("hw-ok");
        when(enrolmentRepository.findByStatusAndEnrolledByStartingWith("ACTIVE", "SELF_OPT_IN:RECOGNITION"))
                .thenReturn(List.of(ok));
        when(recognitionClient.resolve(tenantId, "hw-ok"))
                .thenReturn(new HealthWorkerRecognitionClient.Recognition(true, "HEALTH_WORKFORCE", true));

        assertThat(service.sweepOnce()).isZero();
        assertThat(ok.getStatus()).isEqualTo("ACTIVE");
        verify(enrolmentRepository, never()).save(any());
    }

    @Test
    void sweepNeverSuspendsOnRegistryOutage() {
        SubsidyEnrolmentEntity unknown = activeOptIn("hw-unknown");
        when(enrolmentRepository.findByStatusAndEnrolledByStartingWith("ACTIVE", "SELF_OPT_IN:RECOGNITION"))
                .thenReturn(List.of(unknown));
        when(recognitionClient.resolve(tenantId, "hw-unknown"))
                .thenReturn(HealthWorkerRecognitionClient.Recognition.unreachable());

        assertThat(service.sweepOnce()).isZero();
        assertThat(unknown.getStatus()).isEqualTo("ACTIVE");
        verify(enrolmentRepository, never()).save(any());
    }
}
