package zw.gov.mohcc.impilo.llm.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptPiiScrubberTest {

    private final PromptPiiScrubber scrubber = new PromptPiiScrubber();

    @Test
    void redactsDirectIdentifiers() {
        String prompt = "Patient c0000000-0000-4000-8000-000000000001 (DOB 1980-04-12), "
                + "phone +263771000001, email moyo@example.com, ID 63-123456-A-42 needs review.";
        PromptPiiScrubber.Result r = scrubber.scrub(prompt);

        assertThat(r.redacted()).isTrue();
        assertThat(r.text())
                .doesNotContain("c0000000-0000-4000-8000-000000000001")
                .doesNotContain("1980-04-12")
                .doesNotContain("+263771000001")
                .doesNotContain("moyo@example.com")
                .doesNotContain("63-123456-A-42")
                .contains("[REDACTED-ID]", "[REDACTED-DATE]", "[REDACTED-PHONE]", "[REDACTED-EMAIL]", "[REDACTED-NID]");
    }

    @Test
    void leavesCleanTextUnchanged() {
        String prompt = "Summarise the ward round guidance for hypertension management.";
        PromptPiiScrubber.Result r = scrubber.scrub(prompt);
        assertThat(r.redacted()).isFalse();
        assertThat(r.text()).isEqualTo(prompt);
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(scrubber.scrub(null).redacted()).isFalse();
        assertThat(scrubber.scrub("  ").redacted()).isFalse();
    }
}
