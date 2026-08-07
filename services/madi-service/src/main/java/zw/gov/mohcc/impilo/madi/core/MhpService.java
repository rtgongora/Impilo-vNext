package zw.gov.mohcc.impilo.madi.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.MhpActivationEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.MhpPackEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.MhpActivationRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.MhpPackRepository;
import zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Massive Haemorrhage Protocol activation + pack tracking (madi V200, W8a).
 *
 * <p>The protocol-level layer, distinct from {@link BloodOrderService#emergencyRelease}, which
 * releases one existing blood order. Every action here (activation AND each pack issuance) reuses
 * the SAME {@link EmergencyAccessGuard} break-glass gate {@code emergencyRelease} already enforces —
 * this is not a second authorisation mechanism. Each pack is gated independently, not just the
 * initial activation: a pack issued an hour into a protocol is its own high-risk action and gets its
 * own audited break-glass decision, the same way this pack's acceptance handshake (pct W9) requires
 * the accepting party's own act rather than trusting an earlier authorisation to cover everything
 * that follows.
 */
@Service
public class MhpService {

    private final MhpActivationRepository activationRepository;
    private final MhpPackRepository packRepository;
    private final MadiEventEmitter eventEmitter;
    private final EmergencyAccessGuard emergencyAccessGuard;

    public MhpService(MhpActivationRepository activationRepository,
                      MhpPackRepository packRepository,
                      MadiEventEmitter eventEmitter,
                      EmergencyAccessGuard emergencyAccessGuard) {
        this.activationRepository = activationRepository;
        this.packRepository = packRepository;
        this.eventEmitter = eventEmitter;
        this.emergencyAccessGuard = emergencyAccessGuard;
    }

    /** @throws EmergencyAccessGuard.EmergencyAccessDeniedException without an emergency purpose OR an active grant. */
    @Transactional
    public MhpActivationEntity activate(UUID tenantId, UUID facilityId, String patientCpid,
                                        UUID traumaEpisodeId, UUID emergencyEpisodeId,
                                        String activatedBy, String purposeOfUse,
                                        String escalationGrantId) {
        emergencyAccessGuard.requireBreakGlass(new EmergencyAccessGuard.EmergencyAccessRequest(
                tenantId == null ? null : tenantId.toString(), activatedBy, purposeOfUse,
                "MHP_ACTIVATION", patientCpid, escalationGrantId));

        MhpActivationEntity activation = new MhpActivationEntity();
        activation.setTenantId(tenantId);
        activation.setFacilityId(facilityId);
        activation.setPatientCpid(patientCpid);
        activation.setTraumaEpisodeId(traumaEpisodeId);
        activation.setEmergencyEpisodeId(emergencyEpisodeId);
        activation.setActivatedBy(activatedBy);
        activation.setPurposeOfUse(purposeOfUse.trim().toUpperCase());
        activation = activationRepository.save(activation);

        emit(activation, "MHP_ACTIVATED", Map.of());
        return activation;
    }

    /**
     * Issue one pack (a component + unit count) under an active activation. Refused if the
     * activation is already stood down, or if this exact (activation, pack_number, component_type)
     * was already issued — the database is the real guarantee (uq_mhp_pack_activation_number_
     * component); this maps that into a clear 409 instead of an opaque 500.
     */
    @Transactional
    public MhpPackEntity issuePack(UUID activationId, UUID tenantId, int packNumber, String componentType,
                                   int unitsIssued, String bloodGroup, String issuedBy, String purposeOfUse,
                                     String escalationGrantId) {
        MhpActivationEntity activation = loadActivation(activationId, tenantId);
        if (!"ACTIVE".equals(activation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "MHP activation " + activationId + " is " + activation.getStatus() + ", not ACTIVE");
        }
        emergencyAccessGuard.requireBreakGlass(new EmergencyAccessGuard.EmergencyAccessRequest(
                tenantId == null ? null : tenantId.toString(), issuedBy, purposeOfUse,
                "MHP_PACK_ISSUE", activation.getPatientCpid(), escalationGrantId));

        MhpPackEntity pack = new MhpPackEntity();
        pack.setTenantId(tenantId);
        pack.setActivationId(activationId);
        pack.setPackNumber(packNumber);
        pack.setComponentType(componentType);
        pack.setUnitsIssued(unitsIssued);
        pack.setBloodGroup(bloodGroup);
        pack.setIssuedBy(issuedBy);

        try {
            pack = packRepository.save(pack);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pack " + packNumber + "/" + componentType + " was already issued for this activation "
                            + "(retry of the same request, not a new release)");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packNumber", packNumber);
        payload.put("componentType", componentType);
        payload.put("unitsIssued", unitsIssued);
        payload.put("bloodGroup", bloodGroup);
        emit(activation, "MHP_PACK_ISSUED", payload);
        return pack;
    }

    /** Stand down an activation. Requires a reason — enforced again by chk_mhp_standdown_pair. */
    @Transactional
    public MhpActivationEntity standDown(UUID activationId, UUID tenantId, String stoodDownBy, String reason) {
        MhpActivationEntity activation = loadActivation(activationId, tenantId);
        if (!"ACTIVE".equals(activation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "MHP activation " + activationId + " is already " + activation.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stand_down_reason is required");
        }
        activation.setStatus("STOOD_DOWN");
        activation.setStoodDownBy(stoodDownBy);
        activation.setStoodDownAt(OffsetDateTime.now());
        activation.setStandDownReason(reason);
        activation = activationRepository.save(activation);
        emit(activation, "MHP_STOOD_DOWN", Map.of("reason", reason));
        return activation;
    }

    @Transactional(readOnly = true)
    public MhpActivationEntity getActivation(UUID activationId, UUID tenantId) {
        return loadActivation(activationId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<MhpPackEntity> packs(UUID activationId, UUID tenantId) {
        loadActivation(activationId, tenantId); // tenant-scoping check
        return packRepository.findByActivationIdOrderByPackNumberAsc(activationId);
    }

    /** Sum of units issued per component — the ratio actually delivered, derived from the packs. */
    @Transactional(readOnly = true)
    public Map<String, Integer> ratioSummary(UUID activationId, UUID tenantId) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (MhpPackEntity p : packs(activationId, tenantId)) {
            totals.merge(p.getComponentType(), p.getUnitsIssued(), Integer::sum);
        }
        return totals;
    }

    private MhpActivationEntity loadActivation(UUID activationId, UUID tenantId) {
        return activationRepository.findByActivationIdAndTenantId(activationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MHP activation not found"));
    }

    private void emit(MhpActivationEntity a, String eventType, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("activationId", a.getActivationId().toString());
        payload.put("patientCpid", a.getPatientCpid());
        payload.put("traumaEpisodeId", a.getTraumaEpisodeId() != null ? a.getTraumaEpisodeId().toString() : null);
        payload.put("emergencyEpisodeId", a.getEmergencyEpisodeId() != null ? a.getEmergencyEpisodeId().toString() : null);
        payload.put("status", a.getStatus());
        payload.put("breakGlass", true);
        eventEmitter.emit("MHP_ACTIVATION", a.getActivationId().toString(), eventType,
                "MHP_ACTIVATION", a.getActivationId().toString(), payload, a.getTenantId());
    }
}
