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

/** C4 — directory lists only ACCREDITED providers + their academies; micro-spaces included. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoDirectoryTest {

    private static final UUID TENANT = UUID.fromString("c4c4c4c4-0000-4000-8000-000000000001");

    @Autowired private FundoLearningProviderService providers;
    @Autowired private FundoAccreditationService accreditation;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void directoryShowsAccreditedOnly() {
        // unaccredited provider — should NOT appear
        providers.createProvider(TENANT, "admin", Map.of("providerKind", "ORGANISATION", "displayName", "Pending Org"));

        // individual micro-space provider accredited via the workflow
        Map<String, Object> app = accreditation.submit(TENANT, Map.of(
                "providerKind", "INDIVIDUAL", "requestedName", "Solo Trainer", "targetIdentityRef", "VARAPI-1"));
        Map<String, Object> result = accreditation.accredit(TENANT, (String) app.get("id"), "regulator", Map.of());
        String providerId = (String) ((Map<String, Object>) result.get("provider")).get("id");
        providers.createSpace(TENANT, providerId, "admin", Map.of("name", "Solo CPD micro-academy", "spaceKind", "DEPARTMENT"));

        List<Map<String, Object>> dir = (List<Map<String, Object>>) providers.directory(TENANT, 50).get("items");
        assertThat(dir).hasSize(1);
        assertThat(dir.get(0).get("displayName")).isEqualTo("Solo Trainer");
        assertThat(((List<?>) dir.get(0).get("academies"))).hasSize(1);
    }
}
