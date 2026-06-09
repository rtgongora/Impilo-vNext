package zw.gov.mohcc.impilo.inpatient.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.api.dto.AddWardRoundEntryRequest;
import zw.gov.mohcc.impilo.inpatient.api.dto.StartWardRoundRequest;
import zw.gov.mohcc.impilo.inpatient.core.WardRoundService;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardRoundEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardRoundEntryEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
public class WardRoundController {

    private final WardRoundService wardRoundService;

    public WardRoundController(WardRoundService wardRoundService) {
        this.wardRoundService = wardRoundService;
    }

    @PostMapping("/ward-rounds")
    public ResponseEntity<WardRoundEntity> startWardRound(@Valid @RequestBody StartWardRoundRequest request) {
        WardRoundEntity round = wardRoundService.startRound(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(round);
    }

    @PostMapping("/ward-rounds/{roundId}/entries")
    public ResponseEntity<WardRoundEntryEntity> addRoundEntry(
            @PathVariable UUID roundId,
            @Valid @RequestBody AddWardRoundEntryRequest request) {
        WardRoundEntryEntity entry = wardRoundService.addEntry(roundId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @GetMapping("/ward-rounds")
    public ResponseEntity<List<WardRoundEntity>> listWardRounds(@RequestParam UUID wardId) {
        return ResponseEntity.ok(wardRoundService.listByWard(wardId));
    }

    @GetMapping("/admissions/{admissionRef}/ward-rounds")
    public ResponseEntity<List<WardRoundEntity>> listWardRoundsForAdmission(@PathVariable UUID admissionRef) {
        return ResponseEntity.ok(wardRoundService.listByAdmission(admissionRef));
    }
}
