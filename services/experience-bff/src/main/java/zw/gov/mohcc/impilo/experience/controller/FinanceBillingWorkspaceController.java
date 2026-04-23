package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.finance.FinancePlaneAuthorizationService;

/**
 * Proxies COSTA {@code /costa/v1/finance/lifecycle} for the finance workspace UI.
 * Tshepo synthetic path {@code /internal/v1/finance/billing-workspace} (V007).
 */
@RestController
@RequestMapping("/internal/v1/finance/billing-workspace")
public class FinanceBillingWorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(FinanceBillingWorkspaceController.class);

    private final CostaServiceClient costaClient;
    private final FinancePlaneAuthorizationService financePlaneAuthorizationService;

    public FinanceBillingWorkspaceController(CostaServiceClient costaClient,
                                             FinancePlaneAuthorizationService financePlaneAuthorizationService) {
        this.costaClient = costaClient;
        this.financePlaneAuthorizationService = financePlaneAuthorizationService;
    }

    @GetMapping("/lifecycle/invoices/{invoiceId}")
    public ResponseEntity<String> getInvoice(@PathVariable String invoiceId,
                                              @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                              @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertBillingWorkspaceAccess("GET");
        try {
            ResponseEntity<String> upstream = costaClient.financeLifecycleGet("/invoices/" + invoiceId);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Finance billing-workspace invoice fetch failed status={}", exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    @GetMapping("/lifecycle/invoices/{invoiceId}/lines")
    public ResponseEntity<String> listInvoiceLines(@PathVariable String invoiceId,
                                                    @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                    @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertBillingWorkspaceAccess("GET");
        try {
            ResponseEntity<String> upstream = costaClient.financeLifecycleGet("/invoices/" + invoiceId + "/lines");
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            return withHeaders(exception, requestId, correlationId);
        }
    }

    @GetMapping("/lifecycle/invoices/{invoiceId}/allocations")
    public ResponseEntity<String> listAllocations(@PathVariable String invoiceId,
                                                  @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                                  @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertBillingWorkspaceAccess("GET");
        try {
            ResponseEntity<String> upstream =
                    costaClient.financeLifecycleGet("/invoices/" + invoiceId + "/allocations");
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            return withHeaders(exception, requestId, correlationId);
        }
    }

    @PostMapping("/lifecycle/charges")
    public ResponseEntity<String> createCharge(@RequestBody(required = false) String body,
                                               @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                               @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertBillingWorkspaceAccess("POST");
        try {
            ResponseEntity<String> upstream = costaClient.financeLifecyclePost("/charges", body);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            return withHeaders(exception, requestId, correlationId);
        }
    }

    @GetMapping("/lifecycle/charges")
    public ResponseEntity<String> listCharges(@RequestParam String billId,
                                              @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                              @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        financePlaneAuthorizationService.assertBillingWorkspaceAccess("GET");
        String path = UriComponentsBuilder.fromPath("/charges")
                .queryParam("billId", billId)
                .build()
                .toUriString();
        try {
            ResponseEntity<String> upstream = costaClient.financeLifecycleGet(path);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
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
