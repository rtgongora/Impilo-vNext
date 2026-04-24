package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

/**
 * Proxies MusheX custodial wallet, remittance, card profile, and reversal platform APIs.
 * Tshepo synthetic path {@code /internal/v1/finance/mushex-platform} (V007).
 */
@RestController
@RequestMapping("/internal/v1/finance/mushex-platform")
public class FinanceMushexPlatformController {

    private static final Logger log = LoggerFactory.getLogger(FinanceMushexPlatformController.class);

    private final MushexServiceClient mushexClient;
    private final FinancePlaneAuthorizationService financePlaneAuthorizationService;

    public FinanceMushexPlatformController(MushexServiceClient mushexClient,
                                             FinancePlaneAuthorizationService financePlaneAuthorizationService) {
        this.mushexClient = mushexClient;
        this.financePlaneAuthorizationService = financePlaneAuthorizationService;
    }

    @GetMapping("/wallets")
    public ResponseEntity<String> listWallets(@RequestParam MultiValueMap<String, String> queryParams,
                                               @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                               @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("GET");
        return invoke(() -> mushexClient.platformListWallets(new LinkedMultiValueMap<>(queryParams)),
                requestId, correlationId);
    }

    @PostMapping("/wallets")
    public ResponseEntity<String> createWallet(@RequestBody(required = false) String body,
                                                @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("POST");
        return invoke(() -> mushexClient.platformCreateWallet(body != null ? body : "{}"),
                requestId, correlationId);
    }

    @PostMapping("/wallets/{walletId}/credit")
    public ResponseEntity<String> creditWallet(@PathVariable String walletId,
                                                @RequestBody(required = false) String body,
                                                @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("POST");
        return invoke(() -> mushexClient.platformWalletCredit(walletId, body != null ? body : "{}"),
                requestId, correlationId);
    }

    @PostMapping("/wallets/{walletId}/debit")
    public ResponseEntity<String> debitWallet(@PathVariable String walletId,
                                               @RequestBody(required = false) String body,
                                               @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                               @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("POST");
        return invoke(() -> mushexClient.platformWalletDebit(walletId, body != null ? body : "{}"),
                requestId, correlationId);
    }

    @GetMapping("/wallets/{walletId}/transactions")
    public ResponseEntity<String> walletTransactions(@PathVariable String walletId,
                                                     @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                     @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("GET");
        return invoke(() -> mushexClient.platformWalletTransactions(walletId), requestId, correlationId);
    }

    @GetMapping("/remittance-transfers")
    public ResponseEntity<String> listRemittance(@RequestParam MultiValueMap<String, String> queryParams,
                                                @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("GET");
        return invoke(() -> mushexClient.platformListRemittanceTransfers(new LinkedMultiValueMap<>(queryParams)),
                requestId, correlationId);
    }

    @PostMapping("/remittance-transfers")
    public ResponseEntity<String> createRemittance(@RequestBody(required = false) String body,
                                                    @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                    @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("POST");
        return invoke(() -> mushexClient.platformCreateRemittanceTransfer(body != null ? body : "{}"),
                requestId, correlationId);
    }

    @GetMapping("/card-profiles")
    public ResponseEntity<String> listCards(@RequestParam MultiValueMap<String, String> queryParams,
                                            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("GET");
        return invoke(() -> mushexClient.platformListCardProfiles(new LinkedMultiValueMap<>(queryParams)),
                requestId, correlationId);
    }

    @PostMapping("/card-profiles")
    public ResponseEntity<String> createCard(@RequestBody(required = false) String body,
                                             @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                             @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("POST");
        return invoke(() -> mushexClient.platformCreateCardProfile(body != null ? body : "{}"),
                requestId, correlationId);
    }

    @GetMapping("/reversals")
    public ResponseEntity<String> listReversals(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("GET");
        return invoke(mushexClient::platformListReversals, requestId, correlationId);
    }

    @PostMapping("/reversals")
    public ResponseEntity<String> createReversal(@RequestBody(required = false) String body,
                                                 @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                 @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertMushexPlatformAccess("POST");
        return invoke(() -> mushexClient.platformCreateReversal(body != null ? body : "{}"),
                requestId, correlationId);
    }

    private ResponseEntity<String> invoke(java.util.function.Supplier<ResponseEntity<String>> call,
                                          String requestId, String correlationId) {
        try {
            return withHeaders(call.get(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Finance mushex-platform proxy failed status={}", exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null
                ? upstream.getResponseHeaders().getContentType()
                : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }
}
