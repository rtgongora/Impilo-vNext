package zw.gov.mohcc.impilo.abis.api.dto;

import java.util.List;

/**
 * 1:N identification result: candidates for human adjudication — NEVER an automatic merge.
 * {@code decision} is always {@code CANDIDATES_FOR_ADJUDICATION}; downstream systems must
 * route candidates through their adjudication workflow (e.g. VITO dedup case handling).
 */
public record IdentifyResponse(
        String reason,
        String decision,
        List<IdentifyCandidate> candidates) {

    public static final String DECISION_CANDIDATES_FOR_ADJUDICATION = "CANDIDATES_FOR_ADJUDICATION";

    /** {@code subjectRef} is the opaque ABIS subject reference — never a Health ID. */
    public record IdentifyCandidate(String subjectRef, double confidence, String summary) {}
}
