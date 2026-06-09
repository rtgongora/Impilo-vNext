package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingNudgeEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.CoachingNudgeRepository;

import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/internal/v1/wellness/coaching/nudges")
public class CoachingNudgeController {

    private final CoachingNudgeRepository repository;

    public CoachingNudgeController(CoachingNudgeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CoachingNudgeEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(value = "person_cpid", required = false) String personCpid) {
        List<CoachingNudgeEntity> all = repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "ACTIVE");
        if (personCpid == null || personCpid.isBlank()) {
            return all;
        }
        return all.stream()
                .filter(n -> n.getPersonCpid() == null || personCpid.equals(n.getPersonCpid()))
                .toList();
    }
}
