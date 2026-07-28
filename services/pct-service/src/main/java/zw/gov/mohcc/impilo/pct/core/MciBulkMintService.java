package zw.gov.mohcc.impilo.pct.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCI bulk-mint (W11) — "PCT bulk-mint" from the design doc: for a declared mass-casualty incident,
 * mint one {@code emergency_episode} per not-yet-minted casualty daidzai is tracking, then write the
 * episode id back onto the casualty. Reuses {@link EmergencyEpisodeService#open} — the same idempotent
 * mint path every other entry route already uses (idempotent on {@code entrySourceRef=casualtyId}, so
 * a re-run of the bulk-mint pass is safe).
 *
 * <p>Minted episodes carry no journey (a casualty being triaged/transported has none yet) — they open
 * PRE_ARRIVAL, exactly like a prehospital pre-alert. The receiving facility resolves the anchor via
 * the EXISTING {@code /arrive} endpoint once the patient physically checks in, reusing established
 * machinery rather than inventing a parallel path for the MCI case.
 */
@Service
public class MciBulkMintService {

    private static final String SYSTEM_ACTOR = "system:mci-bulk-mint";

    private final EmergencyEpisodeService episodeService;
    private final DaidzaiEpisodeClient daidzaiClient;

    public MciBulkMintService(EmergencyEpisodeService episodeService, DaidzaiEpisodeClient daidzaiClient) {
        this.episodeService = episodeService;
        this.daidzaiClient = daidzaiClient;
    }

    /**
     * @return one result row per unminted casualty found this round: {casualtyId, episodeId, created}.
     *         An empty list means either every casualty is already minted, or daidzai could not be
     *         reached this round (the client already logs the distinction) — never fabricated.
     */
    public List<Map<String, Object>> bulkMint(UUID tenantId, UUID facilityId, UUID incidentId) {
        if (facilityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "facilityId is required");
        }
        List<Map<String, Object>> casualties = daidzaiClient.listUnmintedMciCasualties(tenantId, incidentId);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> casualty : casualties) {
            UUID casualtyId = uuidVal(casualty, "id");
            if (casualtyId == null) continue; // malformed row — skip rather than fail the whole pass

            String subjectCpid = strVal(casualty, "subjectCpid");
            OffsetDateTime taggedAt = offsetDateTimeVal(casualty, "taggedAt");

            var cmd = new EmergencyEpisodeService.OpenEpisodeCommand(
                    tenantId,
                    facilityId,
                    "MASS_CASUALTY",
                    "daidzai-service",
                    casualtyId.toString(),
                    null,
                    "UNDIFFERENTIATED",
                    subjectCpid,
                    null,
                    null,
                    null,
                    null,
                    false,
                    taggedAt != null ? taggedAt : OffsetDateTime.now(),
                    SYSTEM_ACTOR);
            var result = episodeService.open(cmd);

            daidzaiClient.linkMciCasualtyEpisode(tenantId, incidentId, casualtyId, result.episode().getEpisodeId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("casualtyId", casualtyId.toString());
            row.put("episodeId", result.episode().getEpisodeId().toString());
            row.put("created", result.created());
            results.add(row);
        }
        return results;
    }

    private static String strVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    private static UUID uuidVal(Map<String, Object> body, String key) {
        String s = strVal(body, key);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    private static OffsetDateTime offsetDateTimeVal(Map<String, Object> body, String key) {
        String s = strVal(body, key);
        if (s == null) return null;
        try { return OffsetDateTime.parse(s.trim()); } catch (RuntimeException e) { return null; }
    }
}
