package zw.gov.mohcc.impilo.community.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.community.api.dto.CompleteVisitRequest;
import zw.gov.mohcc.impilo.community.api.dto.CreateOutreachVisitRequest;
import zw.gov.mohcc.impilo.community.core.OutreachVisitService;
import zw.gov.mohcc.impilo.community.persistence.entity.OutreachVisitEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/community/visits")
public class OutreachVisitController {

    private final OutreachVisitService outreachVisitService;

    public OutreachVisitController(OutreachVisitService outreachVisitService) {
        this.outreachVisitService = outreachVisitService;
    }

    @PostMapping
    public ResponseEntity<OutreachVisitEntity> createVisit(@Valid @RequestBody CreateOutreachVisitRequest request) {
        OutreachVisitEntity created = outreachVisitService.createVisit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<OutreachVisitEntity>> listVisits(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID unitId) {
        return ResponseEntity.ok(outreachVisitService.listVisits(tenantId, unitId));
    }

    @PutMapping("/{id}/start")
    public ResponseEntity<OutreachVisitEntity> startVisit(@PathVariable("id") UUID visitId) {
        return ResponseEntity.ok(outreachVisitService.startVisit(visitId));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<OutreachVisitEntity> completeVisit(
            @PathVariable("id") UUID visitId,
            @RequestBody(required = false) CompleteVisitRequest body) {
        return ResponseEntity.ok(outreachVisitService.completeVisit(visitId, body));
    }
}
