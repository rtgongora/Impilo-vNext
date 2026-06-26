package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** C1 — provider registry across the 3 regulated kinds + academy spaces. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoLearningProviderIT {

    private static final UUID TENANT = UUID.fromString("c1c1c1c1-0000-4000-8000-000000000001");

    @Autowired private FundoLearningProviderService providers;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void threeRegulatedKindsAndSpaces() {
        Map<String, Object> individual = providers.createProvider(TENANT, "admin", Map.of(
                "providerKind", "INDIVIDUAL", "displayName", "Dr Banda (CPD trainer)", "sorRef", "VARAPI-PRV-7", "councilRef", "MDPC-12345"));
        Map<String, Object> org = providers.createProvider(TENANT, "admin", Map.of(
                "providerKind", "ORGANISATION", "displayName", "Skills Academy ZW", "sorRef", "WGV-ORG-100"));
        Map<String, Object> facility = providers.createProvider(TENANT, "admin", Map.of(
                "providerKind", "FACILITY", "displayName", "Parirenyatwa Training Unit", "sorRef", "TUSO-FAC-55"));

        assertThat(individual.get("providerKind")).isEqualTo("INDIVIDUAL");
        assertThat(facility.get("providerKind")).isEqualTo("FACILITY");
        assertThat(individual.get("accreditationStatus")).isEqualTo("UNACCREDITED");

        assertThat(((List<?>) providers.listProviders(TENANT, null, 50).get("items"))).hasSize(3);
        assertThat(((List<?>) providers.listProviders(TENANT, "ORGANISATION", 50).get("items"))).hasSize(1);

        // an academy space under the organisation provider, bound to a governance org-unit
        String orgId = (String) org.get("id");
        Map<String, Object> space = providers.createSpace(TENANT, orgId, "admin", Map.of(
                "name", "Nursing Academy", "spaceKind", "ACADEMY", "orgUnitRef", "WGV-UNIT-200"));
        assertThat(space.get("orgUnitRef")).isEqualTo("WGV-UNIT-200");
        assertThat(((List<?>) providers.listSpaces(TENANT, orgId).get("items"))).hasSize(1);

        // invalid kind rejected
        assertThatThrownBy(() -> providers.createProvider(TENANT, "admin", Map.of(
                "providerKind", "ROBOT", "displayName", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
