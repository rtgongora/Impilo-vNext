package zw.gov.mohcc.impilo.tshepo.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyRouteInventoryGuardTest {

    private static final Pattern REQUEST_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");

    @Test
    void tshepoRouteBasesRemainClassifiedForRetirementExecution() throws IOException {
        Path apiRoot = Path.of("src", "main", "java", "zw", "gov", "mohcc", "impilo", "tshepo");
        Set<String> actual = new TreeSet<>();
        try (Stream<Path> stream = Files.walk(apiRoot)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(path -> collectMappings(path, actual));
        }

        Set<String> expected = Set.of(
                "/v1/authorize",
                "/v1/biometric-policy",
                "/v1/council-regulatory",
                "/v1/patient-share-policy",
                "/internal/v1",
                "/internal/v1/council-regulatory/learning",
                "/internal/v1/federation",
                "/internal/v1/legacy");

        assertThat(actual).isEqualTo(new TreeSet<>(expected));
    }

    private static void collectMappings(Path path, Set<String> mappings) {
        try {
            String text = Files.readString(path);
            Matcher matcher = REQUEST_MAPPING.matcher(text);
            while (matcher.find()) {
                mappings.add(matcher.group(1));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
