package zw.gov.mohcc.impilo.oros.integration.hl7;

import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v25.datatype.ST;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.OBR;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import ca.uhn.hl7v2.model.v25.segment.PID;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Build an HL7 v2.5 {@code ORU^R01} (result) message from an order + result.
 *
 * <p>Pure construction/encoding (no transport), unit-testable by asserting the encoded segments.
 * MSH (control), PID (patient = CPID), OBR (filler = accession, service = result code), OBX
 * (the report impression/summary).</p>
 */
@Component
public class Hl7OruMapper {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final HapiContext hapiContext;

    public Hl7OruMapper(HapiContext hapiContext) {
        this.hapiContext = hapiContext;
    }

    /** Build and encode an ORU^R01 to its pipe-delimited string form. */
    public String buildOru(OrderEntity order, ResultEntity result) {
        try {
            ORU_R01 oru = new ORU_R01();
            oru.initQuickstart("ORU", "R01", "P");

            MSH msh = oru.getMSH();
            msh.getSendingApplication().getNamespaceID().setValue("IMPILO_OROS");
            msh.getDateTimeOfMessage().getTime().setValue(OffsetDateTime.now().format(TS));

            PID pid = oru.getPATIENT_RESULT().getPATIENT().getPID();
            pid.getPatientIdentifierList(0).getIDNumber().setValue(order.getPatientCpid());

            OBR obr = oru.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR();
            obr.getSetIDOBR().setValue("1");
            if (order.getAccessionNumber() != null) {
                obr.getFillerOrderNumber().getEntityIdentifier().setValue(order.getAccessionNumber());
            }
            obr.getUniversalServiceIdentifier().getIdentifier().setValue(
                    result.getZiboResultCodes() != null ? result.getZiboResultCodes() : "RESULT");
            obr.getResultStatus().setValue("F");

            OBX obx = oru.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATION().getOBX();
            obx.getSetIDOBX().setValue("1");
            obx.getValueType().setValue("TX");
            obx.getObservationIdentifier().getIdentifier().setValue("REPORT");
            String text = result.getImpression() != null ? result.getImpression()
                    : (result.getSummary() != null ? result.getSummary() : "");
            Varies varies = obx.getObservationValue(0);
            ST st = new ST(oru);
            st.setValue(text);
            varies.setData(st);
            obx.getObservationResultStatus().setValue("F");

            return hapiContext.getPipeParser().encode(oru);

        } catch (HL7Exception | IOException e) {
            throw new IllegalStateException("Failed to build ORU^R01: " + e.getMessage(), e);
        }
    }
}
