package zw.gov.mohcc.impilo.sharedkernel.terminology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A concept that may be defined by one or more coding systems, aligned with
 * the FHIR R4 CodeableConcept data type.
 *
 * <p>This is the canonical representation for all coded clinical data in the
 * Health Operating System. A CodeableConcept can carry multiple {@link Coding}
 * entries from different systems (e.g., an ICD-11 code and a SNOMED CT code
 * for the same diagnosis).</p>
 *
 * <h3>Usage Examples</h3>
 * <pre>{@code
 * // Single-coded concept
 * CodeableConcept diagnosis = CodeableConcept.ofSingle(
 *     CodingSystem.ICD_11, "BA00", "Cholera"
 * );
 *
 * // Multi-coded concept (ICD-11 + SNOMED CT)
 * CodeableConcept diagnosis = CodeableConcept.builder()
 *     .coding(Coding.of(CodingSystem.ICD_11, "BA00", "Cholera"))
 *     .coding(Coding.of(CodingSystem.SNOMED_CT, "63650001", "Cholera"))
 *     .text("Cholera due to Vibrio cholerae")
 *     .build();
 *
 * // Look up a specific coding system
 * Optional<Coding> icd = diagnosis.getCoding(CodingSystem.ICD_11);
 * }</pre>
 *
 * <h3>Database Representation</h3>
 * <p>For simple single-coded fields, use the triple-column pattern:</p>
 * <pre>
 * coding_system   VARCHAR(255)  -- System URI
 * code            VARCHAR(100)  -- Code value
 * display         VARCHAR(500)  -- Display name
 * </pre>
 * <p>For multi-coded fields, serialize to JSONB as a {@code codings} array.</p>
 *
 * @see Coding
 * @see CodingSystem
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CodeableConcept {

    @JsonProperty("coding")
    private final List<Coding> coding;

    @JsonProperty("text")
    private final String text;

    private CodeableConcept(List<Coding> coding, String text) {
        this.coding = coding != null ? List.copyOf(coding) : List.of();
        this.text = text;
    }

    /** Jackson deserialization constructor. */
    @SuppressWarnings("unused")
    private CodeableConcept() {
        this(List.of(), null);
    }

    /**
     * Create a CodeableConcept with a single coding.
     *
     * @param system  the coding system
     * @param code    the code value
     * @param display the display name
     * @return a new CodeableConcept with one coding
     */
    public static CodeableConcept ofSingle(CodingSystem system, String code, String display) {
        return new CodeableConcept(
                List.of(Coding.of(system, code, display)),
                display
        );
    }

    /**
     * Create a CodeableConcept with a single coding using a raw system URI.
     *
     * @param systemUri the system URI string
     * @param code      the code value
     * @param display   the display name
     * @return a new CodeableConcept with one coding
     */
    public static CodeableConcept ofSingle(String systemUri, String code, String display) {
        return new CodeableConcept(
                List.of(Coding.of(systemUri, code, display)),
                display
        );
    }

    /**
     * Create a CodeableConcept with free text only (no structured coding).
     *
     * @param text the free-text description
     * @return a new CodeableConcept with text only
     */
    public static CodeableConcept ofText(String text) {
        return new CodeableConcept(List.of(), text);
    }

    /** Start building a CodeableConcept. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the list of codings (immutable). */
    public List<Coding> getCoding() { return coding; }

    /** Returns the free-text description, or {@code null}. */
    public String getText() { return text; }

    /**
     * Find the first coding from a specific system.
     *
     * @param system the coding system to search for
     * @return the first matching Coding, or empty
     */
    public Optional<Coding> getCoding(CodingSystem system) {
        return coding.stream()
                .filter(c -> system.getUri().equals(c.getSystem()))
                .findFirst();
    }

    /**
     * Find the first coding from a specific system URI.
     *
     * @param systemUri the system URI to search for
     * @return the first matching Coding, or empty
     */
    public Optional<Coding> getCoding(String systemUri) {
        return coding.stream()
                .filter(c -> systemUri.equals(c.getSystem()))
                .findFirst();
    }

    /**
     * Check whether this concept has a coding from the given system.
     *
     * @param system the coding system to check for
     * @return {@code true} if at least one coding uses the given system
     */
    public boolean hasCoding(CodingSystem system) {
        return getCoding(system).isPresent();
    }

    /**
     * Returns the display text of the first coding, falling back to the
     * text field if no coding has a display value.
     *
     * @return the best available display text, or {@code null}
     */
    @JsonIgnore
    public String getDisplayText() {
        for (Coding c : coding) {
            if (c.getDisplay() != null && !c.getDisplay().isBlank()) {
                return c.getDisplay();
            }
        }
        return text;
    }

    /** Returns {@code true} if this concept has no codings and no text. */
    @JsonIgnore
    public boolean isEmpty() {
        return coding.isEmpty() && (text == null || text.isBlank());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeableConcept that)) return false;
        return Objects.equals(coding, that.coding) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coding, text);
    }

    @Override
    public String toString() {
        if (!coding.isEmpty()) {
            return coding.getFirst().toString();
        }
        return text != null ? text : "(empty)";
    }

    /**
     * Builder for constructing CodeableConcept instances with multiple codings.
     */
    public static final class Builder {
        private final List<Coding> codings = new ArrayList<>();
        private String text;

        private Builder() {}

        /** Add a coding entry. */
        public Builder coding(Coding coding) {
            this.codings.add(Objects.requireNonNull(coding, "coding must not be null"));
            return this;
        }

        /** Add a coding by system, code, and display. */
        public Builder coding(CodingSystem system, String code, String display) {
            return coding(Coding.of(system, code, display));
        }

        /** Set the free-text description. */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /** Build the CodeableConcept. */
        public CodeableConcept build() {
            return new CodeableConcept(codings, text);
        }
    }
}
