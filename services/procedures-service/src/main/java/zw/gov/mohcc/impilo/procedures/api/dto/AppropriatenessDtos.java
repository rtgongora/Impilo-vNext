package zw.gov.mohcc.impilo.procedures.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Shapes for appropriateness and duplication detection (pipeline §5). */
public final class AppropriatenessDtos {

    private AppropriatenessDtos() {
    }

    /**
     * What the caller knows about the request and the patient.
     *
     * <p>Boolean fields are deliberately {@link Boolean} rather than {@code boolean}: null means
     * "not asserted" and is treated differently from false. A caller that has not checked
     * whether the facility supports a procedure has not established that it does not, and
     * collapsing those two into one value is how an unknown becomes a confident answer.</p>
     */
    public record AppropriatenessRequest(
            String definitionCode,
            String setting,
            String anatomicalSite,
            String side,
            LocalDate requestedFor,
            Integer patientAgeDays,
            Boolean pregnant,
            Boolean onAnticoagulant,
            Boolean patientIdentityConfirmed,
            Boolean openRequestExists,
            LocalDate lastCompletedOn,
            List<String> otherOpenProcedureCodes,
            List<String> completedProcedureCodes,
            Boolean facilitySupportsProcedure,
            Boolean requiredSpecialistAvailable,
            Boolean requiredEquipmentAvailable) {
    }

    /**
     * The engine's answer.
     *
     * <p>{@code outcome} is one of APPROPRIATE, PROCEED_WITH_CLARIFICATION or BLOCKED. There is
     * deliberately no CANCELLED: §5 forbids silently cancelling, and an engine that can return
     * a cancellation will eventually be wired to one.</p>
     */
    public record AppropriatenessVerdict(String outcome, int detectionCount, List<Detection> detections) {
    }

    /**
     * One detection. {@code suggestedAction} is mandatory by construction — a detection that
     * tells a clinician something is wrong without telling them what to do next is an
     * obstruction, and obstructions get overridden by habit.
     */
    public record Detection(String code, String disposition, String severity,
                            String message, String suggestedAction) {
    }
}
