package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.GrantPrivilegeRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.PrivilegeDecisionRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeApprovalEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.PrivilegeApprovalRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.PrivilegeRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.util.List;

/**
 * Privilege (credentialing) management service.
 *
 * Manages the granting, approval, and revocation of facility-level
 * privileges for healthcare providers. Privileges control which facilities
 * and workspaces a provider can access.
 *
 * Privilege lifecycle:
 *   PENDING -> APPROVED (via approvePrivilege)
 *   PENDING -> REJECTED (via approvePrivilege)
 *   APPROVED -> REVOKED (via revokePrivilege)
 *
 * Every state change creates an approval audit record and publishes
 * a domain event via the outbox.
 */
@Service
public class CredentialingService {

    private static final Logger log = LoggerFactory.getLogger(CredentialingService.class);

    private final PrivilegeRepository privilegeRepository;
    private final PrivilegeApprovalRepository approvalRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public CredentialingService(PrivilegeRepository privilegeRepository,
                                PrivilegeApprovalRepository approvalRepository,
                                ProviderRepository providerRepository,
                                EventOutboxRepository outboxRepository) {
        this.privilegeRepository = privilegeRepository;
        this.approvalRepository = approvalRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    // ---- Public API ----

    @Transactional
    public PrivilegeEntity grantPrivilege(String providerPublicId, GrantPrivilegeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Granting privilege: providerPublicId={}, facilityId={}, type={}, actor={}",
                providerPublicId, request.facilityId(), request.privilegeType(), ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        PrivilegeEntity privilege = new PrivilegeEntity();
        privilege.setProvider(provider);
        privilege.setTenantId(ctx.tenantId());
        privilege.setFacilityId(request.facilityId());
        privilege.setWorkspaceId(request.workspaceId());
        privilege.setScope(request.scope());
        privilege.setPrivilegeType(request.privilegeType());
        privilege.setStatus("PENDING");
        privilege.setGrantedBy(ctx.actorId());
        privilege.setGrantedAt(Instant.now());
        privilege.setValidFrom(request.validFrom());
        privilege.setValidTo(request.validTo());
        privilege.setNotes(request.notes());

        if (request.supervisingProviderId() != null) {
            ProviderEntity supervisor = providerRepository.findById(request.supervisingProviderId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Supervising provider not found: " + request.supervisingProviderId()));
            privilege.setSupervisingProvider(supervisor);
        }

        privilege = privilegeRepository.save(privilege);

        PrivilegeApprovalEntity approval = new PrivilegeApprovalEntity();
        approval.setPrivilege(privilege);
        approval.setTenantId(ctx.tenantId());
        approval.setAction("GRANT_REQUESTED");
        approval.setActorId(ctx.actorId());
        approval.setActorRole(ctx.actorType());
        approval.setReason("Privilege grant requested");
        approvalRepository.save(approval);

        log.info("Privilege granted: privilegeId={}, status=PENDING", privilege.getId());

        publishEvent("PRIVILEGE", providerPublicId,
                "varapi.privilege.granted",
                String.format("{\"privilegeId\":%d,\"providerPublicId\":\"%s\"," +
                                "\"facilityId\":%s,\"privilegeType\":\"%s\",\"status\":\"PENDING\"}",
                        privilege.getId(), providerPublicId,
                        request.facilityId(), request.privilegeType()));

        return privilege;
    }

    @Transactional
    public PrivilegeEntity revokePrivilege(String providerPublicId, Long privilegeId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking privilege: providerPublicId={}, privilegeId={}, reason={}, actor={}",
                providerPublicId, privilegeId, reason, ctx.actorId());

        PrivilegeEntity privilege = privilegeRepository.findById(privilegeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Privilege not found: " + privilegeId));

        privilege.setStatus("REVOKED");
        privilege.setRevokedBy(ctx.actorId());
        privilege.setRevokedAt(Instant.now());
        privilege.setRevocationReason(reason);
        privilege = privilegeRepository.save(privilege);

        PrivilegeApprovalEntity approval = new PrivilegeApprovalEntity();
        approval.setPrivilege(privilege);
        approval.setTenantId(ctx.tenantId());
        approval.setAction("REVOKED");
        approval.setActorId(ctx.actorId());
        approval.setActorRole(ctx.actorType());
        approval.setReason(reason);
        approvalRepository.save(approval);

        log.info("Privilege revoked: privilegeId={}, providerPublicId={}", privilegeId, providerPublicId);

        publishEvent("PRIVILEGE", providerPublicId,
                "varapi.privilege.revoked",
                String.format("{\"privilegeId\":%d,\"providerPublicId\":\"%s\"," +
                                "\"reason\":\"%s\",\"status\":\"REVOKED\"}",
                        privilegeId, providerPublicId, reason != null ? reason : ""));

        return privilege;
    }

    @Transactional(readOnly = true)
    public List<PrivilegeEntity> listPrivileges(String providerPublicId) {
        TrustContextHolder.require();
        log.debug("Listing privileges: providerPublicId={}", providerPublicId);

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        return privilegeRepository.findByProviderId(provider.getId());
    }

    @Transactional
    public PrivilegeEntity approvePrivilege(Long privilegeId, PrivilegeDecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String action = request.action().name();
        log.info("Approving privilege: privilegeId={}, action={}, actor={}",
                privilegeId, action, ctx.actorId());

        PrivilegeEntity privilege = privilegeRepository.findById(privilegeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Privilege not found: " + privilegeId));

        if (!privilege.isPending()) {
            throw new IllegalStateException(
                    "Privilege is not in PENDING status: current=" + privilege.getStatus());
        }

        privilege.setStatus(action);
        privilege = privilegeRepository.save(privilege);

        PrivilegeApprovalEntity approval = new PrivilegeApprovalEntity();
        approval.setPrivilege(privilege);
        approval.setTenantId(ctx.tenantId());
        approval.setAction(action);
        approval.setActorId(ctx.actorId());
        approval.setActorRole(ctx.actorType());
        approval.setReason(request.reason());
        approvalRepository.save(approval);

        log.info("Privilege {} : privilegeId={}", action, privilegeId);

        return privilege;
    }

    @Transactional(readOnly = true)
    public Page<PrivilegeEntity> listPendingApprovals(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Listing pending approvals: tenantId={}", ctx.tenantId());

        return privilegeRepository.findByTenantIdAndStatus(ctx.tenantId(), "PENDING", pageable);
    }

    // ---- Private helpers ----

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        // Mandatory outbox hygiene — null pod_id/idempotency_key poisons the publisher drain.
        zw.gov.mohcc.impilo.shared.auth.TrustContext ctx = zw.gov.mohcc.impilo.shared.auth.TrustContextHolder.get();
        if (ctx != null && ctx.tenantId() != null) {
            event.setTenantId(ctx.tenantId().toString());
        }
        event.setPodId("national-spine");
        event.setIdempotencyKey("varapi:cred:" + eventType + ":" + java.util.UUID.randomUUID());
        outboxRepository.save(event);
    }
}
