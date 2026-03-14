package zw.gov.mohcc.impilo.offlinesync.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.offlinesync.api.dto.CreateSyncPackRequest;
import zw.gov.mohcc.impilo.offlinesync.core.SyncPackService;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.SyncPackEntity;

import java.util.List;

/**
 * REST controller for sync pack operations: list, get, create, and replay.
 */
@RestController
@RequestMapping("/internal/v1/sync-packs")
public class SyncPackController {

    private final SyncPackService syncPackService;

    public SyncPackController(SyncPackService syncPackService) {
        this.syncPackService = syncPackService;
    }

    @GetMapping
    public ResponseEntity<List<SyncPackEntity>> listSyncPacks() {
        return ResponseEntity.ok(syncPackService.listSyncPacks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SyncPackEntity> getSyncPack(@PathVariable Long id) {
        return ResponseEntity.ok(syncPackService.getSyncPack(id));
    }

    @PostMapping
    public ResponseEntity<SyncPackEntity> createSyncPack(@Valid @RequestBody CreateSyncPackRequest request) {
        SyncPackEntity created = syncPackService.createSyncPack(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<SyncPackEntity> replaySyncPack(@PathVariable Long id) {
        SyncPackEntity replayed = syncPackService.replaySyncPack(id);
        return ResponseEntity.ok(replayed);
    }
}
