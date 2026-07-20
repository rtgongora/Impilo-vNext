package zw.gov.mohcc.impilo.abis.core;

/**
 * Raised when an enrol capture's quality_score is below the governed per-modality minimum
 * (W3d). Fail-closed: a low-quality template is rejected rather than silently enrolled, so
 * downstream verification never matches against a degraded reference.
 */
public class QualityBelowThresholdException extends RuntimeException {

    private final int qualityScore;
    private final int minQuality;

    public QualityBelowThresholdException(int qualityScore, int minQuality) {
        super("quality_score " + qualityScore + " is below the required minimum " + minQuality);
        this.qualityScore = qualityScore;
        this.minQuality = minQuality;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public int getMinQuality() {
        return minQuality;
    }
}
