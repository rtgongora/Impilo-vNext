package zw.gov.mohcc.impilo.butano.core;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two halves of the reconciliation contract to what BUTANO actually ingests.
 *
 * <p>When VITO promotes a provisional O-CPID to a permanent CPID, {@link ReconciliationService}
 * walks {@code RECONCILABLE_RESOURCES} and repoints every matching resource at the surviving
 * Patient. Two independent ways to lose a clinical record to that merge:</p>
 *
 * <ol>
 *   <li><b>Ingested but not listed.</b> A resource type {@code ButanoEventConsumer} writes but the
 *       map omits is never searched, so it keeps pointing at the merged-away Patient. The merge
 *       reports success; the record is simply gone from the surviving patient's chart. Three types
 *       were in exactly this state — ImagingStudy, ClinicalImpression and DetectedIssue.</li>
 *   <li><b>Listed but not rewritten.</b> A type in the map that {@code rewriteSubjectReference}
 *       does not handle falls through the {@code instanceof} chain, is re-saved with a
 *       reconciliation provenance tag and counted in {@code resourcesUpdated} — while still bound
 *       to the old Patient. That is worse than the omission, because the audit trail asserts the
 *       rewrite happened.</li>
 * </ol>
 *
 * <p>Both are checked here. The ingest set is recovered from {@code ButanoEventConsumer}'s source
 * rather than from a hand-kept list, because a hand-kept list is exactly what drifted; the scan
 * carries its own anchor assertions so a regex that stops matching fails loudly instead of
 * reporting an empty ingest set as full coverage.</p>
 */
class ReconcilableResourceCoverageTest {

    private static final Path CONSUMER_SOURCE = Path.of(
            "src/main/java/zw/gov/mohcc/impilo/butano/events/ButanoEventConsumer.java");

    /** {@code daoRegistry.getResourceDao(Observation.class)} — the only way this service writes FHIR. */
    private static final Pattern DAO_LOOKUP = Pattern.compile("getResourceDao\\(([A-Za-z]+)\\.class\\)");

    /**
     * Patient is the merge anchor, not a merge subject: reconciliation resolves the old and new
     * Patient by CPID and rewrites references INTO them. It is deliberately not reconcilable.
     */
    private static final Set<String> NOT_A_MERGE_SUBJECT = Set.of("Patient");

    private static Set<String> ingestedResourceTypes() throws IOException {
        assertThat(CONSUMER_SOURCE)
                .describedAs("ButanoEventConsumer source must be readable from the module root — "
                        + "a scan that finds no file reports perfect coverage")
                .exists();

        String source = Files.readString(CONSUMER_SOURCE, StandardCharsets.UTF_8);
        Set<String> types = new LinkedHashSet<>();
        Matcher matcher = DAO_LOOKUP.matcher(source);
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }

    @Test
    void the_ingest_scan_finds_the_types_it_is_supposed_to_find() throws IOException {
        Set<String> ingested = ingestedResourceTypes();

        // Anchors. If the regex or the source layout changes so that these stop matching, this
        // test goes red rather than the coverage test silently passing on an empty set.
        assertThat(ingested)
                .describedAs("the DAO-lookup scan must recover the known ingest paths")
                .contains("Patient", "Observation", "Condition", "ImagingStudy",
                        "ClinicalImpression", "DetectedIssue", "DiagnosticReport", "DocumentReference");
        assertThat(ingested).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void every_ingested_resource_type_survives_a_cpid_merge() throws IOException {
        Set<String> reconcilable = new LinkedHashSet<>();
        ReconciliationService.reconcilableResources().keySet()
                .forEach(type -> reconcilable.add(type.getSimpleName()));

        Set<String> orphaned = new LinkedHashSet<>(ingestedResourceTypes());
        orphaned.removeAll(NOT_A_MERGE_SUBJECT);
        orphaned.removeAll(reconcilable);

        assertThat(orphaned)
                .describedAs("BUTANO writes these types but reconciliation never visits them, so "
                        + "after a CPID merge they stay bound to the merged-away Patient — add each "
                        + "to RECONCILABLE_RESOURCES and to rewriteSubjectReference")
                .isEmpty();
    }

    @Test
    void every_reconcilable_type_is_actually_rewritten_not_merely_retagged() {
        ReconciliationService service = new ReconciliationService(null, null, null, null);

        Map<Class<? extends Resource>, String> reconcilable =
                ReconciliationService.reconcilableResources();
        assertThat(reconcilable)
                .describedAs("an empty map would make this test vacuous")
                .isNotEmpty();

        for (Map.Entry<Class<? extends Resource>, String> entry : reconcilable.entrySet()) {
            Class<? extends Resource> type = entry.getKey();
            String linkageField = entry.getValue();

            Resource resource = newInstance(type);
            setLinkage(resource, linkageField, new Reference("Patient/old"));

            service.rewriteSubjectReference(resource, "Patient/new", "O-CPID-1");

            assertThat(readLinkage(resource, linkageField))
                    .describedAs("%s is listed as reconcilable but rewriteSubjectReference does not "
                            + "handle it — the resource would be re-saved with a reconciliation tag "
                            + "and counted as updated while still pointing at Patient/old",
                            type.getSimpleName())
                    .isEqualTo("Patient/new");
        }
    }

    @Test
    void the_merge_anchor_itself_is_not_reconcilable() {
        assertThat(ReconciliationService.reconcilableResources())
                .describedAs("Patient is resolved by CPID as the merge's source and target; "
                        + "rewriting Patient.subject onto itself is meaningless")
                .doesNotContainKey(Patient.class);
    }

    private static Resource newInstance(Class<? extends Resource> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot instantiate " + type.getSimpleName(), e);
        }
    }

    private static void setLinkage(Resource resource, String field, Reference value) {
        invoke(resource, "set" + capitalise(field), Reference.class, value);
    }

    private static String readLinkage(Resource resource, String field) {
        Object value = invoke(resource, "get" + capitalise(field), null, null);
        return value instanceof Reference ref ? ref.getReference() : null;
    }

    private static Object invoke(Resource resource, String method, Class<?> argType, Object arg) {
        try {
            if (argType == null) {
                return resource.getClass().getMethod(method).invoke(resource);
            }
            return resource.getClass().getMethod(method, argType).invoke(resource, arg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(resource.fhirType() + " has no " + method
                    + "(Reference) — the linkage field named in RECONCILABLE_RESOURCES is wrong", e);
        }
    }

    private static String capitalise(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
