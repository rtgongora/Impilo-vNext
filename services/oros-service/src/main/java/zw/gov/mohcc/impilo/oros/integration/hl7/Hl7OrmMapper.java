package zw.gov.mohcc.impilo.oros.integration.hl7;

import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v25.message.ORM_O01;
import ca.uhn.hl7v2.model.v25.segment.OBR;
import ca.uhn.hl7v2.model.v25.segment.ORC;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.parser.PipeParser;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.core.ExternalOrderIntake;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

/**
 * Parse an HL7 v2.5 {@code ORM^O01} (new order) message into a normalized {@link ExternalOrderIntake}.
 *
 * <p>Pure mapping (no transport), so it is fully unit-testable from a raw HL7 string. Patient id is
 * taken from PID-3; the orderable code from OBR-4 (Universal Service Identifier); the external
 * idempotency ref from ORC-2 (Placer Order Number). Priority/order-type default conservatively.</p>
 */
@Component
public class Hl7OrmMapper {

    private final HapiContext hapiContext;

    public Hl7OrmMapper(HapiContext hapiContext) {
        this.hapiContext = hapiContext;
    }

    public ExternalOrderIntake parse(String rawMessage) {
        try {
            PipeParser parser = hapiContext.getPipeParser();
            ORM_O01 orm = (ORM_O01) parser.parse(rawMessage);

            PID pid = orm.getPATIENT().getPID();
            String cpid = trimToNull(pid.getPatientIdentifierList(0).getIDNumber().getValue());

            ORC orc = orm.getORDER().getORC();
            OBR obr = orm.getORDER().getORDER_DETAIL().getOBR();

            String code = trimToNull(obr.getUniversalServiceIdentifier().getIdentifier().getValue());
            String display = trimToNull(obr.getUniversalServiceIdentifier().getText().getValue());
            String placer = trimToNull(orc.getPlacerOrderNumber().getEntityIdentifier().getValue());

            return new ExternalOrderIntake(
                    cpid,
                    inferOrderType(code, display),
                    OrderPriority.ROUTINE,
                    code != null ? code : "UNSPECIFIED",
                    display != null ? display : code,
                    null,
                    placer != null ? "hl7:" + placer : null);

        } catch (HL7Exception e) {
            throw new IllegalArgumentException("Invalid HL7 ORM^O01 message: " + e.getMessage(), e);
        }
    }

    private OrderType inferOrderType(String code, String display) {
        String hay = ((code != null ? code : "") + " " + (display != null ? display : "")).toLowerCase();
        if (hay.contains("xr") || hay.contains("ct") || hay.contains("mri") || hay.contains("us")
                || hay.contains("imag") || hay.contains("rad")) {
            return OrderType.IMAGING;
        }
        return OrderType.LAB;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
