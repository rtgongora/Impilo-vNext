package zw.gov.mohcc.impilo.llm.core;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Redacts high-confidence direct identifiers from free-text prompt content before it egresses to a
 * third-party model (PII Enforcement Wave, 0.2). The LLM boundary previously forwarded caller
 * {@code messages} + {@code actorContext} to Anthropic/OpenAI/Gemini/DeepSeek verbatim, so a patient
 * name/national-ID/UUID typed into a prompt left the country unmodified.
 *
 * <p>This is a <b>value-level</b> scrubber (regex over content), complementing the structured
 * de-identification pipeline. It reliably catches machine-shaped identifiers — Health/CPID UUIDs,
 * national IDs, phone numbers, emails, ISO dates. It cannot reliably detect free-text personal names
 * (that needs NER); the guard is defence-in-depth, not a licence to put PII in prompts. Callers must
 * still avoid sending demographics; the {@code scrubbed} audit flag records when redaction fired.</p>
 */
@Component
public class PromptPiiScrubber {

    // Health ID / CPID and any other UUID (the internal identifiers that must never leave the estate).
    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    // Zimbabwe mobile numbers: +2637XXXXXXXX or 07XXXXXXXX.
    private static final Pattern PHONE = Pattern.compile("(?:\\+?263|0)7\\d{8}\\b");
    // Zimbabwe national ID: 2 digits - 6/7 digits - letter - 2 digits (separators optional).
    private static final Pattern NATIONAL_ID = Pattern.compile(
            "\\b\\d{2}[- ]?\\d{6,7}[- ]?[A-Za-z][- ]?\\d{2}\\b");
    // ISO dates (dates of birth etc.).
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    /** Result of a scrub: the cleaned text and whether anything was redacted. */
    public record Result(String text, boolean redacted) {}

    public Result scrub(String content) {
        if (content == null || content.isBlank()) {
            return new Result(content, false);
        }
        String out = content;
        out = UUID.matcher(out).replaceAll("[REDACTED-ID]");
        out = EMAIL.matcher(out).replaceAll("[REDACTED-EMAIL]");
        out = NATIONAL_ID.matcher(out).replaceAll("[REDACTED-NID]");
        out = PHONE.matcher(out).replaceAll("[REDACTED-PHONE]");
        out = ISO_DATE.matcher(out).replaceAll("[REDACTED-DATE]");
        return new Result(out, !out.equals(content));
    }
}
