package zw.gov.mohcc.impilo.varapi.api.dto;

import java.util.UUID;

/**
 * A provider as it appears in search and directory results.
 *
 * <p><b>Registration and participation are different things, and this record keeps them
 * apart.</b> A practitioner on the Health Professions Authority roll is registered, and that
 * alone makes them searchable, verifiable and findable. Whether they have opted in to
 * receiving appointment and prescription requests on this platform is a separate, later
 * decision — and it is the one that gates booking.</p>
 *
 * <p>Conflating the two is what hid 4,241 HPA-registered practitioners from search: they
 * carry {@code status = INACTIVE} because they have not opted in, and the default browse
 * filtered on exactly that. "Not yet participating" was read as "not a real provider".</p>
 *
 * <p>So the standing fields travel with every result, and a consumer must not present a
 * result without them. Showing a {@code PRELOADED} / {@code PENDING_VERIFICATION} record as
 * though it were an established, bookable provider would be the opposite error: a directory
 * that overclaims instead of one that hides.</p>
 *
 * @param lifecycleStatus where the record came from — {@code PRELOADED} for an HPA import,
 *                        {@code REGISTERED} for a completed platform registration.
 * @param registryStatus  verification standing of the imported record, e.g.
 *                        {@code PENDING_VERIFICATION}. Null once it is no longer an import
 *                        awaiting checks.
 * @param claimed         whether the practitioner has claimed this record — the opt-in
 *                        signal. <b>Booking and request-routing gate on this, never on
 *                        whether the provider was searchable.</b>
 */
public record ProviderSummary(
        String providerPublicId,
        UUID impiloHealthId,
        String title,
        String givenName,
        String familyName,
        String profession,
        String cadre,
        String status,
        String practiceNumber,
        String lifecycleStatus,
        String registryStatus,
        boolean claimed
) {}
