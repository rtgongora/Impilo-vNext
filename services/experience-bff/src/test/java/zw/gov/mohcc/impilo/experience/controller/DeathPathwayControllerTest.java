package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.UbomiServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** BFF facade composes pct-service (death/cert/mortuary) + ubomi-service (CRVS); never owns truth. */
class DeathPathwayControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private DeathPathwayController controller(PctServiceClient pct, UbomiServiceClient ubomi) {
        return new DeathPathwayController(pct, ubomi);
    }

    @Test
    void confirm_delegatesToPctAndReturns201() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UbomiServiceClient ubomi = mock(UbomiServiceClient.class);
        ObjectNode created = mapper.createObjectNode();
        created.put("caseId", "dc-1");
        created.put("coronerReferralRequired", false);
        when(pct.confirmDeath(any())).thenReturn(created);

        var resp = controller(pct, ubomi).confirm(Map.of("deathDatetime", "2026-06-30T10:00:00Z"));
        assertEquals(201, resp.getStatusCode().value());
        assertEquals("dc-1", resp.getBody().get("caseId").asText());
        verify(pct).confirmDeath(any());
    }

    @Test
    void listCases_delegatesToPct() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UbomiServiceClient ubomi = mock(UbomiServiceClient.class);
        when(pct.listDeathCases()).thenReturn(mapper.createArrayNode());
        var resp = controller(pct, ubomi).listCases();
        assertEquals(200, resp.getStatusCode().value());
        verify(pct).listDeathCases();
    }

    @Test
    void certify_delegatesToPct() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UbomiServiceClient ubomi = mock(UbomiServiceClient.class);
        ObjectNode signed = mapper.createObjectNode();
        signed.put("certificationStatus", "SIGNED");
        when(pct.certifyDeathCause(eq("dc-1"), any())).thenReturn(signed);
        var resp = controller(pct, ubomi).certify("dc-1", Map.of("immediateCause", "Sepsis", "certifierLicence", "L1", "sign", true));
        assertEquals("SIGNED", resp.getBody().get("certificationStatus").asText());
        verify(pct).certifyDeathCause(eq("dc-1"), any());
    }

    @Test
    void releaseBody_delegatesToPct() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UbomiServiceClient ubomi = mock(UbomiServiceClient.class);
        ObjectNode released = mapper.createObjectNode();
        released.put("status", "RELEASED");
        when(pct.releaseDeathBody(eq("dc-1"), any())).thenReturn(released);
        var resp = controller(pct, ubomi).releaseBody("dc-1", Map.of("releasedToName", "Jane"));
        assertEquals("RELEASED", resp.getBody().get("status").asText());
        verify(pct).releaseDeathBody(eq("dc-1"), any());
    }

    @Test
    void crvsRegister_delegatesToUbomi() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UbomiServiceClient ubomi = mock(UbomiServiceClient.class);
        ObjectNode reg = mapper.createObjectNode();
        reg.put("status", "REGISTERED");
        when(ubomi.registerDeath(eq(7L), any())).thenReturn(reg);
        var resp = controller(pct, ubomi).crvsRegister(7L, Map.of("civilRegNumber", "CR-1"));
        assertEquals("REGISTERED", resp.getBody().get("status").asText());
        verify(ubomi).registerDeath(eq(7L), any());
    }

    @Test
    void crvsPackageReady_delegatesToUbomi() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UbomiServiceClient ubomi = mock(UbomiServiceClient.class);
        ObjectNode pkg = mapper.createObjectNode();
        pkg.put("packageReady", true);
        when(ubomi.markDeathPackageReady(7L)).thenReturn(pkg);
        var resp = controller(pct, ubomi).crvsPackageReady(7L);
        assertTrue(resp.getBody().get("packageReady").asBoolean());
        verify(ubomi).markDeathPackageReady(7L);
    }
}
