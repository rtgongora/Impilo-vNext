package zw.gov.mohcc.impilo.clinical.interpretation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the ZIBO-backed provider's fetch → parse → cache → lookup, and its fail-closed behaviour when
 * ZIBO is unreachable (returns no intervals — never fabricated).
 */
class ZiboObservationDefinitionProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BODY = """
        {"success":true,"data":[
          {"artifactId":"art-K","version":"1.0.0","canonicalUrl":"urn:od:k","status":"DRAFT",
           "content":{
             "code":{"coding":[{"system":"http://loinc.org","code":"2823-3"},{"system":"http://lims","code":"K"}]},
             "qualifiedInterval":[
               {"category":"reference","range":{"low":{"value":3.5,"unit":"mmol/L"},"high":{"value":5.1,"unit":"mmol/L"}}},
               {"category":"critical","range":{"low":{"value":2.5,"unit":"mmol/L"},"high":{"value":6.5,"unit":"mmol/L"}}}
             ]}}
        ]}
        """;

    @Test
    void fetchParseCache_findByLoincAndLocal() throws Exception {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(ResponseEntity.ok(mapper.readTree(BODY)));

        var provider = new ZiboObservationDefinitionProvider(rt, "http://zibo", 600000);

        List<QualifiedInterval> byLoinc = provider.findIntervals("2823-3", null);
        assertThat(byLoinc).hasSize(1);
        assertThat(byLoinc.get(0).low()).isEqualByComparingTo("3.5");
        assertThat(byLoinc.get(0).criticalHigh()).isEqualByComparingTo("6.5");
        assertThat(byLoinc.get(0).artifactId()).isEqualTo("art-K");

        assertThat(provider.findIntervals(null, "K")).hasSize(1);
        assertThat(provider.findIntervals("0000-0", null)).isEmpty();
    }

    @Test
    void ziboUnreachable_failsClosed_returnsEmpty() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.getForEntity(anyString(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new RuntimeException("connection refused"));

        var provider = new ZiboObservationDefinitionProvider(rt, "http://zibo", 600000);

        assertThat(provider.findIntervals("2823-3", "K")).isEmpty();
    }
}
