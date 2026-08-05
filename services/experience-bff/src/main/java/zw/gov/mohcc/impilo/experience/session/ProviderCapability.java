package zw.gov.mohcc.impilo.experience.session;

/**
 * Whether an actor holds regulated professional capacity, and on what evidence.
 *
 * <p>Doctrine, from {@code CLAUDE.md}: <em>"Sign in as a person → Health ID establishes who;
 * practice as a provider → Provider ID activates regulated professional capacity."</em> Capacity
 * may be established only by a Health ID with a valid Provider ID attached, or by the Provider ID
 * itself — <strong>never by a name and never by a role label</strong>.</p>
 *
 * <h2>Why the third state exists</h2>
 *
 * <p>{@code SessionExperienceService.fetchLinkedIds} catches every exception and returns an empty
 * map, so a VARAPI outage and "this person is not a provider" produce byte-identical results. That
 * is survivable while nothing enforces on the answer. The moment it does, an outage would strip
 * professional capacity from every clinician at once, and the estate would report it as though each
 * of them had failed a check — a denial, an authentication failure and an infrastructure failure
 * conflated, which is exactly what the trust doctrine forbids.</p>
 *
 * <p>So {@link Resolution#UNAVAILABLE} is a distinct answer from {@link Resolution#NOT_A_PROVIDER}.
 * A caller may treat both as "do not grant", but must never report them the same way.</p>
 */
public record ProviderCapability(Resolution resolution,
                                 String providerId,
                                 String status,
                                 Boolean licenceValid) {

    public enum Resolution {
        /** A Provider ID resolved against VARAPI with an acceptable status. */
        PROVIDER,
        /** VARAPI answered, and this Health ID has no usable Provider ID attached. */
        NOT_A_PROVIDER,
        /** VARAPI could not be asked. Says nothing about whether the person is a provider. */
        UNAVAILABLE
    }

    public static ProviderCapability provider(String providerId, String status, Boolean licenceValid) {
        return new ProviderCapability(Resolution.PROVIDER, providerId, status, licenceValid);
    }

    public static ProviderCapability notAProvider(String status) {
        return new ProviderCapability(Resolution.NOT_A_PROVIDER, null, status, null);
    }

    public static ProviderCapability unavailable() {
        return new ProviderCapability(Resolution.UNAVAILABLE, null, null, null);
    }

    /** True only on positive evidence. Never true for {@code UNAVAILABLE}. */
    public boolean isProvider() {
        return resolution == Resolution.PROVIDER;
    }

    /**
     * The actor type this capability supports, or {@code null} when it supports none.
     *
     * <p>Deliberately returns {@code null} rather than {@code "CITIZEN"} for {@code UNAVAILABLE}:
     * "we could not tell" must not be silently rendered as "they are a citizen", which is a claim
     * about the person rather than about the lookup.</p>
     */
    public String actorTypeOrNull() {
        return switch (resolution) {
            case PROVIDER -> "PROVIDER";
            case NOT_A_PROVIDER -> "CITIZEN";
            case UNAVAILABLE -> null;
        };
    }
}
