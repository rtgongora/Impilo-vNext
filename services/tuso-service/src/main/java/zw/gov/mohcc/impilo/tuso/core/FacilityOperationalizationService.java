package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.CreateWorkspaceRequest;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCapabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk default operationalization for registry-absorbed facilities.
 *
 * <p>An imported facility is registry truth but an operational dead-end: no
 * workspace means facility → workspace → shift can never start. This service
 * gives each such facility an honest default operating skeleton — one workspace
 * (typed from its facility type), one OPD service point, a baseline (unassessed)
 * readiness row, and derived service capabilities — all marked
 * {@code derived=true} so curated configuration is distinguishable from
 * defaults. The setup wizard then refines; it never depends on this having run.</p>
 *
 * <p>Idempotent: only facilities with zero active workspaces are candidates,
 * and capability/readiness rows are only created where none exist. Each
 * facility is provisioned in its own transaction so one bad row cannot roll
 * back a batch.</p>
 */
@Service
public class FacilityOperationalizationService {

    private static final Logger log = LoggerFactory.getLogger(FacilityOperationalizationService.class);

    private static final int MAX_BATCH = 500;

    private final FacilityRepository facilityRepository;
    private final WorkspaceService workspaceService;
    private final ServicePointService servicePointService;
    private final FacilityCapabilityRepository capabilityRepository;
    private final FacilityReadinessRepository readinessRepository;
    private final TransactionTemplate transactionTemplate;

    public FacilityOperationalizationService(FacilityRepository facilityRepository,
                                             WorkspaceService workspaceService,
                                             ServicePointService servicePointService,
                                             FacilityCapabilityRepository capabilityRepository,
                                             FacilityReadinessRepository readinessRepository,
                                             PlatformTransactionManager transactionManager) {
        this.facilityRepository = facilityRepository;
        this.workspaceService = workspaceService;
        this.servicePointService = servicePointService;
        this.capabilityRepository = capabilityRepository;
        this.readinessRepository = readinessRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record OperationalizeResult(
            int processed,
            int workspacesCreated,
            int servicePointsCreated,
            int capabilitiesCreated,
            int readinessCreated,
            int failed,
            long remaining,
            boolean dryRun,
            List<Long> failedFacilityIds) {}

    public OperationalizeResult operationalizeDefaults(String province, int batchSize, boolean dryRun) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        int size = Math.min(Math.max(batchSize, 1), MAX_BATCH);
        String provinceFilter = province != null && !province.isBlank() ? province : null;

        Page<FacilityEntity> candidates = facilityRepository.findWithoutActiveWorkspace(
                tenantId, provinceFilter, PageRequest.of(0, size));
        long totalCandidates = candidates.getTotalElements();

        if (dryRun) {
            return new OperationalizeResult(0, 0, 0, 0, 0, 0, totalCandidates, true, List.of());
        }

        int processed = 0;
        int workspaces = 0;
        int servicePoints = 0;
        int capabilities = 0;
        int readiness = 0;
        List<Long> failedIds = new ArrayList<>();

        for (FacilityEntity candidate : candidates.getContent()) {
            Long facilityId = candidate.getId();
            try {
                ProvisionOutcome outcome = transactionTemplate.execute(status -> provisionOne(facilityId, tenantId));
                if (outcome != null) {
                    processed++;
                    workspaces += outcome.workspaces();
                    servicePoints += outcome.servicePoints();
                    capabilities += outcome.capabilities();
                    readiness += outcome.readiness();
                }
            } catch (Exception e) {
                log.warn("Default operationalization failed for facility {}: {}", facilityId, e.toString());
                failedIds.add(facilityId);
            }
        }

        long remaining = Math.max(0, totalCandidates - processed);
        log.info("Operationalize-defaults batch done: processed={}, failed={}, remaining={}",
                processed, failedIds.size(), remaining);
        return new OperationalizeResult(processed, workspaces, servicePoints, capabilities, readiness,
                failedIds.size(), remaining, false, failedIds);
    }

