package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.RegisterEntryRestrictionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only council view of register-entry restrictions. New restrictions are imposed through
 * disciplinary determination — this controller does not accept writes.
 */
@RestController
@RequestMapping("/v1/internal/councils/{councilId}/register-entry-restrictions")
public class RegisterEntryRestrictionController {

    private final RegisterEntryRestrictionRepository restrictionRepository;
    private final CouncilRepository councilRepository;

    public RegisterEntryRestrictionController(RegisterEntryRestrictionRepository restrictionRepository,
                                              CouncilRepository councilRepository) {
        this.restrictionRepository = restrictionRepository;
        this.councilRepository = councilRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader("X-Tenant-ID") UUID tenantId,
                                          @PathVariable Long councilId) {
        CouncilEntity council = councilRepository.findById(councilId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Council not found"));
        return restrictionRepository.findCouncilRestrictions(tenantId, council.getId()).stream()
                .map(RegisterEntryRestrictionController::row)
                .toList();
    }

    private static Map<String, Object> row(Object[] cols) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", cols[0] == null ? null : ((Number) cols[0]).longValue());
        body.put("restrictionType", cols[1]);
        body.put("description", cols[2]);
        body.put("status", cols[3]);
        body.put("validFrom", cols[4] == null ? null : cols[4].toString());
        body.put("validTo", cols[5] == null ? null : cols[5].toString());
        body.put("imposedBy", cols[6]);
        body.put("decisionRef", cols[7]);
        body.put("registrationRecordId", cols[8] == null ? null : ((Number) cols[8]).longValue());
        body.put("registrationNumber", cols[9]);
        body.put("providerId", cols[10] == null ? null : ((Number) cols[10]).longValue());
        body.put("registerCode", cols[11]);
        body.put("registerName", cols[12]);
        return body;
    }
}
