package zw.gov.mohcc.impilo.surgery.api.dto;

/** Shapes for the surgical episode (S1). */
public final class SurgicalEpisodeDtos {

    private SurgicalEpisodeDtos() {
    }

    public record OpenEpisodeRequest(
            String subjectCpid,
            String journeyId,
            String encounterId,
            String operativeIndication,
            String nonOperativeOptionsConsidered,
            String conditionDisplay,
            String diagnosticCertainty,
            String evidence,
            String specialty) {
    }

    /**
     * Reopening an episode (V010, surgical demonstration 9). The reason is mandatory: a reopen
     * that nobody can explain later is not an auditable clinical act. {@code reoperationOfEpisodeId}
     * is for the OTHER representation — a reoperation given its own episode, pointing back at its
     * predecessor — and is not supplied when reopening in place.
     */
    public record ReopenEpisodeRequest(
            String reason,
            String reoperationOfEpisodeId) {
    }

    public record SurgicalEpisodeView(
            String id,
            String subjectCpid,
            String journeyId,
            String encounterId,
            String procedureEpisodeRef,
            String pctProblemRef,
            String operativeIndication,
            String nonOperativeOptionsConsidered,
            String status,
            String specialty,
            String reoperationOfEpisodeId,
            String reopenedAt,
            String reopenedBy,
            String reopenReason,
            boolean pctContributed) {
    }
}
