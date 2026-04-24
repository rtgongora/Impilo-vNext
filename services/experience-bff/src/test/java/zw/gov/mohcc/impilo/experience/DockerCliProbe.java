package zw.gov.mohcc.impilo.experience;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Detects a usable Docker CLI/engine without relying solely on Testcontainers' docker-java
 * discovery (which can fail on Docker Desktop 29.x when HTTP(S) proxies intercept API calls).
 */
final class DockerCliProbe {

    private DockerCliProbe() {
    }

    /**
     * @return true if {@code docker info} exits 0 within the timeout (engine reachable from CLI).
     */
    static boolean dockerInfoSucceeds() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            drainQuietly(p.getInputStream());
            if (!p.waitFor(25, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drainQuietly(InputStream in) {
        try (InputStream ignored = in) {
            in.readAllBytes();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
