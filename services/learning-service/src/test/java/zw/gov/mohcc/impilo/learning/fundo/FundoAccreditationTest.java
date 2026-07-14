package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;

/** C2 — request → review → accredit → provision, regulator routed by kind. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoAccreditationTest {

    private static final UUID TENANT = UUID.fromString("c2c2c2c2-0000-4000-8000-000000000001");

    @Autowired private FundoAccreditationService accreditation;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void individualProviderAccreditationRoutesToCouncil() {
        Map<String, Object> app = accreditation.submit(TENANT, Map.of(
                "providerKind", "INDIVIDUAL", "requestedName", "Dr Banda CPD", "targetIdentityRef", "VARAPI-PRV-7"));
        String appId = (String) app.get("id");
        assertThat(app.get("status")).isEqualTo("SUBMITTED");

        accreditation.decide(TENANT, appId, "regulator", Map.of("status", "UNDER_REVIEW"));
        assertThat(((List<?>) accreditation.listApplications(TENANT, "UNDER_REVIEW", 50).get("items"))).hasSize(1);

        Map<String, Object> result = accreditation.accredit(TENANT, appId, "regulator", Map.of("councilRef", "MDPC-999"));
        assertThat(result.get("regulator")).isEqualTo("PROFESSIONAL_COUNCIL");
        Map<String, Object> provider = (Map<String, Object>) result.get("provider");
        assertThat(provider.get("providerKind")).isEqualTo("INDIVIDUAL");
        assertThat(provider.get("accreditationStatus")).isEqualTo("ACCREDITED");

        String providerId = (String) provider.get("id");
        assertThat(((List<?>) accreditation.listAccreditations(TENANT, providerId).get("items"))).hasSize(1);
    }

    @Test
    void facilityProviderRoutesToFacilityRegulator() {
        Map<String, Object> app = accreditation.submit(TENANT, Map.of(
                "providerKind", "FACILITY", "requestedName", "Pari Training Unit", "targetIdentityRef", "TUSO-FAC-55"));
        Map<String, Object> result = accreditation.accredit(TENANT, (String) app.get("id"), "regulator", Map.of());
        assertThat(result.get("regulator")).isEqualTo("FACILITY_REGULATOR");
    }

    @Test
    void organisationProviderRoutesToOrganisationalAccreditation() {
        Map<String, Object> app = accreditation.submit(TENANT, Map.of(
                "providerKind", "ORGANISATION", "requestedName", "Skills Academy", "targetIdentityRef", "WGV-ORG-100"));
        Map<String, Object> result = accreditation.accredit(TENANT, (String) app.get("id"), "regulator", Map.of());
        assertThat(result.get("regulator")).isEqualTo("ORGANISATIONAL_ACCREDITATION");
    }
}
