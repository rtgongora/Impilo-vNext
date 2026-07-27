package zw.gov.mohcc.impilo.governance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.RegulatorBootstrapRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.RegulatorBootstrapRequestRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The regulator bootstrap request (RB-3): a request to appoint a regulator's <em>first</em>
 * administrator, when there is nobody there yet to do it.
 *
 * <p>Owns the domain record and its lifecycle. The four-eyes vehicle is delegated to
 * {@link PlatformOriginService}'s existing two-person rail unchanged — so initiator-cannot-approve
 * and the per-approver unique constraint come for free — and the founding appointment is created
 * by org-registry from the activation event, never here.</p>
 *
 * <p><b>What this class guards.</b> Only the founding role may be bootstrapped; a claimant must be
 * named; and one live request per organisation. It does NOT gate on person assurance: that gate
 * lives at the BFF, where the identity-assurance service is reachable and where the facility- and
 * provider-claim gates already sit. A sovereign service that trusted a body-supplied assurance
 * level would be letting the caller rate themselves.</p>
 */
@Service
public class RegulatorBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(RegulatorBootstrapService.class);

    static final String FOUNDING_ROLE = "FOUNDING_REGULATOR_ADMINISTRATOR";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_PENDING_SECOND = "PENDING_SECOND_APPROVAL";
    private static final String STATUS_ACTIVATED = "ACTIVATED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final List<String> LIVE_STATUSES = List.of(STATUS_SUBMITTED, STATUS_PENDING_SECOND);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I

    private final RegulatorBootstrapRequestRepository repository;
    private final PlatformOriginService platformOriginService;

    public RegulatorBootstrapService(RegulatorBootstrapRequestRepository repository,
                                     PlatformOriginService platformOriginService) {
        this.repository = repository;
        this.platformOriginService = platformOriginService;
    }

    /**
     * Submit a bootstrap request and start its four-eyes vehicle.
     *
     * @param claimantHealthId the person asking to found the regulator — taken from the
     *                         authenticated actor upstream, never from a request body
     */
    @Transactional
    public RegulatorBootstrapRequestEntity submit(UUID tenantId, UUID organizationId,
                                                  String claimantHealthId, String roleCode,
                                                  String evidenceRef,
                                                  Map<String, Object> authoritativeCheckResult,
                                                  String actor) {
        if (claimantHealthId == null || claimantHealthId.isBlank()) {
            throw new IllegalArgumentException("a bootstrap request needs a claimant");
        }
        String role = roleCode == null ? FOUNDING_ROLE : roleCode.trim().toUpperCase();
        if (!FOUNDING_ROLE.equals(role)) {
            // Ordinary administrative roles are granted by an existing administrator once one
            // exists; the cold-start rail is not a general way to mint privileged appointments.
            throw new IllegalArgumentException(
                    "only " + FOUNDING_ROLE + " may be bootstrapped; other roles are appointed by "
                    + "an existing administrator");
        }
        if (repository.existsByTenantIdAndOrganizationIdAndStatusIn(tenantId, organizationId, LIVE_STATUSES)) {
            throw new IllegalStateException(
                    "a bootstrap request is already open for this regulator; withdraw or decide it first");
        }

        RegulatorBootstrapRequestEntity entity = new RegulatorBootstrapRequestEntity();
        entity.setTenantId(tenantId);
        entity.setPublicId(newPublicId());
        entity.setOrganizationId(organizationId);
        entity.setClaimantHealthId(claimantHealthId.trim());
        entity.setClaimedRoleCode(role);
        entity.setEvidenceRef(evidenceRef);
        entity.setAuthoritativeCheckResult(authoritativeCheckResult);
        entity.setStatus(STATUS_SUBMITTED);
        entity.setCreatedBy(actor);
        RegulatorBootstrapRequestEntity saved = repository.save(entity);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("bootstrapRequestId", saved.getId().toString());
        params.put("organizationId", organizationId.toString());
        params.put("personHealthId", saved.getClaimantHealthId());
        params.put("roleCode", role);
        params.put("evidenceRef", evidenceRef);
        AccessRequestEntity vehicle =
                platformOriginService.initiateFoundingRegulatorAdmin(tenantId, claimantHealthId, params);

        saved.setAccessRequestId(vehicle.getId());
        RegulatorBootstrapRequestEntity linked = repository.save(saved);
        log.info("Regulator bootstrap request {} submitted for org {} via access request {}",
                linked.getPublicId(), organizationId, vehicle.getId());
        return linked;
    }

    /**
     * Record one approver's decision, keeping the domain row's status truthful: after the first
     * approval the request reads PENDING_SECOND_APPROVAL, so the console can show "one to go"
     * without joining to the access request. The authoritative approval state stays on the vehicle.
     */
    @Transactional
    public Map<String, Object> approve(UUID accessRequestId, String approverUserId,
                                       String approverRole, String decision, String note) {
        Map<String, Object> result =
                platformOriginService.approve(accessRequestId, approverUserId, approverRole, decision, note);
        repository.findByAccessRequestId(accessRequestId).ifPresent(row -> {
            Object recorded = result.get("approvalsRecorded");
            boolean firstApprovalIn = recorded instanceof Number n && n.longValue() >= 1;
            if (firstApprovalIn && STATUS_SUBMITTED.equals(row.getStatus())) {
                row.setStatus(STATUS_PENDING_SECOND);
                repository.save(row);
            }
        });
        return result;
    }

    /**
     * Activate an approved request. Delegates execution (and the org-registry activation event) to
     * the platform rail, then marks the domain row ACTIVATED. The rail refuses to execute anything
     * that is not APPROVED, so the four-eyes gate cannot be skipped here.
     */
    @Transactional
    public RegulatorBootstrapRequestEntity activate(UUID accessRequestId, String actorId) {
        RegulatorBootstrapRequestEntity row = repository.findByAccessRequestId(accessRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no bootstrap request for access request " + accessRequestId));
        if (STATUS_ACTIVATED.equals(row.getStatus())) {
            return row; // idempotent
        }
        platformOriginService.execute(accessRequestId, actorId);
        row.setStatus(STATUS_ACTIVATED);
        row.setActivatedAt(Instant.now());
        return repository.save(row);
    }

    @Transactional
    public RegulatorBootstrapRequestEntity withdraw(UUID tenantId, String publicId, String reason) {
        RegulatorBootstrapRequestEntity row = require(tenantId, publicId);
        if (STATUS_ACTIVATED.equals(row.getStatus())) {
            throw new IllegalStateException("an activated bootstrap request cannot be withdrawn");
        }
        row.setStatus(STATUS_WITHDRAWN);
        row.setRejectionReason(reason);
        return repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<RegulatorBootstrapRequestEntity> listByStatus(UUID tenantId, String status) {
        return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                tenantId, status == null ? STATUS_SUBMITTED : status.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public RegulatorBootstrapRequestEntity require(UUID tenantId, String publicId) {
        return repository.findByTenantIdAndPublicId(tenantId, publicId)
                .orElseThrow(() -> new IllegalArgumentException("bootstrap request not found: " + publicId));
    }

    public Map<String, Object> view(RegulatorBootstrapRequestEntity r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publicId", r.getPublicId());
        out.put("organizationId", r.getOrganizationId().toString());
        out.put("claimedRoleCode", r.getClaimedRoleCode());
        out.put("status", r.getStatus());
        out.put("accessRequestId", r.getAccessRequestId() == null ? null : r.getAccessRequestId().toString());
        out.put("hasEvidence", r.getEvidenceRef() != null && !r.getEvidenceRef().isBlank());
        out.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        out.put("activatedAt", r.getActivatedAt() == null ? null : r.getActivatedAt().toString());
        // Deliberately NOT surfaced: claimant health id and the raw evidence ref/authoritative
        // check. A request's status is a legitimate read; who claimed it and on what document is
        // steward-only.
        return out;
    }

    private static String newPublicId() {
        StringBuilder sb = new StringBuilder("RBR-");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
