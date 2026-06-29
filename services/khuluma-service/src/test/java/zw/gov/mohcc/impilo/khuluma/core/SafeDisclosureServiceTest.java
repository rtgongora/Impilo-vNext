package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.khuluma.core.SafeDisclosureService.Disclosure;
import zw.gov.mohcc.impilo.khuluma.core.SafeDisclosureService.MessageSensitivity;
import zw.gov.mohcc.impilo.khuluma.core.SafeDisclosureService.RecipientRelationship;

import static org.assertj.core.api.Assertions.assertThat;

class SafeDisclosureServiceTest {

    private static final String FULL = "Your HIV result is ready: positive.";
    private static final String SUMMARY = "An update is available.";

    private final SafeDisclosureService service = new SafeDisclosureService();

    private Disclosure decide(RecipientRelationship rel, MessageSensitivity sens, boolean consent) {
        return service.decide(rel, sens, consent, FULL, SUMMARY);
    }

    @Test
    void subject_receives_their_own_full_content() {
        Disclosure d = decide(RecipientRelationship.SELF, MessageSensitivity.RESTRICTED, false);
        assertThat(d.fullContentAllowed()).isTrue();
        assertThat(d.body()).isEqualTo(FULL);
        assertThat(d.redacted()).isFalse();
    }

    @Test
    void no_relationship_always_redacts() {
        Disclosure d = decide(RecipientRelationship.NONE, MessageSensitivity.ROUTINE, true);
        assertThat(d.fullContentAllowed()).isFalse();
        assertThat(d.body()).isEqualTo(SUMMARY);
        assertThat(d.reason()).isEqualTo("NO_RELATIONSHIP");
    }

    @Test
    void delegate_without_consent_redacts() {
        Disclosure d = decide(RecipientRelationship.CAREGIVER, MessageSensitivity.ROUTINE, false);
        assertThat(d.fullContentAllowed()).isFalse();
        assertThat(d.reason()).isEqualTo("NO_CONSENT");
    }

    @Test
    void restricted_category_is_never_pushed_to_a_consented_delegate() {
        for (RecipientRelationship rel : new RecipientRelationship[]{
                RecipientRelationship.GUARDIAN, RecipientRelationship.CAREGIVER, RecipientRelationship.CHW}) {
            Disclosure d = decide(rel, MessageSensitivity.RESTRICTED, true);
            assertThat(d.fullContentAllowed()).as(rel.name()).isFalse();
            assertThat(d.reason()).isEqualTo("RESTRICTED_CATEGORY");
            assertThat(d.body()).isEqualTo(SUMMARY);
        }
    }

    @Test
    void consented_delegate_gets_non_restricted_content() {
        assertThat(decide(RecipientRelationship.GUARDIAN, MessageSensitivity.ROUTINE, true).fullContentAllowed()).isTrue();
        assertThat(decide(RecipientRelationship.CAREGIVER, MessageSensitivity.SENSITIVE, true).fullContentAllowed()).isTrue();
    }

    @Test
    void unknown_relationship_or_sensitivity_fails_safe() {
        // null relationship -> treated as NONE.
        assertThat(service.decide(null, MessageSensitivity.ROUTINE, true, FULL, SUMMARY).fullContentAllowed()).isFalse();
        // null sensitivity -> treated as RESTRICTED (most protected).
        Disclosure d = service.decide(RecipientRelationship.GUARDIAN, null, true, FULL, SUMMARY);
        assertThat(d.fullContentAllowed()).isFalse();
        assertThat(d.reason()).isEqualTo("RESTRICTED_CATEGORY");
    }

    @Test
    void redacted_body_never_leaks_the_full_content() {
        for (RecipientRelationship rel : RecipientRelationship.values()) {
            for (MessageSensitivity sens : MessageSensitivity.values()) {
                for (boolean consent : new boolean[]{true, false}) {
                    Disclosure d = decide(rel, sens, consent);
                    if (d.redacted()) {
                        assertThat(d.body()).as(rel + "/" + sens + "/" + consent).doesNotContain("HIV");
                        assertThat(d.fullContentAllowed()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void a_blank_safe_summary_falls_back_to_a_generic_default() {
        Disclosure d = service.decide(RecipientRelationship.NONE, MessageSensitivity.ROUTINE, false, FULL, null);
        assertThat(d.body()).isNotBlank();
        assertThat(d.body()).doesNotContain("HIV");
    }
}
