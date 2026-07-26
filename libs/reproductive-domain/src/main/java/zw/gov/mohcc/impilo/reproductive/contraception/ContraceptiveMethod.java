package zw.gov.mohcc.impilo.reproductive.contraception;

/**
 * The contraceptive methods this platform can record.
 *
 * <p>A closed vocabulary, deliberately. A method code has to mean the same thing in the clinical
 * record, the eligibility engine, the commodity pipeline and the national report — and a free-text
 * method field produces "implant", "Implanon", "IMPLANT_1ROD" and "jadelle" as four different
 * methods in one register, none of which can be counted.
 *
 * <p>The DURATIONS are deliberately not here. How long an implant protects for is a national
 * programme decision that changes — Jadelle moved from four years to five — and it belongs in
 * content a ministry can revise without a code release. This enum carries only what is intrinsic:
 * what class of method it is, and whether it has to be removed.
 */
public enum ContraceptiveMethod {

    IMPLANT_LEVONORGESTREL_2ROD(MethodClass.LARC, true, false),
    IMPLANT_ETONOGESTREL_1ROD(MethodClass.LARC, true, false),
    IUD_COPPER_T380A(MethodClass.LARC, true, false),
    IUS_LEVONORGESTREL(MethodClass.LARC, true, false),

    INJECTABLE_DMPA_IM(MethodClass.SHORT_ACTING, false, true),
    INJECTABLE_DMPA_SC(MethodClass.SHORT_ACTING, false, true),
    INJECTABLE_NET_EN(MethodClass.SHORT_ACTING, false, true),
    COMBINED_ORAL_PILL(MethodClass.SHORT_ACTING, false, true),
    PROGESTOGEN_ONLY_PILL(MethodClass.SHORT_ACTING, false, true),

    MALE_CONDOM(MethodClass.BARRIER, false, false),
    FEMALE_CONDOM(MethodClass.BARRIER, false, false),
    DIAPHRAGM(MethodClass.BARRIER, false, false),

    EMERGENCY_LEVONORGESTREL(MethodClass.EMERGENCY, false, false),
    EMERGENCY_ULIPRISTAL(MethodClass.EMERGENCY, false, false),
    EMERGENCY_COPPER_IUD(MethodClass.EMERGENCY, true, false),

    FEMALE_STERILISATION(MethodClass.PERMANENT, false, false),
    VASECTOMY(MethodClass.PERMANENT, false, false),

    LACTATIONAL_AMENORRHOEA(MethodClass.FERTILITY_AWARENESS, false, false),
    FERTILITY_AWARENESS(MethodClass.FERTILITY_AWARENESS, false, false),
    WITHDRAWAL(MethodClass.TRADITIONAL, false, false),

    OTHER(MethodClass.OTHER, false, false);

    private final MethodClass methodClass;
    private final boolean requiresRemoval;
    private final boolean scheduled;

    ContraceptiveMethod(MethodClass methodClass, boolean requiresRemoval, boolean scheduled) {
        this.methodClass = methodClass;
        this.requiresRemoval = requiresRemoval;
        this.scheduled = scheduled;
    }

    public MethodClass methodClass() {
        return methodClass;
    }

    /**
     * True when a device stays in the body and a clinician must take it out.
     *
     * <p>Load-bearing rather than descriptive: a method requiring removal that is never removed
     * leaves a woman believing she is protected by something that expired years ago, and it is the
     * reason the record tracks devices separately from episodes.
     */
    public boolean requiresRemoval() {
        return requiresRemoval;
    }

    /** True when protection depends on a repeat dose at an interval. */
    public boolean scheduled() {
        return scheduled;
    }

    /** True where the method also reduces sexually transmitted infection risk. */
    public boolean protectsAgainstSti() {
        return this == MALE_CONDOM || this == FEMALE_CONDOM;
    }

    public enum MethodClass {
        LARC, SHORT_ACTING, BARRIER, PERMANENT, EMERGENCY, FERTILITY_AWARENESS, TRADITIONAL, OTHER
    }
}
