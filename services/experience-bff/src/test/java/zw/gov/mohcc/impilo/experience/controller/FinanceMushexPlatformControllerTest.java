package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceMushexPlatformControllerTest {

    @Mock
    private MushexServiceClient mushexClient;
    @Mock
    private FinancePlaneAuthorizationService authorizationService;

    private FinanceMushexPlatformController controller;

    @BeforeEach
    void setUp() {
        controller = new FinanceMushexPlatformController(mushexClient, authorizationService);
    }

    @Test
    void walletById_forwardsToMushexAndPropagatesCompanionHeaders() {
        when(mushexClient.platformWalletById("WLT-1"))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"walletId\":\"WLT-1\"}}"));

        ResponseEntity<String> response = controller.walletById("WLT-1", "req-1", "corr-1");

        verify(authorizationService).assertMushexPlatformAccess("GET");
        verify(mushexClient).platformWalletById("WLT-1");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
    }

    @Test
    void remittanceById_forwardsToMushex() {
        when(mushexClient.platformRemittanceTransferById("REM-1"))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"remittanceRequestId\":\"REM-1\"}}"));

        ResponseEntity<String> response = controller.remittanceById("REM-1", "req-2", "corr-2");

        verify(authorizationService).assertMushexPlatformAccess("GET");
        verify(mushexClient).platformRemittanceTransferById("REM-1");
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void cardById_forwardsToMushex() {
        when(mushexClient.platformCardProfileById("CARD-1"))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"cardProfileId\":\"CARD-1\"}}"));

        ResponseEntity<String> response = controller.cardById("CARD-1", "req-3", "corr-3");

        verify(authorizationService).assertMushexPlatformAccess("GET");
        verify(mushexClient).platformCardProfileById("CARD-1");
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void reversalById_forwardsToMushex() {
        when(mushexClient.platformReversalById("REV-1"))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"reversalId\":\"REV-1\"}}"));

        ResponseEntity<String> response = controller.reversalById("REV-1", "req-4", "corr-4");

        verify(authorizationService).assertMushexPlatformAccess("GET");
        verify(mushexClient).platformReversalById("REV-1");
        assertEquals(200, response.getStatusCode().value());
    }
}

