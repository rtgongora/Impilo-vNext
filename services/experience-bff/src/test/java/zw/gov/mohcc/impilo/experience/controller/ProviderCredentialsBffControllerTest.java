package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.providerregistry.ProviderRegistryAuditHelper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderCredentialsBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObjectNode providerWithId(long id) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("providerId", id);
        return p;
    }

    @Test
    void createQualification_resolvesNumericId_injectsIt_andAudits() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        when(varapi.getProvider("PRV-1")).thenReturn(providerWithId(42L));
        when(varapi.createQualification(any())).thenReturn(MAPPER.createObjectNode().put("id", 7));

        var controller = new ProviderCredentialsBffController(varapi, audit);
        var response = controller.createQualification("PRV-1",
                new java.util.HashMap<>(Map.of("qualificationName", "MBChB")),
                "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(201, response.getStatusCode().value());
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(varapi).createQualification(captor.capture());
        assertEquals(42L, captor.getValue().get("providerId"));
        verify(audit).emit(eq("REGISTRY_QUALIFICATION_SUBMITTED"), eq("actor"), any(), any(), any(),
                eq("PROVIDER"), eq("PRV-1"), eq("SUCCESS"), any());
    }

    @Test
    void practiceContextOp_rejectsUnsupportedOp() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        var controller = new ProviderCredentialsBffController(varapi, audit);

        var response = controller.practiceContextOp(5L, "delete", Map.of(),
                "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("UNSUPPORTED_OP", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        verify(varapi, never()).practiceContextOp(anyLong(), any(), any());
    }

    @Test
    void verifyQualification_passesThrough_andAudits() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        when(varapi.verifyQualification(anyLong(), any())).thenReturn(MAPPER.createObjectNode().put("verificationStatus", "VERIFIED"));

        var controller = new ProviderCredentialsBffController(varapi, audit);
        var response = controller.verifyQualification(7L,
                new java.util.HashMap<>(Map.of("verificationStatus", "VERIFIED")),
                "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(200, response.getStatusCode().value());
        verify(varapi).verifyQualification(eq(7L), any());
        verify(audit).emit(eq("REGISTRY_QUALIFICATION_VERIFIED"), eq("actor"), any(), any(), any(),
                eq("PROVIDER_QUALIFICATION"), eq("7"), eq("SUCCESS"), any());
    }
}
