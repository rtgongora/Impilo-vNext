package zw.gov.mohcc.impilo.inventory.elmis.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.elmis.api.dto.TriggerSyncRequest;
import zw.gov.mohcc.impilo.inventory.elmis.core.SyncStateService;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.entity.SyncStateEntity;

import java.util.List;

/**
 * REST controller for managing eLMIS sync state.
 */
@RestController
@RequestMapping("/internal/v1/sync-states")
public class SyncStateController {

    private final SyncStateService syncStateService;

    public SyncStateController(SyncStateService syncStateService) {
        this.syncStateService = syncStateService;
    }

    @GetMapping
    public ResponseEntity<List<SyncStateEntity>> listAll() {
        return ResponseEntity.ok(syncStateService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SyncStateEntity> getById(@PathVariable Long id) {
        return ResponseEntity.ok(syncStateService.findById(id));
    }

    @PostMapping("/trigger")
    public ResponseEntity<SyncStateEntity> triggerSync(@Valid @RequestBody TriggerSyncRequest request) {
        var entity = syncStateService.triggerSync(
                request.getTenantId(),
                request.getFacilityId(),
                request.getSyncType()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(entity);
    }
}
