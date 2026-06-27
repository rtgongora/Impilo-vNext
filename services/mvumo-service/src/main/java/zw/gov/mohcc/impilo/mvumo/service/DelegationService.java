package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.DelegationRelationshipEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.DelegationRelationshipRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates L5 delegated/assisted-access relationships (G-CZO-03). Mvumo owns the relationship
 * act-of-record; the trust plane (PolicyEngine Step 4.5) authorises each delegated action by calling
 * {@link #resolve}. This service never decides access — it records, lists, revokes, and resolves.
 */
@Service
public class DelegationService {

    private static final Set<String> RELATIONSHIP_TYPES =
            Set.of("GUARDIAN", "CAREGIVER", "CHW", "FACILITY_STAFF");
    private static final Set<String> LEGAL_BASES =
            Set.of("CONSENT", "GUARDIANSHIP", "FACILITY_ASSIGNMENT");
    /**
     * Non-CONSENT delegations assert authority OVER another subject (guardian over ward, facility over
     * patient); they must be created by an administrative authority, never self-asserted by a citizen.
     */
    private static final Set<String> ADMIN_ACTOR_TYPES = Set.of("PROVIDER", "OPERATOR", "SYSTEM");
    /** Consent-request states that constitute a valid, identity-verified grant. */
    private static final Set<String> GRANTED_CONSENT_STATES = Set.of("GRANTED", "ACTIVE", "VERIFIED");
    /** A delegation grant may not lower the delegate's assurance floor below this policy minimum. */
    private static final int MIN_DELEGATE_LOA_FLOOR = 2;

    private final DelegationRelationshipRepository repository;
    private final ConsentEventRepository consentEventRepository;
    private final ConsentRequestRepository consentRequestRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public DelegationService(DelegationRelationshipRepository repository,
                             ConsentEventRepository consentEventRepository,
                             ConsentRequestRepository consentRequestRepository,
                             EventOutboxRepository eventOutboxRepository,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.consentEventRepository = consentEventRepository;
        this.consentRequestRepository = consentRequestRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> create(UUID tenantId, String callerActorId, String callerActorType,
                                      Map<String, Object> body) {
        // Accountability: a delegation grant is an authority-asserting act; it must be attributable.
        if (callerActorId == null || callerActorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "an authenticated actor (X-Actor-ID) is required to create a delegation");
        }
        DelegationRelationshipEntity e = new DelegationRelationshipEntity();
        e.setTenantId(tenantId);
        e.setDelegatorSubjectRef(required(body, "delegatorSubjectRef"));
        e.setDelegateActorId(required(body, "delegateActorId"));
        e.setRelationshipType(enumOf(required(body, "relationshipType"), RELATIONSHIP_TYPES, "relationshipType"));
        e.setLegalBasis(enumOf(required(body, "legalBasis"), LEGAL_BASES, "legalBasis"));
        e.setScope(writeJson(body.getOrDefault("scope", List.of())));
        // Clamp the assurance floor: a grant may RAISE the delegate's required LOA but never lower it
        // below the policy minimum (otherwise the grant could defeat the PDP's assurance-floor check).
        int requestedLoa = body.get("minDelegateLoa") != null
                ? ((Number) body.get("minDelegateLoa")).intValue() : MIN_DELEGATE_LOA_FLOOR;
        e.setMinDelegateLoa(Math.max(requestedLoa, MIN_DELEGATE_LOA_FLOOR));
        if (body.get("backingConsentRequestId") != null) {
            e.setBackingConsentRequestId(UUID.fromString(body.get("backingConsentRequestId").toString()));
        }
        if (body.get("expiresAt") != null && !body.get("expiresAt").toString().isBlank()) {
            e.setExpiresAt(Instant.parse(body.get("expiresAt").toString()));
        }

        // ── Authorisation to assert this delegation (closes the self-grant IDOR) ──
        if ("CONSENT".equals(e.getLegalBasis())) {
            // Self-service: the SUBJECT delegates their own access. The grant must be backed by a real,
            // identity-verified consent record FOR THIS SUBJECT — not merely any UUID. A forged/foreign
            // id cannot reference a granted consent whose subject matches the claimed delegator.
            if (e.getBackingConsentRequestId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "CONSENT-based delegation requires backingConsentRequestId (identity-verified grant)");
            }
            ConsentRequestEntity consent = consentRequestRepository
                    .findByIdAndTenantId(e.getBackingConsentRequestId(), tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "backingConsentRequestId does not reference a known consent in this tenant"));
            if (consent.getState() == null
                    || !GRANTED_CONSENT_STATES.contains(consent.getState().toUpperCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "backing consent is not granted");
            }
            if (!subjectsMatch(consent.getSubjectPatientRef(), e.getDelegatorSubjectRef())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "backing consent subject does not match the delegation subject");
            }
        } else {
            // GUARDIANSHIP / FACILITY_ASSIGNMENT assert authority over ANOTHER subject. They must be
            // created by an administrative authority (registrar/provider/facility/system) — a self-service
            // citizen or caregiver can never establish guardianship/facility authority by request body alone.
            String type = callerActorType == null ? "" : callerActorType.trim().toUpperCase(Locale.ROOT);
            if (!ADMIN_ACTOR_TYPES.contains(type)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        e.getLegalBasis() + " delegation may only be created by an administrative actor "
                                + "(PROVIDER/OPERATOR/SYSTEM), not self-asserted");
            }
        }

        e.setStatus(DelegationRelationshipEntity.Status.GRANTED.name());
        e = repository.save(e);
        audit(e, "DELEGATION_GRANTED", "impilo.mvumo.delegation.granted.v1");
        return toView(e);
    }

    /** Compare subject references tolerant of a {@code Patient/} (or similar) namespace prefix. */
    private static boolean subjectsMatch(String a, String b) {
        return stripRefPrefix(a).equalsIgnoreCase(stripRefPrefix(b));
    }

    private static String stripRefPrefix(String ref) {
        if (ref == null) return "";
        int slash = ref.lastIndexOf('/');
        return (slash >= 0 ? ref.substring(slash + 1) : ref).trim();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForDelegate(UUID tenantId, String delegateActorId) {
        return repository.findByTenantIdAndDelegateActorIdOrderByCreatedAtDesc(tenantId, delegateActorId)
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForSubject(UUID tenantId, String subjectRef) {
        return repository.findByTenantIdAndDelegatorSubjectRefOrderByCreatedAtDesc(tenantId, subjectRef)
                .stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> revoke(UUID tenantId, UUID id, String reason) {
        DelegationRelationshipEntity e = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delegation not found"));
        if (DelegationRelationshipEntity.Status.WITHDRAWN.name().equals(e.getStatus())) {
            return toView(e); // idempotent
        }
        e.setStatus(DelegationRelationshipEntity.Status.WITHDRAWN.name());
        e.setRevokedReason(reason);
        e.setUpdatedAt(Instant.now());
        e = repository.save(e);
        audit(e, "DELEGATION_REVOKED", "impilo.mvumo.delegation.revoked.v1");
        return toView(e);
    }

    /**
     * Resolve "may {@code delegateActorId} act for {@code subjectRef}?" for the trust plane. Returns the
     * active relationship's facts (scope, assurance floor, expiry, type/basis) for the PDP to evaluate;
     * {@code active=false} when there is no current, unexpired GRANTED relationship. This service does NOT
     * make the access decision — the PDP checks scope-containment, assurance floor and expiry.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> resolve(UUID tenantId, String delegateActorId, String subjectRef) {
        Instant now = Instant.now();
        DelegationRelationshipEntity active = repository
                .findByTenantIdAndDelegateActorIdAndDelegatorSubjectRefAndStatus(
                        tenantId, delegateActorId, subjectRef, DelegationRelationshipEntity.Status.GRANTED.name())
                .stream()
                .filter(e -> e.getExpiresAt() == null || e.getExpiresAt().isAfter(now))
                .findFirst()
                .orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        if (active == null) {
            out.put("active", false);
            return out;
        }
        out.put("active", true);
        out.put("principalId", active.getDelegatorSubjectRef());
        out.put("agentId", active.getDelegateActorId());
        out.put("relationshipType", active.getRelationshipType());
        out.put("legalBasis", active.getLegalBasis());
        out.put("scope", readScope(active.getScope()));
        out.put("assuranceFloor", active.getMinDelegateLoa());
        out.put("expiresAt", active.getExpiresAt() != null ? active.getExpiresAt().toString() : null);
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void audit(DelegationRelationshipEntity e, String eventType, String outboxType) {
        ConsentEventEntity ev = new ConsentEventEntity();
        ev.setTenantId(e.getTenantId());
        ev.setConsentId(e.getId());
        ev.setEventType(eventType);
        ev.setActorRef(e.getDelegateActorId());
        ev.setDetail(writeJson(Map.of(
                "delegatorSubjectRef", e.getDelegatorSubjectRef(), "delegateActorId", e.getDelegateActorId(),
                "relationshipType", e.getRelationshipType(), "legalBasis", e.getLegalBasis(),
                "status", e.getStatus())));
        consentEventRepository.save(ev);

        EventOutboxEntity row = new EventOutboxEntity();
        row.setAggregateType("DelegationRelationship");
        row.setAggregateId(e.getId().toString());
        row.setEventType(outboxType);
        row.setPayload(writeJson(Map.of(
                "delegationId", e.getId().toString(), "delegatorSubjectRef", e.getDelegatorSubjectRef(),
                "delegateActorId", e.getDelegateActorId(), "status", e.getStatus())));
        eventOutboxRepository.save(row);
    }

    private Map<String, Object> toView(DelegationRelationshipEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("delegatorSubjectRef", e.getDelegatorSubjectRef());
        m.put("delegateActorId", e.getDelegateActorId());
        m.put("relationshipType", e.getRelationshipType());
        m.put("legalBasis", e.getLegalBasis());
        m.put("scope", readScope(e.getScope()));
        m.put("minDelegateLoa", e.getMinDelegateLoa());
        m.put("status", e.getStatus());
        m.put("startsAt", e.getStartsAt() != null ? e.getStartsAt().toString() : null);
        m.put("expiresAt", e.getExpiresAt() != null ? e.getExpiresAt().toString() : null);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<String> readScope(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return v.toString();
    }

    private static String enumOf(String value, Set<String> allowed, String field) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be one of " + allowed);
        }
        return v;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "json serialisation");
        }
    }
}
