package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Asks the shell what commit it is running, instead of asserting it from a deploy-time value.
 *
 * The BFF cannot know the shell's commit. It used to report one anyway, from
 * IMPILO_SHELL_GIT_COMMIT baked in at Helm-release time — correct immediately after a full boot,
 * and quietly wrong from the first targeted shell roll onwards. It did not go blank or error; it
 * named the previous commit with full confidence, and the no-stale deploy checklist believed it.
 *
 * So the value is resolved from the shell's own /api/health/version, and when that cannot be
 * reached the deploy-time value is still reported but explicitly labelled as unverified. The same
 * reasoning already governs imageDigests in {@link PreviewVersionController} — report the
 * artifact, and say whether it still binds this runtime.
 */
@Component
public class ShellCommitResolver {

    private static final Logger log = LoggerFactory.getLogger(ShellCommitResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How long a successful answer is trusted. /health/version is polled by deploy scripts. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    /** A version endpoint must never be the reason a health check hangs. */
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    public enum Source {
        /** Read from the running shell just now. */
        LIVE,
        /** The shell could not be reached; this is the deploy-time value and may be stale. */
        DEPLOY_MANIFEST_UNVERIFIED,
        /** No live answer and nothing was baked in either. */
        UNKNOWN,
    }

    /** @param commit resolved commit, possibly blank; @param source how much to trust it. */
    public record Resolution(String commit, Source source) {}

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Value("${IMPILO_SHELL_VERSION_URL:http://one-ui-shell:3000/api/health/version}")
    private String shellVersionUrl;

    private volatile String cachedCommit;
    private volatile Instant cachedAt = Instant.EPOCH;

    /**
     * @param deployTimeCommit the value baked in at Helm-release time, used only as a labelled
     *                         fallback — never returned as if it were observed.
     */
    public Resolution resolve(String deployTimeCommit) {
        String live = liveCommit();
        if (live != null && !live.isBlank() && !"unknown".equals(live)) {
            return new Resolution(live, Source.LIVE);
        }
        if (deployTimeCommit != null && !deployTimeCommit.isBlank()) {
            return new Resolution(deployTimeCommit, Source.DEPLOY_MANIFEST_UNVERIFIED);
        }
        return new Resolution("", Source.UNKNOWN);
    }

    private String liveCommit() {
        String cached = cachedCommit;
        if (cached != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return cached;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(shellVersionUrl))
                    .timeout(TIMEOUT)
                    .header("accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode body = MAPPER.readTree(response.body());
            String commit = body.path("commit").asText("");
            if (commit.isBlank()) {
                return null;
            }
            cachedCommit = commit;
            cachedAt = Instant.now();
            return commit;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            // Debug, not warn: the shell being briefly unreachable mid-roll is expected, and this
            // path already degrades honestly rather than reporting a wrong commit.
            log.debug("shell version probe failed ({}): {}", shellVersionUrl, e.toString());
            return null;
        }
    }
}
