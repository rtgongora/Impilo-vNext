package zw.gov.mohcc.impilo.orgregistry.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos.CreateAppointmentRequest;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AppointmentRoleEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.RegulatoryAppointmentEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AppointmentRoleRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.RegulatoryAppointmentRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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

    private static final Logger log = LoggerFactory.getLogger(RegulatoryAppointmentService.class);

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
        // Checked at the moment of activation rather than at create: an appointment sitting
        // PENDING is harmless, and the seat it claims may have been filled while it waited.
        assertBootstrapRoleAllowed(tenantId, appt.getOrganizationId(), appt.getRoleCode());
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

    /**
     * End or revoke an appointment.
     *
     * <p><b>Succession guard (RB-2).</b> Ending the last ACTIVE administrator is refused: a
     * regulator left with nobody who can administer it cannot add a user, cannot fix its own
     * access, and cannot even appoint a replacement — recovery would mean a platform-root
     * intervention on a live regulator. This is also the mandatory-second-administrator invariant
     * viewed from the other end, and it is checked here rather than trusted to a process, because
     * the moment it matters is exactly the moment somebody is in a hurry.</p>
     *
     * <p>It could not be implemented in RB-1: "administrator" had no definition until V009 marked
     * the role class. The expiry sweep deliberately does NOT consult this guard — expiry is a fact
     * about the world, and declining to record it in order to keep somebody signed in would be the
     * system granting regulatory authority to itself. A lapse that empties the administrator seat
     * is surfaced loudly instead.</p>
     */
    @Transactional
    public RegulatoryAppointmentEntity end(UUID tenantId, UUID appointmentId, String reason, String actor)
            throws Exception {
        RegulatoryAppointmentEntity appt = require(tenantId, appointmentId);
        if ("ENDED".equals(appt.getStatus()) || "REVOKED".equals(appt.getStatus())) {
            return appt;
        }
        if (GRANT_STATUS_ACTIVE.equals(appt.getStatus()) && isAdministrative(appt.getRoleCode())
                && appointmentRepository.countActiveAdministrators(
                        tenantId, appt.getOrganizationId(), appt.getId()) == 0) {
            throw new IllegalStateException(
                    "LAST_ADMINISTRATOR: this is the only active administrator at this regulator. "
                    + "Appoint a replacement first — a regulator with no administrator cannot add "
                    + "users, restore its own access, or appoint anyone.");
        }
        appt.setStatus("REVOKED".equalsIgnoreCase(reason) ? "REVOKED" : "ENDED");
        RegulatoryAppointmentEntity saved = appointmentRepository.save(appt);
        outboxWriter.publish(tenantId, "REGULATORY_APPOINTMENT", saved.getId().toString(),
                "regulatory_appointment", "ended",
                "org-registry:appointment:ended:" + saved.getId() + ":" + saved.getStatus(),
                payload(saved, null));
        return saved;
    }

    /**
     * Expiry as a state transition (RB-1, V008). ACTIVE appointments whose {@code validTo} has
     * passed are ENDED and stamped, emitting the same {@code ended} event a registrar's action
     * would — which is what lets the trust plane revoke the tokens already issued.
     *
     * <p>This exists because {@code validTo} was written by {@link #create} and read by nothing:
     * an appointment that lapsed a year ago stayed ACTIVE and kept minting work-context tokens,
     * since the session path filters on status alone.</p>
     *
     * <p>The sweep never renews and never extends. An appointment reaching its end date lapses;
     * putting it back is a registrar's decision, made with evidence through {@link #verify}. It
     * also does not spare the last administrator at an organisation — expiry is a fact about the
     * world, and a system that declined to record it in order to keep someone signed in would be
     * granting regulatory authority to itself. Leaving an organisation administrator-less is
     * surfaced loudly instead (see the warning below and the RB-2 succession guard on the manual
     * path).</p>
     *
     * @return the number of appointments ended
     */
    @Transactional
    public int expireLapsed(LocalDate asOf) throws Exception {
        List<RegulatoryAppointmentEntity> lapsed = appointmentRepository
                .findByStatusAndValidToBeforeAndExpirySweptAtIsNull(GRANT_STATUS_ACTIVE, asOf);
        for (RegulatoryAppointmentEntity appt : lapsed) {
            appt.setStatus("ENDED");
            appt.setExpirySweptAt(OffsetDateTime.now());
            RegulatoryAppointmentEntity saved = appointmentRepository.save(appt);

            Map<String, Object> payload = new LinkedHashMap<>(payload(saved, null));
            // The holder's access just stopped; "why" must survive to the audit trail and to
            // whatever tells them. ENDED alone cannot distinguish a lapse from a revocation.
            payload.put("endReason", "EXPIRED");
            payload.put("validTo", String.valueOf(saved.getValidTo()));

            outboxWriter.publish(saved.getTenantId(), "REGULATORY_APPOINTMENT", saved.getId().toString(),
                    "regulatory_appointment", "ended",
                    "org-registry:appointment:ended:" + saved.getId() + ":EXPIRED",
                    payload);

            log.warn("Regulatory appointment expired: id={} org={} role={} validTo={}",
                    saved.getId(), saved.getOrganizationId(), saved.getRoleCode(), saved.getValidTo());
        }
        return lapsed.size();
    }

    public List<RegulatoryAppointmentEntity> listForOrganization(UUID tenantId, UUID organizationId) {
        return appointmentRepository.findByTenantIdAndOrganizationId(tenantId, organizationId);
    }

    public List<RegulatoryAppointmentEntity> listForPerson(UUID tenantId, String personHealthId) {
        return appointmentRepository.findByTenantIdAndPersonHealthId(tenantId, personHealthId);
    }

    /**
     * Whether a role administers the regulator (V009). Read from the vocabulary rather than matched
     * against a hard-coded list, so a role added later is administrative because it is flagged, not
     * because somebody remembered to update this class.
     */
    private boolean isAdministrative(String roleCode) {
        return roleRepository.findById(roleCode)
                .map(AppointmentRoleEntity::isAdministrative)
                .orElse(false);
    }

    /**
     * Bootstrap-only roles exist to solve the cold start — the first administrator cannot be
     * appointed by an administrator — so they may be granted only while the seat is genuinely
     * empty. Without this a founding role could be handed out later as an ordinary appointment,
     * bypassing the four-eyes rail it was created to require.
     */
    private void assertBootstrapRoleAllowed(UUID tenantId, UUID organizationId, String roleCode) {
        AppointmentRoleEntity role = roleRepository.findById(roleCode).orElse(null);
        if (role == null || !role.isBootstrapOnly()) {
            return;
        }
        if (appointmentRepository.countActiveAdministrators(tenantId, organizationId, null) > 0) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ROLE_NOT_AVAILABLE: " + roleCode + " may only be granted while the "
                    + "regulator has no active administrator. Appoint through the ordinary "
                    + "administrative roles instead.");
        }
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
