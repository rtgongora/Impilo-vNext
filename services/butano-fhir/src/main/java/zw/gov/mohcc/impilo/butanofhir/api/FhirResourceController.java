package zw.gov.mohcc.impilo.butanofhir.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.butanofhir.core.FhirResourceService;
import zw.gov.mohcc.impilo.butanofhir.persistence.entity.FhirResourceEntity;

@RestController
@RequestMapping("/internal/v1/fhir/resources")
public class FhirResourceController {

    private final FhirResourceService fhirResourceService;

    public FhirResourceController(FhirResourceService fhirResourceService) {
        this.fhirResourceService = fhirResourceService;
    }

    @GetMapping
    public ResponseEntity<List<FhirResourceEntity>> listResources(
            @RequestParam UUID tenantId,
            @RequestParam String resourceType) {
        List<FhirResourceEntity> resources = fhirResourceService.listByType(tenantId, resourceType);
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FhirResourceEntity> getResource(
            @PathVariable Long id,
            @RequestParam UUID tenantId) {
        FhirResourceEntity resource = fhirResourceService.findById(id, tenantId);
        return ResponseEntity.ok(resource);
    }

    @PostMapping
    public ResponseEntity<FhirResourceEntity> createOrUpdateResource(
            @RequestBody CreateFhirResourceRequest request) {
        FhirResourceEntity resource = fhirResourceService.createOrUpdate(
                request.tenantId(),
                request.resourceType(),
                request.resourceId(),
                request.fhirVersion(),
                request.payload(),
                request.correlationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resource);
    }

    public record CreateFhirResourceRequest(
            UUID tenantId,
            String resourceType,
            String resourceId,
            String fhirVersion,
            String payload,
            String correlationId
    ) {}
}
