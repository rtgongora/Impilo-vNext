package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.HearingDocketService.RecusalRequiredException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CaseDocketAssignmentRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.DisciplinaryAppealRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.DisciplinaryHearingRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderDisciplinaryCaseRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingDocketServiceTest {

    @Mock ProviderDisciplinaryCaseRepository caseRepository;
    @Mock DisciplinaryHearingRepository hearingRepository;
    @Mock CaseDocketAssignmentRepository docketRepository;
    @Mock DisciplinaryAppealRepository appealRepository;

    private final UUID tenant = UUID.randomUUID();
    private final UUID subjectHealthId = UUID.randomUUID();

    private HearingDocketService service() {
        lenient().when(docketRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new HearingDocketService(caseRepository, hearingRepository, docketRepository, appealRepository);
    }

    private ProviderDisciplinaryCaseEntity caseWithSubject() {
        ProviderEntity subject = new ProviderEntity();
        subject.setImpiloHealthId(subjectHealthId);
        ProviderDisciplinaryCaseEntity c = new ProviderDisciplinaryCaseEntity();
        c.setTenantId(tenant); c.setProvider(subject);
        return c;
    }

    @Test
    void memberSeesOnlyDocketedCases() {
        // Docketed → visible.
        when(docketRepository.existsByTenantIdAndCaseIdAndCommitteeMemberHealthIdAndStatus(tenant, 1L, "member-A", "ACTIVE"))
                .thenReturn(true);
        // Not docketed → not visible.
        when(docketRepository.existsByTenantIdAndCaseIdAndCommitteeMemberHealthIdAndStatus(tenant, 2L, "member-A", "ACTIVE"))
                .thenReturn(false);
        HearingDocketService svc = service();
        assertThat(svc.memberCanSee(tenant, 1L, "member-A")).isTrue();
        assertThat(svc.memberCanSee(tenant, 2L, "member-A")).isFalse();
    }

    @Test
    void memberCannotBeDocketedToOwnCase() {
        when(caseRepository.findByIdAndTenantId(1L, tenant)).thenReturn(Optional.of(caseWithSubject()));
        assertThatThrownBy(() -> service().assignToDocket(tenant, 1L, subjectHealthId.toString(), "NCZ-PCC"))
                .isInstanceOf(RecusalRequiredException.class);
    }

    @Test
    void aDifferentMemberMayBeDocketed() {
        when(caseRepository.findByIdAndTenantId(1L, tenant)).thenReturn(Optional.of(caseWithSubject()));
        var a = service().assignToDocket(tenant, 1L, UUID.randomUUID().toString(), "NCZ-PCC");
        assertThat(a.getStatus()).isEqualTo("ACTIVE");
        assertThat(a.getCommitteeRef()).isEqualTo("NCZ-PCC");
    }
}
