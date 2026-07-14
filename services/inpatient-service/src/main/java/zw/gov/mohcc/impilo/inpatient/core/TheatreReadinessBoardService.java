package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureBlockerResolutionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureConsentEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureReadinessCheckEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureBlockerResolutionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureConsentRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureReadinessCheckRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ── Wave 2 readiness board ──
 * Theatre-day readiness board depth. Reuses the existing
 * {@link TheatreService#evaluateReadiness} for the base domains (ROOM/TEAM/
 * EQUIPMENT/BLOOD/ANAESTHESIA — Wave 1 owns those) and adds the theatre-day
 * domains BED/RECOVERY/ICU/INSTRUMENT_SET/IMPLANT/FASTING/IDENTITY/PROPHYLAXIS/
 * PREGNANCY/CLEARANCE/CONSENT, each recording a procedure_readiness_check row.
 * Deliberately a separate service so it does not touch the Wave 1 TheatreService
 * constructor/body; the coordinator composes the two.
 */
@Service
public class TheatreReadinessBoardService {

    /** Owner-service routing for each Wave-2 domain. */
    private static final Map<String, String> DOMAIN_OWNER = Map.ofEntries(
            Map.entry("BED", "tuso"),
            Map.entry("RECOVERY", "tuso"),
            Map.entry("ICU", "tuso"),
            Map.entry("INSTRUMENT_SET", "tuso"),
            Map.entry("IMPLANT", "inpatient"),
            Map.entry("FASTING", "inpatient"),
            Map.entry("IDENTITY", "inpatient"),
            Map.entry("PROPHYLAXIS", "inpatient"),
            Map.entry("PREGNANCY", "inpatient"),
            Map.entry("CLEARANCE", "inpatient"),
            Map.entry("CONSENT", "mvumo"));

    private final TheatreService theatreService;
    private final ProcedureEpisodeRepository episodeRepository;
    private final ProcedureConsentRepository consentRepository;
    private final ProcedureReadinessCheckRepository readinessRepository;
    private final ProcedureBlockerResolutionRepository resolutionRepository;
    private final ObjectMapper objectMapper;

    public TheatreReadinessBoardService(TheatreService theatreService,
                                        ProcedureEpisodeRepository episodeRepository,
                                        ProcedureConsentRepository consentRepository,
                                        ProcedureReadinessCheckRepository readinessRepository,
                                        ProcedureBlockerResolutionRepository resolutionRepository,
                                        ObjectMapper objectMapper) {
        this.theatreService = theatreService;
        this.episodeRepository = episodeRepository;
        this.consentRepository = consentRepository;
        this.readinessRepository = readinessRepository;
        this.resolutionRepository = resolutionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluate the full theatre-day board for an episode. Reuses base readiness
     * then adds the Wave-2 domains and maps to a board state.
     */
    @Transactional
    public Map<String, Object> evaluateBoard(UUID episodeId, Map<String, Object> body) {
        ProcedureEpisodeEntity episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found"));

        Map<String, Object> base = theatreService.evaluateReadiness(episodeId, body);
        List<Map<String, Object>> domains = new ArrayList<>();
        List<Map<String, String>> blockers = new ArrayList<>();

        // Base blockers carry into the board.
        Object baseBlockers = base.get("blockers");
        if (baseBlockers instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    blockers.add(Map.of("code", String.valueOf(m.get("code")),
                            "message", String.valueOf(m.get("message"))));
                }
            }
        }

        // Wave-2 evidence-based domains.
        addDomain(domains, blockers, episodeId, "BED", flagStatus(body, "bedAvailable", "bed_available"));
        addDomain(domains, blockers, episodeId, "RECOVERY", flagStatus(body, "recoveryBedAvailable", "recovery_bed_available"));
        addDomain(domains, blockers, episodeId, "ICU", conditionalStatus(body, "icuRequired", "icuBedAvailable"));
        addDomain(domains, blockers, episodeId, "INSTRUMENT_SET", flagStatus(body, "instrumentSetSterile", "instrument_set_sterile"));
        addDomain(domains, blockers, episodeId, "IMPLANT", conditionalStatus(body, "implantRequired", "implantAvailable"));
        addDomain(domains, blockers, episodeId, "FASTING", flagStatus(body, "fastingConfirmed", "fasting_confirmed"));
        addDomain(domains, blockers, episodeId, "IDENTITY", flagStatus(body, "identityConfirmed", "identity_confirmed"));
        addDomain(domains, blockers, episodeId, "PROPHYLAXIS", flagStatus(body, "prophylaxisGiven", "prophylaxis_given"));
        addDomain(domains, blockers, episodeId, "PREGNANCY", flagStatus(body, "pregnancyChecked", "pregnancy_checked"));
        addDomain(domains, blockers, episodeId, "CLEARANCE", flagStatus(body, "clearanceObtained", "clearance_obtained"));
        addDomain(domains, blockers, episodeId, "CONSENT", consentStatus(episodeId));

