package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyIdentityLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyIdentityLinkRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Records the clinical resolution of an emergency episode's identity — the audit trail
 * {@code emergency_identity_link} exists to carry (see V210 for the full rationale).
 *
 * <p>This is deliberately separate from {@link PctEmergencyRepointHook}, which mechanically bulk-
 * repoints {@code subject_cpid} across every wired table when a VITO merge event arrives. That
 * mechanism needs no clinical context to be correct — it fires on any merge, resolved through this
 * service or not. This service is the other direction: a clinician (or a resolution workflow)
 * telling emergency HOW it now knows who an episode's patient is, which the VITO fan-out alone
 * cannot express.
 */
@Service
public class EmergencyIdentityLinkService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyIdentityLinkService.class);

    private static final Set<String> RESOLUTION_METHODS = Set.of(
            "BIOMETRIC", "DOCUMENT", "RELATIVE", "SMART_CARD", "CLINICAL_RECOGNITION");

    private final EmergencyEpisodeRepository episodeRepository;
    private final EmergencyIdentityLinkRepository linkRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EmergencyIdentityLinkService(EmergencyEpisodeRepository episodeRepository,
                                        EmergencyIdentityLinkRepository linkRepository,
                                        EventOutboxRepository outboxRepository,
                                        ObjectMapper objectMapper) {
        this.episodeRepository = episodeRepository;
        this.linkRepository = linkRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmergencyIdentityLinkEntity linkIdentity(UUID tenantId, UUID episodeId, String resolvedSubjectCpid,
                                                     String resolutionMethod, String evidenceRef,
                                                     String vitoMergeId, String resolvedBy) {
        if (resolvedSubjectCpid == null || resolvedSubjectCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolvedSubjectCpid is required");
        }
        if (resolutionMethod == null || !RESOLUTION_METHODS.contains(resolutionMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "resolutionMethod must be one of " + RESOLUTION_METHODS);
        }
        if (resolvedBy == null || resolvedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolvedBy is required");
        }

        EmergencyEpisodeEntity episode = episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Emergency episode not found in this tenant: " + episodeId));

        OffsetDateTime now = OffsetDateTime.now();
        UUID newLinkId = UUID.randomUUID();

        // Correct, never overwrite: the previously-active link (if any) is superseded FIRST — before
        // the new row is inserted, not after. uq_eil_active_per_episode allows at most one row with
        // superseded_by IS NULL per episode, so inserting the new active row while the old one is
        // still active would violate that constraint under a real unique index (caught on real
        // Postgres verification; the in-memory test fake used before that did not enforce it).
        linkRepository.findByTenantIdAndEpisodeIdAndSupersededByIsNull(tenantId, episodeId)
                .ifPresent(active -> {
                    active.setSupersededBy(newLinkId);
                    linkRepository.save(active);
                });

        EmergencyIdentityLinkEntity link = new EmergencyIdentityLinkEntity();
        link.setLinkId(newLinkId);
        link.setTenantId(tenantId);
        link.setEpisodeId(episodeId);
        link.setPreviousIdentityMode(episode.getIdentityMode());
        link.setPreviousSubjectCpid(episode.getSubjectCpid());
        link.setResolvedSubjectCpid(resolvedSubjectCpid);
        link.setResolutionMethod(resolutionMethod);
        link.setEvidenceRef(evidenceRef);
        link.setVitoMergeId(vitoMergeId);
        link.setResolvedBy(resolvedBy);
        link.setResolvedAt(now);
        link.setCreatedAt(now);
        linkRepository.save(link);

        // The episode's own subject_cpid is an ATTRIBUTE (see V200) — this resolution updates it
        // directly, same as PctEmergencyRepointHook does for an asynchronous VITO merge.
        episode.setSubjectCpid(resolvedSubjectCpid);
        episode.setIdentityMode("KNOWN");
        episode.setIdentityResolvedAt(now);
        episodeRepository.save(episode);

        emitLinked(link);

        return link;
    }

    public List<EmergencyIdentityLinkEntity> history(UUID tenantId, UUID episodeId) {
        return linkRepository.findByTenantIdAndEpisodeIdOrderByResolvedAtDesc(tenantId, episodeId);
    }

    private void emitLinked(EmergencyIdentityLinkEntity link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("linkId", link.getLinkId().toString());
        payload.put("episodeId", link.getEpisodeId().toString());
        payload.put("tenantId", link.getTenantId().toString());
        payload.put("resolutionMethod", link.getResolutionMethod());
        payload.put("resolvedSubjectCpid", link.getResolvedSubjectCpid());
        payload.put("vitoMergeId", link.getVitoMergeId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("EMERGENCY_IDENTITY_LINK");
        outbox.setAggregateId(link.getEpisodeId().toString());
        outbox.setEventType("EMERGENCY_IDENTITY_LINKED");
        outbox.setTenantId(link.getTenantId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.error("Emergency identity link payload could not be serialised: {}", ex.getMessage());
            outbox.setPayload("{\"episodeId\":\"" + link.getEpisodeId() + "\"}");
        }
        outboxRepository.save(outbox);
    }
}
