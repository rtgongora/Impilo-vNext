package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityDepartmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityDepartmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages canonical facility departments (TUSO SoR). Every mutation is
 * audited via the outbox; downstream consumers (Vashandi work assignments,
 * the experience-bff work-context resolver) validate department_id against
 * this table rather than trusting a free-text string (see A5).
 */
@Service
public class FacilityDepartmentService {

    private static final Logger log = LoggerFactory.getLogger(FacilityDepartmentService.class);

    private final FacilityDepartmentRepository repository;
    private final FacilityRepository facilityRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilityDepartmentService(FacilityDepartmentRepository repository,
                                      FacilityRepository facilityRepository,
                                      EventOutboxRepository outboxRepository) {
        this.repository = repository;
        this.facilityRepository = facilityRepository;
        this.outboxRepository = outboxRepository;
    }

    public record CreateDepartmentRequest(
            String departmentCode, String officialName, String displayName, String departmentType,
            UUID parentDepartmentId, LocalDate openedAt, UUID headPersonHealthId, String locationRef,
            Map<String, Object> contact, Map<String, Object> escalation, Map<String, Object> metadata) {}

    /** Partial-update request; null fields are left unchanged. */
    public record UpdateDepartmentRequest(
            String officialName, String displayName, String departmentType, UUID parentDepartmentId,
            String operatingStatus, UUID headPersonHealthId, String locationRef,
            Map<String, Object> contact, Map<String, Object> escalation, Map<String, Object> metadata) {}

    @Transactional(readOnly = true)
    public List<FacilityDepartmentEntity> list(Long facilityId) {
        return repository.findByFacilityIdAndActiveTrueOrderByOfficialNameAsc(facilityId);
    }

    @Transactional(readOnly = true)
    public FacilityDepartmentEntity get(UUID departmentId) {
        return repository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Facility department not found: " + departmentId));
    }

    @Transactional
    public FacilityDepartmentEntity create(TrustContext ctx, Long facilityId, CreateDepartmentRequest req) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        if (ctx != null && !facility.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }
        if (req.departmentCode() == null || req.departmentCode().isBlank()) {
            throw new IllegalArgumentException("departmentCode is required");
        }
        repository.findByFacilityIdAndDepartmentCodeAndActiveTrue(facilityId, req.departmentCode())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "A department with code " + req.departmentCode() + " already exists at this facility");
                });

        FacilityDepartmentEntity dept = new FacilityDepartmentEntity();
        dept.setFacilityId(facilityId);
        dept.setTenantId(facility.getTenantId());
        dept.setDepartmentCode(req.departmentCode());
        dept.setOfficialName(req.officialName());
        dept.setDisplayName(req.displayName() != null ? req.displayName() : req.officialName());
        if (req.departmentType() != null && !req.departmentType().isBlank()) {
            dept.setDepartmentType(req.departmentType());
        }
        dept.setParentDepartmentId(req.parentDepartmentId());
        dept.setOpenedAt(req.openedAt() != null ? req.openedAt() : LocalDate.now());
        dept.setHeadPersonHealthId(req.headPersonHealthId());
        dept.setLocationRef(req.locationRef());
        dept.setContact(req.contact());
        dept.setEscalation(req.escalation());
        dept.setMetadata(req.metadata());
        dept.setProvenance("NATIVE");
        dept.setCreatedBy(actor(ctx));
        dept.setUpdatedBy(actor(ctx));
        FacilityDepartmentEntity saved = repository.save(dept);

        publishEvent(facilityId, saved.getId(), "FACILITY_DEPARTMENT_CREATED");
        log.info("Facility department {} ({}) created for facility {} (actor={})",
                saved.getId(), saved.getDepartmentCode(), facilityId, actor(ctx));
        return saved;
    }

    @Transactional
    public FacilityDepartmentEntity update(TrustContext ctx, UUID departmentId, UpdateDepartmentRequest req) {
        FacilityDepartmentEntity dept = get(departmentId);
        if (ctx != null && !dept.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: department belongs to different tenant");
        }
        if (req.officialName() != null && !req.officialName().isBlank()) {
            dept.setOfficialName(req.officialName());
        }
        if (req.displayName() != null) {
            dept.setDisplayName(req.displayName());
        }
        if (req.departmentType() != null && !req.departmentType().isBlank()) {
            dept.setDepartmentType(req.departmentType());
        }
        if (req.parentDepartmentId() != null) {
            if (req.parentDepartmentId().equals(departmentId)) {
                throw new IllegalArgumentException("A department cannot be its own parent");
            }
            dept.setParentDepartmentId(req.parentDepartmentId());
        }
        if (req.operatingStatus() != null && !req.operatingStatus().isBlank()) {
            dept.setOperatingStatus(req.operatingStatus());
            if ("CLOSED".equals(req.operatingStatus())) {
                dept.setClosedAt(LocalDate.now());
            }
        }
        if (req.headPersonHealthId() != null) {
            dept.setHeadPersonHealthId(req.headPersonHealthId());
        }
        if (req.locationRef() != null) {
            dept.setLocationRef(req.locationRef());
        }
        if (req.contact() != null) {
            dept.setContact(req.contact());
        }
        if (req.escalation() != null) {
            dept.setEscalation(req.escalation());
        }
        if (req.metadata() != null) {
            dept.setMetadata(req.metadata());
        }
        dept.setUpdatedBy(actor(ctx));
        FacilityDepartmentEntity saved = repository.save(dept);
        publishEvent(dept.getFacilityId(), departmentId, "FACILITY_DEPARTMENT_UPDATED");
        log.info("Facility department {} updated for facility {} (actor={})",
                departmentId, dept.getFacilityId(), actor(ctx));
        return saved;
    }

    @Transactional
    public FacilityDepartmentEntity close(TrustContext ctx, UUID departmentId) {
        FacilityDepartmentEntity dept = get(departmentId);
        if (ctx != null && !dept.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: department belongs to different tenant");
        }
        dept.setOperatingStatus("CLOSED");
        dept.setClosedAt(LocalDate.now());
        dept.setActive(false);
        dept.setUpdatedBy(actor(ctx));
        FacilityDepartmentEntity saved = repository.save(dept);
        publishEvent(dept.getFacilityId(), departmentId, "FACILITY_DEPARTMENT_CLOSED");
        return saved;
    }

    @Transactional
    public FacilityDepartmentEntity reactivate(TrustContext ctx, UUID departmentId) {
        FacilityDepartmentEntity dept = get(departmentId);
        if (ctx != null && !dept.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: department belongs to different tenant");
        }
        dept.setOperatingStatus("ACTIVE");
        dept.setClosedAt(null);
        dept.setActive(true);
        dept.setUpdatedBy(actor(ctx));
        FacilityDepartmentEntity saved = repository.save(dept);
        publishEvent(dept.getFacilityId(), departmentId, "FACILITY_DEPARTMENT_REACTIVATED");
        return saved;
    }

    private void publishEvent(Long facilityId, UUID departmentId, String eventType) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY_DEPARTMENT");
        event.setAggregateId(departmentId.toString());
        event.setEventType(eventType);
        event.setSubjectType("FACILITY");
        event.setSubjectId(String.valueOf(facilityId));
        event.setPayload(String.format("{\"facilityId\":%d,\"departmentId\":\"%s\"}", facilityId, departmentId));
        outboxRepository.save(event);
    }

    private static String actor(TrustContext ctx) {
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
