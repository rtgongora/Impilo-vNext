package zw.gov.mohcc.impilo.vito.core.issuance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.*;
import zw.gov.mohcc.impilo.vito.core.id.ImpiloIdFormat;
import zw.gov.mohcc.impilo.vito.core.card.CardLifecycleService;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class IssuanceStateMachineService {

    private final IssuanceRequestRepository issuanceRepo;
    private final ClientRepository clientRepo;
    private final ImpiloIdFormat impiloIdFormat;
    private final ImpiloIdAliasService aliasService;
    private final CardLifecycleService cardService;
    private final EventOutboxRepository outboxRepo;

    public IssuanceStateMachineService(IssuanceRequestRepository issuanceRepo,
                                        ClientRepository clientRepo,
                                        ImpiloIdFormat impiloIdFormat,
                                        ImpiloIdAliasService aliasService,
                                        CardLifecycleService cardService,
                                        EventOutboxRepository outboxRepo) {
        this.issuanceRepo = issuanceRepo;
        this.clientRepo = clientRepo;
        this.impiloIdFormat = impiloIdFormat;
        this.aliasService = aliasService;
        this.cardService = cardService;
        this.outboxRepo = outboxRepo;
    }

    /**
     * Submit a new issuance request.
     * Portal channel requests are auto-converted to PROOFING (require appointment).
     */
    @Transactional
    public IssuanceRequestEntity submit(UUID tenantId, UUID healthId, IssuanceType type,
                                         IssuanceChannel channel, String actorId) {
        // Verify client exists
        ClientEntity client = clientRepo.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        // Check no active issuance exists
        List<IssuanceState> terminal = List.of(IssuanceState.DELIVERED, IssuanceState.REJECTED, IssuanceState.EXPIRED);
        issuanceRepo.findByTenantIdAndHealthIdAndStateNotIn(tenantId, healthId, terminal)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Active issuance already exists: " + existing.getState());
                });

        IssuanceRequestEntity req = new IssuanceRequestEntity();
        req.setTenantId(tenantId);
        req.setHealthId(healthId);
        req.setType(type);
        req.setChannel(channel);
        // Portal issuance defaults to PROOFING (requires in-person appointment)
        req.setState(channel == IssuanceChannel.PORTAL ? IssuanceState.PROOFING : IssuanceState.SUBMITTED);
        req = issuanceRepo.save(req);

        publishEvent("ISSUANCE", req.getId().toString(), "vito.issuance.submitted",
                "{\"tenantId\":\"" + tenantId + "\",\"healthId\":\"" + healthId + "\",\"type\":\"" + type + "\"}");

        return req;
    }

    /**
     * Move to PROOFING state (assign an operator).
     */
    @Transactional
    public IssuanceRequestEntity startProofing(UUID tenantId, Long requestId, String assignedTo) {
        IssuanceRequestEntity req = findAndValidateState(tenantId, requestId, IssuanceState.SUBMITTED);
        req.setState(IssuanceState.PROOFING);
        req.setAssignedTo(assignedTo);
        req.setProofingStartedAt(OffsetDateTime.now());
        return issuanceRepo.save(req);
    }

    /**
     * Approve after proofing is complete.
     */
    @Transactional
    public IssuanceRequestEntity approve(UUID tenantId, Long requestId, String approvedBy) {
        IssuanceRequestEntity req = findAndValidateState(tenantId, requestId, IssuanceState.PROOFING);
        req.setState(IssuanceState.APPROVED);
        req.setApprovedAt(OffsetDateTime.now());
        req = issuanceRepo.save(req);

        publishEvent("ISSUANCE", req.getId().toString(), "vito.issuance.approved",
                "{\"tenantId\":\"" + tenantId + "\",\"healthId\":\"" + req.getHealthId() + "\"}");
        return req;
    }

    /**
     * Issue the Impilo ID and card.
     * Generates the human-facing Impilo ID (#########X) and requests a card.
     */
    @Transactional
    public IssuanceRequestEntity issue(UUID tenantId, Long requestId, String issuedBy) {
        IssuanceRequestEntity req = findAndValidateState(tenantId, requestId, IssuanceState.APPROVED);

        // Generate Impilo ID
        String impiloId = impiloIdFormat.generate();
        req.setImpiloIdIssued(impiloId);

        // Update client
        ClientEntity client = clientRepo.findByTenantIdAndHealthId(tenantId, req.getHealthId())
                .orElseThrow();
        client.setImpiloId(impiloId);
        if (client.getStatus() == IdentityStatus.PROVISIONAL) {
            client.setStatus(IdentityStatus.VERIFIED);
        }
        clientRepo.save(client);

        // Issue alias for the Impilo ID
        aliasService.issueAlias(tenantId, req.getHealthId(), "IMPILO_ID", impiloId);

        // Request card
        SmartCardEntity card = cardService.requestCard(tenantId, req.getHealthId(), null, issuedBy);
        req.setCardId(card.getId());

        req.setState(IssuanceState.ISSUED);
        req.setIssuedAt(OffsetDateTime.now());
        req = issuanceRepo.save(req);

        publishEvent("ISSUANCE", req.getId().toString(), "vito.issuance.issued",
                "{\"tenantId\":\"" + tenantId + "\",\"healthId\":\"" + req.getHealthId() + "\",\"impiloId\":\"" + impiloId + "\"}");
        return req;
    }

    /**
     * Mark as delivered (card collected by client or delegate).
     */
    @Transactional
    public IssuanceRequestEntity deliver(UUID tenantId, Long requestId) {
        IssuanceRequestEntity req = findAndValidateState(tenantId, requestId, IssuanceState.ISSUED);
        req.setState(IssuanceState.DELIVERED);
        req.setDeliveredAt(OffsetDateTime.now());
        return issuanceRepo.save(req);
    }

    /**
     * Reject an issuance request.
     */
    @Transactional
    public IssuanceRequestEntity reject(UUID tenantId, Long requestId, String reason) {
        IssuanceRequestEntity req = getRequest(tenantId, requestId);
        if (req.getState() == IssuanceState.DELIVERED || req.getState() == IssuanceState.REJECTED) {
            throw new IllegalStateException("Cannot reject from state: " + req.getState());
        }
        req.setState(IssuanceState.REJECTED);
        req.setRejectionReason(reason);
        req.setRejectedAt(OffsetDateTime.now());
        return issuanceRepo.save(req);
    }

    @Transactional(readOnly = true)
    public Page<IssuanceRequestEntity> listByState(UUID tenantId, IssuanceState state, Pageable pageable) {
        return issuanceRepo.findByTenantIdAndState(tenantId, state, pageable);
    }

    @Transactional(readOnly = true)
    public IssuanceRequestEntity getRequest(UUID tenantId, Long requestId) {
        return issuanceRepo.findById(requestId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Issuance request not found"));
    }

    private IssuanceRequestEntity findAndValidateState(UUID tenantId, Long requestId, IssuanceState expected) {
        IssuanceRequestEntity req = getRequest(tenantId, requestId);
        if (req.getState() != expected) {
            throw new IllegalStateException("Expected state " + expected + " but got " + req.getState());
        }
        return req;
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepo.save(event);
    }
}
