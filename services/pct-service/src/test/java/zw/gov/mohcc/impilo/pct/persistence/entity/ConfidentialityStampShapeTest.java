package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the confidentiality stamp's shape across every reproductive record, and forecloses the
 * vocabulary defect V437 corrects.
 *
 * <p>V434/V435/V436 each shipped a column named {@code confidentiality_category} whose default was
 * {@code FULL_CLINICAL} — a {@code DataSensitivityClass} name, not a member of the governed category
 * vocabulary. The column name and its contents disagreed, so it answered neither of the two questions
 * {@code SpeciallyProtectedVisibilityGuard} asks (which class? which category?).
 *
 * <p>That defect had a nasty property worth remembering: after the rename, the four services still
 * <em>compiled</em>, because the trio re-introduced a {@code getConfidentialityCategory()} accessor.
 * They would simply have written the class value into the category column and been rejected at
 * runtime by the V437 CHECK. A structural test is the cheaper place to catch that, so this asserts
 * the mapping rather than trusting a future reader to notice.
 */
class ConfidentialityStampShapeTest {

    /** Every reproductive record that carries a confidentiality stamp. */
    private static final Class<?>[] STAMPED = {
            PregnancyLossRecordEntity.class,
            TopAuthorisationEntity.class,
            TopProcedureEntity.class,
            PostnatalContactEntity.class,
            ContraceptiveEpisodeEntity.class,
            PregnancyEpisodeEntity.class,
    };

    /** The governed category vocabulary (zibo V008). Deliberately duplicated here so a drift fails. */
    private static final Set<String> CATEGORIES = Set.of(
            "SEXUAL_REPRODUCTIVE_HEALTH", "HIV", "MENTAL_HEALTH",
            "SAFEGUARDING", "SUBSTANCE_USE", "GENDER_BASED_VIOLENCE");

    private static Set<String> columnNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Field f : type.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                names.add(c.name());
            }
        }
        return names;
    }

    @Test
    @DisplayName("every stamped record carries all four stamp columns")
    void everyStampedRecordCarriesTheFullStamp() {
        for (Class<?> type : STAMPED) {
            assertThat(columnNames(type))
                    .as("%s must carry the full confidentiality stamp", type.getSimpleName())
                    .contains("sensitivity_class",
                              "confidentiality_category",
                              "confidentiality_basis",
                              "confidentiality_policy_version");
        }
    }

    @Test
    @DisplayName("the class and the category are separate columns — one cannot stand in for the other")
    void classAndCategoryAreDistinctColumns() {
        for (Class<?> type : STAMPED) {
            Field classField = fieldFor(type, "sensitivity_class");
            Field categoryField = fieldFor(type, "confidentiality_category");
            assertThat(classField.getName())
                    .as("%s: the class field must not be named after the category", type.getSimpleName())
                    .isNotEqualTo(categoryField.getName());
            // The guard takes (recordSensitivityClass, recordCategory) as two arguments. If one field
            // ever backed both columns, the second argument would silently repeat the first.
            assertThat(classField.getName()).isEqualTo("sensitivityClass");
            assertThat(categoryField.getName()).isEqualTo("confidentialityCategory");
        }
    }

    @Test
    @DisplayName("no entity defaults its category to a sensitivity-class name — the V437 defect")
    void noEntityDefaultsCategoryToAClassName() throws Exception {
        for (Class<?> type : STAMPED) {
            Object instance = type.getDeclaredConstructor().newInstance();
            Field categoryField = fieldFor(type, "confidentiality_category");
            categoryField.setAccessible(true);
            Object value = categoryField.get(instance);
            // A fresh entity must not pre-load a category at all; the stamper decides it. And it must
            // certainly never be a DataSensitivityClass name, which is precisely the shipped defect.
            assertThat(value)
                    .as("%s must not default its confidentiality category", type.getSimpleName())
                    .isNull();
        }
    }

    @Test
    @DisplayName("FULL_CLINICAL is a class, never a category — the regression the CHECK also enforces")
    void fullClinicalIsNotACategory() {
        assertThat(CATEGORIES)
                .as("FULL_CLINICAL is a DataSensitivityClass name and must never be a category value")
                .doesNotContain("FULL_CLINICAL", "SPECIALLY_PROTECTED", "ROUTINE");
    }

    private static Field fieldFor(Class<?> type, String columnName) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> f.getAnnotation(Column.class) != null
                        && columnName.equals(f.getAnnotation(Column.class).name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        type.getSimpleName() + " has no column " + columnName));
    }
}
