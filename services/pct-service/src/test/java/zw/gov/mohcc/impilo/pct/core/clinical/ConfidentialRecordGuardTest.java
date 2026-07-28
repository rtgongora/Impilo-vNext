package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyLossRecordEntity;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read half of the confidentiality seam: what a requester sees, and what it never learns.
 */
class ConfidentialRecordGuardTest {

    private static final String SRH = "SEXUAL_REPRODUCTIVE_HEALTH";

    private final ConfidentialRecordGuard guard = new ConfidentialRecordGuard();

    @AfterEach
    void clearContext() {
        VisibilityContextHolder.clear();
    }

    private static PregnancyLossRecordEntity row(String sensitivityClass, String category) {
        var e = new PregnancyLossRecordEntity();
        e.setSensitivityClass(sensitivityClass);
        e.setConfidentialityCategory(category);
        return e;
    }

    private static List<PregnancyLossRecordEntity> filterWith(ConfidentialRecordGuard guard,
                                                              List<PregnancyLossRecordEntity> rows) {
        return guard.filter(rows,
                PregnancyLossRecordEntity::getSensitivityClass,
                PregnancyLossRecordEntity::getConfidentialityCategory);
    }

    @Test
    @DisplayName("SHADOW: nothing is withheld while records are stamped FULL_CLINICAL")
    void shadowStateWithholdsNothing() {
        // No profile at all — the harshest case, since the guard fails closed on protected content.
        var rows = List.of(row("FULL_CLINICAL", SRH), row("FULL_CLINICAL", null));
        assertThat(filterWith(guard, rows))
                .as("before the flip the seam must be inert, or the ward loses records it can see today")
                .hasSize(2);
    }

    @Test
    @DisplayName("POST-FLIP: a protected row is withheld from a requester holding no grant")
    void protectedRowWithheldWithoutGrant() {
        var rows = List.of(row("SPECIALLY_PROTECTED", SRH), row("FULL_CLINICAL", null));
        var kept = filterWith(guard, rows);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).getSensitivityClass()).isEqualTo("FULL_CLINICAL");
    }

    @Test
    @DisplayName("POST-FLIP: the same row IS returned to a requester granted its category")
    void protectedRowReturnedWithGrant() {
        VisibilityContextHolder.set(profileGranting(SRH));
        var rows = List.of(row("SPECIALLY_PROTECTED", SRH));
        assertThat(filterWith(guard, rows))
                .as("a guard that withholds from everyone is not access control, it is an outage")
                .hasSize(1);
    }

    @Test
    @DisplayName("grants are category-scoped: an HIV grant does not open SRH")
    void grantsAreCategoryScoped() {
        VisibilityContextHolder.set(profileGranting("HIV"));
        assertThat(filterWith(guard, List.of(row("SPECIALLY_PROTECTED", SRH)))).isEmpty();
    }

    @Test
    @DisplayName("a withheld single record is indistinguishable from one that does not exist")
    void withheldRecordLooksAbsent() {
        Optional<PregnancyLossRecordEntity> withheld = guard.visible(
                Optional.of(row("SPECIALLY_PROTECTED", SRH)),
                PregnancyLossRecordEntity::getSensitivityClass,
                PregnancyLossRecordEntity::getConfidentialityCategory);
        assertThat(withheld)
                .as("a 403 distinguishable from a 404 tells the guardian the record IS there")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard has no path to a 403 — enforced by return type, not by discipline")
    void guardNeverThrows() {
        for (Method m : ConfidentialRecordGuard.class.getDeclaredMethods()) {
            for (Class<?> thrown : m.getExceptionTypes()) {
                assertThat(thrown.getName())
                        .as("%s must not declare a thrown exception: a withheld record must read as absent",
                                m.getName())
                        .isNull();
            }
            // The public surface returns collections/Optionals only, so there is nothing to throw with.
            if (m.getName().equals("filter")) {
                assertThat(m.getReturnType()).isEqualTo(List.class);
            }
            if (m.getName().equals("visible")) {
                assertThat(m.getReturnType()).isEqualTo(Optional.class);
            }
        }
    }

    @Test
    @DisplayName("an empty or null collection answers empty, never null")
    void emptyInputsAreSafe() {
        assertThat(filterWith(guard, List.of())).isEmpty();
        assertThat(guard.filter(null,
                PregnancyLossRecordEntity::getSensitivityClass,
                PregnancyLossRecordEntity::getConfidentialityCategory)).isEmpty();
        assertThat(guard.visible(Optional.empty(),
                PregnancyLossRecordEntity::getSensitivityClass,
                PregnancyLossRecordEntity::getConfidentialityCategory)).isEmpty();
    }

    private static VisibilityProfile profileGranting(String category) {
        // Categories are an obligation overlay the PDP applies last, so they are granted on the
        // builder rather than passed as a record component.
        VisibilityProfile.Builder builder = VisibilityProfile.builder(VisibilityProfile.legacyUnrestricted());
        builder.grantConfidentialCategories(List.of(category));
        return builder.build();
    }
}
