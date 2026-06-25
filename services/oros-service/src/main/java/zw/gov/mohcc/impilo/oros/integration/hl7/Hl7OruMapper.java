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
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    /** Build and encode an ORU^R01 with the summary impression as a single OBX. */
    public String buildOru(OrderEntity order, ResultEntity result) {
        return buildOru(order, result, List.of());
    }

    /**
     * Build and encode an ORU^R01. When structured {@code observations} are supplied, each becomes
     * its own OBX (NM/ST value, units in OBX-6, reference range in OBX-7, abnormal flag in OBX-8);
     * otherwise a single summary OBX carries the report impression.
     */
    public String buildOru(OrderEntity order, ResultEntity result, List<ResultObservationEntity> observations) {
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

            if (observations == null || observations.isEmpty()) {
                addSummaryObx(oru, result);
            } else {
                int idx = 0;
                for (ResultObservationEntity o : observations) {
                    addObservationObx(oru, idx, o);
                    idx++;
                }
            }

            return hapiContext.getPipeParser().encode(oru);

        } catch (HL7Exception | IOException e) {
            throw new IllegalStateException("Failed to build ORU^R01: " + e.getMessage(), e);
        }
    }

    /** Single free-text OBX carrying the report impression/summary. */
    private void addSummaryObx(ORU_R01 oru, ResultEntity result) throws HL7Exception {
        OBX obx = oru.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATION(0).getOBX();
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
    }

    /** One OBX per structured observation (analyte/value/unit/reference-range/abnormal flag). */
    private void addObservationObx(ORU_R01 oru, int idx, ResultObservationEntity o) throws HL7Exception {
        OBX obx = oru.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATION(idx).getOBX();
        obx.getSetIDOBX().setValue(String.valueOf(idx + 1));

        boolean numeric = o.getValueNumeric() != null;
        obx.getValueType().setValue(numeric ? "NM" : "ST");
        if (o.getAnalyteCode() != null) {
            obx.getObservationIdentifier().getIdentifier().setValue(o.getAnalyteCode());
        }
        obx.getObservationIdentifier().getText().setValue(o.getAnalyteName());

        Varies varies = obx.getObservationValue(0);
        ST st = new ST(oru);
        st.setValue(numeric ? o.getValueNumeric().toPlainString()
                : (o.getValueText() != null ? o.getValueText() : ""));
        varies.setData(st);

        if (o.getUnit() != null) {
            obx.getUnits().getIdentifier().setValue(o.getUnit());
        }
        String refRange = o.getRefRangeText() != null ? o.getRefRangeText() : buildRange(o);
        if (refRange != null) {
            obx.getReferencesRange().setValue(refRange);
        }
        String flag = o.isCriticalFlag() ? (o.getAbnormalFlag() != null ? o.getAbnormalFlag() : "AA")
                : o.getAbnormalFlag();
        if (flag != null && !flag.isBlank()) {
            obx.getAbnormalFlags(0).setValue(flag);
        }
        // F = final; the report lifecycle gates when this is sent.
        obx.getObservationResultStatus().setValue("F");
    }

    private static String buildRange(ResultObservationEntity o) {
        if (o.getRefRangeLow() != null && o.getRefRangeHigh() != null) {
            return o.getRefRangeLow().toPlainString() + "-" + o.getRefRangeHigh().toPlainString();
        }
        return null;
    }
}
