package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

import java.util.List;

/**
 * HAPI FHIR interceptor that prevents Personally Identifiable Information (PII)
 * from being stored in the Shared Health Record.
 *
 * <p>This is a core architectural constraint of the Impilo platform: BUTANO
 * (the SHR) uses only CPID (Clinical Person Identifier) to reference patients.
 * All PII (names, telecom numbers, addresses, photos, contact persons) is stored
 * exclusively in VITO (the identity service).</p>
 *
 * <h3>Patient resource restrictions</h3>
 * <p>For Patient resources, only the following fields are permitted:</p>
 * <ul>
 *   <li>{@code identifier} — but ONLY with the CPID system ({@code https://impilo.gov.zw/cpid})</li>
 *   <li>{@code active} — patient active status</li>
 *   <li>{@code gender} — administrative gender</li>
 *   <li>{@code birthDate} — date of birth (optional, for age-based clinical rules)</li>
 *   <li>{@code meta} — resource metadata (tags, profiles, etc.)</li>
 * </ul>
 *
 * <p>The following fields are REJECTED if populated:</p>
 * <ul>
 *   <li>{@code name} — full names are PII, stored in VITO</li>
 *   <li>{@code telecom} — phone/email are PII, stored in VITO</li>
 *   <li>{@code address} — physical addresses are PII, stored in VITO</li>
 *   <li>{@code photo} — biometric data is PII, stored in VITO</li>
 *   <li>{@code contact} — emergency contacts are PII, stored in VITO</li>
 *   <li>Unknown extensions — could smuggle PII via custom extensions</li>
 * </ul>
 *
 * <h3>Patient/subject references</h3>
 * <p>For all resources that reference a patient (via {@code subject} or {@code patient}
 * fields), the reference must use the CPID identifier format. Direct resource URL
 * references are acceptable (e.g., {@code Patient/123}) since BUTANO Patient resources
 * are themselves PII-free.</p>
 *
 * <p>This interceptor can be disabled by setting {@code butano.pii.enforce=false}
 * (useful for testing or migration scenarios, but NEVER in production).</p>
 */
