package zw.gov.mohcc.impilo.ubomi.integration.rg;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** W1b — startup invariants: endpoint allow-list + mTLS custody refs. */
class RegistrarGeneralClientTest {

    private UbomiRgProperties props(String baseUrl, List<String> allowed, String keyRef, String trustRef) {
        UbomiRgProperties p = new UbomiRgProperties();
        p.setEnabled(true);
        p.setBaseUrl(baseUrl);
        p.setAllowedHosts(allowed);
        p.getMtls().setKeyRef(keyRef);
        p.getMtls().setTruststoreRef(trustRef);
        return p;
    }

    @Test
    void rejects_base_url_host_not_on_allow_list() {
        UbomiRgProperties p = props("https://evil.example.com", List.of("rg.gov.zw"), "k", "t");
        assertThatThrownBy(() -> new RegistrarGeneralClient(new RestTemplate(), p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not on ubomi.rg.allowed-hosts");
    }

    @Test
    void rejects_missing_mtls_refs() {
        UbomiRgProperties p = props("https://rg.gov.zw", List.of("rg.gov.zw"), "", "");
        assertThatThrownBy(() -> new RegistrarGeneralClient(new RestTemplate(), p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mtls");
    }

    @Test
    void constructs_when_allow_listed_and_mtls_present() {
        UbomiRgProperties p = props("https://rg.gov.zw", List.of("rg.gov.zw"), "keys://rg-client", "keys://rg-trust");
        RegistrarGeneralClient client = new RegistrarGeneralClient(new RestTemplate(), p);
        assertThat(client).isNotNull();
    }
}
