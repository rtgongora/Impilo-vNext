package zw.gov.mohcc.impilo.matcher.engine;

import java.util.List;

/**
 * Per-modality matching contract. One implementation per modality; the
 * {@link MatchDispatcher} routes by {@link Modality}. Every method is
 * fail-closed by construction — an implementation that cannot match (no model,
 * bad input) returns {@code UNAVAILABLE} / no candidates rather than guessing.
 */
public interface ModalityMatcher {

    Modality modality();

    /** True when this matcher can actually compare templates (e.g. its model is loaded). */
    boolean capable();

    /**
     * Extract a comparable template from a raw capture sample (image bytes).
     * @return the extracted template bytes, or empty if extraction is unsupported/failed.
     */
    ExtractionResult extract(byte[] sampleImage, int width, int height, int dpi);

    /** 1:1 verify a probe template against one enrolled template. */
    VerificationResult verify(byte[] probeTemplate, byte[] enrolledTemplate);

    /** 1:N identify a probe against a candidate gallery; returns scored candidates, never a merge. */
    List<Candidate> identify(byte[] probeTemplate, List<GalleryEntry> gallery, int maxCandidates);

    /** Extraction outcome. {@code quality} is a 0..100 NFIQ-like score when the matcher provides one. */
    record ExtractionResult(boolean ok, byte[] template, double quality, String detail) {
        public static ExtractionResult unavailable(String detail) {
            return new ExtractionResult(false, new byte[0], 0.0, detail);
        }
    }

    /** result ∈ {MATCH, NO_MATCH, UNAVAILABLE}; score is the raw matcher score; confidence 0..1. */
    record VerificationResult(String result, double score, double confidence, String detail) {
        public static VerificationResult unavailable(String detail) {
            return new VerificationResult("UNAVAILABLE", 0.0, 0.0, detail);
        }
    }

    /** A candidate gallery member: opaque subjectRef (never a Health ID) + its template. */
    record GalleryEntry(String subjectRef, byte[] template) {}

    /** A scored identification candidate for adjudication (never an automatic merge). */
    record Candidate(String subjectRef, double score, double confidence) {}
}
