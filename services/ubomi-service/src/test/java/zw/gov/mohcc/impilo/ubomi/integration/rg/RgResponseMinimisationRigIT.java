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
 * Response-minimisation acceptance: even when the RG returns a fat record
 * (National ID, names, address, parents, ...), <b>none</b> of that civil PII is
 * persisted in the {@code rg_verification} ledger or emitted in the
 * {@code civil.*} event. Only the opaque {@code civilIdentityToken} survives —
 * enforced by the {@code RgWireResponse} DTO shape
 * ({@code @JsonIgnoreProperties(ignoreUnknown=true)}), not by downstream care.
 */
class RgResponseMinimisationRigIT {

    private static final String PII_NID = "63-9999999Z44";
    private static final String PII_NAME = "Rutendo Chikafu";
    private static final String PII_ADDRESS = "14 Samora Machel Ave, Harare";
    private static final String PII_MOTHER = "Grace Chikafu";

    private WireMockServer wireMock;
    private RgVerificationService service;
    private RgVerificationRepository ledger;
    private EventOutboxRepository outbox;

    private final UUID TENANT = UUID.randomUUID();
    private final String SUBJECT = UUID.randomUUID().toString();

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
                .setConnectTimeout(Duration.ofMillis(2000))
                .setReadTimeout(Duration.ofMillis(5000))
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
    void full_rg_record_is_never_persisted_or_emitted() {
        // RG returns far more than we asked for — the classic over-sharing case.
        wireMock.stubFor(post(urlEqualTo("/v1/verify"))
                .willReturn(okJson("""
                        {"matched": true,
                         "civilIdentityToken": "CIT-MINIMAL-1",
                         "verifiedFullName": true,
                         "verifiedDateOfBirth": true,
                         "nationalId": "%s",
                         "fullName": "%s",
                         "residentialAddress": "%s",
                         "motherName": "%s",
                         "biometricTemplate": "BASE64-BLOB"}""".formatted(
                        PII_NID, PII_NAME, PII_ADDRESS, PII_MOTHER))));

        service.verify(TENANT, SUBJECT, "NATIONAL_ID", PII_NID, PII_NAME, "1988-02-02");

        // 1) Ledger row holds only the opaque token + a one-way hash — no PII.
        ArgumentCaptor<RgVerificationEntity> row = ArgumentCaptor.forClass(RgVerificationEntity.class);
        verify(ledger, atLeastOnce()).save(row.capture());
        RgVerificationEntity saved = row.getValue();
        assertThat(saved.getCivilIdentityToken()).isEqualTo("CIT-MINIMAL-1");
        assertThat(saved.getReferenceHash()).doesNotContain(PII_NID); // it is a SHA-256, not the NID
        assertThat(saved.getReferenceHash()).hasSize(64);
        assertThatNoPii(saved.getReferenceHash());
        assertThatNoPii(String.valueOf(saved.getCivilIdentityToken()));

        // 2) Emitted civil.* event carries only opaque token + status — no PII.
        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(ev.capture());
        String payload = ev.getValue().getPayload();
        assertThat(payload).contains("CIT-MINIMAL-1");
        assertThatNoPii(payload);
    }

    private void assertThatNoPii(String s) {
        assertThat(s)
                .doesNotContain(PII_NID)
                .doesNotContain(PII_NAME)
                .doesNotContain(PII_ADDRESS)
                .doesNotContain(PII_MOTHER)
                .doesNotContain("BASE64-BLOB")
                .doesNotContain("biometricTemplate")
                .doesNotContain("residentialAddress");
    }
}
