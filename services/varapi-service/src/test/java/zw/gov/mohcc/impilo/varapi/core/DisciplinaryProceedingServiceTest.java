package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.DisciplinaryProceedingService.RecusalRequiredException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderDisciplinaryCaseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.RegisterEntryRestrictionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisciplinaryProceedingServiceTest {

    @Mock ProviderDisciplinaryCaseRepository caseRepository;
    @Mock ProviderRepository providerRepository;
    @Mock ProviderCouncilRegistrationRecordRepository registrationRepository;
    @Mock RegisterEntryRestrictionRepository restrictionRepository;

    private final UUID tenant = UUID.randomUUID();
    private final UUID subjectHealthId = UUID.randomUUID();

    private DisciplinaryProceedingService service() {
        lenient().when(caseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new DisciplinaryProceedingService(caseRepository, providerRepository,
                registrationRepository, restrictionRepository);
    }

    private ProviderEntity subject() {
        ProviderEntity p = new ProviderEntity();
        p.setId(9L);
        p.setImpiloHealthId(subjectHealthId);
        return p;
    }

    @Test
    void officerOpensCaseFromRitoReferralSettingTheSourceLink() {
        when(providerRepository.findByIdAndTenantId(9L, tenant)).thenReturn(Optional.of(subject()));
        var c = service().openFromReferral(tenant, UUID.randomUUID().toString(), 9L, 3L, "RITO-CASE-123", "alleged misconduct");
        assertThat(c.getCaseStage()).isEqualTo("RECEIVED");
        assertThat(c.getSourceRitoCaseId()).isEqualTo("RITO-CASE-123"); // human-mediated referral link
        assertThat(c.getTriggerType()).isEqualTo("COMPLAINT_REFERRAL");
    }

    @Test
    void officerCannotOpenAProceedingAgainstThemselves() {
        when(providerRepository.findByIdAndTenantId(9L, tenant)).thenReturn(Optional.of(subject()));
        assertThatThrownBy(() -> service().openFromReferral(
                tenant, subjectHealthId.toString(), 9L, 3L, "RITO-CASE-1", "x"))
                .isInstanceOf(RecusalRequiredException.class);
    }

    @Test
    void illegalFsmTransitionIsRejected() {
        ProviderDisciplinaryCaseEntity c = new ProviderDisciplinaryCaseEntity();
        c.setTenantId(tenant); c.setProvider(subject()); c.setCaseStage("RECEIVED");
        when(caseRepository.findByIdAndTenantId(1L, tenant)).thenReturn(Optional.of(c));
        // RECEIVED cannot jump straight to HEARING
        assertThatThrownBy(() -> service().advance(tenant, UUID.randomUUID().toString(), 1L, "HEARING"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void determinationWritesAFirstClassRestriction() {
        ProviderDisciplinaryCaseEntity c = new ProviderDisciplinaryCaseEntity();
        c.setTenantId(tenant); c.setProvider(subject()); c.setCaseStage("HEARING");
        when(caseRepository.findByIdAndTenantId(1L, tenant)).thenReturn(Optional.of(c));
        when(registrationRepository.findByTenantIdAndProvider_Id(tenant, 9L)).thenReturn(java.util.List.of());
        var determined = service().determine(tenant, UUID.randomUUID().toString(), 1L,
                "SUSPENDED_6_MONTHS", "INTERDICTION", "Suspended for six months");
        assertThat(determined.getCaseStage()).isEqualTo("DETERMINED");
        assertThat(determined.getFinalDecision()).isEqualTo("SUSPENDED_6_MONTHS");
    }
}
