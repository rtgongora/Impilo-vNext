package zw.gov.mohcc.impilo.orgregistry.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrgInvitationRepository;

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
    private final OrgRegistryOutboxWriter outboxWriter;

    public OrgInvitationService(OrgInvitationRepository invitationRepository,
                                OrganizationRepository organizationRepository,
                                AffiliationService affiliationService,
                                OrgRegistryOutboxWriter outboxWriter) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.affiliationService = affiliationService;
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
        if (request.role() == null || request.role().isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        int expiryHours = request.expiresInHours() != null && request.expiresInHours() > 0
                ? request.expiresInHours() : DEFAULT_EXPIRY_HOURS;

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
        invitation.setRole(request.role().trim());
        invitation.setToken(newToken());
        invitation.setStatus("PENDING");
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusHours(expiryHours));

        OrgInvitationEntity saved = invitationRepository.save(invitation);
        publish(saved, "issued");
        log.info("org-invitation issued id={} org={} role={}", saved.getId(), organizationId, saved.getRole());
        return saved;
    }

    /**
     * Accept a pending invitation by token. Creates an affiliation for the accepting person and
     * moves the invitation to ACCEPTED. Idempotent per invitation: a second accept returns the
     * already-accepted invitation only when the accepting Health ID matches.
     */
    @Transactional
    public OrgInvitationEntity accept(UUID tenantId, OrgRegistryDtos.AcceptInvitationRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (request.acceptedByHealthId() == null || request.acceptedByHealthId().isBlank()) {
            throw new IllegalArgumentException("acceptedByHealthId is required");
        }
        OrgInvitationEntity invitation = invitationRepository
                .findByTokenAndTenantId(request.token().trim(), tenantId)
                .orElseThrow(() -> new NoSuchElementException("Invitation token not recognised"));

        String healthId = request.acceptedByHealthId().trim();

        if ("ACCEPTED".equals(invitation.getStatus())) {
            if (healthId.equals(invitation.getAcceptedByHealthId())) {
                return invitation; // idempotent replay
            }
            throw new IllegalStateException("Invitation already accepted by another person");
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            throw new IllegalStateException("Invitation is " + invitation.getStatus() + " and cannot be accepted");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (invitation.getExpiresAt().isBefore(now)) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
            publish(invitation, "expired");
            throw new IllegalStateException("Invitation has expired");
        }

        AffiliationEntity affiliation;
        try {
            affiliation = affiliationService.create(tenantId, invitation.getOrganizationId(),
                    new OrgRegistryDtos.CreateAffiliationRequest(
                            "PERSON",
                            healthId,
                            invitation.getRole(),
                            "ACTIVE",
                            "ORG_INVITATION",
                            LocalDate.now(ZoneOffset.UTC),
                            null));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create affiliation for accepted invitation", e);
        }

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(now);
        invitation.setAcceptedByHealthId(healthId);
        invitation.setAffiliationId(affiliation.getId());
        OrgInvitationEntity saved = invitationRepository.save(invitation);
        publish(saved, "accepted");
        log.info("org-invitation accepted id={} affiliation={} health={}",
                saved.getId(), affiliation.getId(), healthId);
        return saved;
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
}
