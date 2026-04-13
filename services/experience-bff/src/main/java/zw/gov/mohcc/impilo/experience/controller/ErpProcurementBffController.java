package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.ProcurementServiceClient;

@RestController
@RequestMapping("/internal/v1/erp/procurement")
public class ErpProcurementBffController {

    private final ProcurementServiceClient client;

    public ErpProcurementBffController(ProcurementServiceClient client) {
        this.client = client;
    }

    @GetMapping("/suppliers")
    public ResponseEntity<JsonNode> suppliers() {
        return ResponseEntity.ok(client.getSuppliers());
    }

    @GetMapping("/requisitions")
    public ResponseEntity<JsonNode> requisitions() {
        return ResponseEntity.ok(client.getRequisitions());
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<JsonNode> pos() {
        return ResponseEntity.ok(client.getPurchaseOrders());
    }

    @GetMapping("/grn")
    public ResponseEntity<JsonNode> grn() {
        return ResponseEntity.ok(client.getGrn());
    }

    @PostMapping("/grn")
    public ResponseEntity<JsonNode> postGrn(@RequestBody JsonNode body) {
        return ResponseEntity.ok(client.postGrn(body));
    }

    @GetMapping("/invoices")
    public ResponseEntity<JsonNode> invoices() {
        return ResponseEntity.ok(client.getInvoices());
    }

    @GetMapping("/rfqs")
    public ResponseEntity<JsonNode> rfqs() {
        return ResponseEntity.ok(client.getRfqs());
    }

    @GetMapping("/rfqs/{id}/responses")
    public ResponseEntity<JsonNode> rfqResponses(@PathVariable String id) {
        return ResponseEntity.ok(client.getRfqResponses(id));
    }
}
