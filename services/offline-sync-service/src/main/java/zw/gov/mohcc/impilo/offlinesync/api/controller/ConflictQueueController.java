package zw.gov.mohcc.impilo.offlinesync.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.offlinesync.api.dto.ResolveConflictRequest;
import zw.gov.mohcc.impilo.offlinesync.core.ConflictService;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.ConflictQueueEntity;

import java.util.List;

/**
 * REST controller for conflict queue operations: list and resolve.
 */
@RestController
@RequestMapping("/internal/v1/conflicts")
public class ConflictQueueController {

    private final ConflictService conflictService;

    public ConflictQueueController(ConflictService conflictService) {
        this.conflictService = conflictService;
    }

    @GetMapping
    public ResponseEntity<List<ConflictQueueEntity>> listConflicts() {
        return ResponseEntity.ok(conflictService.listConflicts());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ConflictQueueEntity> resolveConflict(
            @PathVariable Long id,
            @Valid @RequestBody ResolveConflictRequest request) {
        ConflictQueueEntity resolved = conflictService.resolveConflict(id, request);
        return ResponseEntity.ok(resolved);
    }
}
