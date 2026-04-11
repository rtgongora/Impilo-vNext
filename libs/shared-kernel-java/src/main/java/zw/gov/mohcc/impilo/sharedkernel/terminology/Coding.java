package zw.gov.mohcc.impilo.sharedkernel.terminology;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A single coded value within a terminology system, aligned with the
 * FHIR R4 Coding data type.
 *
 * <p>Represents a reference to a code defined by a terminology system.
 * Every coded clinical value in the Health Operating System uses this
 * structure for interoperability.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * Coding labTest = Coding.of(CodingSystem.LOINC, "85354-9", "Blood pressure panel");
 * }</pre>
 *
 * @see CodeableConcept
 * @see CodingSystem
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Coding {

    @JsonProperty("system")
    private final String system;

    @JsonProperty("version")
    private final String version;

    @JsonProperty("code")
    private final String code;

    @JsonProperty("display")
    private final String display;

    @JsonProperty("userSelected")
    private final Boolean userSelected;

    private Coding(String system, String version, String code, String display, Boolean userSelected) {
        this.system = system;
        this.version = version;
        this.code = code;
        this.display = display;
        this.userSelected = userSelected;
    }

    /**
     * Create a Coding with system URI, code, and display name.
     *
     * @param system  the CodingSystem enum value
     * @param code    the code value within the system
     * @param display the human-readable display name
     * @return a new Coding instance
     */
    public static Coding of(CodingSystem system, String code, String display) {
        return new Coding(system.getUri(), null, code, display, null);
    }

    /**
     * Create a Coding with a raw system URI, code, and display name.
     *
     * @param systemUri the system URI string
     * @param code      the code value within the system
     * @param display   the human-readable display name
     * @return a new Coding instance
     */
    public static Coding of(String systemUri, String code, String display) {
        return new Coding(systemUri, null, code, display, null);
    }

    /**
     * Create a Coding with system, version, code, and display.
     *
     * @param system  the CodingSystem enum value
     * @param version the version of the coding system
     * @param code    the code value within the system
     * @param display the human-readable display name
     * @return a new Coding instance
     */
    public static Coding of(CodingSystem system, String version, String code, String display) {
        return new Coding(system.getUri(), version, code, display, null);
    }

    /**
     * Create a fully-specified Coding.
     *
     * @param systemUri    the system URI string
     * @param version      the version of the coding system
     * @param code         the code value
     * @param display      the human-readable display name
     * @param userSelected whether the code was selected by the user
     * @return a new Coding instance
     */
    public static Coding of(String systemUri, String version, String code,
                             String display, Boolean userSelected) {
        return new Coding(systemUri, version, code, display, userSelected);
    }

    /** Jackson deserialization constructor. */
    @SuppressWarnings("unused")
    private Coding() {
        this(null, null, null, null, null);
    }

    public String getSystem() { return system; }
    public String getVersion() { return version; }
    public String getCode() { return code; }
    public String getDisplay() { return display; }
    public Boolean getUserSelected() { return userSelected; }

    /**
     * Returns the CodingSystem enum for this coding's system URI, if recognized.
     *
     * @return the CodingSystem, or {@code null} if the URI is not a known system
     */
    public CodingSystem getCodingSystem() {
        if (system == null) return null;
        try {
            return CodingSystem.fromUri(system);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coding that)) return false;
        return Objects.equals(system, that.system) && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(system, code);
    }

    @Override
    public String toString() {
        return system + "|" + code + (display != null ? " (" + display + ")" : "");
    }
}
