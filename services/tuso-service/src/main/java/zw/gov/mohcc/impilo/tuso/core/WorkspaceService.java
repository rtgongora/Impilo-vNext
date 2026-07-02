package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.ShiftResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.StartShiftOptionsResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.WorkspaceResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.WorkspaceRuleDto;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceDashboardEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceOverrideLogEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceDashboardRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceOverrideLogRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRuleRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for managing workspaces within facilities.
 *
 * <p>Handles workspace CRUD, eligibility checks for providers,
 * shift management, and override logging.</p>
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceRuleRepository ruleRepository;
    private final WorkspaceDashboardRepository dashboardRepository;
    private final WorkspaceOverrideLogRepository overrideLogRepository;
    private final ShiftRepository shiftRepository;
    private final FacilityRepository facilityRepository;
    private final WorkspaceEligibilityEngine eligibilityEngine;
    private final EventOutboxRepository outboxRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceRuleRepository ruleRepository,
                            WorkspaceDashboardRepository dashboardRepository,
                            WorkspaceOverrideLogRepository overrideLogRepository,
                            ShiftRepository shiftRepository,
                            FacilityRepository facilityRepository,
                            WorkspaceEligibilityEngine eligibilityEngine,
                            EventOutboxRepository outboxRepository) {
        this.workspaceRepository = workspaceRepository;
        this.ruleRepository = ruleRepository;
        this.dashboardRepository = dashboardRepository;
        this.overrideLogRepository = overrideLogRepository;
        this.shiftRepository = shiftRepository;
        this.facilityRepository = facilityRepository;
        this.eligibilityEngine = eligibilityEngine;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a new workspace with eligibility rules and dashboard panels.
     *
     * @param facilityId the owning facility ID
     * @param dto        the workspace creation data
     * @return the created workspace response
     */
    @Transactional
    public WorkspaceResponse createWorkspace(Long facilityId,
                                              zw.gov.mohcc.impilo.tuso.api.dto.CreateWorkspaceRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        log.info("Creating workspace '{}' for facility {} in tenant {}", dto.name(), facilityId, tenantId);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setFacility(facility);
        workspace.setTenantId(tenantId);
        workspace.setName(dto.name());
        workspace.setWorkspaceType(dto.workspaceType());
        workspace.setDescription(dto.description());
        workspace.setQueueConfig(dto.queueConfig());
        workspace.setDefaultPanels(dto.defaultPanels());
        workspace.setActive(true);
        workspace.setCreatedBy(actorId);
        workspace.setUpdatedBy(actorId);
        workspace = workspaceRepository.save(workspace);

        UUID workspaceId = workspace.getId();

        // Create eligibility rules from the api DTO (List<WorkspaceRuleDto>)
        List<WorkspaceRuleDto> savedRules = new ArrayList<>();
        if (dto.rules() != null) {
            for (WorkspaceRuleDto ruleDto : dto.rules()) {
                WorkspaceRuleEntity rule = new WorkspaceRuleEntity();
                rule.setWorkspace(workspace);
                rule.setRuleType(ruleDto.ruleType());
                rule.setActorType(ruleDto.actorType());
                rule.setRole(ruleDto.role());
                rule.setPrivilege(ruleDto.privilege());
                rule.setRequired(ruleDto.required());
                ruleRepository.save(rule);
                savedRules.add(ruleDto);
            }
        }

        // dashboards in CreateWorkspaceRequest are List<String> (panel names only)
        // No WorkspaceDashboardEntity rows created from panel-name strings alone

        publishEvent("WORKSPACE", workspaceId.toString(), "tuso.workspace.created",
                String.format("{\"workspaceId\":\"%s\",\"facilityId\":%d,\"tenantId\":\"%s\"," +
                        "\"name\":\"%s\",\"workspaceType\":\"%s\",\"action\":\"CREATED\"}",
                        workspaceId, facilityId, tenantId, dto.name(), dto.workspaceType()));

        log.info("Workspace '{}' created with id {} for facility {}", dto.name(), workspaceId, facilityId);
        return toWorkspaceResponse(workspace, savedRules, Collections.emptyList());
    }

    /**
     * Update an existing workspace.
     *
     * @param workspaceId the workspace UUID
     * @param dto         the update data
     * @return the updated workspace response
     */
    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId,
                                              zw.gov.mohcc.impilo.tuso.api.dto.UpdateWorkspaceRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        if (!workspace.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: workspace belongs to different tenant");
        }

        log.info("Updating workspace {} for tenant {}", workspaceId, tenantId);

        if (dto.name() != null) {
            workspace.setName(dto.name());
        }
        if (dto.workspaceType() != null) {
            workspace.setWorkspaceType(dto.workspaceType());
        }
        if (dto.description() != null) {
            workspace.setDescription(dto.description());
        }
        if (dto.queueConfig() != null) {
            workspace.setQueueConfig(dto.queueConfig());
        }
        if (dto.defaultPanels() != null) {
            workspace.setDefaultPanels(dto.defaultPanels());
        }

        workspace.setUpdatedBy(actorId);
        workspace = workspaceRepository.save(workspace);

        publishEvent("WORKSPACE", workspaceId.toString(), "tuso.workspace.updated",
                String.format("{\"workspaceId\":\"%s\",\"facilityId\":%d,\"tenantId\":\"%s\"," +
                        "\"name\":\"%s\",\"action\":\"UPDATED\"}",
                        workspaceId, workspace.getFacility().getId(), tenantId, workspace.getName()));

        log.info("Workspace {} updated", workspaceId);

        List<WorkspaceRuleEntity> rules = ruleRepository.findByWorkspaceId(workspaceId);
        List<WorkspaceDashboardEntity> dashboards =
                dashboardRepository.findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(workspaceId);
        return toWorkspaceResponse(workspace, toRuleDtos(rules), toDashboardDetails(dashboards));
    }

    /**
     * Retire (deactivate) a workspace. Emits {@code tuso.workspace.updated} (the event PCT consumes) with
     * {@code action=RETIRED} so downstream can drop the materialised queue for a retired workspace.
     *
     * @param workspaceId the workspace UUID
     * @return the retired workspace response
     */
    @Transactional
    public WorkspaceResponse retireWorkspace(UUID workspaceId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        if (!workspace.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: workspace belongs to different tenant");
        }

        workspace.setActive(false);
        workspace.setUpdatedBy(actorId);
        workspace = workspaceRepository.save(workspace);

        Long facilityId = workspace.getFacility() != null ? workspace.getFacility().getId() : null;
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("WORKSPACE");
        event.setAggregateId(workspaceId.toString());
        event.setEventType("tuso.workspace.updated");
        event.setSubjectType(FacilityConfigurationService.SUBJECT_FACILITY);
        if (facilityId != null) {
            event.setSubjectId(String.valueOf(facilityId));
        }
        event.setPayload(String.format("{\"workspaceId\":\"%s\",\"facilityId\":%s,\"tenantId\":\"%s\"," +
                "\"name\":\"%s\",\"action\":\"RETIRED\"}",
                workspaceId, facilityId, tenantId, workspace.getName()));
        outboxRepository.save(event);

        log.info("Workspace {} retired (actor={})", workspaceId, actorId);
        List<WorkspaceRuleEntity> rules = ruleRepository.findByWorkspaceId(workspaceId);
        List<WorkspaceDashboardEntity> dashboards =
                dashboardRepository.findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(workspaceId);
        return toWorkspaceResponse(workspace, toRuleDtos(rules), toDashboardDetails(dashboards));
    }

    /**
     * Get a workspace with its rules and dashboard panels.
     *
     * @param workspaceId the workspace UUID
     * @return the workspace response
     */
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID workspaceId) {
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        List<WorkspaceRuleEntity> rules = ruleRepository.findByWorkspaceId(workspaceId);
        List<WorkspaceDashboardEntity> dashboards =
                dashboardRepository.findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(workspaceId);

        return toWorkspaceResponse(workspace, toRuleDtos(rules), toDashboardDetails(dashboards));
    }

    /**
     * List all active workspaces for a facility.
     *
     * @param facilityId the facility ID
     * @return list of workspace responses
     */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listWorkspaces(Long facilityId) {
        List<WorkspaceEntity> entities = workspaceRepository.findByFacilityIdAndActiveTrue(facilityId);
        List<WorkspaceResponse> result = new ArrayList<>();
        for (WorkspaceEntity ws : entities) {
            List<WorkspaceRuleEntity> rules = ruleRepository.findByWorkspaceId(ws.getId());
            List<WorkspaceDashboardEntity> dashboards =
                    dashboardRepository.findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(ws.getId());
            result.add(toWorkspaceResponse(ws, toRuleDtos(rules), toDashboardDetails(dashboards)));
        }
        return result;
    }

    /**
     * Log a workspace override with a mandatory reason.
     * Applies the override change to the workspace and records the audit trail.
     *
     * @param workspaceId  the workspace UUID
     * @param overrideType the type of override (e.g. "QUEUE_CONFIG", "PANEL_CHANGE")
     * @param reason       mandatory reason for the override (must not be blank)
     * @param oldValue     the previous value (JSONB map)
     * @param newValue     the new value (JSONB map)
     * @return the workspace response after override
     */
    @Transactional
    public WorkspaceResponse overrideWorkspace(UUID workspaceId, String overrideType,
                                                String reason, Map<String, Object> oldValue,
                                                Map<String, Object> newValue) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Override reason is mandatory and must not be blank");
        }

        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        if (!workspace.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: workspace belongs to different tenant");
        }

        log.info("Override on workspace {} by actor {}: type={}, reason='{}'",
                workspaceId, actorId, overrideType, reason);

        WorkspaceOverrideLogEntity overrideLog = new WorkspaceOverrideLogEntity();
        overrideLog.setWorkspace(workspace);
        overrideLog.setTenantId(tenantId);
        overrideLog.setActorId(actorId);
        overrideLog.setOverrideType(overrideType);
        overrideLog.setReason(reason);
        overrideLog.setOldValue(oldValue);
        overrideLog.setNewValue(newValue);
        overrideLogRepository.save(overrideLog);

        // Apply the override depending on type
        switch (overrideType) {
            case "QUEUE_CONFIG" -> {
                workspace.setQueueConfig(newValue);
                workspace.setUpdatedBy(actorId);
                workspace = workspaceRepository.save(workspace);
            }
            case "DEFAULT_PANELS" -> {
                workspace.setDefaultPanels(newValue);
                workspace.setUpdatedBy(actorId);
                workspace = workspaceRepository.save(workspace);
            }
            case "ACTIVE_STATUS" -> {
                workspace.setActive(Boolean.TRUE.equals(newValue.get("active")));
                workspace.setUpdatedBy(actorId);
                workspace = workspaceRepository.save(workspace);
            }
            default -> log.warn("Unknown override type '{}'; logged but not applied to workspace", overrideType);
        }

        List<WorkspaceRuleEntity> rules = ruleRepository.findByWorkspaceId(workspaceId);
        List<WorkspaceDashboardEntity> dashboards =
                dashboardRepository.findByWorkspaceIdAndActiveTrueOrderBySortOrderAsc(workspaceId);
        return toWorkspaceResponse(workspace, toRuleDtos(rules), toDashboardDetails(dashboards));
    }

    /**
     * Get the workspaces a provider is eligible to start a shift in.
     *
     * @param facilityId   the facility ID
     * @param providerInfo the provider's eligibility info (actor type, roles, privileges)
     * @return the start-shift options response
     */
    @Transactional(readOnly = true)
    public StartShiftOptionsResponse getStartShiftOptions(Long facilityId,
                                                           ProviderEligibilityInfo providerInfo) {
        List<WorkspaceEntity> workspaces = workspaceRepository.findByFacilityIdAndActiveTrue(facilityId);

        List<List<WorkspaceRuleEntity>> rulesPerWorkspace = new ArrayList<>();
        for (WorkspaceEntity ws : workspaces) {
            rulesPerWorkspace.add(ruleRepository.findByWorkspaceId(ws.getId()));
        }

        Set<String> roles = providerInfo.roles() != null ? providerInfo.roles() : new HashSet<>();
        Set<String> privileges = providerInfo.privileges() != null ? providerInfo.privileges() : new HashSet<>();

        List<WorkspaceEligibilityEngine.EligibilityResult> results = eligibilityEngine.evaluateAll(
                providerInfo.actorType(), roles, privileges, workspaces, rulesPerWorkspace);

        List<StartShiftOptionsResponse.EligibleWorkspace> eligible = results.stream()
                .map(r -> new StartShiftOptionsResponse.EligibleWorkspace(
                        r.workspace().getId(),
                        r.workspace().getName(),
                        r.workspace().getWorkspaceType(),
                        r.eligible(),
                        r.ineligibilityReasons()))
                .toList();

        return new StartShiftOptionsResponse(eligible);
    }

    /**
     * Start a shift for a provider in a workspace.
     * Automatically ends any existing active shift for this provider in the same tenant.
     *
     * @param facilityId   the facility ID
     * @param workspaceId  the workspace UUID
     * @param providerId   the provider identifier
     * @param providerType the provider type (e.g. "DOCTOR", "NURSE")
     * @return the created shift response
     */
    @Transactional
    public ShiftResponse startShift(Long facilityId, UUID workspaceId,
                                     String providerId, String providerType) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        WorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        log.info("Starting shift for provider {} in workspace {} at facility {}",
                providerId, workspaceId, facilityId);

        // End any existing active shifts for this provider
        List<ShiftEntity> activeShifts = shiftRepository.findByTenantIdAndProviderIdAndStatus(
                tenantId, providerId, "ACTIVE");
        for (ShiftEntity activeShift : activeShifts) {
            activeShift.setStatus("ENDED");
            activeShift.setEndedAt(Instant.now());
            shiftRepository.save(activeShift);
            log.info("Ended previous active shift {} for provider {}", activeShift.getId(), providerId);
        }

        // Create new shift
        ShiftEntity shift = new ShiftEntity();
        shift.setTenantId(tenantId);
        shift.setFacility(facility);
        shift.setWorkspace(workspace);
        shift.setProviderId(providerId);
        shift.setProviderType(providerType);
        shift.setStartedAt(Instant.now());
        shift.setStatus("ACTIVE");
        shift = shiftRepository.save(shift);

        log.info("Shift {} started for provider {} in workspace {} at facility {}",
                shift.getId(), providerId, workspaceId, facilityId);
        return toShiftResponse(shift);
    }

    // ---- Mapping helpers ----

    private WorkspaceResponse toWorkspaceResponse(WorkspaceEntity ws,
                                                    List<WorkspaceRuleDto> rules,
                                                    List<WorkspaceResponse.DashboardDetail> dashboards) {
        return new WorkspaceResponse(
                ws.getId(),
                ws.getFacility() != null ? ws.getFacility().getId() : null,
                ws.getName(),
                ws.getWorkspaceType(),
                Boolean.TRUE.equals(ws.getZiboValidated()),
                ws.getDescription(),
                ws.getQueueConfig(),
                ws.getDefaultPanels(),
                Boolean.TRUE.equals(ws.getActive()),
                rules,
                dashboards,
                ws.getCreatedAt(),
                ws.getUpdatedAt(),
                ws.getCreatedBy(),
                ws.getUpdatedBy());
    }

    private List<WorkspaceRuleDto> toRuleDtos(List<WorkspaceRuleEntity> rules) {
        if (rules == null) return Collections.emptyList();
        return rules.stream()
                .map(r -> new WorkspaceRuleDto(r.getRuleType(), r.getActorType(),
                        r.getRole(), r.getPrivilege(), r.isRequired()))
                .toList();
    }

    private List<WorkspaceResponse.DashboardDetail> toDashboardDetails(
            List<WorkspaceDashboardEntity> dashboards) {
        if (dashboards == null) return Collections.emptyList();
        return dashboards.stream()
                .map(d -> new WorkspaceResponse.DashboardDetail(
                        d.getId(), d.getPanelName(), d.getPanelType(),
                        d.getConfig(), d.getSortOrder() != null ? d.getSortOrder() : 0,
                        Boolean.TRUE.equals(d.getActive())))
                .toList();
    }

    private ShiftResponse toShiftResponse(ShiftEntity shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getTenantId(),
                shift.getFacility() != null ? shift.getFacility().getId() : null,
                shift.getWorkspace() != null ? shift.getWorkspace().getId() : null,
                shift.getWorkspace() != null ? shift.getWorkspace().getName() : null,
                shift.getProviderId(),
                shift.getProviderType(),
                shift.getStartedAt(),
                shift.getEndedAt(),
                shift.getStatus(),
                shift.getMetadata(),
                shift.getCreatedAt());
    }

    // ---- Private helpers ----

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    // ---- Internal DTOs ----

    public record ProviderEligibilityInfo(
            String actorType,
            Set<String> roles,
            Set<String> privileges
    ) {}
}
