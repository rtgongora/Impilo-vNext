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
            boolean pctContributed) {
    }
}
