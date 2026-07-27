package zw.gov.mohcc.impilo.orgregistry.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AppointmentRoleRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrgInvitationRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Organisation-invitation onboarding (Channel-C complement to self-submitted claims).
 *
 * <p>An organisation's authorized representative invites a person to a role; the invitee
 * accepts by token to establish an affiliation. Because the inviting organisation is the
 * authority asserting the role, acceptance creates the affiliation directly — there is no
 * adjudication queue (that path is the self-submitted {@link ClaimSubmissionService}).
 */
@Service
public class OrgInvitationService {

    private static final Logger log = LoggerFactory.getLogger(OrgInvitationService.class);
    private static final int DEFAULT_EXPIRY_HOURS = 168; // 7 days
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrgInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final AffiliationService affiliationService;
    private final RegulatoryAppointmentService appointmentService;
    private final AppointmentRoleRepository roleRepository;
    private final OrgRegistryOutboxWriter outboxWriter;

    public OrgInvitationService(OrgInvitationRepository invitationRepository,
                                OrganizationRepository organizationRepository,
                                AffiliationService affiliationService,
                                RegulatoryAppointmentService appointmentService,
                                AppointmentRoleRepository roleRepository,
                                OrgRegistryOutboxWriter outboxWriter) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.affiliationService = affiliationService;
        this.appointmentService = appointmentService;
        this.roleRepository = roleRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<OrgInvitationEntity> list(UUID organizationId) {
        return invitationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Transactional
    public OrgInvitationEntity create(UUID tenantId, UUID organizationId,
                                      OrgRegistryDtos.CreateInvitationRequest request) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new NoSuchElementException("Organization not found: " + organizationId);
        }
        if (request.inviteeIdentifier() == null || request.inviteeIdentifier().isBlank()) {
            throw new IllegalArgumentException("inviteeIdentifier is required");
        }
        // A governed role_code, when present, must be one of the closed appointment-role vocabulary.
        // The DB FK enforces this too, but validating here turns a DataIntegrityViolation at save
        // into a clean 4xx and lets the caller be told which value was wrong.
        String roleCode = request.roleCode() == null || request.roleCode().isBlank()
                ? null : request.roleCode().trim().toUpperCase();
        if (roleCode != null && roleRepository.findById(roleCode).isEmpty()) {
            throw new IllegalArgumentException("roleCode must be one of the appointment-role vocabulary");
        }
        // Exactly one of role_code (governed) or role (legacy free-text) must describe the invite.
        if (roleCode == null && (request.role() == null || request.role().isBlank())) {
            throw new IllegalArgumentException("role or roleCode is required");
        }
        int expiryHours = request.expiresInHours() != null && request.expiresInHours() > 0
                ? request.expiresInHours() : DEFAULT_EXPIRY_HOURS;

