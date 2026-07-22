package zw.gov.mohcc.impilo.fhirgateway.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.fhirgateway.core.GatewayForwardService;
import zw.gov.mohcc.impilo.fhirgateway.core.GatewayRouteService;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirRouteEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;
import zw.gov.mohcc.impilo.shared.visibility.ClinicalVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

@RestController
@RequestMapping("/internal/v1/gateway")
public class GatewayRouteController {

    private static final Set<String> CLINICAL_FHIR_RESOURCE_TYPES = Set.of(
            "Patient", "Encounter", "Observation", "Condition", "MedicationRequest",
            "DiagnosticReport", "ImagingStudy", "Procedure", "MedicationAdministration", "Immunization",
            "CarePlan", "ClinicalImpression", "ServiceRequest", "Specimen", "DocumentReference",
            "AllergyIntolerance", "MedicationStatement", "FamilyMemberHistory", "RiskAssessment",
            "QuestionnaireResponse", "EpisodeOfCare", "Composition"
    );

    private final GatewayRouteService routeService;
    private final GatewayForwardService forwardService;
    private final FhirAuditLogRepository auditLogRepository;

    public GatewayRouteController(GatewayRouteService routeService,
                                  GatewayForwardService forwardService,
                                  FhirAuditLogRepository auditLogRepository) {
        this.routeService = routeService;
        this.forwardService = forwardService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> listRoutes(
            @RequestParam(name = "tenantId", required = false) UUID tenantId) {
        UUID resolvedTenantId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<FhirRouteEntity> routes = routeService.listRoutes(resolvedTenantId);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "data", routes
        ));
    }

    @PostMapping("/routes")
    public ResponseEntity<Map<String, Object>> createRoute(@RequestBody CreateRouteRequest request) {
        UUID resolvedTenantId = request.tenantId() != null
                ? request.tenantId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        FhirRouteEntity route = routeService.createRoute(
                resolvedTenantId,
                request.correlationId(),
                request.sourceSystem(),
                request.resourceType(),
                request.targetEndpoint(),
                request.enabled());

        return ResponseEntity.status(201).body(Map.of(
                "status", "ok",
                "data", route
        ));
    }

    @PostMapping("/forward")
    public ResponseEntity<Map<String, Object>> forwardRequest(@RequestBody ForwardRequest request,
                                                                HttpServletRequest httpRequest) {
        UUID resolvedTenantId = request.tenantId() != null
                ? request.tenantId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID correlationId = request.correlationId() != null
                ? request.correlationId()
                : UUID.randomUUID();

        VisibilityProfile visibility = VisibilityHeaderParser.parseFlat(httpRequest);
        if (isClinicalFhirRead(request.resourceType(), request.operation())
                && ClinicalVisibilityGuard.deniesClinicalRead(visibility)) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "denied",
                    "errorCode", "VISIBILITY_CLINICAL_BLOCKED",
                    "message", "FHIR clinical read is not permitted for the current visibility profile.",
                    "correlationId", correlationId.toString()
            ));
        }

        GatewayForwardService.ForwardResult result = forwardService.forward(
                resolvedTenantId,
                request.actorId(),
                correlationId,
                request.sourceIp(),
                request.resourceType(),
                request.operation(),
                request.payload(),
                request.subjectCpid(),
                request.purposeOfUse());

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "data", result
        ));
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> listAuditLogs(
            @RequestParam(name = "tenantId", required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID resolvedTenantId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        Page<FhirAuditLogEntity> results = auditLogRepository.findByTenantId(
                resolvedTenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "data", results.getContent(),
                "page", page,
                "size", size,
                "totalElements", results.getTotalElements()
        ));
    }

    public record CreateRouteRequest(
            UUID tenantId,
            String correlationId,
            String sourceSystem,
            String resourceType,
            String targetEndpoint,
            Boolean enabled
    ) {}

    public record ForwardRequest(
            UUID tenantId,
            UUID correlationId,
            String actorId,
            String sourceIp,
            String resourceType,
            String operation,
            String payload,
            String subjectCpid,
            String purposeOfUse
    ) {}

    private static boolean isClinicalFhirRead(String resourceType, String operation) {
        if (resourceType == null || !CLINICAL_FHIR_RESOURCE_TYPES.contains(resourceType)) {
            return false;
        }
        if (operation == null || operation.isBlank()) {
            return true;
        }
        String u = operation.toUpperCase();
        return u.contains("READ") || u.contains("SEARCH") || u.contains("GET") || u.contains("VREAD")
                || u.contains("HISTORY");
    }
}
