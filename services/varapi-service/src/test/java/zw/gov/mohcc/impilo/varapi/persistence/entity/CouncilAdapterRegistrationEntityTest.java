package zw.gov.mohcc.impilo.varapi.persistence.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract of the live-adapter registration entity (IATG Wave 2, Channel A): the seam is
 * honestly gated OFF by default — a freshly constructed registration is {@code enabled = false},
 * auth defaults to {@code NONE}, and sane timeouts are pre-set. Exercises the create/read/update
 * surface of the entity.
 */
class CouncilAdapterRegistrationEntityTest {

    @Test
    void defaults_areHonestlyGatedOff() {
        CouncilAdapterRegistrationEntity reg = new CouncilAdapterRegistrationEntity(
                UUID.randomUUID(), "MDPCZ", "https://authority.example/verify");

        assertThat(reg.isEnabled()).as("registration must default OFF").isFalse();
        assertThat(reg.getAuthType()).isEqualTo("NONE");
        assertThat(reg.getConnectTimeoutMs()).isEqualTo(3000);
        assertThat(reg.getReadTimeoutMs()).isEqualTo(5000);
        assertThat(reg.getCreatedAt()).isNotNull();
        assertThat(reg.getUpdatedAt()).isNotNull();
        assertThat(reg.getAuthConfigJson()).isNull();
    }

    @Test
    void mutators_roundTripAllFields() {
        UUID tenant = UUID.randomUUID();
        CouncilAdapterRegistrationEntity reg = new CouncilAdapterRegistrationEntity();
        reg.setId(42L);
        reg.setTenantId(tenant);
        reg.setCouncilCode("NMCZ");
        reg.setEndpointUrl("https://nurses.example/api/verify");
        reg.setAuthType("API_KEY");
        reg.setAuthConfigJson("{\"headerName\":\"X-Api-Key\"}");
        reg.setEnabled(true);
        reg.setConnectTimeoutMs(1500);
        reg.setReadTimeoutMs(2500);

        assertThat(reg.getId()).isEqualTo(42L);
        assertThat(reg.getTenantId()).isEqualTo(tenant);
        assertThat(reg.getCouncilCode()).isEqualTo("NMCZ");
        assertThat(reg.getEndpointUrl()).isEqualTo("https://nurses.example/api/verify");
        assertThat(reg.getAuthType()).isEqualTo("API_KEY");
        assertThat(reg.getAuthConfigJson()).isEqualTo("{\"headerName\":\"X-Api-Key\"}");
        assertThat(reg.isEnabled()).isTrue();
        assertThat(reg.getConnectTimeoutMs()).isEqualTo(1500);
        assertThat(reg.getReadTimeoutMs()).isEqualTo(2500);
    }
}
