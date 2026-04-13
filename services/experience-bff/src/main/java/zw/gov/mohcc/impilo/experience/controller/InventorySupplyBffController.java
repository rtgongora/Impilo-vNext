package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.InventoryServiceClient;

import java.util.UUID;

/**
 * BFF proxy for {@code inventory-service} sovereign APIs.
 */
@RestController
@RequestMapping("/internal/v1/inventory")
public class InventorySupplyBffController {

    private final InventoryServiceClient inventory;

    public InventorySupplyBffController(InventoryServiceClient inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/on-hand")
    public ResponseEntity<JsonNode> onHand(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID binId,
            @RequestParam(required = false) String itemCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventory.getOnHand(facilityId, storeId, binId, itemCode, page, size));
    }

    @GetMapping("/on-hand/near-expiry")
    public ResponseEntity<JsonNode> nearExpiry(
            @RequestParam UUID facilityId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(inventory.getNearExpiry(facilityId, days));
    }

    @GetMapping("/on-hand/stockouts")
    public ResponseEntity<JsonNode> stockouts(@RequestParam UUID facilityId) {
        return ResponseEntity.ok(inventory.getStockouts(facilityId));
    }

    @GetMapping("/ledger")
    public ResponseEntity<JsonNode> ledger(
            @RequestParam UUID facilityId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String itemCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventory.getLedger(facilityId, storeId, itemCode, page, size));
    }

    @PostMapping("/ledger/receipt")
    public ResponseEntity<JsonNode> ledgerReceipt(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.postLedger("receipt", body));
    }

    @PostMapping("/ledger/issue")
    public ResponseEntity<JsonNode> ledgerIssue(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.postLedger("issue", body));
    }

    @PostMapping("/ledger/transfer")
    public ResponseEntity<JsonNode> ledgerTransfer(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.postLedger("transfer", body));
    }

    @PostMapping("/ledger/adjust")
    public ResponseEntity<JsonNode> ledgerAdjust(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.postLedger("adjust", body));
    }

    @GetMapping("/items/{itemCode}")
    public ResponseEntity<JsonNode> getItem(@PathVariable String itemCode) {
        return ResponseEntity.ok(inventory.getItem(itemCode));
    }

    @GetMapping("/items/lookup-by-barcode")
    public ResponseEntity<JsonNode> lookupBarcode(@RequestParam String code) {
        return ResponseEntity.ok(inventory.lookupBarcode(code));
    }

    @PostMapping("/items")
    public ResponseEntity<JsonNode> createItem(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.createItem(body));
    }

    @GetMapping("/reconcile/pending")
    public ResponseEntity<JsonNode> reconcilePending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventory.getReconcilePending(page, size));
    }

    @PostMapping("/reconcile/{id}/resolve")
    public ResponseEntity<JsonNode> resolveReconcile(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.resolveReconcile(id, body));
    }

    @PostMapping("/counts")
    public ResponseEntity<JsonNode> createCount(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.createCountSession(body));
    }

    @PostMapping("/counts/{id}/start")
    public ResponseEntity<JsonNode> startCount(@PathVariable UUID id) {
        return ResponseEntity.ok(inventory.startCount(id));
    }

    @PostMapping("/counts/{sessionId}/lines/{lineId}")
    public ResponseEntity<JsonNode> updateCountLine(
            @PathVariable UUID sessionId,
            @PathVariable UUID lineId,
            @RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.updateCountLine(sessionId, lineId, body));
    }

    @PostMapping("/counts/{id}/submit")
    public ResponseEntity<JsonNode> submitCount(@PathVariable UUID id) {
        return ResponseEntity.ok(inventory.submitCount(id));
    }

    @PostMapping("/counts/{id}/approve")
    public ResponseEntity<JsonNode> approveCount(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.ok(inventory.approveCount(id, body));
    }

    @PostMapping("/counts/{id}/reject")
    public ResponseEntity<JsonNode> rejectCount(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.ok(inventory.rejectCount(id, body));
    }

    @GetMapping("/counts/{id}")
    public ResponseEntity<JsonNode> getCount(@PathVariable UUID id) {
        return ResponseEntity.ok(inventory.getCountSession(id));
    }

    // createRequisition removed — handled by InventoryController#createRequisition
    // which uses a typed CreateRequisitionRequest and companion headers.

    @PostMapping("/requisitions/{id}/submit")
    public ResponseEntity<JsonNode> submitRequisition(@PathVariable UUID id) {
        return ResponseEntity.ok(inventory.submitRequisition(id));
    }

    @PostMapping("/requisitions/{id}/approve")
    public ResponseEntity<JsonNode> approveRequisition(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.approveRequisition(id, body));
    }

    @PostMapping("/requisitions/{id}/reject")
    public ResponseEntity<JsonNode> rejectRequisition(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.rejectRequisition(id, body));
    }

    @PostMapping("/requisitions/{id}/fulfill")
    public ResponseEntity<JsonNode> fulfillRequisition(@PathVariable UUID id, @RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.fulfillRequisition(id, body));
    }
}
