package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every multi-word key these services read must be readable in both spellings.
 *
 * <p>The failure mode this exists to prevent is asymmetric and that asymmetry is the whole point.
 * A <em>required</em> field read snake-only fails loudly when a caller sends camelCase — annoying,
 * but safe. An <em>optional</em> field read snake-only is simply absent: the write returns 201 with
 * the clinical value silently null, and no layer above can tell the difference between a value that
 * was dropped in transit and one nobody stated.</p>
 *
 * <p>That is not hypothetical here. The emergency lane's frozen handover contract requires
 * diagnostic certainty to travel with a problem raised in ED, and the experience shell already
 * posts camelCase while reading snake_case in responses — the RMNP lane found the same split in the
 * partograph hook, where PCT reading snake-only would have turned a restored route from a loud 404
 * into a 200 with every clinical value null.</p>
 *
 * <p>Scanning the source rather than asserting per-field, because the list of fields grows and a
 * per-field test only covers the fields someone remembered to add.</p>
 */
class ClinicalKeySpellingTest {

    private static final List<Path> SERVICES = List.of(
            Path.of("src/main/java/zw/gov/mohcc/impilo/pct/core/clinical/ProblemService.java"),
            Path.of("src/main/java/zw/gov/mohcc/impilo/pct/core/clinical/MedicalEpisodeService.java"));

    /** {@code body.get("some_key")} — a multi-word snake_case read. */
    private static final Pattern SNAKE_READ =
            Pattern.compile("body\\.get\\(\"([a-z]+(?:_[a-z]+)+)\"\\)");

    @Test
    void everyMultiWordKeyIsAlsoReadableInCamelCase() throws IOException {
        List<String> unpaired = new ArrayList<>();

        for (Path service : SERVICES) {
            String source = Files.readString(service);
            Matcher m = SNAKE_READ.matcher(source);
            while (m.find()) {
                String snake = m.group(1);
                String camel = toCamel(snake);
                if (!source.contains("body.get(\"" + camel + "\")")) {
                    unpaired.add(service.getFileName() + " reads \"" + snake
                                 + "\" but never \"" + camel + "\"");
                }
            }
        }

        assertThat(unpaired)
                .withFailMessage(
                        "These keys are readable in only one spelling. A caller sending the other "
                        + "gets a silent null on an optional field — a 201 with the clinical value "
                        + "missing.%n  %s", String.join("%n  ", unpaired))
                .isEmpty();
    }

    /** The scan is only worth anything if it is actually finding keys. */
    @Test
    void theScanFindsTheKeysItClaimsToCheck() throws IOException {
        int found = 0;
        for (Path service : SERVICES) {
            Matcher m = SNAKE_READ.matcher(Files.readString(service));
            while (m.find()) found++;
        }
        assertThat(found)
                .withFailMessage("the key scan matched nothing — the pattern has stopped working")
                .isGreaterThan(5);
    }

    private static String toCamel(String snake) {
        String[] parts = snake.split("_");
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            out.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return out.toString();
    }
}
