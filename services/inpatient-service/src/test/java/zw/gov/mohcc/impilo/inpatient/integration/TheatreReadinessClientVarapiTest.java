package zw.gov.mohcc.impilo.inpatient.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave 1 regression: the varapi provider-scope leg (surgeon readiness + operative-note signer scope).
 * Reproduces the three Wave 0 bugs and locks the fixes:
 *   1. varapi wraps the privilege list in an ApiResponse envelope {data:[...]} — must be unwrapped;
 *   2. the privilege lifecycle uses APPROVED, not ACTIVE;
 *   3. the domain scope value is SURGERY, not SURGEON.
 */
class TheatreReadinessClientVarapiTest {

    @SuppressWarnings("unchecked")
    private TheatreReadinessClient clientReturning(Object body) {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(contains("/privileges"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(body));
        return new TheatreReadinessClient(rt, "http://asset", "http://varapi", "http://vashandi");
    }

    @Test
    void approvedSurgeryPrivilege_inEnvelope_isReady() {
        Object envelope = Map.of("data", List.of(Map.of("status", "APPROVED", "scope", "SURGERY")));
        TheatreReadinessClient client = clientReturning(envelope);

        TheatreReadinessClient.ReadinessResult result = client.checkProviderScope("PROV-1", "SURGERY");

        assertTrue(result.ready(), "APPROVED + SURGERY privilege inside {data:[...]} must be READY");
    }

    @Test
    void draftPrivilege_isBlockedNotReady() {
        Object envelope = Map.of("data", List.of(Map.of("status", "DRAFT", "scope", "SURGERY")));
        TheatreReadinessClient client = clientReturning(envelope);

        TheatreReadinessClient.ReadinessResult result = client.checkProviderScope("PROV-1", "SURGERY");

        assertEquals("BLOCKED", result.status(), "a non-APPROVED privilege must not clear scope");
    }

    @Test
    void wrongScope_isBlocked() {
        Object envelope = Map.of("data", List.of(Map.of("status", "APPROVED", "scope", "GENERAL_PRACTICE")));
        TheatreReadinessClient client = clientReturning(envelope);

        TheatreReadinessClient.ReadinessResult result = client.checkProviderScope("PROV-1", "SURGERY");

        assertEquals("BLOCKED", result.status());
    }
}
