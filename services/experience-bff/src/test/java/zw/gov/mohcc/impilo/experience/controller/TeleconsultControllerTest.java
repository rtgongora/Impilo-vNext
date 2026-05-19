package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TeleconsultControllerTest {

    private PctServiceClient pctClient;
    private TeleconsultController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pctClient = Mockito.mock(PctServiceClient.class);
        MvumoServiceClient mvumoClient = Mockito.mock(MvumoServiceClient.class);
        DocumentServiceClient documentClient = Mockito.mock(DocumentServiceClient.class);
        VarapiServiceClient varapiClient = Mockito.mock(VarapiServiceClient.class);
        TusoServiceClient tusoClient = Mockito.mock(TusoServiceClient.class);
        NotificationServiceClient notificationClient = Mockito.mock(NotificationServiceClient.class);
        FhirGatewayServiceClient fhirGatewayClient = Mockito.mock(FhirGatewayServiceClient.class);
        CostaServiceClient costaClient = Mockito.mock(CostaServiceClient.class);
        TelemedicineGovernanceService governanceService = Mockito.mock(TelemedicineGovernanceService.class);
        controller = new TeleconsultController(
                pctClient, mvumoClient, documentClient, varapiClient, tusoClient,
                notificationClient, fhirGatewayClient, costaClient, governanceService, objectMapper
        );
    }

    @Test
    void completeRejectsBreakGlassWithoutReasonAndApprover() {
        var response = controller.complete(
                "ref-001",
                "req-1",
                "corr-1",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-1",
                Map.of("breakGlassOverride", true)
        );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("BREAK_GLASS_REQUIREMENTS_MISSING", error.get("code"));
        Mockito.verifyNoInteractions(pctClient);
    }

    @Test
    void updateReferralAcceptsSpecialtyPoolRouting() {
        ObjectNode updated = objectMapper.createObjectNode();
        updated.put("id", "ref-123");
        updated.put("status", "IN_REVIEW");
        updated.put("specialty", "CARDIOLOGY");
        Mockito.when(pctClient.updateReferralStage(eq("ref-123"), any())).thenReturn(updated);

        var response = controller.updateReferral(
                "ref-123",
                "req-2",
                "corr-2",
                "tenant-a",
                "TREATMENT",
                "fac-1",
                "provider-1",
                Map.of(
                        "routingType", "SPECIALTY_POOL",
                        "routingTarget", "cardiology_pool",
                        "clinicalQuestion", "Review ECG abnormalities"
                )
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Mockito.verify(pctClient).updateReferralStage(eq("ref-123"), any());
    }
}
