package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mvumo.domain.ConsentRequestState;
import zw.gov.mohcc.impilo.mvumo.integration.TshepoConsentClient;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reconciles Mvumo's granted consent requests against the trust-layer FHIR directive in
 * tshepo-consent (Phase 2 / TPL-4). Create/withdraw are already synchronous, so the only drift is
 * an <em>out-of-band</em> change to the directive (e.g. revoked directly in tshepo-consent). This
 * job flips the Mvumo aggregate to a terminal conflict state so the divergence is visible and
 * audited, rather than silently stale.
 *
 * <p>OFF by default — ops enables with {@code mvumo.reconciliation.enabled=true}. Reuses the
 * existing {@code SYNC_CONFLICT}/{@code INVALIDATED} states (no migration) and the consent-event
 * audit + outbox. It never *grants* anything; it can only invalidate a stale grant.</p>
 */
@Service
@ConditionalOnProperty(prefix = "mvumo.reconciliation", name = "enabled", havingValue = "true")
public class MvumoConsentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(MvumoConsentReconciliationService.class);
    private static final List<String> GRANTED_STATES =
            List.of(ConsentRequestState.GRANTED.name(), ConsentRequestState.PARTIALLY_GRANTED.name());

    private final ConsentRequestRepository requestRepository;
    private final TshepoConsentClient tshepoConsentClient;
    private final ConsentEventRepository consentEventRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public MvumoConsentReconciliationService(ConsentRequestRepository requestRepository,
                                             TshepoConsentClient tshepoConsentClient,
                                             ConsentEventRepository consentEventRepository,
                                             EventOutboxRepository eventOutboxRepository,
                                             ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.tshepoConsentClient = tshepoConsentClient;
        this.consentEventRepository = consentEventRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${mvumo.reconciliation.interval-ms:3600000}",
            initialDelayString = "${mvumo.reconciliation.initial-delay-ms:120000}")
    public void scheduledReconcile() {
        try {
            int flagged = reconcileOnce();
            if (flagged > 0) {
                log.warn("Consent reconciliation flagged {} drifted grant(s)", flagged);
            }
        } catch (Exception e) {
            log.error("Consent reconciliation run failed: {}", e.getMessage());
        }
    }

    /** Run one reconciliation pass; returns the number of grants flagged as drifted. */
    @Transactional
    public int reconcileOnce() {
        List<ConsentRequestEntity> candidates =
                requestRepository.findByStateInAndTshepoConsentIdIsNotNull(GRANTED_STATES);
        int flagged = 0;
        for (ConsentRequestEntity e : candidates) {
            String status;
            try {
                status = tshepoConsentClient.getDirectiveStatus(e.getTshepoConsentId());
            } catch (Exception ex) {
                // Transient failure — leave as-is, retry next pass (never invalidate on an error).
                log.debug("Reconcile skip {} (status check failed): {}", e.getId(), ex.getMessage());
                continue;
            }
            if (status == null) {
                flagDrift(e, ConsentRequestState.INVALIDATED, "DIRECTIVE_NOT_FOUND");
                flagged++;
            } else if (!"ACTIVE".equalsIgnoreCase(status)) {
                flagDrift(e, ConsentRequestState.SYNC_CONFLICT, "DIRECTIVE_" + status.toUpperCase(java.util.Locale.ROOT));
                flagged++;
            }
        }
        return flagged;
    }

    private void flagDrift(ConsentRequestEntity e, ConsentRequestState newState, String reason) {
        String previous = e.getState();
        e.setState(newState.name());
        e.setUpdatedAt(Instant.now());
        requestRepository.save(e);

        ConsentEventEntity ev = new ConsentEventEntity();
        ev.setTenantId(e.getTenantId());
        ev.setConsentId(e.getId());
        ev.setEventType("CONSENT_RECONCILED");
        ev.setActorRef("system:reconciliation");
        ev.setDetail(writeJson(Map.of("previousState", previous, "newState", newState.name(),
                "reason", reason, "tshepoConsentId", String.valueOf(e.getTshepoConsentId()))));
        consentEventRepository.save(ev);

        EventOutboxEntity row = new EventOutboxEntity();
        row.setAggregateType("ConsentRequest");
        row.setAggregateId(e.getId().toString());
        row.setEventType("impilo.mvumo.consent.reconciled.v1");
        row.setPayload(writeJson(Map.of("consentRequestId", e.getId().toString(),
                "previousState", previous, "newState", newState.name(), "reason", reason)));
        eventOutboxRepository.save(row);

        log.info("Reconciliation flagged consent {} {} -> {} ({})", e.getId(), previous, newState, reason);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
