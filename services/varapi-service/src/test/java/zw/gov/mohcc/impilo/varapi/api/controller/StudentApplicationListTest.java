package zw.gov.mohcc.impilo.varapi.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.CouncilFeeGate;
import zw.gov.mohcc.impilo.varapi.core.StudentAdmissionService;
import zw.gov.mohcc.impilo.varapi.core.StudentApplicationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderApplicationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentApplicationListTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock StudentApplicationService applicationService;
    @Mock StudentAdmissionService admissionService;
    @Mock CouncilFeeGate feeGate;
    @Mock ProviderApplicationRepository applicationRepository;
    @Mock CouncilRepository councilRepository;
    @InjectMocks StudentApplicationController controller;

    @Test
    void listsStudentApplicationsForOrganisation() {
        CouncilEntity council = new CouncilEntity();
        council.setId(3L);
        council.setCouncilCode("NCZ");
        when(councilRepository.findByTenantIdAndOrgRegistryOrgId(TENANT, ORG))
                .thenReturn(Optional.of(council));

        ProviderEntity provider = new ProviderEntity();
        provider.setId(42L);
        provider.setProviderPublicId("PRV-42");
        ProviderApplicationEntity app = new ProviderApplicationEntity();
        app.setId(100L);
        app.setApplicationType("STUDENT_REGISTRATION");
        app.setWorkflowState("SUBMITTED");
        app.setCouncil(council);
        app.setProvider(provider);
        when(applicationRepository
                .findByTenantIdAndCouncil_IdAndApplicationTypeOrderBySubmittedAtDesc(
                        TENANT, 3L, "STUDENT_REGISTRATION"))
                .thenReturn(List.of(app));

        List<Map<String, Object>> rows = controller.listStudentApplications(TENANT, null, ORG, null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id")).isEqualTo(100L);
        assertThat(rows.get(0).get("providerPublicId")).isEqualTo("PRV-42");
        assertThat(rows.get(0).get("councilCode")).isEqualTo("NCZ");
    }
}
