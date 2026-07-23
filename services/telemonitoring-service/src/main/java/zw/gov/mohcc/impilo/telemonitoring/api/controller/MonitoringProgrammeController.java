package zw.gov.mohcc.impilo.telemonitoring.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.telemonitoring.api.dto.MonitoringPlanDtos.ProgrammeResponse;
import zw.gov.mohcc.impilo.telemonitoring.core.TelemonitoringDomainException;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.util.List;

/**
 * Read-only programme profile catalogue (§14.1). Catalogue governance (create/version)
 * belongs to the clinical-governance function and lands with OF-B21.
 */
@RestController
@RequestMapping("/v1/monitoring-programmes")
public class MonitoringProgrammeController {

    private final MonitoringProgrammeRepository programmeRepository;

    public MonitoringProgrammeController(MonitoringProgrammeRepository programmeRepository) {
        this.programmeRepository = programmeRepository;
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
}
