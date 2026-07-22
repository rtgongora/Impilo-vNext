package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos.CreateAppointmentRequest;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AppointmentRoleEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.RegulatoryAppointmentEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AppointmentRoleRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.RegulatoryAppointmentRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regulatory appointment lifecycle (ROM-W1): create (PENDING_VERIFICATION) → verify (ACTIVE) →
 * end/revoke. An ACTIVE appointment is the login-context anchor for regulatory personnel; its
 * verification emits an event that vashandi mirrors into an org-scoped work assignment (W2).
 * An appointment never confers access while PENDING (grants-no-authority law).
 */
@Service
public class RegulatoryAppointmentService {

    private static final String GRANT_STATUS_ACTIVE = "ACTIVE";

    private final RegulatoryAppointmentRepository appointmentRepository;
    private final AppointmentRoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgRegistryOutboxWriter outboxWriter;

    public RegulatoryAppointmentService(RegulatoryAppointmentRepository appointmentRepository,
                                        AppointmentRoleRepository roleRepository,
                                        OrganizationRepository organizationRepository,
                                        OrgRegistryOutboxWriter outboxWriter) {
        this.appointmentRepository = appointmentRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<AppointmentRoleEntity> listRoles() {
        return roleRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public RegulatoryAppointmentEntity create(UUID tenantId, UUID organizationId,
                                              CreateAppointmentRequest request, String actor) throws Exception {
        OrganizationEntity org = organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + organizationId));
        if (request.personHealthId() == null || request.personHealthId().isBlank()) {
            throw new IllegalArgumentException("personHealthId is required");
        }
        String roleCode = request.roleCode() == null ? null : request.roleCode().trim().toUpperCase();
        if (roleCode == null || roleRepository.findById(roleCode).isEmpty()) {
            throw new IllegalArgumentException("roleCode must be one of the appointment-role vocabulary");
        }

        RegulatoryAppointmentEntity appt = new RegulatoryAppointmentEntity();
        appt.setTenantId(tenantId);
        appt.setOrganizationId(org.getId());
        appt.setPersonHealthId(request.personHealthId().trim());
        appt.setRoleCode(roleCode);
        appt.setJurisdictionCode(request.jurisdictionCode() != null && !request.jurisdictionCode().isBlank()
                ? request.jurisdictionCode().trim().toUpperCase() : "NATIONAL");
        appt.setStatus("PENDING_VERIFICATION");
        appt.setSource(request.source() != null ? request.source() : "NATIVE");
        appt.setEvidenceRef(request.evidenceRef());
        appt.setAppointedBy(request.appointedBy() != null ? request.appointedBy() : actor);
        appt.setValidFrom(request.validFrom());
        appt.setValidTo(request.validTo());
        RegulatoryAppointmentEntity saved = appointmentRepository.save(appt);

        outboxWriter.publish(tenantId, "REGULATORY_APPOINTMENT", saved.getId().toString(),
                "regulatory_appointment", "created",
                "org-registry:appointment:created:" + saved.getId(),
                payload(saved, org));
        return saved;
    }

    /**
     * National/registrar action: promote a PENDING appointment to ACTIVE. Enforces one ACTIVE per
     * (person, org, role) — a second activation must first end the prior one. The emitted
     * {@code verified} event is what vashandi consumes to mint the org-scoped assignment (W2).
     */
    @Transactional
    public RegulatoryAppointmentEntity verify(UUID tenantId, UUID appointmentId, String verifiedBy) throws Exception {
        RegulatoryAppointmentEntity appt = require(tenantId, appointmentId);
        if (!"PENDING_VERIFICATION".equals(appt.getStatus())) {
            throw new IllegalStateException("appointment is not pending verification: " + appt.getStatus());
        }
        if (verifiedBy == null || verifiedBy.isBlank()) {
            throw new IllegalArgumentException("verifiedBy (regulator/national actor) is required");
        }
        if (appointmentRepository.existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
                tenantId, appt.getOrganizationId(), appt.getPersonHealthId(), appt.getRoleCode(), GRANT_STATUS_ACTIVE)) {
            throw new IllegalStateException("an ACTIVE appointment already exists for this person, org and role");
        }
        appt.setStatus(GRANT_STATUS_ACTIVE);
        appt.setVerifiedBy(verifiedBy);
        appt.setVerifiedAt(OffsetDateTime.now());
        RegulatoryAppointmentEntity saved = appointmentRepository.save(appt);

        OrganizationEntity org = organizationRepository.findByTenantIdAndId(tenantId, appt.getOrganizationId())
                .orElse(null);
        outboxWriter.publish(tenantId, "REGULATORY_APPOINTMENT", saved.getId().toString(),
                "regulatory_appointment", "verified",
                "org-registry:appointment:verified:" + saved.getId(),
                payload(saved, org));
        return saved;
    }

    @Transactional
    public RegulatoryAppointmentEntity end(UUID tenantId, UUID appointmentId, String reason, String actor)
            throws Exception {
        RegulatoryAppointmentEntity appt = require(tenantId, appointmentId);
        if ("ENDED".equals(appt.getStatus()) || "REVOKED".equals(appt.getStatus())) {
            return appt;
        }
        appt.setStatus("REVOKED".equalsIgnoreCase(reason) ? "REVOKED" : "ENDED");
        RegulatoryAppointmentEntity saved = appointmentRepository.save(appt);
        outboxWriter.publish(tenantId, "REGULATORY_APPOINTMENT", saved.getId().toString(),
                "regulatory_appointment", "ended",
                "org-registry:appointment:ended:" + saved.getId() + ":" + saved.getStatus(),
                payload(saved, null));
        return saved;
    }

    public List<RegulatoryAppointmentEntity> listForOrganization(UUID tenantId, UUID organizationId) {
        return appointmentRepository.findByTenantIdAndOrganizationId(tenantId, organizationId);
    }

    public List<RegulatoryAppointmentEntity> listForPerson(UUID tenantId, String personHealthId) {
        return appointmentRepository.findByTenantIdAndPersonHealthId(tenantId, personHealthId);
    }

    private RegulatoryAppointmentEntity require(UUID tenantId, UUID id) {
        return appointmentRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("appointment not found: " + id));
    }

    private Map<String, Object> payload(RegulatoryAppointmentEntity a, OrganizationEntity org) {
        return Map.of(
                "appointmentId", a.getId().toString(),
                "organizationId", a.getOrganizationId().toString(),
                "organizationCode", org != null ? org.getCode() : "",
                "personHealthId", a.getPersonHealthId(),
                "roleCode", a.getRoleCode(),
                "jurisdictionCode", a.getJurisdictionCode(),
                "status", a.getStatus());
    }
}
