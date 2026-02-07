package zw.gov.mohcc.impilo.butano.core;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.*;

/**
 * Generates an IPS (International Patient Summary) document Bundle for a given CPID.
 *
 * The IPS Bundle is a FHIR Document containing a Composition resource with sections
 * for allergies, problems, medications, immunizations, vital signs, lab results,
 * procedures, and plan of care. All resources are tenant-filtered.
 *
 * See: https://build.fhir.org/ig/HL7/fhir-ips/
 */
@Service
public class IpsBundleGenerator {

    private static final Logger log = LoggerFactory.getLogger(IpsBundleGenerator.class);

    private static final String IPS_COMPOSITION_PROFILE = "http://hl7.org/fhir/uv/ips/StructureDefinition/Composition-uv-ips";
    private static final String LOINC_SYSTEM = "http://loinc.org";
    private static final String IPS_DOC_CODE = "60591-5";
    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";

    private final DaoRegistry daoRegistry;

    @Value("${butano.tenant.tag-system:https://impilo.gov.zw/tenant}")
    private String tenantTagSystem;

    public IpsBundleGenerator(DaoRegistry daoRegistry) {
        this.daoRegistry = daoRegistry;
    }

    /**
     * Generate an IPS document Bundle for the given CPID.
     *
     * @param cpid the Clinical Patient Identifier
     * @return a FHIR Bundle of type DOCUMENT conforming to IPS structure
     */
    public Bundle generate(String cpid) {
        String tenantId = TrustContextHolder.require().tenantId().toString();
        log.info("Generating IPS Bundle for CPID={} tenant={}", cpid, tenantId);

        // 1. Resolve Patient by CPID identifier
        Patient patient = resolvePatient(cpid, tenantId);
        if (patient == null) {
            throw new IllegalArgumentException("No Patient found for CPID: " + cpid);
        }
        String patientRef = "Patient/" + patient.getIdElement().getIdPart();

        // 2. Gather clinical resources for each IPS section
        List<AllergyIntolerance> allergies = searchBySubject(AllergyIntolerance.class, patientRef, tenantId);
        List<Condition> problems = searchConditions(patientRef, tenantId);
        List<MedicationRequest> medications = searchBySubject(MedicationRequest.class, patientRef, tenantId);
        List<Immunization> immunizations = searchByPatient(Immunization.class, patientRef, tenantId);
        List<Observation> vitalSigns = searchObservationsByCategory(patientRef, tenantId, "vital-signs");
        List<Observation> labResults = searchObservationsByCategory(patientRef, tenantId, "laboratory");
        List<Procedure> procedures = searchBySubject(Procedure.class, patientRef, tenantId);
        List<CarePlan> carePlans = searchBySubject(CarePlan.class, patientRef, tenantId);

        // 3. Build the Document Bundle
        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(new Date());
        bundle.getMeta().addProfile(IPS_COMPOSITION_PROFILE);

        // 4. Build IPS Composition
        Composition composition = buildComposition(cpid, patientRef);
        List<Resource> allResources = new ArrayList<>();
        allResources.add(patient);

        // Sections
        addSection(composition, "Allergies and Intolerances", "48765-2",
                allergies, allResources);
        addSection(composition, "Problem List", "11450-4",
                problems, allResources);
        addSection(composition, "Medication Summary", "10160-0",
                medications, allResources);
        addSection(composition, "Immunizations", "11369-6",
                immunizations, allResources);
        addSection(composition, "Vital Signs", "8716-3",
                vitalSigns, allResources);
        addSection(composition, "Results", "30954-2",
                labResults, allResources);
        addSection(composition, "History of Procedures", "47519-4",
                procedures, allResources);
        addSection(composition, "Plan of Care", "18776-5",
                carePlans, allResources);

        // 5. Composition must be first entry in a Document bundle
        bundle.addEntry()
                .setFullUrl("urn:uuid:" + composition.getId())
                .setResource(composition);

        // Patient entry
        bundle.addEntry()
                .setFullUrl("urn:uuid:" + patient.getIdElement().getIdPart())
                .setResource(patient);

        // All other resource entries
        for (Resource resource : allResources) {
            if (resource instanceof Patient) continue; // already added
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + resource.getIdElement().getIdPart())
                    .setResource(resource);
        }

