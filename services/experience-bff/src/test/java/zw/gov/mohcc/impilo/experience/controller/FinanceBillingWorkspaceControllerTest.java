package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceBillingWorkspaceControllerTest {

    @Mock private CostaServiceClient costaServiceClient;
    @Mock private FinancePlaneAuthorizationService authorizationService;

    private FinanceBillingWorkspaceController controller;

    @BeforeEach
    void setUp() {
        controller = new FinanceBillingWorkspaceController(costaServiceClient, authorizationService);
    }

    @Test
    void listInvoicesByEncounter_forwardsToFinanceLifecycleWithEncounterQuery() {
        when(costaServiceClient.financeLifecycleGet(contains("/invoices?encounter_id=ENC-1")))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":[]}"));

        ResponseEntity<String> response = controller.listInvoicesByEncounter("ENC-1", "req-1", "corr-1");

        verify(authorizationService).assertBillingWorkspaceAccess("GET");
        verify(costaServiceClient).financeLifecycleGet(contains("/invoices?encounter_id=ENC-1"));
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }

    @Test
    void listSubsidiesByEncounter_forwardsToFinanceLifecycleWithEncounterQuery() {
        when(costaServiceClient.financeLifecycleGet(contains("/subsidies?encounter_id=ENC-9")))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":[]}"));

        ResponseEntity<String> response = controller.listSubsidiesByEncounter("ENC-9", "req-9", "corr-9");

        verify(authorizationService).assertBillingWorkspaceAccess("GET");
        verify(costaServiceClient).financeLifecycleGet(contains("/subsidies?encounter_id=ENC-9"));
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-9", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-9", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }
}

