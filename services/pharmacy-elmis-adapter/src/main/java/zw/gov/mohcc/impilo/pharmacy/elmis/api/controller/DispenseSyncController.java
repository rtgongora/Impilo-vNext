package zw.gov.mohcc.impilo.pharmacy.elmis.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pharmacy.elmis.api.dto.TriggerSyncRequest;
import zw.gov.mohcc.impilo.pharmacy.elmis.core.DispenseSyncService;
import zw.gov.mohcc.impilo.pharmacy.elmis.persistence.entity.DispenseSyncEntity;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing dispense synchronization runs.
 */
@RestController
@RequestMapping("/internal/v1/dispense-sync")
public class DispenseSyncController {

    private final DispenseSyncService syncService;

    public DispenseSyncController(DispenseSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping
    public ResponseEntity<List<DispenseSyncEntity>> listAll() {
        return ResponseEntity.ok(syncService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DispenseSyncEntity> getById(@PathVariable Long id) {
        return ResponseEntity.ok(syncService.findById(id));
    }

    @PostMapping("/trigger")
    public ResponseEntity<DispenseSyncEntity> trigger(@Valid @RequestBody TriggerSyncRequest request) {
        var ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        var entity = syncService.triggerSync(tenantId, request.getFacilityId(), request.getSyncType());
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }
}
