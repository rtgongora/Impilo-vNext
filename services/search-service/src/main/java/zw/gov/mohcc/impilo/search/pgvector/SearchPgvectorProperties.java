package zw.gov.mohcc.impilo.search.pgvector;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional PostgreSQL pgvector ANN recall (corpus-wide) for semantic / hybrid rank modes.
 * {@link #dimensions} must match the {@code vector(N)} width in Flyway {@code V003__pgvector_ann.sql} (1536).
 */
@ConfigurationProperties(prefix = "impilo.search.pgvector")
public class SearchPgvectorProperties {

    private boolean enabled = false;

    /** Must match {@code embedding_vec vector(N)} in V003. */
    private int dimensions = 1536;

    /** Max ANN hits considered before merge / pagination (bounds memory and scan). */
    private int annCandidateLimit = 500;

    /** When true, union top lexical SQL hits with ANN ids before re-ranking (bounded by merge-lexical-cap). */
    private boolean mergeLexicalCandidates = true;

    private int mergeLexicalCap = 80;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getAnnCandidateLimit() {
        return annCandidateLimit;
    }

    public void setAnnCandidateLimit(int annCandidateLimit) {
        this.annCandidateLimit = annCandidateLimit;
    }

    public boolean isMergeLexicalCandidates() {
        return mergeLexicalCandidates;
    }

    public void setMergeLexicalCandidates(boolean mergeLexicalCandidates) {
        this.mergeLexicalCandidates = mergeLexicalCandidates;
    }

    public int getMergeLexicalCap() {
        return mergeLexicalCap;
    }

    public void setMergeLexicalCap(int mergeLexicalCap) {
        this.mergeLexicalCap = mergeLexicalCap;
    }
}
