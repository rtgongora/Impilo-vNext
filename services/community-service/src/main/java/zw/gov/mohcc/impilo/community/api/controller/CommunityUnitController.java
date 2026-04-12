package zw.gov.mohcc.impilo.community.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.community.api.dto.CreateCommunityUnitRequest;
import zw.gov.mohcc.impilo.community.core.CommunityUnitService;
import zw.gov.mohcc.impilo.community.persistence.entity.CommunityUnitEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/community/units")
public class CommunityUnitController {

    private final CommunityUnitService communityUnitService;

    public CommunityUnitController(CommunityUnitService communityUnitService) {
        this.communityUnitService = communityUnitService;
    }

    @PostMapping
    public ResponseEntity<CommunityUnitEntity> createUnit(@Valid @RequestBody CreateCommunityUnitRequest request) {
        CommunityUnitEntity created = communityUnitService.createUnit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<CommunityUnitEntity>> listUnits(@RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(communityUnitService.listUnits(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommunityUnitEntity> getUnit(@PathVariable("id") UUID unitId) {
        return ResponseEntity.ok(communityUnitService.getUnit(unitId));
    }
}
