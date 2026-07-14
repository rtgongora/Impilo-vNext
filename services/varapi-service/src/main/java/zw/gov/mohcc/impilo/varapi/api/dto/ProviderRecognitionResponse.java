package zw.gov.mohcc.impilo.varapi.api.dto;

/**
 * Recognition read-model keyed by the canonical Impilo / Health ID.
 *
 * <p>Consumed by downstream perk/advantage surfaces (experience BFF, COSTA
 * billing enrichment, marketplace professional self-authorization) to answer
 * ONE question: is this person a recognised, licensed provider in good
 * standing right now?</p>
 *
 * <p>ENUMERATION RESISTANCE: the response shape is identical whether the
 * health ID is unknown, malformed, or belongs to a provider who is not in
 * good standing — always {@code recognised=false} with all optional fields
 * {@code null}. Optional detail is only ever populated when
 * {@code recognised=true}, so a caller (or attacker) cannot distinguish
 * "no such provider" from "suspended provider".</p>
 */
public record ProviderRecognitionResponse(
        boolean recognised,
        String licenceStatus,
        String profession,
        String cadre) {

    public static ProviderRecognitionResponse notRecognised() {
        return new ProviderRecognitionResponse(false, null, null, null);
    }
}
