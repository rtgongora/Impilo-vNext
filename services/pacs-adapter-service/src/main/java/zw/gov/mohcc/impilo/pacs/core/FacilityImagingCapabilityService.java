package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pacs.api.dto.FacilityImagingCapabilityRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ImagingModalityRequest;
import zw.gov.mohcc.impilo.pacs.domain.ImagingDeploymentMode;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.FacilityImagingCapabilityEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingAccessAuditEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingModalityEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.FacilityImagingCapabilityRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAccessAuditRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingModalityRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-facility imaging capability (deployment mode) and modality/machine registry.
 *
 * <p>Facility identity stays owned by TUSO; this service records the facility's imaging
 * integration truth so the platform can distinguish a central hospital with an existing
 * PACS/VNA from a district site with machines-only, manual-import, digitisation-bridge,
 * referral-only, or no imaging capability. Readiness is <em>derived from evidence</em>
 * (configured capability + registered modalities), never faked.</p>
 */
@Service
public class FacilityImagingCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(FacilityImagingCapabilityService.class);

    public static final String EVENT_CAPABILITY_UPDATED = "imaging.facility_capability.updated";
    public static final String EVENT_MODALITY_REGISTERED = "imaging.modality.registered";
    public static final String EVENT_MODALITY_UPDATED = "imaging.modality.updated";

    /** Modes that acquire images locally and therefore need at least one registered modality. */
    private static final Set<ImagingDeploymentMode> ACQUISITION_MODES = Set.of(
            ImagingDeploymentMode.PACS_VNA,
            ImagingDeploymentMode.GATEWAY_STORE_FORWARD,
            ImagingDeploymentMode.DIGITAL_NO_PACS,
            ImagingDeploymentMode.LEGACY_LIMITED_DICOM,
            ImagingDeploymentMode.DIGITISATION_BRIDGE,
            ImagingDeploymentMode.OFFLINE_SYNC);

    private final FacilityImagingCapabilityRepository capabilityRepository;
    private final ImagingModalityRepository modalityRepository;
    private final EventOutboxRepository outboxRepository;
    private final ImagingAccessAuditRepository accessAuditRepository;
    private final ObjectMapper objectMapper;

    public FacilityImagingCapabilityService(
            FacilityImagingCapabilityRepository capabilityRepository,
            ImagingModalityRepository modalityRepository,
            EventOutboxRepository outboxRepository,
            ImagingAccessAuditRepository accessAuditRepository,
            ObjectMapper objectMapper) {
        this.capabilityRepository = capabilityRepository;
        this.modalityRepository = modalityRepository;
        this.outboxRepository = outboxRepository;
        this.accessAuditRepository = accessAuditRepository;
        this.objectMapper = objectMapper;
    }

    // ---- Capability ----

    public List<FacilityImagingCapabilityEntity> listCapabilities(UUID tenantId, String deploymentMode) {
        if (deploymentMode != null && !deploymentMode.isBlank()) {
            return capabilityRepository.findByTenantIdAndDeploymentModeOrderByFacilityNameAsc(
                    tenantId, normalizeMode(deploymentMode).name());
        }
        return capabilityRepository.findByTenantIdOrderByFacilityNameAsc(tenantId);
    }

    public Optional<FacilityImagingCapabilityEntity> getCapability(UUID tenantId, String facilityId) {
        return capabilityRepository.findByTenantIdAndFacilityId(tenantId, facilityId);
    }

    @Transactional
    public FacilityImagingCapabilityEntity upsertCapability(
            UUID tenantId, String facilityId, FacilityImagingCapabilityRequest request,
            String actorId, String actorType) {
        ImagingDeploymentMode mode = normalizeMode(request.getDeploymentMode());

        FacilityImagingCapabilityEntity entity = capabilityRepository
                .findByTenantIdAndFacilityId(tenantId, facilityId)
                .orElseGet(() -> {
                    FacilityImagingCapabilityEntity created = new FacilityImagingCapabilityEntity();
                    created.setTenantId(tenantId);
                    created.setFacilityId(facilityId);
                    return created;
                });

        entity.setDeploymentMode(mode.name());
        if (request.getFacilityName() != null) entity.setFacilityName(request.getFacilityName());
        if (request.getPacsVnaAvailable() != null) entity.setPacsVnaAvailable(request.getPacsVnaAvailable());
        if (request.getLocalGatewayAvailable() != null) entity.setLocalGatewayAvailable(request.getLocalGatewayAvailable());
        if (request.getDicomwebAvailable() != null) entity.setDicomwebAvailable(request.getDicomwebAvailable());
        if (request.getDimseAvailable() != null) entity.setDimseAvailable(request.getDimseAvailable());
        if (request.getMwlSupported() != null) entity.setMwlSupported(request.getMwlSupported());
        if (request.getCstoreReceiveSupported() != null) entity.setCstoreReceiveSupported(request.getCstoreReceiveSupported());
        if (request.getMppsSupported() != null) entity.setMppsSupported(request.getMppsSupported());
        if (request.getStoreForwardSupported() != null) entity.setStoreForwardSupported(request.getStoreForwardSupported());
        if (request.getLocalCacheSupported() != null) entity.setLocalCacheSupported(request.getLocalCacheSupported());
        if (request.getManualImportSupported() != null) entity.setManualImportSupported(request.getManualImportSupported());
        if (request.getDigitisationBridgeSupported() != null) entity.setDigitisationBridgeSupported(request.getDigitisationBridgeSupported());
        if (request.getViewerAvailable() != null) entity.setViewerAvailable(request.getViewerAvailable());
        if (request.getReportingAvailable() != null) entity.setReportingAvailable(request.getReportingAvailable());
        if (request.getInternetQuality() != null) entity.setInternetQuality(request.getInternetQuality());
        if (request.getOperationalStatus() != null) entity.setOperationalStatus(request.getOperationalStatus());
        if (request.getDefaultArchiveTarget() != null) entity.setDefaultArchiveTarget(request.getDefaultArchiveTarget());
        if (request.getDefaultWorklistTarget() != null) entity.setDefaultWorklistTarget(request.getDefaultWorklistTarget());
        if (request.getSupportedModalities() != null) entity.setSupportedModalities(request.getSupportedModalities());
        if (request.getConfigurationStatus() != null) entity.setConfigurationStatus(request.getConfigurationStatus());
        if (request.getGoLiveReadiness() != null) entity.setGoLiveReadiness(request.getGoLiveReadiness());
        if (request.getNotes() != null) entity.setNotes(request.getNotes());
        entity.setUpdatedBy(actorId);

        entity = capabilityRepository.save(entity);

        recordAudit(actorId, actorType, "CAPABILITY_UPSERT", facilityId + ":" + mode.name());
        appendOutbox(EVENT_CAPABILITY_UPDATED, "FACILITY_IMAGING_CAPABILITY",
                facilityId, tenantId, Map.of(
                        "facility_id", facilityId,
                        "deployment_mode", mode.name(),
                        "operational_status", entity.getOperationalStatus(),
                        "configuration_status", entity.getConfigurationStatus()));

        log.info("Facility imaging capability upserted: facility={}, mode={}", facilityId, mode);
        return entity;
    }

    // ---- Modality registry ----

    public List<ImagingModalityEntity> listModalities(UUID tenantId, String facilityId, boolean includeInactive) {
        return includeInactive
                ? modalityRepository.findByTenantIdAndFacilityIdOrderByNameAsc(tenantId, facilityId)
                : modalityRepository.findByTenantIdAndFacilityIdAndActiveTrueOrderByNameAsc(tenantId, facilityId);
    }

    @Transactional
    public ImagingModalityEntity registerModality(
            UUID tenantId, String facilityId, ImagingModalityRequest request,
            String actorId, String actorType) {
        if (request.getAeTitle() != null && !request.getAeTitle().isBlank()) {
            modalityRepository.findByTenantIdAndFacilityIdAndAeTitle(tenantId, facilityId, request.getAeTitle().trim())
                    .ifPresent(existing -> {
                        throw new IllegalStateException("AE Title already registered at this facility: "
                                + request.getAeTitle() + " (modality id " + existing.getId() + ")");
                    });
        }
        ImagingModalityEntity entity = new ImagingModalityEntity();
        entity.setTenantId(tenantId);
        entity.setFacilityId(facilityId);
        applyModalityRequest(entity, request);
        entity.setUpdatedBy(actorId);
        entity = modalityRepository.save(entity);

        recordAudit(actorId, actorType, "MODALITY_REGISTER", facilityId + ":" + entity.getModalityType());
        appendOutbox(EVENT_MODALITY_REGISTERED, "IMAGING_MODALITY",
                String.valueOf(entity.getId()), tenantId, modalityEventPayload(entity));

        log.info("Imaging modality registered: facility={}, type={}, name={}",
                facilityId, entity.getModalityType(), entity.getName());
        return entity;
    }

    @Transactional
    public ImagingModalityEntity updateModality(
            UUID tenantId, String facilityId, Long modalityId, ImagingModalityRequest request,
            String actorId, String actorType) {
        ImagingModalityEntity entity = requireModality(tenantId, facilityId, modalityId);
        applyModalityRequest(entity, request);
        entity.setUpdatedBy(actorId);
        entity = modalityRepository.save(entity);

        recordAudit(actorId, actorType, "MODALITY_UPDATE", facilityId + ":" + modalityId);
        appendOutbox(EVENT_MODALITY_UPDATED, "IMAGING_MODALITY",
                String.valueOf(entity.getId()), tenantId, modalityEventPayload(entity));
        return entity;
    }

    @Transactional
    public ImagingModalityEntity deactivateModality(
            UUID tenantId, String facilityId, Long modalityId, String actorId, String actorType) {
        ImagingModalityEntity entity = requireModality(tenantId, facilityId, modalityId);
        entity.setActive(false);
        entity.setUpdatedBy(actorId);
        entity = modalityRepository.save(entity);

        recordAudit(actorId, actorType, "MODALITY_DEACTIVATE", facilityId + ":" + modalityId);
        appendOutbox(EVENT_MODALITY_UPDATED, "IMAGING_MODALITY",
                String.valueOf(entity.getId()), tenantId, modalityEventPayload(entity));
        return entity;
    }

    // ---- Readiness (derived from evidence; never faked) ----

    /**
     * Evidence-based readiness checklist. {@code READY} only when every required check for the
     * facility's declared deployment mode passes; {@code PARTIAL} when some do; otherwise
     * {@code NOT_READY}. Unconfigured facilities report {@code NOT_CONFIGURED}.
     */
    public Map<String, Object> readiness(UUID tenantId, String facilityId) {
        Optional<FacilityImagingCapabilityEntity> maybe =
                capabilityRepository.findByTenantIdAndFacilityId(tenantId, facilityId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("facilityId", facilityId);

        if (maybe.isEmpty()) {
            out.put("configured", false);
            out.put("readiness", "NOT_CONFIGURED");
            out.put("checks", List.of());
            return out;
        }

        FacilityImagingCapabilityEntity cap = maybe.get();
        ImagingDeploymentMode mode = normalizeMode(cap.getDeploymentMode());
        List<ImagingModalityEntity> modalities =
                modalityRepository.findByTenantIdAndFacilityIdAndActiveTrueOrderByNameAsc(tenantId, facilityId);

        List<Map<String, Object>> checks = new ArrayList<>();
        boolean requiresModalities = ACQUISITION_MODES.contains(mode);
        boolean requiresArchive = mode == ImagingDeploymentMode.PACS_VNA
                || mode == ImagingDeploymentMode.GATEWAY_STORE_FORWARD
                || mode == ImagingDeploymentMode.OFFLINE_SYNC;

        checks.add(check("DEPLOYMENT_MODE_DECLARED", true,
                "Deployment mode " + mode.name(), true));
        if (mode == ImagingDeploymentMode.NONE) {
            out.put("configured", true);
            out.put("deploymentMode", mode.name());
            out.put("readiness", "NO_IMAGING");
            out.put("checks", checks);
            return out;
        }

        if (requiresModalities) {
            checks.add(check("MODALITY_REGISTERED", !modalities.isEmpty(),
                    modalities.size() + " active modality/modalities registered", true));
        }
        if (requiresArchive) {
            boolean archiveOk = cap.isPacsVnaAvailable() || cap.isLocalGatewayAvailable()
                    || (cap.getDefaultArchiveTarget() != null && !cap.getDefaultArchiveTarget().isBlank());
            checks.add(check("ARCHIVE_TARGET_CONFIGURED", archiveOk,
                    archiveOk ? "Archive target present" : "No PACS/VNA, gateway, or archive target configured", true));
        }
        if (cap.isMwlSupported()) {
            boolean worklistOk = cap.getDefaultWorklistTarget() != null && !cap.getDefaultWorklistTarget().isBlank();
            checks.add(check("WORKLIST_TARGET_CONFIGURED", worklistOk,
                    worklistOk ? "Worklist target present" : "MWL declared supported but no worklist target", false));
        }
        checks.add(check("VIEWER_AVAILABLE", cap.isViewerAvailable(),
                cap.isViewerAvailable() ? "Viewer available" : "Viewer not available", true));
        checks.add(check("REPORTING_AVAILABLE", cap.isReportingAvailable(),
                cap.isReportingAvailable() ? "Reporting available" : "Reporting not available", false));
        boolean operational = "OPERATIONAL".equalsIgnoreCase(cap.getOperationalStatus());
        checks.add(check("OPERATIONAL_STATUS", operational,
                "Operational status " + cap.getOperationalStatus(), true));

        long requiredTotal = checks.stream().filter(c -> Boolean.TRUE.equals(c.get("required"))).count();
        long requiredPassed = checks.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("required")) && Boolean.TRUE.equals(c.get("passed")))
                .count();

        String readiness;
        if (requiredPassed == requiredTotal) {
            readiness = "READY";
        } else if (requiredPassed > 1) {
            readiness = "PARTIAL";
        } else {
            readiness = "NOT_READY";
        }

        out.put("configured", true);
        out.put("deploymentMode", mode.name());
        out.put("declaredGoLiveReadiness", cap.getGoLiveReadiness());
        out.put("readiness", readiness);
        out.put("activeModalities", modalities.size());
        out.put("checks", checks);
        return out;
    }

    // ---- internals ----

    private ImagingModalityEntity requireModality(UUID tenantId, String facilityId, Long modalityId) {
        ImagingModalityEntity entity = modalityRepository.findById(modalityId)
                .orElseThrow(() -> new IllegalArgumentException("Modality not found: " + modalityId));
        if (!entity.getTenantId().equals(tenantId) || !entity.getFacilityId().equals(facilityId)) {
            throw new IllegalArgumentException("Modality " + modalityId + " does not belong to facility " + facilityId);
        }
        return entity;
    }

    private static void applyModalityRequest(ImagingModalityEntity entity, ImagingModalityRequest request) {
        entity.setModalityType(request.getModalityType().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.getName());
        if (request.getAeTitle() != null) entity.setAeTitle(blankToNull(request.getAeTitle()));
        if (request.getHost() != null) entity.setHost(blankToNull(request.getHost()));
        if (request.getPort() != null) entity.setPort(request.getPort());
        if (request.getCalledAeTitle() != null) entity.setCalledAeTitle(blankToNull(request.getCalledAeTitle()));
        if (request.getCallingAeTitle() != null) entity.setCallingAeTitle(blankToNull(request.getCallingAeTitle()));
        if (request.getVendor() != null) entity.setVendor(blankToNull(request.getVendor()));
        if (request.getModel() != null) entity.setModel(blankToNull(request.getModel()));
        if (request.getSerialNumber() != null) entity.setSerialNumber(blankToNull(request.getSerialNumber()));
        if (request.getSupportsDicomStorage() != null) entity.setSupportsDicomStorage(request.getSupportsDicomStorage());
        if (request.getSupportsWorklist() != null) entity.setSupportsWorklist(request.getSupportsWorklist());
        if (request.getSupportsMpps() != null) entity.setSupportsMpps(request.getSupportsMpps());
        if (request.getSupportsDicomweb() != null) entity.setSupportsDicomweb(request.getSupportsDicomweb());
        if (request.getExportOptions() != null) entity.setExportOptions(request.getExportOptions());
        if (request.getOperationalStatus() != null) entity.setOperationalStatus(request.getOperationalStatus());
        if (request.getDefaultStorageDestination() != null) entity.setDefaultStorageDestination(blankToNull(request.getDefaultStorageDestination()));
        if (request.getDefaultWorklistSource() != null) entity.setDefaultWorklistSource(blankToNull(request.getDefaultWorklistSource()));
        if (request.getNotes() != null) entity.setNotes(request.getNotes());
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ImagingDeploymentMode normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("deploymentMode is required");
        }
        try {
            return ImagingDeploymentMode.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown imaging deployment mode: " + raw);
        }
    }

    private static Map<String, Object> check(String code, boolean passed, String evidence, boolean required) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("passed", passed);
        item.put("evidence", evidence);
        item.put("required", required);
        return item;
    }

    private Map<String, Object> modalityEventPayload(ImagingModalityEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modality_id", entity.getId());
        payload.put("facility_id", entity.getFacilityId());
        payload.put("modality_type", entity.getModalityType());
        payload.put("operational_status", entity.getOperationalStatus());
        payload.put("active", entity.isActive());
        // Deliberately no AE title / host / port in events — network details stay off the bus.
        return payload;
    }

    private void recordAudit(String actorId, String actorType, String accessType, String context) {
        ImagingAccessAuditEntity row = new ImagingAccessAuditEntity();
        row.setActorId(actorId);
        row.setActorType(actorType);
        row.setAccessType(accessType);
        row.setWorkflowContext(context);
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            row.setCorrelationId(ctx.correlationId());
        }
        accessAuditRepository.save(row);
    }

    private void appendOutbox(String eventType, String aggregateType, String aggregateId,
                              UUID tenantId, Map<String, Object> payload) {
        try {
            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(aggregateType);
            row.setAggregateId(aggregateId);
            row.setEventType(eventType);
            row.setPayload(objectMapper.writeValueAsString(payload));
            RequestContext ctx = RequestContextHolder.get();
            if (ctx != null) {
                row.setTenantId(ctx.tenantId());
                row.setPodId(ctx.podId());
                row.setCorrelationId(ctx.correlationId());
            } else {
                row.setTenantId(tenantId.toString());
                row.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
                row.setCorrelationId(UUID.randomUUID().toString());
            }
            row.setSubjectType(aggregateType);
            row.setSubjectId(aggregateId);
            row.setPartitionKey(aggregateId);
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize " + eventType + " payload", e);
        }
    }
}
