package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessRouteEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.WellnessRouteRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/routes")
public class WellnessRouteController {

    private final WellnessRouteRepository repository;

    public WellnessRouteController(WellnessRouteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<WellnessRouteEntity> list(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return repository.findByTenantIdAndStatusOrderByTitleAsc(tenantId, "ACTIVE");
    }
}