        String boardState = boardState(episode, blockers, domains);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episode_id", episodeId.toString());
        out.put("patient_id", episode.getSubjectCpid());
        out.put("board_state", boardState);
        out.put("base_checks", base.get("checks"));
        out.put("domains", domains);
        out.put("blockers", blockers);
        return out;
    }

    /** Record a resolve-blocker action routed to the owning service (Wave 2). */
    @Transactional
    public Map<String, Object> resolveBlocker(UUID episodeId, Map<String, Object> body) {
        episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Episode not found"));
        String domain = str(body, "domain");
        if (domain == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "domain is required");
        }
        domain = domain.toUpperCase();
        ProcedureBlockerResolutionEntity r = new ProcedureBlockerResolutionEntity();
        r.setEpisodeId(episodeId);
        r.setDomain(domain);
        r.setAction(str(body, "action") != null ? str(body, "action") : "ROUTE_TO_OWNER");
        r.setRoutedOwner(DOMAIN_OWNER.getOrDefault(domain, "inpatient"));
        r.setOwnerRef(str(body, "ownerRef"));
        r.setNote(str(body, "note"));
        r.setResolvedBy(actor());
        resolutionRepository.save(r);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episode_id", episodeId.toString());
        out.put("domain", domain);
        out.put("routed_owner", r.getRoutedOwner());
        out.put("action", r.getAction());
        return out;
    }

    private void addDomain(List<Map<String, Object>> domains, List<Map<String, String>> blockers,
                           UUID episodeId, String domain, String status) {
        String owner = DOMAIN_OWNER.getOrDefault(domain, "inpatient");
        List<Map<String, String>> domainBlockers = new ArrayList<>();
        if ("BLOCKED".equals(status)) {
            Map<String, String> b = Map.of("code", domain + "_NOT_READY",
                    "message", domain + " readiness not satisfied");
            domainBlockers.add(b);
            blockers.add(b);
        }
        recordCheck(episodeId, domain, owner, status, domainBlockers);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("domain", domain);
        d.put("owner_service", owner);
        d.put("status", status);
        d.put("blockers", domainBlockers);
        domains.add(d);
    }

    private void recordCheck(UUID episodeId, String domain, String owner, String status,
                             List<Map<String, String>> blockers) {
        ProcedureReadinessCheckEntity r = new ProcedureReadinessCheckEntity();
        r.setEpisodeId(episodeId);
        r.setDomain(domain);
        r.setOwnerService(owner);
        r.setStatus(status);
        r.setCheckedBy(actor());
        try {
            r.setBlockersJson(objectMapper.writeValueAsString(blockers));
            r.setDetailJson("{}");
        } catch (JsonProcessingException ex) {
            r.setBlockersJson("[]");
        }
        readinessRepository.save(r);
    }

    /** CONSENT domain from the multi-type bundle: all required GRANTED, none withdrawn. */
    private String consentStatus(UUID episodeId) {
        List<ProcedureConsentEntity> bundle = consentRepository.findByEpisodeId(episodeId);
        if (bundle.isEmpty()) {
            return "NOT_CHECKED";
        }
        for (ProcedureConsentEntity c : bundle) {
            if (!c.isRequired()) {
                continue;
            }
            if (c.isWithdrawn() || !"GRANTED".equals(c.getStatus())) {
                return "BLOCKED";
            }
        }
        return "READY";
    }

    /** Board state mapping from episode status + domain outcomes. */
    private String boardState(ProcedureEpisodeEntity episode, List<Map<String, String>> blockers,
                              List<Map<String, Object>> domains) {
        String status = episode.getStatus();
        if ("CANCELLED".equals(status)) {
            return "Cancelled";
        }
        if ("IN_PROGRESS".equals(status)) {
            return "In-progress";
        }
        if ("COMPLETED".equals(status) || "PACU".equals(status)) {
            return "Completed";
        }
        if ("DEFERRED".equals(status)) {
            return "Deferred";
        }
        if (!blockers.isEmpty()) {
            return "Blocked";
        }
        boolean anyNotChecked = domains.stream().anyMatch(d -> "NOT_CHECKED".equals(d.get("status")));
        return anyNotChecked ? "Ready-with-warnings" : "Ready";
    }

    private static String flagStatus(Map<String, Object> body, String... keys) {
        Boolean b = boolFlag(body, keys);
        if (b == null) {
            return "NOT_CHECKED";
        }
        return b ? "READY" : "BLOCKED";
    }

    /** Only required when the gating flag is set; otherwise READY (not applicable). */
    private static String conditionalStatus(Map<String, Object> body, String requiredKey, String availableKey) {
        Boolean required = boolFlag(body, requiredKey);
        if (!Boolean.TRUE.equals(required)) {
            return "READY";
        }
        Boolean available = boolFlag(body, availableKey);
        if (available == null) {
            return "NOT_CHECKED";
        }
        return available ? "READY" : "BLOCKED";
    }

    private static Boolean boolFlag(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null) {
                return Boolean.parseBoolean(v.toString());
            }
        }
        return null;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null && !v.toString().isBlank() ? v.toString() : null;
    }

    private String actor() {
        try {
            String a = TrustContextHolder.require().actorId();
            return a != null ? a : "system";
        } catch (RuntimeException ex) {
            return "system";
        }
    }
}
