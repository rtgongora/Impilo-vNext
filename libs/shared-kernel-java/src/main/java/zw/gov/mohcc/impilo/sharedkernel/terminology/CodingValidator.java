package zw.gov.mohcc.impilo.sharedkernel.terminology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validator for coded clinical data.
 *
 * <p>Performs format-level validation of {@link Coding} and {@link CodeableConcept}
 * values without requiring a terminology server connection. This validator checks:</p>
 * <ul>
 *   <li>That the system URI is a recognized coding system</li>
 *   <li>That the code is non-empty and matches expected format patterns</li>
 *   <li>That required fields are present</li>
 * </ul>
 *
 * <p>For semantic validation (does code X actually exist in system Y?), use the
 * ZIBO terminology service's {@code /api/v1/terminology/validate} endpoint.</p>
 *
 * @see CodingSystem
 */
public final class CodingValidator {

    // Code format patterns for known systems
    private static final Pattern ICD_11_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-/]+$");
    private static final Pattern ICD_10_PATTERN = Pattern.compile("^[A-Z]\\d{2}(\\.\\d{1,4})?$");
    private static final Pattern SNOMED_PATTERN = Pattern.compile("^\\d{6,18}$");
    private static final Pattern LOINC_PATTERN = Pattern.compile("^\\d{1,5}-\\d$");
    private static final Pattern ATC_PATTERN = Pattern.compile("^[A-Z]\\d{2}[A-Z]{2}\\d{2}$|^[A-Z]\\d{2}[A-Z]{2}$|^[A-Z]\\d{2}[A-Z]$|^[A-Z]\\d{2}$|^[A-Z]$");
    private static final Pattern CVX_PATTERN = Pattern.compile("^\\d{1,3}$");

    private static final Set<String> KNOWN_SYSTEM_URIS;

    static {
        Set<String> uris = new java.util.HashSet<>();
        for (CodingSystem system : CodingSystem.values()) {
            uris.add(system.getUri());
        }
        KNOWN_SYSTEM_URIS = Collections.unmodifiableSet(uris);
    }

    private CodingValidator() {}

    /**
     * Validate a single Coding structurally.
     *
     * @param coding the coding to validate
     * @return a list of validation issues (empty if valid)
     */
    public static List<String> validate(Coding coding) {
        if (coding == null) {
            return List.of("Coding must not be null");
        }

        List<String> issues = new ArrayList<>();

        // System URI is required
        if (coding.getSystem() == null || coding.getSystem().isBlank()) {
            issues.add("Coding.system is required");
        } else if (!CodingSystem.isKnown(coding.getSystem())) {
            issues.add("Unrecognized coding system URI: " + coding.getSystem());
        }

        // Code is required
        if (coding.getCode() == null || coding.getCode().isBlank()) {
            issues.add("Coding.code is required");
        }

        // Format validation for known systems
        if (coding.getSystem() != null && coding.getCode() != null && !coding.getCode().isBlank()) {
            validateCodeFormat(coding.getSystem(), coding.getCode(), issues);
        }

        return issues;
    }

    /**
     * Validate a CodeableConcept structurally.
     *
     * @param concept the codeable concept to validate
     * @return a list of validation issues (empty if valid)
     */
    public static List<String> validate(CodeableConcept concept) {
        if (concept == null) {
            return List.of("CodeableConcept must not be null");
        }

        if (concept.isEmpty()) {
            return List.of("CodeableConcept must have at least one coding or text");
        }

        List<String> issues = new ArrayList<>();

        for (int i = 0; i < concept.getCoding().size(); i++) {
            List<String> codingIssues = validate(concept.getCoding().get(i));
            for (String issue : codingIssues) {
                issues.add("coding[" + i + "]: " + issue);
            }
        }

        return issues;
    }

    /**
     * Validate a CodeableConcept against a required coding system.
     *
     * <p>Checks that the concept contains at least one coding from the
     * required system, in addition to standard structural validation.</p>
     *
     * @param concept        the codeable concept to validate
     * @param requiredSystem the coding system that must be present
     * @return a list of validation issues (empty if valid)
     */
    public static List<String> validate(CodeableConcept concept, CodingSystem requiredSystem) {
        List<String> issues = validate(concept);

        if (concept != null && !concept.hasCoding(requiredSystem)) {
            issues.add("CodeableConcept must include a coding from " + requiredSystem.getDisplayName()
                    + " (" + requiredSystem.getUri() + ")");
        }

        return issues;
    }

    /**
     * Check whether a system URI is a recognized coding system.
     *
     * @param systemUri the URI to check
     * @return {@code true} if the URI corresponds to a known CodingSystem
     */
    public static boolean isRecognizedSystem(String systemUri) {
        return systemUri != null && KNOWN_SYSTEM_URIS.contains(systemUri);
    }

    private static void validateCodeFormat(String systemUri, String code, List<String> issues) {
        if (!CodingSystem.isKnown(systemUri)) {
            return; // Skip format check for unknown systems
        }

        CodingSystem system = CodingSystem.fromUri(systemUri);
        Pattern pattern = getFormatPattern(system);

        if (pattern != null && !pattern.matcher(code).matches()) {
            issues.add("Code '" + code + "' does not match expected format for "
                    + system.getDisplayName());
        }
    }

    private static Pattern getFormatPattern(CodingSystem system) {
        return switch (system) {
            case ICD_11 -> ICD_11_PATTERN;
            case ICD_10 -> ICD_10_PATTERN;
            case SNOMED_CT -> SNOMED_PATTERN;
            case LOINC -> LOINC_PATTERN;
            case ATC -> ATC_PATTERN;
            case CVX -> CVX_PATTERN;
            default -> null; // No format validation for other systems
        };
    }
}
