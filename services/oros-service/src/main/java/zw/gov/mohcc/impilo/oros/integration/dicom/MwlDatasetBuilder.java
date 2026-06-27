package zw.gov.mohcc.impilo.oros.integration.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds the DICOM Modality Worklist scheduled-procedure-step dataset for an imaging order.
 *
 * <p>Pure dcm4che {@link Attributes} construction (no network), so it is unit-testable. Used by
 * both the DIMSE publisher (UPS N-CREATE) and the REST publisher (serialized to DICOM JSON).</p>
 */
@Component
public class MwlDatasetBuilder {

    private static final DateTimeFormatter DA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM = DateTimeFormatter.ofPattern("HHmmss");

    public Attributes build(OrderEntity order, String modality, String scheduledStationAet) {
        Attributes a = new Attributes();

        a.setString(Tag.AccessionNumber, VR.SH,
                order.getAccessionNumber() != null ? order.getAccessionNumber() : order.getOrderId());
        a.setString(Tag.PatientID, VR.LO, order.getPatientCpid());
        a.setNull(Tag.PatientName, VR.PN); // PII-free: name not carried (CPID only)
        a.setString(Tag.RequestedProcedureID, VR.SH, order.getOrderId());
        if (order.getStudyUid() != null) {
            a.setString(Tag.StudyInstanceUID, VR.UI, order.getStudyUid());
        }

        OffsetDateTime when = order.getScheduledAt() != null ? order.getScheduledAt() : OffsetDateTime.now();
        Attributes sps = new Attributes();
        sps.setString(Tag.Modality, VR.CS, modality != null ? modality : "OT");
        sps.setString(Tag.ScheduledProcedureStepStartDate, VR.DA, when.format(DA));
        sps.setString(Tag.ScheduledProcedureStepStartTime, VR.TM, when.format(TM));
        sps.setString(Tag.ScheduledProcedureStepID, VR.SH, order.getOrderId());
        if (scheduledStationAet != null && !scheduledStationAet.isBlank()) {
            sps.setString(Tag.ScheduledStationAETitle, VR.AE, scheduledStationAet);
        }
        a.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);

        return a;
    }
}
