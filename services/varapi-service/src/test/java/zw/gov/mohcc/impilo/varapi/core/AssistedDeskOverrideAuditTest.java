package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the outcome of the assisted-desk override audit.
 *
 * <p>Both overrides were briefly widened to {@code BACK_OFFICE_WRITERS} on the reasoning that
 * "assisted desk recovery / claim" is what a desk operator does, and that the old
 * {@code {SYSTEM, REGISTRY_ADMIN}} was machine-only only by accident. Measured, neither widening was
 * justified, and the recovery one was the most dangerous change in the migration.</p>
 *
 * <p>The reasoning is recorded on each constant. This class exists because the reasoning was
 * plausible and will be made again.</p>
 */
class AssistedDeskOverrideAuditTest {

    private static Set<String> enforced(Class<?> owner, String field) throws Exception {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        Object duty = f.get(null);
        Field types = duty.getClass().getDeclaredField("actorTypes");
        types.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> v = (Set<String>) types.get(duty);
        return v;
    }

    /**
     * The one that matters. {@code initiate} mints a recovery token and returns it to the caller;
     * {@code complete} re-binds the provider profile to whoever redeems it. Admitting OPERATOR here
     * is a two-call provider-account takeover with no second factor, because the same call issues
     * the token.
     */
    @Test
    void recoveringSomeoneElsesProfileIsMachineOnly() throws Exception {
        assertThat(enforced(ProviderRecoveryService.class, "ASSISTED_DESK_RECOVERY"))
                .containsExactly("SYSTEM");
    }

    @Test
    void claimingSomeoneElsesProfileIsMachineOnly() throws Exception {
        assertThat(enforced(ProviderBootstrapService.class, "ASSISTED_DESK_CLAIM"))
                .containsExactly("SYSTEM");
    }

    /**
     * Neither override admits a human session of any kind — the whole point of the audit.
     */
    @Test
    void neitherOverrideAdmitsAHumanSession() throws Exception {
        for (Set<String> s : java.util.List.of(
                enforced(ProviderRecoveryService.class, "ASSISTED_DESK_RECOVERY"),
                enforced(ProviderBootstrapService.class, "ASSISTED_DESK_CLAIM"))) {
            assertThat(s).doesNotContain("OPERATOR", "PROVIDER", "CITIZEN", "CAREGIVER");
        }
    }

    /**
     * The compensating control on the claim path, which is what makes that override unreachable
     * rather than merely unused. If this controller check is ever softened, the override stops
     * being dead code and the audit above no longer holds.
     */
    @Test
    void theClaimControllerStillRefusesAnyAnchorOtherThanTheAuthenticatedActor() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/varapi/api/controller/ProviderBootstrapController.java"));
        int at = source.indexOf("public ResponseEntity<ClaimProfileResponse> claim(");
        assertThat(at).as("the /claim route must still exist").isGreaterThan(0);
        String body = source.substring(at, source.indexOf("\n    }", at));

        assertThat(body)
                .as("the self-claim check is what makes ASSISTED_DESK_CLAIM unreachable over HTTP")
                .contains("ctx.actorId()")
                .contains("request.claimantHealthId()")
                .contains("HttpStatus.FORBIDDEN");
    }

    /**
     * The recovery path has no equivalent controller-level check — the health id is passed straight
     * through from the request. That asymmetry is the reason the recovery override must stay
     * machine-only, so it is asserted rather than left as prose.
     */
    @Test
    void theRecoveryRouteStillPassesAnArbitraryHealthIdStraightThrough() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/varapi/api/controller/ProviderRecoveryController.java"));
        assertThat(source)
                .as("recovery takes the health id from the request body, so the service-side guard "
                        + "is the only thing standing between a caller and someone else's profile")
                .contains("recoveryService.initiate(request.healthId()");
    }
}
