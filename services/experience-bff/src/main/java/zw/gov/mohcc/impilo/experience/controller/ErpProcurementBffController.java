package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ProcurementServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/erp/procurement")
public class ErpProcurementBffController {

    private final ProcurementServiceClient client;

    public ErpProcurementBffController(ProcurementServiceClient client) {
        this.client = client;
    }

    @GetMapping("/suppliers")
    public ResponseEntity<?> suppliers(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                       @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getSuppliers());
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch suppliers", requestId, correlationId);
        }
    }

    @GetMapping("/requisitions")
    public ResponseEntity<?> requisitions(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                          @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getRequisitions());
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch requisitions", requestId, correlationId);
        }
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<?> pos(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                 @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getPurchaseOrders());
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch purchase orders", requestId, correlationId);
        }
    }

    @GetMapping("/grn")
    public ResponseEntity<?> grn(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                 @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getGrn());
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch GRN entries", requestId, correlationId);
        }
    }

    @PostMapping("/grn")
    public ResponseEntity<?> postGrn(@RequestBody JsonNode body,
                                     @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                     @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postGrn(body));
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to create GRN entry", requestId, correlationId);
        }
    }

    @GetMapping("/invoices")
    public ResponseEntity<?> invoices(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                      @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getInvoices());
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch invoices", requestId, correlationId);
        }
    }

    @PostMapping("/invoices/{invoiceId}/match")
    public ResponseEntity<?> matchInvoice(@PathVariable String invoiceId,
                                          @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                          @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postInvoiceMatch(invoiceId));
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to match invoice", requestId, correlationId);
        }
    }

    @PostMapping("/invoices/{invoiceId}/pay")
    public ResponseEntity<?> payInvoice(@PathVariable String invoiceId,
                                        @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                        @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.postInvoicePay(invoiceId));
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to pay invoice", requestId, correlationId);
        }
    }

    @GetMapping("/rfqs")
    public ResponseEntity<?> rfqs(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                  @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getRfqs());
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch RFQs", requestId, correlationId);
        }
    }

    @GetMapping("/rfqs/{id}/responses")
    public ResponseEntity<?> rfqResponses(@PathVariable String id,
                                          @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
                                          @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(client.getRfqResponses(id));
        } catch (Exception e) {
            return failClose("PROCUREMENT_UNAVAILABLE", "Unable to fetch RFQ responses", requestId, correlationId);
        }
    }

    private static ResponseEntity<Map<String, Object>> failClose(String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