@Interceptor
@Component
public class PiiPreventionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PiiPreventionInterceptor.class);

    /** The CPID identifier system URI — the only permitted identifier system for Patient resources */
    public static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";

    /**
     * Known safe extension URLs that are permitted on Patient resources.
     * All other extensions are rejected as potential PII smuggling vectors.
     */
    private static final List<String> ALLOWED_PATIENT_EXTENSIONS = List.of(
            "http://hl7.org/fhir/StructureDefinition/patient-genderIdentity",
            "http://hl7.org/fhir/StructureDefinition/patient-birthPlace",
            "https://impilo.gov.zw/StructureDefinition/cpid-version"
    );

    private final ButanoProperties properties;

    public PiiPreventionInterceptor(ButanoProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates PII constraints on newly created resources.
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    public void onResourceCreated(RequestDetails requestDetails, Resource resource) {
        if (!properties.getPii().isEnforce()) {
            return;
        }
        validateResource(resource);
    }

    /**
     * Validates PII constraints on updated resources.
     */
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    public void onResourceUpdated(RequestDetails requestDetails, Resource oldResource, Resource newResource) {
        if (!properties.getPii().isEnforce()) {
            return;
        }
        validateResource(newResource);
    }

    // ── Validation logic ────────────────────────────────────────────────

    /**
     * Dispatches validation based on resource type.
     */
    private void validateResource(Resource resource) {
        if (resource instanceof Patient patient) {
            validatePatient(patient);
        }
        // For all resource types, validate patient/subject references
        validatePatientReferences(resource);
    }

    /**
     * Validates that a Patient resource contains no PII fields.
     *
     * <p>Only CPID identifiers, active, gender, birthDate, and meta are permitted.
     * All name, telecom, address, photo, and contact fields must be empty.</p>
     *
     * @throws UnprocessableEntityException if PII fields are populated
     */
    private void validatePatient(Patient patient) {
        StringBuilder violations = new StringBuilder();

        // Check prohibited PII fields
        if (patient.hasName() && !patient.getName().isEmpty()) {
            violations.append("Patient.name is not permitted in the SHR — names are PII stored in VITO. ");
        }

        if (patient.hasTelecom() && !patient.getTelecom().isEmpty()) {
            violations.append("Patient.telecom is not permitted in the SHR — contact info is PII stored in VITO. ");
        }

        if (patient.hasAddress() && !patient.getAddress().isEmpty()) {
            violations.append("Patient.address is not permitted in the SHR — addresses are PII stored in VITO. ");
        }

        if (patient.hasPhoto() && !patient.getPhoto().isEmpty()) {
            violations.append("Patient.photo is not permitted in the SHR — photos are PII stored in VITO. ");
        }

        if (patient.hasContact() && !patient.getContact().isEmpty()) {
            violations.append("Patient.contact is not permitted in the SHR — emergency contacts are PII stored in VITO. ");
        }

        // Check that identifiers only use the CPID system
        if (patient.hasIdentifier()) {
            for (Identifier identifier : patient.getIdentifier()) {
                if (identifier.hasSystem() && !CPID_SYSTEM.equals(identifier.getSystem())) {
                    violations.append("Patient.identifier with system '")
                            .append(identifier.getSystem())
                            .append("' is not permitted — only CPID identifiers (system: ")
                            .append(CPID_SYSTEM)
                            .append(") are allowed in the SHR. ");
                }
                if (!identifier.hasSystem()) {
                    violations.append("Patient.identifier must have a system URI. " +
                            "Only CPID identifiers are allowed in the SHR. ");
                }
            }
        }

        // Check for unknown extensions that could smuggle PII
        if (patient.hasExtension()) {
            for (Extension ext : patient.getExtension()) {
                if (ext.hasUrl() && !ALLOWED_PATIENT_EXTENSIONS.contains(ext.getUrl())) {
                    violations.append("Patient extension '")
                            .append(ext.getUrl())
                            .append("' is not in the allowed list — unknown extensions on Patient are ")
                            .append("rejected to prevent PII smuggling. ");
                }
            }
        }

        if (!violations.isEmpty()) {
            log.warn("PII prevention: Patient resource rejected — {}", violations);
            throw new UnprocessableEntityException(
                    "PII violation in Patient resource: " + violations.toString().trim() +
                    " The BUTANO Shared Health Record is PII-free. Patient demographics " +
                    "must be stored in VITO (the identity service) and referenced by CPID only.");
        }

        log.debug("PII check passed for Patient resource — CPID-only, no PII fields detected");
    }

    /**
     * Validates that patient/subject references use the CPID format.
     *
     * <p>Uses Java reflection via HAPI's resource model to find patient references.
     * Checks common reference patterns: {@code subject}, {@code patient}.</p>
     *
     * <p>Acceptable reference formats:</p>
     * <ul>
     *   <li>{@code Patient/[id]} — internal HAPI reference (the Patient record is PII-free)</li>
     *   <li>Identifier reference with system {@code https://impilo.gov.zw/cpid}</li>
     * </ul>
     *
     * <p>Rejected reference formats:</p>
     * <ul>
     *   <li>Display text containing potential PII (names in the display field)</li>
     * </ul>
     */
    private void validatePatientReferences(Resource resource) {
        // Skip Patient resources themselves (already handled above)
        if (resource instanceof Patient) {
            return;
        }

        // Use HAPI FHIR's model to check for known patient reference fields
        try {
            checkReferenceField(resource, "subject");
            checkReferenceField(resource, "patient");
        } catch (UnprocessableEntityException e) {
            throw e;
        } catch (Exception e) {
            // Field doesn't exist on this resource type — that's fine
            log.trace("No patient/subject reference field on {} — skipping PII reference check",
                    resource.fhirType());
        }
    }

    /**
     * Checks a specific reference field on a resource for PII leakage.
     */
    private void checkReferenceField(Resource resource, String fieldName) {
        try {
            java.lang.reflect.Method getter = findGetter(resource.getClass(), fieldName);
            if (getter == null) {
                return;
            }

            Object value = getter.invoke(resource);
            if (value instanceof Reference ref) {
                validateReference(ref, resource.fhirType() + "." + fieldName);
            }
        } catch (UnprocessableEntityException e) {
            throw e;
        } catch (Exception e) {
            // Getter not found or invocation failed — this resource doesn't have this field
        }
    }

    /**
     * Finds a getter method for a field name (tries getXxx, hasXxx patterns).
     */
    private java.lang.reflect.Method findGetter(Class<?> clazz, String fieldName) {
        String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        try {
            java.lang.reflect.Method hasMethod = clazz.getMethod("has" + capitalized);
            Boolean hasValue = (Boolean) hasMethod.invoke(null);
            // If we get here with no error, try the getter
        } catch (Exception e) {
            // Ignore — just try the getter directly
        }

        try {
            return clazz.getMethod("get" + capitalized);
        } catch (NoSuchMethodException e) {
            // Try with "Target" suffix (some FHIR resources use this pattern)
            try {
                return clazz.getMethod("get" + capitalized + "Target");
            } catch (NoSuchMethodException e2) {
                return null;
            }
        }
    }

    /**
     * Validates a single Reference for PII leakage.
     *
     * <p>The reference display field is checked for potential name leakage.
     * Display values containing patient names are a common PII leak vector.</p>
     */
    private void validateReference(Reference ref, String path) {
        if (ref == null || ref.isEmpty()) {
            return;
        }

        // Check that display doesn't contain potential PII (names)
        if (ref.hasDisplay()) {
            String display = ref.getDisplay();
            // Display should be generic (e.g., "Patient") not a person's name
            // We reject display values that look like they contain a name (multiple words, proper case)
            if (display.contains(",") || containsNamePattern(display)) {
                log.warn("PII prevention: {} contains display text that may contain PII: '{}'",
                        path, display);
                throw new UnprocessableEntityException(
                        "PII violation in " + path + ": the reference display field '" + display +
                        "' appears to contain personally identifiable information. " +
                        "Patient references in BUTANO must not include names or other PII in the display field. " +
                        "Use a generic display like 'Patient' or omit the display entirely.");
            }
        }

        // If using identifier reference, ensure it's CPID
        if (ref.hasIdentifier()) {
            Identifier identifier = ref.getIdentifier();
            if (identifier.hasSystem() && !CPID_SYSTEM.equals(identifier.getSystem())) {
                throw new UnprocessableEntityException(
                        "PII violation in " + path + ": patient reference uses identifier system '" +
                        identifier.getSystem() + "'. Only CPID references (system: " + CPID_SYSTEM +
                        ") are permitted in the BUTANO SHR.");
            }
        }
    }

    /**
     * Heuristic check for whether a string looks like a person's name.
     *
     * <p>Detects patterns like "John Doe", "DOE, JOHN", etc. This is a conservative
     * heuristic — it may produce false positives, but that's preferred over allowing
     * PII leakage.</p>
     */
    private boolean containsNamePattern(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();

        // Allow common generic display values
        if (trimmed.equalsIgnoreCase("Patient") ||
            trimmed.equalsIgnoreCase("Subject") ||
            trimmed.equalsIgnoreCase("Unknown") ||
            trimmed.equalsIgnoreCase("Anonymous") ||
            trimmed.matches("^(Patient|CPID)[/:].*")) {
            return false;
        }

        // Check for "Lastname, Firstname" pattern
        if (trimmed.matches("^[A-Z][a-z]+,\\s*[A-Z][a-z]+.*")) {
            return true;
        }

        // Check for multiple capitalized words that look like a full name
        // (but not clinical terms which are typically single words or use specific patterns)
        String[] words = trimmed.split("\\s+");
        if (words.length >= 2 && words.length <= 4) {
            boolean allCapitalized = true;
            for (String word : words) {
                if (word.isEmpty() || !Character.isUpperCase(word.charAt(0))) {
                    allCapitalized = false;
                    break;
                }
            }
            if (allCapitalized) {
                // Could be a name like "John Michael Doe" — flag it
                // Clinical terms like "Type 2 Diabetes" wouldn't match because "2" isn't capitalized
                return true;
            }
        }

        return false;
    }
}
