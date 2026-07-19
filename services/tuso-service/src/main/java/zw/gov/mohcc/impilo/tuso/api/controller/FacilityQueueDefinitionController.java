package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.List;
import java.util.UUID;

/**
 * Exposes a facility's queue definitions, derived from its (TUSO-owned) service-point configuration and
 * addressed by the canonical facility UUID that downstream services (PCT) key off. This is the read-model
 * PCT materialises queues from — TUSO owns the definitions; PCT only materialises them for care ops.
 */
@RestController
@RequestMapping("/v1/internal/facilities")
public class FacilityQueueDefinitionController {

    private final FacilityRepository facilityRepository;
    private final ServicePointRepository servicePointRepository;

    public FacilityQueueDefinitionController(FacilityRepository facilityRepository,
                                             ServicePointRepository servicePointRepository) {
        this.facilityRepository = facilityRepository;
        this.servicePointRepository = servicePointRepository;
    }

    /**
     * One queue definition per active service point. {@code sourceRef} (service-point id) is the stable
     * key PCT uses for idempotent materialisation.
     */
    public record QueueDefinition(
            String sourceRef,
            String name,
            String queueType,
            String servicePointType,
            String workspaceId,
            boolean active) {}

    @GetMapping("/by-uuid/{facilityUuid}/queue-definitions")
    public ResponseEntity<ApiResponse<List<QueueDefinition>>> queueDefinitions(
            @PathVariable UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .filter(f -> ctx.tenantId().equals(f.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found for uuid: " + facilityUuid));

        List<QueueDefinition> defs = servicePointRepository
                .findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(facility.getId())
                .stream()
                .map(FacilityQueueDefinitionController::toDefinition)
                .toList();

        // Care-not-denied floor: an operational facility with no configured service points still
        // needs a queue so a provider can see a walk-in patient. The bulk-imported Master Facility
        // List brings ~1,700 operational facilities online with no service-point configuration yet;
        // without this default, PCT materialises nothing for them and every walk-in enqueue fails.
        // Synthesize one stable default general/walk-in queue (idempotent sourceRef derived from the
        // facility UUID, so PCT never duplicates it on replay). Once real service points are configured
        // they become the facility's queues and this fallback is no longer emitted (PCT retires it).
        if (defs.isEmpty() && isOperational(facility)) {
            defs = List.of(defaultWalkInQueue(facilityUuid));
        }
        return ResponseEntity.ok(ApiResponse.ok(defs, ctx.correlationId().toString()));
    }

    /** Stable synthetic key for the default walk-in queue; never collides with a service-point UUID. */
    static String defaultWalkInSourceRef(UUID facilityUuid) {
        return "facility:" + facilityUuid + ":default-walk-in";
    }

    private static QueueDefinition defaultWalkInQueue(UUID facilityUuid) {
        return new QueueDefinition(
                defaultWalkInSourceRef(facilityUuid),
                "General / Walk-in",
                "FIFO",
                "GENERAL",
                null,
                true);
    }

    /** A facility can accept walk-ins only when it is administratively active and operational. */
    private static boolean isOperational(FacilityEntity facility) {
        return "ACTIVE".equalsIgnoreCase(facility.getStatus())
                && "OPERATIONAL".equalsIgnoreCase(facility.getOperationalStatus());
    }

    private static QueueDefinition toDefinition(ServicePointEntity sp) {
        boolean active = sp.isActive() && "ACTIVE".equalsIgnoreCase(sp.getStatus());
        String queueType = sp.getServicePointType() != null ? sp.getServicePointType() : "FIFO";
        return new QueueDefinition(
                sp.getId().toString(),
                sp.getName(),
                queueType,
                sp.getServicePointType(),
                null,
                active);
    }
}
