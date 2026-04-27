package zw.gov.mohcc.impilo.mvumo.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live dev-instance check: POSTs the same contract as Mvumo to a running {@code tshepo-consent-service}.
 * <p>
 * Enable by setting: {@code MVUMO_IT_TSHEPO_BASE} (e.g. {@code http://localhost:8182}).
 * Optional: {@code MVUMO_IT_BEARER_TOKEN} for authenticated deployments.
 * </p>
 */
class TshepoConsentDevInstanceIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "MVUMO_IT_TSHEPO_BASE", matches = ".+")
    void postConsentGoldenContract() {
        String base = System.getenv("MVUMO_IT_TSHEPO_BASE").trim().replaceAll("/$", "");
        String token = System.getenv("MVUMO_IT_BEARER_TOKEN");
        String fhir =
                FhirMvumoConsentBuilder.buildJson(
                        "Patient/CPID-IT-1", "Patient/CPID-IT-1", "mvumo-it-1", "GOLDEN_IT", java.time.Instant.now());
        var body = new HashMap<String, Object>();
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        body.put("tenantId", tenant);
        body.put("patientRef", "Patient/CPID-IT-1");
        body.put("grantorRef", "Patient/CPID-IT-1");
        body.put("granteeRef", "");
        body.put("scope", "clinical-data");
        body.put("purpose", "TREATMENT");
        body.put("provision", "permit");
        body.put("fhirConsentJson", fhir);
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            h.setBearerAuth(token);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, h);
        ResponseEntity<String> r = rt.postForEntity(base + "/v1/consent", entity, String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r.getBody()).contains("\"data\"");
    }
}
