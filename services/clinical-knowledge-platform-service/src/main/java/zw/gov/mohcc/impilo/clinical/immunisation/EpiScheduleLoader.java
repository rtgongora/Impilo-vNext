package zw.gov.mohcc.impilo.clinical.immunisation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the national immunisation schedule from versioned classpath content.
 *
 * <p>A malformed antigen is rejected rather than partially loaded. A dose that loses its minimum
 * age would forecast as valid on the day of birth, so a half-parsed schedule is more dangerous
 * than an absent one.</p>
 */
@Component
public class EpiScheduleLoader {

    private static final Logger log = LoggerFactory.getLogger(EpiScheduleLoader.class);

    private final ObjectMapper objectMapper;
    private final Map<String, EpiSchedule> cache = new LinkedHashMap<>();

    public EpiScheduleLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EpiSchedule load(String resourcePath) {
        return cache.computeIfAbsent(resourcePath, this::read);
    }

    private EpiSchedule read(String resourcePath) {
        try (InputStream stream = EpiScheduleLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("EPI schedule content not found on the classpath: {}", resourcePath);
                return empty(resourcePath);
            }
            JsonNode root = objectMapper.readTree(stream);

            List<EpiSchedule.Antigen> antigens = new ArrayList<>();
            int rejected = 0;
            for (JsonNode node : root.path("antigens")) {
                EpiSchedule.Antigen antigen = parseAntigen(node);
                if (antigen == null) {
                    rejected++;
                    continue;
                }
                antigens.add(antigen);
            }

            EpiSchedule schedule = new EpiSchedule(
                    root.path("scheduleId").asText(resourcePath),
                    root.path("name").asText(null),
                    root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"),
                    root.path("adaptationAuthority").asText(null),
                    root.path("codeSystem").asText(null),
                    root.path("provenance").asText(null),
                    List.copyOf(antigens));

            log.info("Loaded EPI schedule {} version={} approval={} antigens={} rejected={}",
                    schedule.scheduleId(), schedule.version(), schedule.approvalStatus(),
                    antigens.size(), rejected);
            if (rejected > 0) {
                log.error("{} antigen(s) in {} were rejected as malformed and are NOT forecast",
                        rejected, resourcePath);
            }
            return schedule;

        } catch (IOException e) {
            log.error("Failed to read EPI schedule {}: {}", resourcePath, e.getMessage(), e);
            return empty(resourcePath);
        }
    }

    private EpiSchedule.Antigen parseAntigen(JsonNode node) {
        String series = text(node, "series");
        if (series == null) {
            log.error("EPI antigen without a series identifier rejected");
            return null;
        }
        List<EpiSchedule.Dose> doses = new ArrayList<>();
        for (JsonNode d : node.path("doses")) {
            Integer doseNumber = intOrNull(d, "doseNumber");
            Integer minimumAge = intOrNull(d, "minimumAgeDays");
            Integer recommendedAge = intOrNull(d, "recommendedAgeDays");
            if (doseNumber == null || minimumAge == null || recommendedAge == null) {
                // Without a minimum age a dose would forecast as due at birth.
                log.error("EPI antigen {} rejected: a dose is missing its number, minimum age or "
                          + "recommended age", series);
                return null;
            }
            doses.add(new EpiSchedule.Dose(doseNumber, recommendedAge, minimumAge,
                    intOrNull(d, "minimumIntervalDays"), intOrNull(d, "overdueAfterAgeDays"),
                    intOrNull(d, "latestUsefulAgeDays"), text(d, "note")));
        }
        if (doses.isEmpty()) {
            log.error("EPI antigen {} rejected: no doses", series);
            return null;
        }
        doses.sort((a, b) -> Integer.compare(a.doseNumber(), b.doseNumber()));

        return new EpiSchedule.Antigen(series, text(node, "name"), text(node, "protectsAgainst"),
                text(node, "route"), text(node, "appliesToSex"), text(node, "safetyNote"),
                List.copyOf(doses));
    }

    private static EpiSchedule empty(String resourcePath) {
        return new EpiSchedule(resourcePath, null, "unavailable", "ENGINEERING_SEED", null, null, null, List.of());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }
}
