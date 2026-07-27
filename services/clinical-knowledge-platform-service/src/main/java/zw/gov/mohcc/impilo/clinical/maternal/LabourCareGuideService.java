package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Assesses intrapartum observations against the WHO Labour Care Guide.
 *
 * <p><b>Exactly one monitoring standard per response.</b> The Labour Care Guide replaced the classic
 * partograph; it does not accompany it. A labour assessed against both would carry two verdicts — the
 * LCG's permissive progress view and the classic 1 cm/hour line — and where they disagree, which they
 * are designed to, the screen would show a contradiction on one woman's labour. This service only
 * ever emits {@code WHO_LABOUR_CARE_GUIDE}, and the classic engine is being frozen and demoted to a
 * degraded fallback rather than run alongside.
 *
 * <p><b>What this service does not yet decide: progress.</b> The LCG's cervical-dilation reference
 * threshold — the permissive replacement for the 1 cm/hour line — is not in the content yet, on
 * purpose (see the pack's {@code sourceVerification.deferred}). This layer asserts the standard and
 * the parameter alerts; deciding whether progress is adequate lands with the freeze of the classic
 * engine, where the reinterpretation can be checked against the primary text rather than approximated.
 */
@Service
public class LabourCareGuideService {

    private static final Logger log = LoggerFactory.getLogger(LabourCareGuideService.class);

    static final String CONTENT_PATH = "clinical/rmnp-labour-care-guide.json";
    public static final String STANDARD = "WHO_LABOUR_CARE_GUIDE";

    private final MaternalDangerSignService dangerSignService;
    private final ObjectMapper objectMapper;

    public LabourCareGuideService(MaternalDangerSignService dangerSignService,
                                  ObjectMapper objectMapper) {
        this.dangerSignService = dangerSignService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param monitoringStandard always {@code WHO_LABOUR_CARE_GUIDE}; carried explicitly so a
     *                           consumer can assert exactly one standard and never render two
     * @param progressDecided    false — this layer does not yet decide adequacy of progress; a
     *                           consumer must not read "no progress alert" as "progress is adequate"
     */
    public record Assessment(
            String monitoringStandard,
            List<MaternalDangerSignService.Alert> parameterAlerts,
            String topLineAction,
            List<String> notAssessed,
            boolean incomplete,
            boolean progressDecided,
            String contentVersion,
            String approvalStatus,
            String note) {
    }

    public Assessment assess(Map<String, Object> observation, boolean screeningComplete) {
        return assess(CONTENT_PATH, observation, screeningComplete);
    }

    Assessment assess(String resourcePath, Map<String, Object> observation, boolean screeningComplete) {
        MaternalDangerSignService.Assessment alerts =
                dangerSignService.evaluate(resourcePath, observation, screeningComplete);

        String standard = readStandard(resourcePath);

        String note = alerts.contentProblems().isEmpty()
                ? "Assessed against the WHO Labour Care Guide. This layer flags parameters; it does "
                    + "not yet decide whether progress is adequate — do not read the absence of a "
                    + "progress alert as adequate progress."
                : "Labour Care Guide content problem: " + String.join("; ", alerts.contentProblems());

        return new Assessment(
                standard,
                alerts.alerts(),
                alerts.topLineAction(),
                alerts.notAssessed(),
                alerts.incomplete(),
                false,
                alerts.contentVersion(),
                alerts.approvalStatus(),
                note);
    }

    /**
     * The monitoring standard is a pack-level declaration, read from content rather than hardcoded,
     * so a future ministry pack cannot silently ship under the wrong standard. Defaults to the LCG
     * — never to the classic partograph — because defaulting to the tool this service is replacing
     * would reintroduce the thing the whole layer exists to remove.
     */
    private String readStandard(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return STANDARD;
            }
            JsonNode root = objectMapper.readTree(stream);
            String declared = root.path("monitoringStandard").asText(null);
            if (declared != null && !declared.equals(STANDARD)) {
                // A labour-monitoring pack that is not the LCG is a governance event, not a silent
                // substitution — surface it rather than emitting a standard this service does not
                // implement.
                log.warn("Labour-monitoring pack {} declares monitoringStandard={}, not {}; "
                        + "emitting the declared standard but this service implements the LCG",
                        resourcePath, declared, STANDARD);
            }
            return declared == null ? STANDARD : declared;
        } catch (IOException e) {
            log.error("Failed to read the labour-monitoring standard from {}: {}",
                    resourcePath, e.getMessage(), e);
            return STANDARD;
        }
    }
}
