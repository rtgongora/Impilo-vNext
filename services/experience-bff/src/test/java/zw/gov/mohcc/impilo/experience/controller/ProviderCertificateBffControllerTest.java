package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
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

class ProviderCertificateBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listForProvider_resolvesPublicIdToNumericAndQueries() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        ObjectNode provider = MAPPER.createObjectNode();
        provider.put("providerId", 42L);
        when(varapi.getProvider("PRV-1")).thenReturn(provider);
        when(varapi.getCertificatesByProvider(42L)).thenReturn(MAPPER.createArrayNode());

        var controller = new ProviderCertificateBffController(varapi, audit);
        var response = controller.listForProvider("PRV-1", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        verify(varapi).getCertificatesByProvider(42L);
    }

    @Test
    void suspend_requiresReason() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        var controller = new ProviderCertificateBffController(varapi, audit);

        var response = controller.act(7L, "suspend", Map.of(), "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("REASON_REQUIRED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        verify(varapi, never()).certificateAction(anyLong(), any(), any());
    }

    @Test
    void revoke_withReason_callsEngineAndAudits() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        when(varapi.certificateAction(eq(7L), eq("revoke"), any())).thenReturn(MAPPER.createObjectNode());
        var controller = new ProviderCertificateBffController(varapi, audit);

        var response = controller.act(7L, "revoke", Map.of("reason", "Sanction"), "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(200, response.getStatusCode().value());
        verify(varapi).certificateAction(7L, "revoke", Map.of("reason", "Sanction"));
        verify(audit).emit(eq("REGISTRY_CERTIFICATE_REVOKE"), eq("actor"), any(), any(), any(),
                eq("PROVIDER_CERTIFICATE"), eq("7"), eq("SUCCESS"), any());
    }

    @Test
    void unsupportedAction_isRejected() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        var controller = new ProviderCertificateBffController(varapi, audit);

        var response = controller.act(7L, "delete", Map.of(), "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("UNSUPPORTED_ACTION", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        verify(varapi, never()).certificateAction(anyLong(), any(), any());
    }
}