        log.info("IPS Bundle generated for CPID={}: {} entries", cpid, bundle.getEntry().size());
        return bundle;
    }

    private Patient resolvePatient(String cpid, String tenantId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(CPID_SYSTEM, cpid));
        addTenantFilter(params, tenantId);
        params.setCount(1);

        IBundleProvider results = patientDao.search(params, (RequestDetails) null);
        List<IBaseResource> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return null;
        }
        return (Patient) resources.get(0);
    }

    private List<Condition> searchConditions(String patientRef, String tenantId) {
        IFhirResourceDao<Condition> dao = daoRegistry.getResourceDao(Condition.class);
        SearchParameterMap params = new SearchParameterMap();
        params.add("subject", new ReferenceParam(patientRef));
        params.add("category", new TokenParam(
                "http://terminology.hl7.org/CodeSystem/condition-category", "problem-list-item"));
        addTenantFilter(params, tenantId);
        params.setCount(200);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        return extractAll(results);
    }

    private List<Observation> searchObservationsByCategory(String patientRef, String tenantId, String category) {
        IFhirResourceDao<Observation> dao = daoRegistry.getResourceDao(Observation.class);
        SearchParameterMap params = new SearchParameterMap();
        params.add("subject", new ReferenceParam(patientRef));
        params.add("category", new TokenParam(
                "http://terminology.hl7.org/CodeSystem/observation-category", category));
        addTenantFilter(params, tenantId);
        params.setCount(200);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        return extractAll(results);
    }

    private <T extends Resource> List<T> searchBySubject(Class<T> resourceClass, String patientRef, String tenantId) {
        IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
        SearchParameterMap params = new SearchParameterMap();
        params.add("subject", new ReferenceParam(patientRef));
        addTenantFilter(params, tenantId);
        params.setCount(200);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        return extractAll(results);
    }

    private <T extends Resource> List<T> searchByPatient(Class<T> resourceClass, String patientRef, String tenantId) {
        IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
        SearchParameterMap params = new SearchParameterMap();
        params.add("patient", new ReferenceParam(patientRef));
        addTenantFilter(params, tenantId);
        params.setCount(200);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        return extractAll(results);
    }

    private Composition buildComposition(String cpid, String patientRef) {
        Composition composition = new Composition();
        composition.setId(UUID.randomUUID().toString());
        composition.getMeta().addProfile(IPS_COMPOSITION_PROFILE);
        composition.setStatus(Composition.CompositionStatus.FINAL);
        composition.setType(new CodeableConcept()
                .addCoding(new Coding(LOINC_SYSTEM, IPS_DOC_CODE, "Patient summary Document")));
        composition.setDate(new Date());
        composition.setTitle("International Patient Summary — CPID " + cpid);
        composition.setSubject(new Reference(patientRef));
        composition.setConfidentiality(Composition.DocumentConfidentiality.N);
        return composition;
    }

    private <T extends Resource> void addSection(
            Composition composition,
            String title,
            String loincCode,
            List<T> resources,
            List<Resource> allResources) {

        Composition.SectionComponent section = new Composition.SectionComponent();
        section.setTitle(title);
        section.setCode(new CodeableConcept()
                .addCoding(new Coding(LOINC_SYSTEM, loincCode, title)));

        if (resources.isEmpty()) {
            section.setEmptyReason(new CodeableConcept()
                    .addCoding(new Coding(
                            "http://terminology.hl7.org/CodeSystem/list-empty-reason",
                            "unavailable", "Unavailable")));
        } else {
            for (T resource : resources) {
                String refStr = resource.fhirType() + "/" + resource.getIdElement().getIdPart();
                section.addEntry(new Reference(refStr));
                allResources.add(resource);
            }
        }

        composition.addSection(section);
    }

    private void addTenantFilter(SearchParameterMap params, String tenantId) {
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId));
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> List<T> extractAll(IBundleProvider provider) {
        if (provider.isEmpty()) {
            return List.of();
        }
        int total = provider.sizeOrThrowNpe();
        List<IBaseResource> raw = provider.getResources(0, Math.min(total, 200));
        List<T> typed = new ArrayList<>(raw.size());
        for (IBaseResource r : raw) {
            typed.add((T) r);
        }
        return typed;
    }
}
