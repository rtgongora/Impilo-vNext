package zw.gov.mohcc.impilo.mvumo.integration;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAPI golden parse/validation: ensures Mvumo-generated FHIR R4 Consent JSON is structurally valid for Tshepo's parser.
 */
class FhirMvumoConsentBuilderHapiTest {

    @Test
    void buildsConsentParsableByHapiR4() {
        String json =
                FhirMvumoConsentBuilder.buildJson(
                        "Patient/test-cpid-1",
                        "Patient/test-cpid-1",
                        "mvumo-req-uuid-1",
                        "GENERAL_TREATMENT",
                        java.time.Instant.parse("2026-01-15T10:00:00Z"));
        FhirContext ctx = FhirContext.forR4();
        Consent c = (Consent) ctx.newJsonParser().parseResource(json);
        assertThat(c.getResourceType().name()).isEqualTo("Consent");
        assertThat(c.getStatus()).isNotNull();
        assertThat(c.getCategory()).isNotEmpty();
        assertThat(c.getScope()).isNotNull();
    }
}
