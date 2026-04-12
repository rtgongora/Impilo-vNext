package zw.gov.mohcc.impilo.community.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.community.api.dto.CreateChwAssignmentRequest;
import zw.gov.mohcc.impilo.community.core.ChwAssignmentService;
import zw.gov.mohcc.impilo.community.persistence.entity.ChwAssignmentEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/community/assignments")
public class ChwAssignmentController {

    private final ChwAssignmentService chwAssignmentService;

    public ChwAssignmentController(ChwAssignmentService chwAssignmentService) {
        this.chwAssignmentService = chwAssignmentService;
    }

    @PostMapping
    public ResponseEntity<ChwAssignmentEntity> createAssignment(
            @Valid @RequestBody CreateChwAssignmentRequest request) {
        ChwAssignmentEntity created = chwAssignmentService.createAssignment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ChwAssignmentEntity>> listAssignments(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID unitId) {
        return ResponseEntity.ok(chwAssignmentService.listAssignments(tenantId, unitId));
    }
}
