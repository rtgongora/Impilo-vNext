package zw.gov.mohcc.impilo.reproductive.contraception;

import java.util.EnumMap;
import java.util.Map;

/**
 * How long each method protects for, and how late a repeat dose may be.
 *
 * <p>Injected rather than compiled in, because every number here is a national programme decision
 * rather than a fact about biology. Jadelle moved from four years to five; the late window for a
 * depot injection differs between guidelines. A ministry must be able to revise these without a code
 * release — the same reason the immunisation schedule is content.
 *
 * <p><b>A revision is not retrospective.</b> Values are stamped onto a contraceptive episode when it
 * is recorded, and the stored expiry is what governs that woman's protection. If the programme
 * later extends an implant from four years to five, that is a decision about new insertions, not a
 * silent extension of every implant already in an arm.
 */
public interface ContraceptiveMethodCatalog {

    /**
     * Null when the catalogue does not describe this method. Never a default profile.
     *
     * <p>{@link ContraceptiveMethod#OTHER} deliberately has none: it means "a method not on this
     * list", and nothing can be said about how long an unnamed method protects for. A no-expiry
     * profile would resolve to NOT_APPLICABLE — claiming there is nothing to compute — when the
     * truth is that we do not know what she is using.
     */
    ContraceptiveMethodProfile profile(ContraceptiveMethod method);

    String contentVersion();

    String approvalStatus();

    static ContraceptiveMethodCatalog engineeringSeed() {
        return new EngineeringSeed();
    }

    /**
     * @param effectiveDurationMonths how long the method protects; null when it does not expire
     * @param reinjectionIntervalDays the nominal interval between doses; null unless scheduled
     * @param graceWindowEarlyDays    how early a repeat dose may be given
     * @param graceWindowLateDays     how late a dose may be and still retain protection. Not a
     *                                tolerance for administrative convenience — it is the window
     *                                within which the method is still working.
     */
    record ContraceptiveMethodProfile(
            ContraceptiveMethod method,
            Integer effectiveDurationMonths,
            Integer reinjectionIntervalDays,
            Integer graceWindowEarlyDays,
            Integer graceWindowLateDays,
            String contentVersion,
            String approvalStatus) {

        public boolean expires() {
            return effectiveDurationMonths != null;
        }

        public boolean scheduled() {
            return reinjectionIntervalDays != null;
        }
    }

    /**
     * Widely used programme values, pending ratification.
     *
     * <p>Explicitly an engineering seed and says so in its own version string. The depot-injection
     * late window in particular varies between guidelines and is one of the values most worth a
     * ministry confirming, because it is the difference between telling a woman who is three weeks
     * late that she is still protected and telling her she is not.
     */
    final class EngineeringSeed implements ContraceptiveMethodCatalog {

        private static final String VERSION = "contraceptive-catalogue-engineering-seed-1.0.0";
        private static final String APPROVAL = "PENDING_MOHCC_RATIFICATION";

        private final Map<ContraceptiveMethod, ContraceptiveMethodProfile> profiles =
                new EnumMap<>(ContraceptiveMethod.class);

        EngineeringSeed() {
            larc(ContraceptiveMethod.IMPLANT_LEVONORGESTREL_2ROD, 60);
            larc(ContraceptiveMethod.IMPLANT_ETONOGESTREL_1ROD, 36);
            larc(ContraceptiveMethod.IUD_COPPER_T380A, 144);
            larc(ContraceptiveMethod.IUS_LEVONORGESTREL, 60);

            injectable(ContraceptiveMethod.INJECTABLE_DMPA_IM, 91, 14, 28);
            injectable(ContraceptiveMethod.INJECTABLE_DMPA_SC, 91, 14, 28);
            injectable(ContraceptiveMethod.INJECTABLE_NET_EN, 60, 14, 14);

            // Pills are dispensed in cycles; the record tracks resupply rather than an expiry, so
            // the profile carries no duration and coverage falls to the resupply date.
            noExpiry(ContraceptiveMethod.COMBINED_ORAL_PILL);
            noExpiry(ContraceptiveMethod.PROGESTOGEN_ONLY_PILL);

            noExpiry(ContraceptiveMethod.MALE_CONDOM);
            noExpiry(ContraceptiveMethod.FEMALE_CONDOM);
            noExpiry(ContraceptiveMethod.DIAPHRAGM);
            noExpiry(ContraceptiveMethod.FEMALE_STERILISATION);
            noExpiry(ContraceptiveMethod.VASECTOMY);
            noExpiry(ContraceptiveMethod.LACTATIONAL_AMENORRHOEA);
            noExpiry(ContraceptiveMethod.FERTILITY_AWARENESS);
            noExpiry(ContraceptiveMethod.WITHDRAWAL);
            noExpiry(ContraceptiveMethod.EMERGENCY_LEVONORGESTREL);
            noExpiry(ContraceptiveMethod.EMERGENCY_ULIPRISTAL);
            larc(ContraceptiveMethod.EMERGENCY_COPPER_IUD, 144);
        }

        private void larc(ContraceptiveMethod method, int months) {
            profiles.put(method, new ContraceptiveMethodProfile(
                    method, months, null, null, null, VERSION, APPROVAL));
        }

        private void injectable(ContraceptiveMethod method, int intervalDays,
                                int earlyDays, int lateDays) {
            profiles.put(method, new ContraceptiveMethodProfile(
                    method, null, intervalDays, earlyDays, lateDays, VERSION, APPROVAL));
        }

        private void noExpiry(ContraceptiveMethod method) {
            profiles.put(method, new ContraceptiveMethodProfile(
                    method, null, null, null, null, VERSION, APPROVAL));
        }

        @Override
        public ContraceptiveMethodProfile profile(ContraceptiveMethod method) {
            return method == null ? null : profiles.get(method);
        }

        @Override
        public String contentVersion() {
            return VERSION;
        }

        @Override
        public String approvalStatus() {
            return APPROVAL;
        }
    }
}
