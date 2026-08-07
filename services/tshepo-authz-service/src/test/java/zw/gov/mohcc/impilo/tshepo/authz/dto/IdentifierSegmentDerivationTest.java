package zw.gov.mohcc.impilo.tshepo.authz.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A per-record clinical read must derive the COLLECTION as its resource type and the identifier as
 * its resource id — whatever shape the identifier takes.
 *
 * <h2>What this closes</h2>
 *
 * <p>{@code deriveResourceType} used to skip only 36-character UUIDs, so
 * {@code /v1/patients/cpid-12345} derived a resource type of {@code "cpid-12345"}. That value is
 * absent from {@code PolicyEngine.CLINICAL_RESOURCE_TYPES}, so {@code requiresConsent} returned
 * false and the read was <strong>never consent-checked</strong>. {@code deriveResourceId} had the
 * same UUID-only rule, so even a consent evaluation that did fire carried no subject.</p>
 *
 * <p>Measured 2026-08-07: every {@code patient_ref} in {@code tshepo_consent.consent_directive} is
 * {@code cpid-…} form. This was the normal shape of a per-record clinical read, not a corner case.</p>
 *
 * <p>The rule is that a resource TYPE is a vocabulary word and an identifier carries a digit,
 * verified against the live decision log: of 128 distinct resource types observed, the only two
 * containing a digit were a map tile and a patient CPID — both identifiers.</p>
 */
@DisplayName("identifier-shaped path segments never become the resource type")
class IdentifierSegmentDerivationTest {

    @Nested
    @DisplayName("the consent gap")
    class ConsentGap {

        @Test
        @DisplayName("a non-UUID CPID read derives `patients`, so the consent gate can see it")
        void nonUuidCpid_derivesCollection() {
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/patients/cpid-12345"))
                    .as("THE GAP: deriving the CPID here silently disabled consent")
                    .isEqualTo("patients");
        }

        @Test
        @DisplayName("...and the CPID becomes the resource id, so consent has a subject to evaluate")
        void nonUuidCpid_becomesResourceId() {
            assertThat(AuthzInternalRequest.deriveResourceId("/v1/patients/cpid-12345"))
                    .as("subjectRef on the consent path is the resourceId; null means no subject")
                    .isEqualTo("cpid-12345");
        }

        @Test
        @DisplayName("an encounter record read derives `encounters`, not the record id")
        void encounterRecord_derivesCollection() {
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/encounters/enc-99"))
                    .isEqualTo("encounters");
            assertThat(AuthzInternalRequest.deriveResourceId("/v1/encounters/enc-99"))
                    .isEqualTo("enc-99");
        }
    }

    @Nested
    @DisplayName("behaviour that must not change")
    class Unchanged {

        @Test
        @DisplayName("a UUID-identified record still derives the collection — the original rule stands")
        void uuidRecord_stillDerivesCollection() {
            String path = "/v1/patients/0f8fad5b-d9cb-469f-a165-70867728950e";
            assertThat(AuthzInternalRequest.deriveResourceType(path)).isEqualTo("patients");
            assertThat(AuthzInternalRequest.deriveResourceId(path))
                    .isEqualTo("0f8fad5b-d9cb-469f-a165-70867728950e");
        }

        @Test
        @DisplayName("a collection read is unaffected and carries no resource id")
        void collectionRead_unaffected() {
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/patients")).isEqualTo("patients");
            assertThat(AuthzInternalRequest.deriveResourceId("/v1/patients")).isNull();
        }

        @Test
        @DisplayName("hyphenated collection names are types, not identifiers — they carry no digit")
        void hyphenatedCollections_areTypes() {
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/diagnostic-reports"))
                    .isEqualTo("diagnostic-reports");
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/medication-requests/mr-7"))
                    .isEqualTo("medication-requests");
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/clinical-notes"))
                    .isEqualTo("clinical-notes");
        }

        @Test
        @DisplayName("sub-resources still win over the parent when they are not identifiers")
        void subResource_stillWins() {
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/patients/cpid-1/observations"))
                    .as("the last non-identifier segment is the thing being acted on")
                    .isEqualTo("observations");
        }

        @Test
        @DisplayName("a path of nothing but identifiers is UNKNOWN, not an identifier")
        void allIdentifiers_isUnknown() {
            assertThat(AuthzInternalRequest.deriveResourceType("/v1/123/456")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("null and trivial paths keep their existing answers")
        void nullAndTrivial() {
            assertThat(AuthzInternalRequest.deriveResourceType(null)).isEqualTo("UNKNOWN");
            assertThat(AuthzInternalRequest.deriveResourceType("/")).isEqualTo("UNKNOWN");
            assertThat(AuthzInternalRequest.deriveResourceId(null)).isNull();
        }
    }

    @Nested
    @DisplayName("the classifier itself")
    class Classifier {

        @Test
        @DisplayName("identifier shapes observed in the live estate are recognised")
        void identifierShapes() {
            assertThat(AuthzInternalRequest.isIdentifierSegment("cpid-12345")).isTrue();
            assertThat(AuthzInternalRequest.isIdentifierSegment("0f8fad5b-d9cb-469f-a165-70867728950e")).isTrue();
            assertThat(AuthzInternalRequest.isIdentifierSegment("42")).isTrue();
            // A real value from the live decision log: a map tile named for a UUID.
            assertThat(AuthzInternalRequest.isIdentifierSegment("11111111-1111-4111-8111-111111111111.mvt")).isTrue();
        }

        @Test
        @DisplayName("collection vocabulary is not mistaken for an identifier")
        void collectionVocabulary() {
            for (String type : new String[]{"patients", "encounters", "observations",
                    "diagnostic-reports", "medication-requests", "appointments", "clinical-notes"}) {
                assertThat(AuthzInternalRequest.isIdentifierSegment(type))
                        .as("%s is a kind of thing, not an instance", type)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("blank and null are not identifiers")
        void blankIsNotAnIdentifier() {
            assertThat(AuthzInternalRequest.isIdentifierSegment(null)).isFalse();
            assertThat(AuthzInternalRequest.isIdentifierSegment("")).isFalse();
            assertThat(AuthzInternalRequest.isIdentifierSegment("   ")).isFalse();
        }

        @Test
        @DisplayName("route scaffolding is not an identifier, though `v1` carries a digit")
        void structuralSegmentsAreNotIdentifiers() {
            // Regression pin. The first cut of this fix classified `v1` as an identifier, so
            // deriveResourceId("/v1/patients") returned "v1" — a version string standing in for
            // the person whose consent governs the access.
            assertThat(AuthzInternalRequest.isIdentifierSegment("v1")).isFalse();
            assertThat(AuthzInternalRequest.isIdentifierSegment("v2")).isFalse();
            assertThat(AuthzInternalRequest.isIdentifierSegment("api")).isFalse();
            assertThat(AuthzInternalRequest.deriveResourceId("/internal/v1/patients")).isNull();
        }
    }
}
