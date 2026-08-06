package zw.gov.mohcc.impilo.booking.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.booking.integration.VarapiClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;

@Service
public class ProviderResolutionService {

    private final VarapiClient varapiClient;

    public ProviderResolutionService(VarapiClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    public ResolvedProvider resolve(TrustContext ctx, String providerPublicId) {
        if (providerPublicId == null || providerPublicId.isBlank()) {
            throw new IllegalArgumentException("provider_id is required");
        }
        Map<String, Object> provider = varapiClient.getProvider(ctx, providerPublicId.trim());
        if (provider.isEmpty()) {
            throw new IllegalArgumentException("Invalid provider_id: " + providerPublicId);
        }
        Object publicId = firstNonNull(provider.get("providerPublicId"), provider.get("provider_public_id"));
        if (publicId == null || !providerPublicId.trim().equalsIgnoreCase(publicId.toString())) {
            throw new IllegalArgumentException("Invalid provider_id: " + providerPublicId);
        }

        // Existence is not consent.
        //
        // Until now the only questions here were "does the id resolve" and "does it match",
        // so anyone resolvable could be booked. That was survivable while provider search
        // returned only practitioners who had opted in. It stopped being survivable the
        // moment discovery widened to the whole Health Professions Authority roll: 4,241
        // practitioners are now findable and verifiable — correctly, because they are
        // registered — and not one of them has agreed to receive requests here.
        //
        // Being on the HPA roll is what makes a practitioner findable. Claiming their record
        // is what makes them bookable. Booking an unclaimed practitioner would commit a named
        // real person to an appointment they never agreed to and cannot see.
        if (!isClaimed(provider)) {
            throw new ProviderNotParticipatingException(providerPublicId.trim());
        }

        String displayName = stringOrNull(firstNonNull(
                provider.get("displayName"),
                provider.get("display_name"),
                provider.get("givenName"),
                provider.get("familyName")));
        return new ResolvedProvider(providerPublicId.trim(), displayName);
    }

    /**
     * Has this practitioner claimed their record — the platform-participation opt-in?
     *
     * <p>Absent means <em>not</em> claimed. A registry that cannot tell us the answer must not
     * be read as consent: the whole point of this gate is that discovery is far wider than
     * participation, so silence has to fail closed.
     */
    private static boolean isClaimed(Map<String, Object> provider) {
        Object claimed = firstNonNull(provider.get("claimed"), provider.get("claimedAt"),
                provider.get("claimed_at"));
        if (claimed instanceof Boolean b) {
            return b;
        }
        // A claimedAt timestamp is the opt-in moment; any non-blank value means it happened.
        return claimed != null && !claimed.toString().isBlank();
    }

    /** A registered practitioner who has not joined the platform, so cannot be booked. */
    public static class ProviderNotParticipatingException extends RuntimeException {
        private final String providerPublicId;

        ProviderNotParticipatingException(String providerPublicId) {
            super("Provider " + providerPublicId + " is on the register but has not joined "
                    + "Impilo, so they cannot be booked yet. They remain searchable and "
                    + "verifiable; booking becomes available once they claim their record.");
            this.providerPublicId = providerPublicId;
        }

        public String providerPublicId() {
            return providerPublicId;
        }
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    public record ResolvedProvider(String providerPublicId, String providerName) {}
}
