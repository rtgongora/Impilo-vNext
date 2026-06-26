package zw.gov.mohcc.impilo.pct.api.dto;

import java.util.UUID;

/**
 * Request to admit a patient to an inpatient ward.
 *
 * <p>Creates an admission record linking the patient's journey to a
 * specific ward. The bed assignment may be deferred if no bed is
 * immediately available — inpatient-service assigns the bed on the handshake.</p>
 *
 * @param wardId             the ward to admit the patient to
 * @param bedId              the specific bed to assign; may be {@code null} for deferred assignment
 * @param encounterId        the encounter ref to hand to inpatient as the census parent key; may be {@code null}
 * @param admittingDiagnosis clinical context carried into the census admission; may be {@code null}
 * @param admissionType      ELECTIVE | EMERGENCY | TRANSFER; may be {@code null} (defaults EMERGENCY downstream)
 */
public record AdmitRequest(
        UUID wardId,
        UUID bedId,
        UUID encounterId,
        String admittingDiagnosis,
        String admissionType
) {}