    private record ProvisionOutcome(int workspaces, int servicePoints, int capabilities, int readiness) {}

    private ProvisionOutcome provisionOne(Long facilityId, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        String workspaceType = defaultWorkspaceType(facility.getFacilityType());
        int workspaces = 0;
        int servicePoints = 0;
        int capabilities = 0;
        int readiness = 0;

        workspaceService.createWorkspace(facilityId, new CreateWorkspaceRequest(
                truncate(facility.getName() + " — " + workspaceLabel(workspaceType), 255),
                workspaceType,
                "Default operational workspace (bulk provisioning; refine via facility setup)",
                null, null, null, null));
        workspaces++;

        if (servicePointService.list(facilityId).isEmpty()) {
            servicePointService.create(TrustContextHolder.require(), facilityId,
                    new ServicePointService.CreateServicePointRequest(
                            "Outpatient Department",
                            "OPD-" + facilityId,
                            "OPD",
                            null, null, "PRIMARY_CARE",
                            derivedMetadata()));
            servicePoints++;
        }

        if (capabilityRepository.findByFacilityIdAndActiveTrue(facilityId).isEmpty()) {
            for (String[] cap : defaultCapabilities(facility.getFacilityType())) {
                FacilityCapabilityEntity entity = new FacilityCapabilityEntity();
                entity.setFacility(facility);
                entity.setTenantId(tenantId);
                entity.setCapabilityCode(cap[0]);
                entity.setCapabilityType("SERVICE");
                entity.setName(cap[1]);
                entity.setZiboValidated(false);
                entity.setActive(true);
                entity.setMetadata(derivedMetadata());
                capabilityRepository.save(entity);
                capabilities++;
            }
        }

        if (readinessRepository.findByFacilityId(facilityId).isEmpty()) {
            FacilityReadinessEntity entity = new FacilityReadinessEntity();
            entity.setFacility(facility);
            entity.setConnectivity("UNKNOWN");
            entity.setPowerSource("UNKNOWN");
            entity.setPowerBackup(false);
            entity.setDeviceCount(0);
            entity.setEhrReady(false);
            entity.setComplianceFlags(derivedMetadata());
            entity.setAssessedAt(Instant.now());
            entity.setAssessedBy("OPERATIONALIZE_DEFAULTS");
            readinessRepository.save(entity);
            readiness++;
        }

        return new ProvisionOutcome(workspaces, servicePoints, capabilities, readiness);
    }

    /** Default workspace type from the registry facility type — refined later in the wizard. */
    static String defaultWorkspaceType(String facilityType) {
        String t = facilityType == null ? "" : facilityType.toUpperCase(Locale.ROOT);
        if (t.contains("PHARMACY")) return "PHARMACY";
        if (t.contains("LABORATORY") || t.contains("LAB_")) return "LABORATORY";
        if (t.contains("MATERNITY")) return "MATERNITY";
        return "OPD";
    }

    private static String workspaceLabel(String workspaceType) {
        return switch (workspaceType) {
            case "PHARMACY" -> "Pharmacy";
            case "LABORATORY" -> "Laboratory";
            case "MATERNITY" -> "Maternity";
            default -> "OPD";
        };
    }

    /** Honest minimal capability derivation: outpatient always; inpatient/maternity for hospitals. */
    static List<String[]> defaultCapabilities(String facilityType) {
        String t = facilityType == null ? "" : facilityType.toUpperCase(Locale.ROOT);
        List<String[]> caps = new ArrayList<>();
        caps.add(new String[]{"OPD", "Outpatient Department"});
        if (t.contains("HOSPITAL")) {
            caps.add(new String[]{"IPD", "Inpatient Department"});
            caps.add(new String[]{"MATERNITY", "Maternity"});
        } else if (t.contains("RURAL_HEALTH_CENTRE") || t.contains("CLINIC")) {
            caps.add(new String[]{"MATERNITY", "Maternity"});
        }
        return caps;
    }

    private static Map<String, Object> derivedMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("derived", true);
        metadata.put("source", "OPERATIONALIZE_DEFAULTS");
        return metadata;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
