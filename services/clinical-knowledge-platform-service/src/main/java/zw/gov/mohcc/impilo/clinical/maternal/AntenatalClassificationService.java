package zw.gov.mohcc.impilo.clinical.maternal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationEngine;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the antenatal classification tables — graded assessments such as anaemia, where the answer
 * is a severity, not a yes/no.
 *
 * <p>Reuses the IMNCI {@link ClassificationEngine} unchanged. The engine's safety property is the
 * one that matters here too: the reassuring row is reached only when every more severe row above it
 * has been definitively excluded, so a missing haemoglobin yields {@code INDETERMINATE}, never a
 * quiet "no anaemia".
 *
 * <p>Unlike the paediatric service, this one passes {@code ageDays = null} to the engine. The
 * antenatal tables carry no age window — they are scoped by gestation, not by the woman's age — and
 * the engine treats a table with no window as applying to everyone. Requiring an age here would be
 * the paediatric guard misapplied: a woman's haemoglobin does not stop being gradeable because her
 * date of birth is missing.
 */
@Service
public class AntenatalClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AntenatalClassificationService.class);

    static final String CONTENT_PATH = "clinical/rmnp-anc-classification-tables.json";

    private final ClassificationContentLoader contentLoader;

    public AntenatalClassificationService(ClassificationContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    public record Assessment(
            List<ClassificationEngine.Outcome> outcomes,
            boolean urgentReferral,
            boolean referralRequired,
            boolean incomplete,
            String contentVersion,
            String approvalStatus,
            String note) {
    }

    public Assessment evaluate(Map<String, Object> facts) {
        return evaluate(CONTENT_PATH, facts);
    }

    Assessment evaluate(String resourcePath, Map<String, Object> facts) {
        ClassificationContentLoader.Pack pack = contentLoader.load(resourcePath);
        if (pack == null || pack.tables().isEmpty()) {
            return new Assessment(List.of(), false, false, true, null, null,
                    "Antenatal classification content is not loaded, so nothing was classified. "
                            + "This is not a statement that everything is normal.");
        }

        Map<String, Object> workingFacts = facts == null ? Map.of() : facts;
        List<ClassificationEngine.Outcome> outcomes = new ArrayList<>();
        for (ClassificationTable table : pack.tables()) {
            // ageDays = null on purpose: the tables are gestation-scoped, and a table with no age
            // window applies to everyone.
            outcomes.add(ClassificationEngine.classify(table, null, workingFacts));
        }

        boolean urgent = outcomes.stream().anyMatch(o -> "PINK".equals(o.colour())
                && o.status() == ClassificationEngine.Status.CLASSIFIED);
        boolean referral = outcomes.stream().anyMatch(ClassificationEngine.Outcome::referralRequired);
        boolean incomplete = outcomes.stream().anyMatch(o ->
                o.status() == ClassificationEngine.Status.INDETERMINATE
                        || o.status() == ClassificationEngine.Status.NOT_ASSESSED
                        || o.provisional());

        return new Assessment(
                List.copyOf(outcomes), urgent, referral, incomplete,
                pack.version(), pack.approvalStatus(),
                note(outcomes, urgent, incomplete));
    }

    private static String note(List<ClassificationEngine.Outcome> outcomes, boolean urgent,
                               boolean incomplete) {
        if (urgent) {
            return "An urgent classification was reached — act on it now.";
        }
        if (incomplete) {
            return "At least one table could not be completed. Treat the gap as unresolved, not as "
                    + "a normal result.";
        }
        return "Classified on the recorded findings.";
    }
}
