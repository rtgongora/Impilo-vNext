package zw.gov.mohcc.impilo.ubomi.integration.rg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SOFTWARE_CONTRACT proof for the RG adapter using a WireMock RG stub (no live
 * RG, no mTLS — the contract path only). Exercises the three acceptance flows:
 * NID verify hit → {@code civil.identity.verified}; miss → RG_NO_MATCH; outage
 * → RG_PENDING (fail-open-for-care, never throws).
 *
 * <p>Named {@code *RigIT} and run under surefire via the module's surefire
 * {@code <includes>} (there is no failsafe in this estate).</p>
 */
class RgVerificationRigIT {

    private WireMockServer wireMock;
    private RgVerificationService service;
    private RgVerificationRepository ledger;
    private EventOutboxRepository outbox;

    private final UUID TENANT = UUID.randomUUID();
    private final String SUBJECT = UUID.randomUUID().toString();
    private final String NID = "63-1234567X08";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        UbomiRgProperties props = new UbomiRgProperties();
        props.setEnabled(true);
        props.setBaseUrl("http://localhost:" + wireMock.port());
        props.setAllowedHosts(List.of("localhost"));
        props.getMtls().setKeyRef("keys://rg-client");
        props.getMtls().setTruststoreRef("keys://rg-trust");

        RestTemplate rt = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .build();
        RegistrarGeneralClient client = new RegistrarGeneralClient(rt, props);

        ObjectProvider<RegistrarGeneralClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);

        ledger = mock(RgVerificationRepository.class);
        outbox = mock(EventOutboxRepository.class);
        when(ledger.findByTenantIdAndBusinessKey(eq(TENANT), anyString())).thenReturn(Optional.empty());
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));

        service = new RgVerificationService(ledger, outbox, provider, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void nid_verify_hit_produces_verified_and_civil_identity_verified_event() {
        wireMock.stubFor(post(urlEqualTo("/v1/verify"))
                .willReturn(okJson("""
                        {"matched": true,
                         "civilIdentityToken": "CIT-OPAQUE-777",
                         "verifiedFullName": true,
                         "verifiedDateOfBirth": true}""")));

        RgVerificationService.RgVerificationResult r =
                service.verify(TENANT, SUBJECT, "NATIONAL_ID", NID, "Tendai Moyo", "1990-04-01");

        assertThat(r.status()).isEqualTo(RgVerificationEntity.RG_VERIFIED);
        assertThat(r.matched()).isTrue();
        assertThat(r.civilIdentityToken()).isEqualTo("CIT-OPAQUE-777");

        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("CIVIL_IDENTITY_VERIFIED");
        assertThat(ev.getValue().getPayload()).contains("CIT-OPAQUE-777", "VERIFIED", SUBJECT);
        // The raw National ID never appears in the emitted event.
        assertThat(ev.getValue().getPayload()).doesNotContain(NID);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/verify")));
    }

    @Test
    void nid_verify_miss_produces_no_match_and_no_event() {
        wireMock.stubFor(post(urlEqualTo("/v1/verify"))
                .willReturn(okJson("{\"matched\": false}")));

        RgVerificationService.RgVerificationResult r =
                service.verify(TENANT, SUBJECT, "NATIONAL_ID", NID, null, null);

        assertThat(r.status()).isEqualTo(RgVerificationEntity.RG_NO_MATCH);
        assertThat(r.matched()).isFalse();
        verify(outbox, never()).save(any());
    }

    @Test
    void rg_outage_fails_open_to_pending_without_throwing() {
        wireMock.stubFor(post(urlEqualTo("/v1/verify"))
                .willReturn(aResponse().withStatus(503)));

        RgVerificationService.RgVerificationResult r =
                service.verify(TENANT, SUBJECT, "NATIONAL_ID", NID, null, null);

        assertThat(r.status()).isEqualTo(RgVerificationEntity.RG_PENDING);
        assertThat(r.matched()).isFalse();
        verify(outbox, never()).save(any()); // care journey unaffected; no fabricated event
    }
}
