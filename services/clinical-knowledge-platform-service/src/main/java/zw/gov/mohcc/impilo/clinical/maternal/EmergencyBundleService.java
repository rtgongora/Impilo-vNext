package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads emergency-bundle definitions (PPH first, eclampsia and sepsis to follow) and runs
 * {@link EmergencyBundleEngine} against a caller-persisted episode.
 *
 * <p>The engine holds no content and no state; this class loads the bundle from governed content
 * and hands it to the engine with the temporal facts the caller has computed. The PPH episode and
 * its recorded steps are persisted by the owning service (the emergency-episode record), not here —
 * this is the decision layer, deliberately stateless.
 */
@Service
public class EmergencyBundleService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyBundleService.class);

    public static final String PPH_BUNDLE = "clinical/rmnp-pph-bundle.json";

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, EmergencyBundleEngine.Bundle> cache = new ConcurrentHashMap<>();

    public EmergencyBundleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EmergencyBundleEngine.Assessment assessPph(Set<String> completedSteps,
                                                      long minutesSinceTrigger,
                                                      long minutesSinceLastObservation,
                                                      Boolean bleedingControlledConfirmed,
                                                      boolean clinicianConfirmedClose) {
        return EmergencyBundleEngine.assess(bundle(PPH_BUNDLE), completedSteps, minutesSinceTrigger,
                minutesSinceLastObservation, bleedingControlledConfirmed, clinicianConfirmedClose);
    }

    public EmergencyBundleEngine.Bundle bundle(String resourcePath) {
        return cache.computeIfAbsent(resourcePath, this::read);
    }

    private EmergencyBundleEngine.Bundle read(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("Emergency bundle content not found on the classpath: {}", resourcePath);
                return null;
            }
            JsonNode root = objectMapper.readTree(stream);
            List<EmergencyBundleEngine.StepDefinition> steps = new ArrayList<>();
            for (JsonNode node : root.path("steps")) {
                steps.add(new EmergencyBundleEngine.StepDefinition(
                        node.path("code").asText(),
                        node.path("name").asText(),
                        node.path("dueWithinMinutes").asInt(0),
                        node.path("mandatory").asBoolean(false),
                        node.path("action").asText(null)));
            }
            EmergencyBundleEngine.Bundle bundle = new EmergencyBundleEngine.Bundle(
                    root.path("bundleId").asText(resourcePath),
                    root.path("name").asText("emergency bundle"),
                    root.path("lapseWindowMinutes").asInt(15),
                    List.copyOf(steps));
            log.info("Loaded emergency bundle {} steps={} lapseWindow={}m",
                    bundle.bundleId(), steps.size(), bundle.lapseWindowMinutes());
            return bundle;
        } catch (IOException e) {
            log.error("Failed to read emergency bundle {}: {}", resourcePath, e.getMessage(), e);
            return null;
        }
    }
}
