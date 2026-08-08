package zw.gov.mohcc.impilo.oros.integration;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is exactly one route from OROS into the Shared Health Record, and it is the governed one.
 *
 * <p>OROS used to post lab reports, observations, documents and imaging studies straight at
 * {@code butano-service/fhir/*}. Those writes reached the record without a consent decision and
 * left no row in {@code fhir_audit_log} — nothing could answer whether a patient's result had been
 * filed against a valid legal basis, or filed at all.</p>
 *
 * <p>The behavioural tests around this one all pass with a direct-post fallback quietly reinstated:
 * they mock {@link FhirGatewayClient} and assert what it was handed, which says nothing about what
 * else the class might do. This checks the property those tests cannot see — that no other
 * transport exists in {@link ButanoIntegration} at all. A bypass added "just for the outage case"
 * fails here.</p>
 */
class OrosShrWriteSeamTest {

    @Test
    void butanoIntegrationHoldsNoTransportOfItsOwn() {
        for (Field field : ButanoIntegration.class.getDeclaredFields()) {
            assertThat(field.getType().getName())
                    .describedAs("%s holds a %s — the only way out of this class is the gateway",
                            field.getName(), field.getType().getSimpleName())
                    .doesNotContain("RestTemplate")
                    .doesNotContain("RestClient")
                    .doesNotContain("WebClient")
                    .doesNotContain("HttpClient");
        }
    }

    @Test
    void noFhirEndpointIsAddressedDirectly() throws Exception {
        // A URL literal is how the bypass would come back: one "/fhir/Observation" string and the
        // consent PEP is out of the path again for that resource type only — the hardest kind of
        // regression to notice, because everything else still works.
        Path source = Path.of("src/main/java/zw/gov/mohcc/impilo/oros/integration/ButanoIntegration.java");
        assertThat(source).exists();
        String code = Files.readString(source);

        for (String resourceType : new String[]{"ServiceRequest", "DiagnosticReport", "Observation",
                "DocumentReference", "ImagingStudy"}) {
            assertThat(code)
                    .describedAs("a direct /fhir/%s endpoint is a write that skips consent and audit",
                            resourceType)
                    .doesNotContain("\"/fhir/" + resourceType + "\"")
                    .doesNotContain("/fhir/" + resourceType + "\"");
        }
    }
}
