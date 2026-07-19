package zw.gov.mohcc.impilo.abis.api.dto;

/**
 * 1:1 verification decision. With no vendor engine configured the fail-closed default
 * returns {@code UNAVAILABLE} at zero confidence — never a fabricated match.
 */
public record VerifyResponse(
        String decision,
        double confidence,
        String summary) {
}
