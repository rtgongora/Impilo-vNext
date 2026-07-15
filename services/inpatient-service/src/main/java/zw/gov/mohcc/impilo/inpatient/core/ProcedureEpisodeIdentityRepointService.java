package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;

import java.util.UUID;

/**
 * Wave 5b §15 — PROVISIONAL-patient identity repoint for the theatre record.
 *
 * <p>An unidentified trauma patient carries a PROVISIONAL CPID on {@code procedure_episode.subject_cpid}.
 * When the patient is later reconciled ({@code POST /internal/v1/patients/merge} → VITO
 * {@code vito.merge.executed}), the merged (losing) CPID must be repointed to the surviving CPID so the
 * merge does not orphan the surgery record.</p>
 *
 * <p>This is a SEPARATE, procedure-episode-scoped repoint target (my table only). It deliberately does NOT
 * touch the trauma lane's {@code resuscitation_record} repoint hook — both listen to the same
 * {@code vito.merge.executed} event independently. Surgery is NEVER blocked by this side-channel:
 * a repoint failure is logged, not propagated back into any clinical write.</p>
 */
@Service
public class ProcedureEpisodeIdentityRepointService {

    private static final Logger log = LoggerFactory.getLogger(ProcedureEpisodeIdentityRepointService.class);

    private final ProcedureEpisodeRepository episodeRepository;
    private final ObjectMapper objectMapper;

    public ProcedureEpisodeIdentityRepointService(ProcedureEpisodeRepository episodeRepository,
                                                  ObjectMapper objectMapper) {
        this.episodeRepository = episodeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a raw {@code vito.merge.executed} payload and repoint any theatre case on the merged Health ID.
     * VITO emits {@code {tenantId, survivor, merged, survivorHealthId, mergedHealthId}} — subject_cpid holds
     * the Health ID, so we repoint {@code mergedHealthId → survivorHealthId}. Returns rows repointed
     * (0 when the event is irrelevant or malformed — a poison pill is skipped, never retried forever).
     */
    public int handleMergeEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String tenantId = firstNonBlank(text(root, "tenantId"), text(root, "tenant_id"));
            String merged = firstNonBlank(text(root, "mergedHealthId"), text(root, "merged_health_id"));
            String survivor = firstNonBlank(text(root, "survivorHealthId"), text(root, "survivor_health_id"));
            if (tenantId == null || merged == null || survivor == null || merged.equals(survivor)) {
                return 0;
            }
            UUID tenantUuid;
            try {
                tenantUuid = UUID.fromString(tenantId.trim());
            } catch (IllegalArgumentException e) {
                log.warn("INPATIENT-THEATRE: merge event has a malformed tenant id, skipping: {}", e.getMessage());
                return 0;
            }
            return repoint(tenantUuid, merged.trim(), survivor.trim());
        } catch (Exception e) {
            log.error("INPATIENT-THEATRE: failed to parse vito.merge.executed, skipping: {}", e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int repoint(UUID tenantId, String mergedCpid, String survivorCpid) {
        int repointed = episodeRepository.repointSubjectCpid(tenantId, mergedCpid, survivorCpid);
        if (repointed > 0) {
            log.info("INPATIENT-THEATRE: repointed {} theatre case(s) from merged CPID {} to survivor {}",
                    repointed, mergedCpid, survivorCpid);
        }
        return repointed;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
