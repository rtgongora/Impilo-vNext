package zw.gov.mohcc.impilo.emergency.triage;

/**
 * The Interagency Integrated Triage Tool's three tiers, plus the answer the chart cannot give.
 *
 * <p>The IITT is a <b>three-tier</b> tool. It is not a 1–5 acuity scale and does not become one by
 * being mapped to numbers: any 1–5 value this estate stores is a derived projection of one of these,
 * and the derivation is one-way.
 */
public enum TriagePriority {

    /** Move to high acuity resuscitation area immediately. */
    RED("Move to high acuity resuscitation area immediately"),

    /** Move to clinical treatment area. */
    YELLOW("Move to clinical treatment area"),

    /** Move to low acuity or waiting area. */
    GREEN("Move to low acuity or waiting area"),

    /**
     * Not enough was assessed to place the patient.
     *
     * <p><b>This is the tier that keeps the tool honest.</b> GREEN is a positive finding — it means
     * every red criterion, every yellow criterion and every high-risk vital sign was checked and
     * none was present. A patient nobody has assessed has not earned GREEN; they are simply
     * untriaged, and saying GREEN would convert an absence of assessment into a statement of low
     * risk. The engine this replaces did the opposite: unmeasured vital signs were read as zero and
     * scored as not-in-danger, and a patient with no triage data at all was recorded as acuity 3.
     */
    NOT_TRIAGEABLE("Insufficient assessment to triage — assess and repeat");

    private final String destination;

    TriagePriority(String destination) {
        this.destination = destination;
    }

    /** The chart's own wording for where this patient goes. */
    public String destination() {
        return destination;
    }

    /** True for the two tiers that place a patient in a clinical area rather than a queue. */
    public boolean requiresImmediateClinicalArea() {
        return this == RED || this == YELLOW;
    }
}
