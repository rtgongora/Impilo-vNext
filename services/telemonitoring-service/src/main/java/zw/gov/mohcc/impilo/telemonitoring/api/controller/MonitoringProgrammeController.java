package zw.gov.mohcc.impilo.telemonitoring.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.CreateProgrammeRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.ProgrammeResponse;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.RetireProgrammeRequest;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.UpdateProgrammeRequest;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringProgrammeService;
import zw.gov.mohcc.impilo.telemonitoring.core.TelemonitoringDomainException;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.util.List;
import java.util.UUID;

/**
 * Programme profile catalogue (§14.1): reads for every consumer, governance writes for the
 * clinical-governance function (OF-B21). Governance semantics: profiles carry versioned
 * metadata and effective windows; every update bumps the governance version; retirement
 * deactivates but NEVER deletes — the row (and every plan referencing it) survives.
 */
@RestController
@RequestMapping("/v1/monitoring-programmes")
public class MonitoringProgrammeController {

    private final MonitoringProgrammeRepository programmeRepository;
    private final MonitoringProgrammeService programmeService;

    public MonitoringProgrammeController(MonitoringProgrammeRepository programmeRepository,
                                         MonitoringProgrammeService programmeService) {
        this.programmeRepository = programmeRepository;
        this.programmeService = programmeService;
    }

    @GetMapping
    public List<ProgrammeResponse> listActive() {
        return programmeRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(ProgrammeResponse::from)
                .toList();
    }

    @GetMapping("/{code}")
    public ProgrammeResponse byCode(@PathVariable String code) {
        return programmeRepository.findByCode(code)
                .map(ProgrammeResponse::from)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_UNKNOWN_PROGRAMME", 404,
                        "Unknown monitoring programme: " + code));
    }

    // ── Governance writes (OF-B21) ──

    @PostMapping
    public ResponseEntity<ProgrammeResponse> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody CreateProgrammeRequest request) {
        var programme = programmeService.create(new MonitoringProgrammeService.CreateProgrammeCommand(
                tenantId,
                request.code(),
                request.name(),
                request.description(),
                request.conditionCode(),
                request.defaultThresholdsJson(),
                request.metadataJson(),
                request.effectiveFrom(),
                request.effectiveTo(),
                actor(request.actor(), actorId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProgrammeResponse.from(programme));
    }

    @PutMapping("/{code}")
    public ProgrammeResponse update(
            @PathVariable String code,
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody UpdateProgrammeRequest request) {
        return ProgrammeResponse.from(programmeService.update(code,
                new MonitoringProgrammeService.UpdateProgrammeCommand(
                        tenantId,
                        request.name(),
                        request.description(),
                        request.conditionCode(),
                        request.defaultThresholdsJson(),
                        request.metadataJson(),
                        request.effectiveFrom(),
                        request.effectiveTo(),
                        request.active(),
                        actor(request.actor(), actorId))));
    }

    /** Retire never deletes: the profile row stays for referential and audit truth. */
    @PostMapping("/{code}/retire")
    public ProgrammeResponse retire(
            @PathVariable String code,
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody RetireProgrammeRequest request) {
        return ProgrammeResponse.from(programmeService.retire(
                tenantId, code, request.reason(), actor(request.actor(), actorId)));
    }

    private static String actor(String bodyActor, String headerActor) {
        return bodyActor != null && !bodyActor.isBlank() ? bodyActor : headerActor;
    }
}
