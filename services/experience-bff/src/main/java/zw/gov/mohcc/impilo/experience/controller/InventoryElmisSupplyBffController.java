package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.InventoryElmisAdapterClient;

/**
 * BFF proxy for {@code inventory-elmis-adapter}.
 */
@RestController
@RequestMapping("/internal/v1/inventory-elmis")
public class InventoryElmisSupplyBffController {

    private final InventoryElmisAdapterClient client;

    public InventoryElmisSupplyBffController(InventoryElmisAdapterClient client) {
        this.client = client;
    }

    @GetMapping("/sync-states")
    public ResponseEntity<JsonNode> listSyncStates() {
        return ResponseEntity.ok(client.listSyncStates());
    }

    @GetMapping("/sync-states/{id}")
    public ResponseEntity<JsonNode> getSyncState(@PathVariable long id) {
        return ResponseEntity.ok(client.getSyncState(id));
    }

    @PostMapping("/sync-states/trigger")
    public ResponseEntity<JsonNode> trigger(@RequestBody JsonNode body) {
        return ResponseEntity.ok(client.triggerSync(body));
    }
}
