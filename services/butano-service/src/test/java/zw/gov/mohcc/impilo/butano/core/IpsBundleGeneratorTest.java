package zw.gov.mohcc.impilo.butano.core;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link IpsBundleGenerator} — the International Patient
 * Summary (IPS) document generator for the BUTANO Shared Health Record.
 *
 * <p>Tests verify that the generated IPS Bundle conforms to the IPS specification:</p>
 * <ul>
 *   <li>Bundle type is DOCUMENT</li>
 *   <li>First entry is a Composition resource</li>
 *   <li>Composition contains all required IPS sections (allergies, problems, medications, etc.)</li>
 *   <li>Patient reference uses CPID identifier</li>
 *   <li>All resources are correctly included as Bundle entries</li>
 * </ul>
 *
 * <p>Uses mocked DaoRegistry to simulate HAPI FHIR JPA resource retrieval
 * without requiring a running database or FHIR server.</p>
 */
@ExtendWith(MockitoExtension.class)
class IpsBundleGeneratorTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CPID = "CPID-12345";
    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";
    private static final String LOINC_SYSTEM = "http://loinc.org";

    @Mock private DaoRegistry daoRegistry;
    @Mock private IFhirResourceDao<Patient> patientDao;
    @Mock private IFhirResourceDao<AllergyIntolerance> allergyDao;
    @Mock private IFhirResourceDao<Condition> conditionDao;
    @Mock private IFhirResourceDao<MedicationRequest> medicationDao;
    @Mock private IFhirResourceDao<Immunization> immunizationDao;
    @Mock private IFhirResourceDao<Observation> observationDao;
    @Mock private IFhirResourceDao<Procedure> procedureDao;
    @Mock private IFhirResourceDao<CarePlan> carePlanDao;

    private IpsBundleGenerator generator;

    @BeforeEach
    void setUp() {
        // Set up TrustContext so generate() can resolve tenantId
        TrustContext trustContext = new TrustContext(
                TENANT_ID, "actor-123", "PRACTITIONER", "TREATMENT",
                "fp-sha256-abc", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, AccessMode.INTERNAL
        );
        TrustContextHolder.set(trustContext);

        generator = new IpsBundleGenerator(daoRegistry);

        // Use reflection to set the @Value field since we're not using Spring context
        try {
            java.lang.reflect.Field field = IpsBundleGenerator.class.getDeclaredField("tenantTagSystem");
            field.setAccessible(true);
            field.set(generator, "https://impilo.gov.zw/tenant");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set tenantTagSystem field", e);
        }
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /**
     * Creates a test Patient resource with only CPID identifier (PII-free).
     */
    private Patient createTestPatient() {
        Patient patient = new Patient();
        patient.setId("patient-1");
        patient.addIdentifier()
                .setSystem(CPID_SYSTEM)
                .setValue(CPID);
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.setBirthDateElement(new DateType("1985-03-15"));
        patient.setActive(true);
        return patient;
    }

    /**
     * Creates a mock IBundleProvider that returns the specified resources.
     */
    private IBundleProvider mockBundleProvider(List<? extends IBaseResource> resources) {
        IBundleProvider provider = mock(IBundleProvider.class);
        lenient().when(provider.isEmpty()).thenReturn(resources.isEmpty());
        lenient().when(provider.sizeOrThrowNpe()).thenReturn(resources.size());
        lenient().when(provider.getResources(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(resources));
        return provider;
    }

    /**
     * Creates an empty mock IBundleProvider.
     */
    private IBundleProvider emptyBundleProvider() {
        return mockBundleProvider(List.of());
    }

    /**
     * Stubs all DAO registrations with the appropriate mocks.
     */
    @SuppressWarnings("unchecked")
    private void stubDaoRegistry() {
        when(daoRegistry.getResourceDao(Patient.class)).thenReturn(patientDao);
        when(daoRegistry.getResourceDao(AllergyIntolerance.class)).thenReturn(allergyDao);
        when(daoRegistry.getResourceDao(Condition.class)).thenReturn(conditionDao);
        when(daoRegistry.getResourceDao(MedicationRequest.class)).thenReturn(medicationDao);
        when(daoRegistry.getResourceDao(Immunization.class)).thenReturn(immunizationDao);
        when(daoRegistry.getResourceDao(Observation.class)).thenReturn(observationDao);
        when(daoRegistry.getResourceDao(Procedure.class)).thenReturn(procedureDao);
        when(daoRegistry.getResourceDao(CarePlan.class)).thenReturn(carePlanDao);
    }

    /**
     * Stubs all DAOs to return empty results by default.
     */
    private void stubEmptyResults() {
        when(patientDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(mockBundleProvider(List.of(createTestPatient())));
        when(allergyDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(conditionDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(medicationDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(immunizationDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(observationDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(procedureDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(carePlanDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
    }

    // ════════════════════════════════════════════════════════════════════
    // IPS Bundle structure verification
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generates valid IPS Bundle with correct document structure for a CPID")
    void testGenerateIpsBundleForCpid() {
        stubDaoRegistry();

        // Patient DAO returns a test patient
        Patient testPatient = createTestPatient();
        when(patientDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(mockBundleProvider(List.of(testPatient)));

        // Create test clinical resources
        AllergyIntolerance allergy = new AllergyIntolerance();
        allergy.setId("allergy-1");
        allergy.setClinicalStatus(new CodeableConcept()
                .addCoding(new Coding(
                        "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                        "active", "Active")));
        allergy.setPatient(new Reference("Patient/patient-1"));

        Condition condition = new Condition();
        condition.setId("condition-1");
        condition.setSubject(new Reference("Patient/patient-1"));
        condition.setCode(new CodeableConcept()
                .addCoding(new Coding("http://snomed.info/sct", "73211009", "Diabetes mellitus")));

        MedicationRequest medication = new MedicationRequest();
        medication.setId("medrx-1");
        medication.setSubject(new Reference("Patient/patient-1"));
        medication.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);

        // Stub DAOs with test data
        when(allergyDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(mockBundleProvider(List.of(allergy)));
        when(conditionDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(mockBundleProvider(List.of(condition)));
        when(medicationDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(mockBundleProvider(List.of(medication)));
        when(immunizationDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(observationDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(procedureDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());
        when(carePlanDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());

        // Generate the IPS Bundle
        Bundle bundle = generator.generate(CPID);

        // ── Verify Bundle type is DOCUMENT ──
        assertNotNull(bundle, "Generated bundle must not be null");
        assertEquals(Bundle.BundleType.DOCUMENT, bundle.getType(),
                "IPS Bundle type must be DOCUMENT");

        // ── Verify first entry is Composition ──
        assertFalse(bundle.getEntry().isEmpty(),
                "Bundle must have at least one entry");
        Resource firstEntry = bundle.getEntry().get(0).getResource();
        assertInstanceOf(Composition.class, firstEntry,
                "First entry in IPS Document Bundle must be a Composition resource");

        Composition composition = (Composition) firstEntry;

        // ── Verify Composition structure ──
        assertEquals(Composition.CompositionStatus.FINAL, composition.getStatus(),
                "Composition status must be FINAL");
        assertNotNull(composition.getType(),
                "Composition must have a type");
        assertTrue(composition.getType().getCoding().stream()
                        .anyMatch(c -> LOINC_SYSTEM.equals(c.getSystem()) && "60591-5".equals(c.getCode())),
                "Composition type must be LOINC 60591-5 (Patient summary Document)");
        assertTrue(composition.getTitle().contains(CPID),
                "Composition title must reference the CPID");

        // ── Verify patient reference uses CPID ──
        assertNotNull(composition.getSubject(),
                "Composition must have a subject reference");
        assertTrue(composition.getSubject().getReference().contains("Patient/"),
                "Composition subject must reference a Patient resource");

        // Verify Patient entry exists in the Bundle
        boolean hasPatient = bundle.getEntry().stream()
                .anyMatch(e -> e.getResource() instanceof Patient);
        assertTrue(hasPatient, "Bundle must contain a Patient entry");

        // Verify Patient uses CPID
        Patient bundlePatient = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .findFirst()
                .orElseThrow();
        assertTrue(bundlePatient.getIdentifier().stream()
                        .anyMatch(id -> CPID_SYSTEM.equals(id.getSystem()) && CPID.equals(id.getValue())),
                "Bundle Patient must have CPID identifier");

        // ── Verify IPS sections exist ──
        List<Composition.SectionComponent> sections = composition.getSection();
        assertFalse(sections.isEmpty(),
                "Composition must have sections");

        // Required IPS sections with their LOINC codes
        Map<String, String> expectedSections = new LinkedHashMap<>();
        expectedSections.put("48765-2", "Allergies and Intolerances");
        expectedSections.put("11450-4", "Problem List");
        expectedSections.put("10160-0", "Medication Summary");
        expectedSections.put("11369-6", "Immunizations");
        expectedSections.put("8716-3", "Vital Signs");
        expectedSections.put("30954-2", "Results");
        expectedSections.put("47519-4", "History of Procedures");
        expectedSections.put("18776-5", "Plan of Care");

        for (Map.Entry<String, String> expected : expectedSections.entrySet()) {
            String loincCode = expected.getKey();
            String sectionName = expected.getValue();

            boolean found = sections.stream()
                    .anyMatch(s -> s.getCode() != null && s.getCode().getCoding().stream()
                            .anyMatch(c -> LOINC_SYSTEM.equals(c.getSystem()) && loincCode.equals(c.getCode())));
            assertTrue(found,
                    "Composition must have IPS section '" + sectionName + "' (LOINC " + loincCode + ")");
        }

        // ── Verify sections with data have entries ──
        // Allergies section should have entries (we provided one allergy)
        Composition.SectionComponent allergySection = sections.stream()
                .filter(s -> s.getCode() != null && s.getCode().getCoding().stream()
                        .anyMatch(c -> "48765-2".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allergies section missing"));
        assertFalse(allergySection.getEntry().isEmpty(),
                "Allergies section must have entries when allergies exist");

        // Problems section should have entries (we provided one condition)
        Composition.SectionComponent problemSection = sections.stream()
                .filter(s -> s.getCode() != null && s.getCode().getCoding().stream()
                        .anyMatch(c -> "11450-4".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Problems section missing"));
        assertFalse(problemSection.getEntry().isEmpty(),
                "Problems section must have entries when conditions exist");

        // Medications section should have entries
        Composition.SectionComponent medSection = sections.stream()
                .filter(s -> s.getCode() != null && s.getCode().getCoding().stream()
                        .anyMatch(c -> "10160-0".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Medications section missing"));
        assertFalse(medSection.getEntry().isEmpty(),
                "Medications section must have entries when medications exist");

        // Immunizations section should be empty (no test data provided)
        Composition.SectionComponent immunizationSection = sections.stream()
                .filter(s -> s.getCode() != null && s.getCode().getCoding().stream()
                        .anyMatch(c -> "11369-6".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Immunizations section missing"));
        assertNotNull(immunizationSection.getEmptyReason(),
                "Empty sections must have an emptyReason");

        // ── Verify total Bundle entry count ──
        // Expected: Composition + Patient + AllergyIntolerance + Condition + MedicationRequest = 5
        assertEquals(5, bundle.getEntry().size(),
                "Bundle must contain exactly 5 entries: Composition + Patient + 3 clinical resources");
    }

    @Test
    @DisplayName("generates IPS Bundle with empty sections when no clinical data exists")
    void testGenerateIpsBundleWithNoClinicalData() {
        stubDaoRegistry();
        stubEmptyResults();

        Bundle bundle = generator.generate(CPID);

        // Verify Bundle is still valid
        assertNotNull(bundle);
        assertEquals(Bundle.BundleType.DOCUMENT, bundle.getType());

        // First entry must still be Composition
        Composition composition = (Composition) bundle.getEntry().get(0).getResource();
        assertNotNull(composition);

        // All sections should have emptyReason set
        for (Composition.SectionComponent section : composition.getSection()) {
            if (section.getEntry().isEmpty()) {
                assertNotNull(section.getEmptyReason(),
                        "Empty section '" + section.getTitle() + "' must have an emptyReason");
            }
        }

        // Bundle should contain Composition + Patient only
        assertEquals(2, bundle.getEntry().size(),
                "Bundle with no clinical data must contain only Composition + Patient");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when no Patient found for CPID")
    void testThrowsWhenPatientNotFound() {
        when(daoRegistry.getResourceDao(Patient.class)).thenReturn(patientDao);
        when(patientDao.search(any(SearchParameterMap.class), (RequestDetails) isNull()))
                .thenReturn(emptyBundleProvider());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate("CPID-NONEXISTENT"),
                "Must throw when no Patient is found for the given CPID"
        );

        assertTrue(ex.getMessage().contains("No Patient found"),
                "Error message must indicate that no Patient was found");
        assertTrue(ex.getMessage().contains("CPID-NONEXISTENT"),
                "Error message must include the searched CPID");
    }
}
