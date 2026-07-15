package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

/** Generates human-readable references for requests/incidents (e.g. SOS-20260630-7F3A). */
@Component
public class ReferenceGenerator {

    public String requestReference() { return prefixed("SOS"); }
    public String incidentReference() { return prefixed("INC"); }
    public String traumaEpisodeReference() { return prefixed("TEP"); }
    public String emsMissionReference() { return prefixed("EMS"); }
    public String emsUnitReference() { return prefixed("UNIT"); }

    private String prefixed(String prefix) {
        OffsetDateTime now = OffsetDateTime.now();
        String date = String.format("%04d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String rand = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000, 0xFFFFF)).toUpperCase();
        return prefix + "-" + date + "-" + rand;
    }
}
