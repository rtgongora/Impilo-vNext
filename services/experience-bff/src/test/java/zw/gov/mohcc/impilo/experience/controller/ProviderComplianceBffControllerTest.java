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

class ProviderComplianceBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObjectNode providerWithId(long id) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("providerId", id);
        return p;
    }

    @Test
    void openDisciplinary_resolvesNumericId_injectsIt_andAudits() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        when(varapi.getProvider("PRV-1")).thenReturn(providerWithId(42L));
        when(varapi.openDisciplinaryCase(any())).thenReturn(MAPPER.createObjectNode().put("caseId", 7));

        var controller = new ProviderComplianceBffController(varapi, audit);
        var response = controller.openDisciplinary("PRV-1", new java.util.HashMap<>(Map.of("triggerType", "COMPLAINT")),
                "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(201, response.getStatusCode().value());
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(varapi).openDisciplinaryCase(captor.capture());
        assertEquals(42L, captor.getValue().get("providerId"));
        verify(audit).emit(eq("REGISTRY_DISCIPLINARY_CASE_OPENED"), eq("actor"), any(), any(), any(),
                eq("PROVIDER"), eq("PRV-1"), eq("SUCCESS"), any());
    }

    @Test
    void complianceOp_rejectsUnsupportedOp() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ProviderRegistryAuditHelper audit = mock(ProviderRegistryAuditHelper.class);
        var controller = new ProviderComplianceBffController(varapi, audit);

        var response = controller.complianceOp(5L, "delete", Map.of(), "t", "req", "corr", "actor", "REGISTRY_GOVERNANCE");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("UNSUPPORTED_OP", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        verify(varapi, never()).complianceActionOp(anyLong(), any(), any());
    }
}
