package zw.gov.mohcc.impilo.dags.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnforcementServiceTest {

    private EnforcementService enforcementService;

    @BeforeEach
    void setUp() {
        enforcementService = new EnforcementService();
    }

    @Test
    @DisplayName("Issues a non-null permit token")
    void issuesNonNullPermitToken() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Permit token starts with expected prefix")
    void permitTokenHasCorrectPrefix() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(42L, tenantId, "user-2");
        assertThat(token).startsWith("permit-token:");
    }

    @Test
    @DisplayName("Permit token contains tenant and requester info")
    void permitTokenContainsContextInfo() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(7L, tenantId, "dr-jones");
        assertThat(token).contains(tenantId.toString());
        assertThat(token).contains("dr-jones");
        assertThat(token).contains("7");
    }

    @Test
    @DisplayName("Each token is unique")
    void tokensAreUnique() {
        UUID tenantId = UUID.randomUUID();
        String token1 = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        String token2 = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        assertThat(token1).isNotEqualTo(token2);
    }
}