        // The raw token is generated here, hashed for storage, and returned once on the object so the
        // issuer can build the one-time link. It is never persisted.
        String rawToken = newToken();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OrgInvitationEntity invitation = new OrgInvitationEntity();
        invitation.setId(UUID.randomUUID());
        invitation.setTenantId(tenantId);
        invitation.setOrganizationId(organizationId);
        invitation.setFacilityUuid(request.facilityUuid());
        invitation.setInvitedByRepId(request.invitedByRepId());
        invitation.setInviteeIdentifier(request.inviteeIdentifier().trim());
        invitation.setInviteeIdentifierType(
                request.inviteeIdentifierType() == null || request.inviteeIdentifierType().isBlank()
                        ? "EMAIL" : request.inviteeIdentifierType().trim().toUpperCase());
        // role stays non-null for the schema; a governed invite carries the code as the role too.
        invitation.setRole(request.role() != null && !request.role().isBlank()
                ? request.role().trim() : roleCode);
        invitation.setRoleCode(roleCode);
        invitation.setTokenHash(hash(rawToken));
        invitation.setStatus("PENDING");
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusHours(expiryHours));

        OrgInvitationEntity saved = invitationRepository.save(invitation);
        saved.setRawToken(rawToken); // transient, returned once, never stored
        publish(saved, "issued");
        log.info("org-invitation issued id={} org={} role={} roleCode={}",
                saved.getId(), organizationId, saved.getRole(), roleCode);
        return saved;
    }

    /**
     * Accept an invitation by its single-use token.
     *
     * <p><b>The token is matched by hash</b> (V010): the presented token is hashed and looked up,
     * so a table read is never a set of live links. <b>Redemption is atomic</b>: a conditional
     * UPDATE claims the PENDING row, so two acceptances of one token cannot both win — the loser
     * gets a zero row-count, not a second acceptance.</p>
     *
     * <p><b>Non-redeemable tokens get a uniform answer.</b> Unknown, expired, revoked and
     * already-accepted-by-another all surface the same {@link NoSuchElementException} — the previous
     * code returned distinct errors, which told a probing caller whether a token existed and what
     * state it was in. The one exception is an idempotent replay by the <em>same</em> person, which
     * returns the accepted invitation.</p>
     *
     * <p><b>A governed role mints an appointment, not an affiliation.</b> When the invitation carries
     * a {@code role_code}, acceptance creates a PENDING_VERIFICATION regulatory appointment with
     * {@code source=INVITATION} — the value the appointment model allows and that nothing has been
     * writing — so colleagues are invited into the four-eyes-verified appointment path rather than
     * silently affiliated. It lands PENDING, never ACTIVE: the invitation proves the invite, not the
     * person's authority to hold the role.</p>
     */
    @Transactional
    public OrgInvitationEntity accept(UUID tenantId, OrgRegistryDtos.AcceptInvitationRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (request.acceptedByHealthId() == null || request.acceptedByHealthId().isBlank()) {
            throw new IllegalArgumentException("acceptedByHealthId is required");
        }
        String healthId = request.acceptedByHealthId().trim();
        OrgInvitationEntity invitation = invitationRepository
                .findByTokenHashAndTenantId(hash(request.token().trim()), tenantId)
                .orElseThrow(OrgInvitationService::notRecognised);

        // Idempotent replay by the same person is the only non-PENDING state that succeeds.
        if ("ACCEPTED".equals(invitation.getStatus())) {
            if (healthId.equals(invitation.getAcceptedByHealthId())) {
                return invitation;
            }
            throw notRecognised();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!"PENDING".equals(invitation.getStatus()) || invitation.getExpiresAt().isBefore(now)) {
            // Revoked, already-expired, or now expired — a uniform answer, no state leaked. A newly
            // expired PENDING row is stamped so the queue reflects reality.
            if ("PENDING".equals(invitation.getStatus())) {
                invitation.setStatus("EXPIRED");
                invitationRepository.save(invitation);
                publish(invitation, "expired");
            }
            throw notRecognised();
        }

        // Atomic single-use claim. The loser of a concurrent race sees 0 and is refused uniformly.
        if (invitationRepository.redeem(invitation.getId(), now, healthId) != 1) {
            throw notRecognised();
        }

        UUID affiliationId = null;
        if (invitation.getRoleCode() != null) {
            // Governed role: mint a PENDING_VERIFICATION appointment (source=INVITATION), not an
            // affiliation. The invite proves the invite; verification proves the authority.
            try {
                appointmentService.create(tenantId, invitation.getOrganizationId(),
                        new OrgRegistryDtos.CreateAppointmentRequest(
                                healthId, invitation.getRoleCode(), null, "INVITATION",
                                "org-invitation:" + invitation.getId(),
                                invitation.getInvitedByRepId() == null
                                        ? null : invitation.getInvitedByRepId().toString(),
                                null, null),
                        "ORG_INVITATION");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to mint appointment for accepted invitation", e);
            }
        } else {
            try {
                AffiliationEntity affiliation = affiliationService.create(
                        tenantId, invitation.getOrganizationId(),
                        new OrgRegistryDtos.CreateAffiliationRequest(
                                "PERSON", healthId, invitation.getRole(), "ACTIVE", "ORG_INVITATION",
                                LocalDate.now(ZoneOffset.UTC), null));
                affiliationId = affiliation.getId();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create affiliation for accepted invitation", e);
            }
        }

        // Reflect the atomic claim on the managed entity + record the resulting link, in the same tx.
        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(now);
        invitation.setAcceptedByHealthId(healthId);
        invitation.setAffiliationId(affiliationId);
        OrgInvitationEntity saved = invitationRepository.save(invitation);
        publish(saved, "accepted");
        log.info("org-invitation accepted id={} roleCode={} affiliation={} health={}",
                saved.getId(), saved.getRoleCode(), affiliationId, healthId);
        return saved;
    }

    /** The single, state-free answer for any non-redeemable token — nothing about it is disclosed. */
    private static NoSuchElementException notRecognised() {
        return new NoSuchElementException("Invitation is not valid");
    }

    @Transactional
    public OrgInvitationEntity revoke(UUID tenantId, UUID invitationId) {
        OrgInvitationEntity invitation = invitationRepository.findById(invitationId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NoSuchElementException("Invitation not found: " + invitationId));
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new IllegalStateException("Only PENDING invitations can be revoked (is " + invitation.getStatus() + ")");
        }
        invitation.setStatus("REVOKED");
        OrgInvitationEntity saved = invitationRepository.save(invitation);
        publish(saved, "revoked");
        return saved;
    }

    private void publish(OrgInvitationEntity invitation, String action) {
        try {
            outboxWriter.publish(invitation.getTenantId(), "invitation", invitation.getId().toString(),
                    "invitation", action,
                    "org-registry:invitation:" + action + ":" + invitation.getId(),
                    Map.of(
                            "invitationId", invitation.getId().toString(),
                            "organizationId", invitation.getOrganizationId().toString(),
                            "role", invitation.getRole(),
                            "status", invitation.getStatus()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write invitation outbox event", e);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex of a token — only the hash is ever persisted (mirrors ProviderBootstrapService). */
    private static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
