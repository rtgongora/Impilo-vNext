package zw.gov.mohcc.impilo.pct.api.dto;

import java.util.UUID;

/**
 * Request to admit a patient to an inpatient ward.
 *
 * <p>Creates an admission record linking the patient's journey to a
 * specific ward. The bed assignment may be deferred if no bed is
 * immediately available.</p>
 *
 * @param wardId the ward to admit the patient to
 * @param bedId  the specific bed to assign; may be {@code null} for deferred assignment
 */
public record AdmitRequest(
        UUID wardId,
        UUID bedId
) {}
