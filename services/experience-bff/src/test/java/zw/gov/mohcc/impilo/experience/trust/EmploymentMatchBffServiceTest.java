package zw.gov.mohcc.impilo.experience.trust;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.ProviderTrustClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Channel B trust-call composition (IATG WS-D): EMPLOYMENT_MATCHED is asserted
 * with masked-EC evidence only on MATCHED_BOTH / MATCHED_EMPLOYMENT_ONLY with a
 * provider present; confidence maps 0.95 / 0.75; no provider means guidance
 * (providerCreationRequired), never provider creation; the raw EC number never
 * appears in any response.
 */
@ExtendWith(MockitoExtension.class)
class EmploymentMatchBffServiceTest {

    private static final String RAW_EC = "EC123456";
    private static final String HEALTH_ID = "HID-1";
    private static final String PROVIDER_ID = "ZXCV1234";

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private WorkforceGovernanceClient governanceClient;
    @Mock private ProviderTrustClient trustClient;

    private EmploymentMatchBffService service() {
        return new EmploymentMatchBffService(governanceClient, trustClient);
    }

    private ObjectNode matchResponse(String matchStatus) {
        ObjectNode node = mapper.createObjectNode();
        node.put("matchStatus", matchStatus);
        node.put("employmentId", "e0000000-0000-0000-0000-000000000001");
        node.put("cadre", "NURSE");
        node.put("grade", "D1");
        return node;
    }

    @Test
    void matchedBothWithProviderAssertsTrustAtConfidence095WithMaskedRef() throws Exception {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("MATCHED_BOTH"));
        ObjectNode trustBody = mapper.createObjectNode()
                .put("providerPublicId", PROVIDER_ID)
                .put("trustLevel", "EMPLOYMENT_MATCHED")
                .put("registryStatus", "ACTIVE");
        when(trustClient.assertTrust(eq(PROVIDER_ID), any()))
                .thenReturn(ProviderTrustClient.TrustCallResult.ok(200, trustBody));

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(trustClient).assertTrust(eq(PROVIDER_ID), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertThat(body.get("targetLevel")).isEqualTo("EMPLOYMENT_MATCHED");
        assertThat(body.get("channel")).isEqualTo("WORKFORCE_B");
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) body.get("evidence");
        assertThat(evidence.get("type")).isEqualTo("EC_MATCH");
        assertThat(evidence.get("source")).isEqualTo("HSC_EMPLOYMENT");
        assertThat(evidence.get("ref")).isEqualTo("EC***");
        assertThat(evidence.get("confidence")).isEqualTo(0.95);

        assertThat(result.get("matchStatus")).isEqualTo("MATCHED_BOTH");
        @SuppressWarnings("unchecked")
        Map<String, Object> assertion = (Map<String, Object>) result.get("trustAssertion");
        assertThat(assertion.get("status")).isEqualTo("ASSERTED");
        assertThat(assertion.get("trustLevel")).isEqualTo("EMPLOYMENT_MATCHED");
        assertThat(assertion.get("registryStatus")).isEqualTo("ACTIVE");

        // Raw EC must not leak anywhere — trust evidence or response.
        assertThat(mapper.writeValueAsString(result)).doesNotContain(RAW_EC);
        assertThat(mapper.writeValueAsString(body)).doesNotContain(RAW_EC);
    }

    @Test
    void matchedEmploymentOnlyAssertsAtConfidence075() {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("MATCHED_EMPLOYMENT_ONLY"));
        when(trustClient.assertTrust(anyString(), any()))
                .thenReturn(ProviderTrustClient.TrustCallResult.ok(200, mapper.createObjectNode()));

        service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(trustClient).assertTrust(eq(PROVIDER_ID), bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) bodyCaptor.getValue().get("evidence");
        assertThat(evidence.get("confidence")).isEqualTo(0.75);
    }

    @Test
    void matchedWithoutProviderReturnsGuidanceAndNeverCreatesOrAsserts() throws Exception {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("MATCHED_EMPLOYMENT_ONLY"));

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, null);

        assertThat(result.get("providerCreationRequired")).isEqualTo(true);
        assertThat(result).doesNotContainKey("trustAssertion");
        verify(trustClient, never()).assertTrust(anyString(), any());
        assertThat(mapper.writeValueAsString(result)).doesNotContain(RAW_EC);
    }

    @Test
    void notFoundNeverAssertsTrust() {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("NOT_FOUND"));

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        assertThat(result.get("matchStatus")).isEqualTo("NOT_FOUND");
        verify(trustClient, never()).assertTrust(anyString(), any());
    }

    @Test
    void conflictNeverAssertsTrust() {
        ObjectNode conflict = mapper.createObjectNode();
        conflict.put("matchStatus", "CONFLICT");
        conflict.put("conflictingRecordCount", 2);
        when(governanceClient.matchEmployment(any())).thenReturn(conflict);

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        assertThat(result.get("matchStatus")).isEqualTo("CONFLICT");
        assertThat(result.get("conflictingRecordCount")).isEqualTo(2);
        verify(trustClient, never()).assertTrust(anyString(), any());
    }

    @Test
    void governanceOutageDegradesToUnavailableWithoutFabrication() throws Exception {
        when(governanceClient.matchEmployment(any())).thenReturn(null);

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        assertThat(result.get("status")).isEqualTo("UNAVAILABLE");
        assertThat(result).doesNotContainKey("matchStatus");
        verify(trustClient, never()).assertTrust(anyString(), any());
        assertThat(mapper.writeValueAsString(result)).doesNotContain(RAW_EC);
    }

    @Test
    void trustRejection409IsReportedHonestly() {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("MATCHED_BOTH"));
        when(trustClient.assertTrust(anyString(), any()))
                .thenReturn(ProviderTrustClient.TrustCallResult.failed(409, null));

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> assertion = (Map<String, Object>) result.get("trustAssertion");
        assertThat(assertion.get("status")).isEqualTo("REJECTED");
        assertThat(assertion.get("statusCode")).isEqualTo(409);
    }

    @Test
    void trustOutageIsReportedAsUnavailable() {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("MATCHED_BOTH"));
        when(trustClient.assertTrust(anyString(), any()))
                .thenReturn(ProviderTrustClient.TrustCallResult.failed(0, null));

        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, RAW_EC, PROVIDER_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> assertion = (Map<String, Object>) result.get("trustAssertion");
        assertThat(assertion.get("status")).isEqualTo("UNAVAILABLE");
    }

    @Test
    void missingEcNumberIsInvalidRequest() {
        Map<String, Object> result = service().matchAndAssert(HEALTH_ID, " ", PROVIDER_ID);

        assertThat(result.get("status")).isEqualTo("INVALID_REQUEST");
        verify(governanceClient, never()).matchEmployment(any());
    }

    @Test
    void healthIdIsForwardedToGovernanceMatch() {
        when(governanceClient.matchEmployment(any())).thenReturn(matchResponse("NOT_FOUND"));

        service().matchAndAssert(HEALTH_ID, RAW_EC, null);

        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        verify(governanceClient).matchEmployment(requestCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) requestCaptor.getValue();
        assertThat(request.get("ecNumber")).isEqualTo(RAW_EC);
        assertThat(request.get("healthId")).isEqualTo(HEALTH_ID);
    }

    @Test
    void maskEcShapes() {
        assertThat(EmploymentMatchBffService.maskEc("EC123456")).isEqualTo("EC***");
        assertThat(EmploymentMatchBffService.maskEc(" E1 ")).isEqualTo("E1***");
        assertThat(EmploymentMatchBffService.maskEc(null)).isNull();
    }
}
