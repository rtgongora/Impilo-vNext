package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.InventoryServiceClient;

import java.util.UUID;

/**
 * BFF proxy for the Dura sovereign stock brain ({@code inventory-service}
 * {@code /v1/dura/**}). Surfaces commodity catalogue, near-expiry batches,
 * recalls, cold-chain excursions, and PCT stock availability to the experience
 * shell under {@code /internal/v1/dura/**}.
 */
@RestController
@RequestMapping("/internal/v1/dura")
public class DuraBffController {

    private final InventoryServiceClient inventory;

    public DuraBffController(InventoryServiceClient inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/categories")
    public ResponseEntity<JsonNode> categories(@RequestParam(required = false) String programmeArea) {
        return ResponseEntity.ok(inventory.duraCategories(programmeArea));
    }

    @GetMapping("/commodities")
    public ResponseEntity<JsonNode> commodities(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String programmeArea,
            @RequestParam(required = false) Boolean controlled,
            @RequestParam(required = false) Boolean coldChain) {
        return ResponseEntity.ok(inventory.duraCommodities(q, programmeArea, controlled, coldChain));
    }

    @GetMapping("/batches/near-expiry")
    public ResponseEntity<JsonNode> nearExpiry(@RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(inventory.duraNearExpiryBatches(days));
    }

    @GetMapping("/recalls")
    public ResponseEntity<JsonNode> recalls(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(inventory.duraRecalls(status));
    }

    @PostMapping("/recalls")
    public ResponseEntity<JsonNode> createRecall(@RequestBody JsonNode body) {
        return ResponseEntity.ok(inventory.duraCreateRecall(body));
    }

    @GetMapping("/cold-chain/excursions")
    public ResponseEntity<JsonNode> coldChainExcursions(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(inventory.duraColdChainExcursions(status));
    }

    /**
     * eLMIS/NatPharm external sync states (read-only). Backed by the real
     * Dura sync state machine; an empty list is an honest "no sync activity"
     * answer, not a failure.
     */
    @GetMapping("/external-sync")
    public ResponseEntity<JsonNode> externalSync(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(inventory.duraExternalSyncStates(status));
    }

    @GetMapping("/pct/availability")
    public ResponseEntity<JsonNode> pctAvailability(
            @RequestParam UUID facilityId,
            @RequestParam UUID storeId,
            @RequestParam String itemCode) {
        return ResponseEntity.ok(inventory.duraPctAvailability(facilityId, storeId, itemCode));
    }
}
