package zw.gov.mohcc.impilo.zibo.core;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles bulk import of FHIR terminology resources from FHIR Bundles
 * and CSV files.
 *
 * <p>FHIR Bundle import supports extracting CodeSystem, ValueSet, and
 * ConceptMap resources from a Bundle. Each extracted resource is created
 * as a DRAFT artifact in the system.</p>
 *
 * <p>CSV import materializes a flat code list (code, display, optional
 * definition) into a FHIR CodeSystem JSON resource, then creates it as
 * a DRAFT artifact.</p>
 *
 * <p>Imports that encounter duplicate canonical URL + version combinations
 * are skipped with a warning rather than failing the entire import.</p>
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ArtifactService artifactService;
    private final MappingService mappingService;
    private final ObjectMapper objectMapper;
    private final FhirContext fhirContext;

    public ImportService(ArtifactService artifactService,
                         MappingService mappingService,
                         ObjectMapper objectMapper) {
        this.artifactService = artifactService;
        this.mappingService = mappingService;
        this.objectMapper = objectMapper;
        this.fhirContext = FhirContext.forR4();
    }

    /**
     * Import terminology resources from a FHIR Bundle JSON.
     *
     * <p>Parses the bundle and extracts all CodeSystem, ValueSet, and
     * ConceptMap resources. Each resource is created as a DRAFT artifact.
     * Resources with duplicate canonical URL + version combinations within
     * the tenant are skipped.</p>
     *
     * @param bundleJson the FHIR Bundle JSON string
     * @return an import result summarizing what was processed
     */
    @Transactional
    public ImportResult importFhirBundle(String bundleJson) {
        int total = 0;
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        IParser parser = fhirContext.newJsonParser();
        Bundle bundle;

        try {
            bundle = parser.parseResource(Bundle.class, bundleJson);
        } catch (Exception e) {
            errors.add("Failed to parse FHIR Bundle: " + e.getMessage());
            return new ImportResult(0, 0, 0, errors);
        }

        if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) {
            log.info("Bundle contains no entries");
            return new ImportResult(0, 0, 0, errors);
        }

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource == null) {
                continue;
            }

            ArtifactType artifactType = resolveArtifactType(resource);
            if (artifactType == null) {
                // Not a terminology resource we handle, skip
                continue;
            }

            total++;

            try {
                String contentJson = fhirContext.newJsonParser().encodeResourceToString(resource);
                String canonicalUrl = extractCanonicalUrl(resource);
                String version = extractVersion(resource);
                String name = extractName(resource);
                String title = extractTitle(resource);
                String description = extractDescription(resource);
                String publisher = extractPublisher(resource);

                if (canonicalUrl == null || canonicalUrl.isBlank()) {
                    errors.add("Resource of type " + resource.fhirType()
                            + " has no URL, skipping");
                    skipped++;
                    continue;
                }

                if (version == null || version.isBlank()) {
                    version = "1.0.0";
                }

                artifactService.createDraft(artifactType, canonicalUrl, version,
                        name != null ? name : canonicalUrl,
                        title, description, contentJson, publisher);

                created++;
                log.debug("Imported {} artifact: url={}, version={}",
                        artifactType, canonicalUrl, version);

            } catch (IllegalArgumentException e) {
                // Duplicate canonical + version — skip
                skipped++;
                log.debug("Skipping duplicate resource: {}", e.getMessage());
            } catch (Exception e) {
                errors.add("Failed to import " + resource.fhirType() + ": " + e.getMessage());
                log.warn("Failed to import resource: {}", e.getMessage());
            }
        }

        log.info("FHIR Bundle import complete: total={}, created={}, skipped={}, errors={}",
                total, created, skipped, errors.size());

        return new ImportResult(total, created, skipped, errors);
    }

    /**
     * Import a CodeSystem from a CSV file.
     *
     * <p>Expects a CSV with at least two columns: {@code code} and
     * {@code display}. An optional third column {@code definition} is
     * supported. The CSV is materialized into a FHIR CodeSystem JSON
     * resource and created as a DRAFT artifact.</p>
     *
     * @param systemUrl the canonical URL for the new CodeSystem
     * @param name      the computer-friendly name
     * @param version   the version string
     * @param csvReader the reader providing CSV content
     * @return an import result summarizing what was processed
     */
    @Transactional
    public ImportResult importCsv(String systemUrl, String name, String version, Reader csvReader) {
        List<String> errors = new ArrayList<>();
        int total = 0;
        int created = 0;
        int skipped = 0;

        List<CodeSystem.ConceptDefinitionComponent> concepts = new ArrayList<>();

        try (CSVReader reader = new CSVReader(csvReader)) {
            // Read and validate header
            String[] header = reader.readNext();
            if (header == null || header.length < 2) {
                errors.add("CSV must have at least 2 columns: code, display");
                return new ImportResult(0, 0, 0, errors);
            }

            int codeIdx = findColumnIndex(header, "code");
            int displayIdx = findColumnIndex(header, "display");
            int definitionIdx = findColumnIndex(header, "definition");

            if (codeIdx < 0 || displayIdx < 0) {
                errors.add("CSV must have 'code' and 'display' columns in header");
                return new ImportResult(0, 0, 0, errors);
            }

            String[] row;
            while ((row = reader.readNext()) != null) {
                total++;

                if (row.length <= Math.max(codeIdx, displayIdx)) {
                    errors.add("Row " + total + ": insufficient columns");
                    skipped++;
                    continue;
                }

                String code = row[codeIdx].trim();
                String display = row[displayIdx].trim();

                if (code.isEmpty()) {
                    errors.add("Row " + total + ": empty code");
                    skipped++;
                    continue;
                }

                CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
                concept.setCode(code);
                concept.setDisplay(display);

                if (definitionIdx >= 0 && row.length > definitionIdx) {
                    String definition = row[definitionIdx].trim();
                    if (!definition.isEmpty()) {
                        concept.setDefinition(definition);
                    }
                }

                concepts.add(concept);
            }
        } catch (CsvValidationException | IOException e) {
            errors.add("Failed to read CSV: " + e.getMessage());
            return new ImportResult(total, 0, skipped, errors);
        }

        if (concepts.isEmpty()) {
            errors.add("No valid concepts found in CSV");
            return new ImportResult(total, 0, skipped, errors);
        }

        // Build FHIR CodeSystem resource
        CodeSystem codeSystem = new CodeSystem();
        codeSystem.setUrl(systemUrl);
        codeSystem.setName(name);
        codeSystem.setVersion(version);
        codeSystem.setStatus(Enumerations.PublicationStatus.DRAFT);
        codeSystem.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
        codeSystem.setConcept(concepts);
        codeSystem.setCount(concepts.size());

        String contentJson = fhirContext.newJsonParser()
                .setPrettyPrint(false)
                .encodeResourceToString(codeSystem);

        try {
            artifactService.createDraft(
                    ArtifactType.CODE_SYSTEM,
                    systemUrl,
                    version,
                    name,
                    name, // title = name for CSV imports
                    "Imported from CSV with " + concepts.size() + " concepts",
                    contentJson,
                    null);
            created = 1;
        } catch (Exception e) {
            errors.add("Failed to create artifact: " + e.getMessage());
        }

        log.info("CSV import complete: totalRows={}, concepts={}, created={}, skipped={}, errors={}",
                total, concepts.size(), created, skipped, errors.size());

        return new ImportResult(total, created, skipped, errors);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Resolve the artifact type for a given FHIR resource.
     * Returns {@code null} if the resource is not a terminology artifact we manage.
     */
    private ArtifactType resolveArtifactType(Resource resource) {
        if (resource instanceof CodeSystem) {
            return ArtifactType.CODE_SYSTEM;
        } else if (resource instanceof ValueSet) {
            return ArtifactType.VALUE_SET;
        } else if (resource instanceof ConceptMap) {
            return ArtifactType.CONCEPT_MAP;
        } else if (resource instanceof NamingSystem) {
            return ArtifactType.NAMING_SYSTEM;
        } else if (resource instanceof StructureDefinition) {
            return ArtifactType.STRUCTURE_DEFINITION;
        } else if (resource instanceof ImplementationGuide) {
            return ArtifactType.IMPLEMENTATION_GUIDE;
        }
        return null;
    }

    private String extractCanonicalUrl(Resource resource) {
        if (resource instanceof CodeSystem cs) return cs.getUrl();
        if (resource instanceof ValueSet vs) return vs.getUrl();
        if (resource instanceof ConceptMap cm) return cm.getUrl();
        if (resource instanceof NamingSystem ns) return ns.hasUrl() ? ns.getUrl() : ns.getName();
        if (resource instanceof StructureDefinition sd) return sd.getUrl();
        if (resource instanceof ImplementationGuide ig) return ig.getUrl();
        return null;
    }

    private String extractVersion(Resource resource) {
        if (resource instanceof CodeSystem cs) return cs.getVersion();
        if (resource instanceof ValueSet vs) return vs.getVersion();
        if (resource instanceof ConceptMap cm) return cm.getVersion();
        if (resource instanceof StructureDefinition sd) return sd.getVersion();
        if (resource instanceof ImplementationGuide ig) return ig.getVersion();
        return null;
    }

    private String extractName(Resource resource) {
        if (resource instanceof CodeSystem cs) return cs.getName();
        if (resource instanceof ValueSet vs) return vs.getName();
        if (resource instanceof ConceptMap cm) return cm.getName();
        if (resource instanceof NamingSystem ns) return ns.getName();
        if (resource instanceof StructureDefinition sd) return sd.getName();
        if (resource instanceof ImplementationGuide ig) return ig.getName();
        return null;
    }

    private String extractTitle(Resource resource) {
        if (resource instanceof CodeSystem cs) return cs.getTitle();
        if (resource instanceof ValueSet vs) return vs.getTitle();
        if (resource instanceof ConceptMap cm) return cm.getTitle();
        if (resource instanceof StructureDefinition sd) return sd.getTitle();
        if (resource instanceof ImplementationGuide ig) return ig.getTitle();
        return null;
    }

    private String extractDescription(Resource resource) {
        if (resource instanceof CodeSystem cs) return cs.getDescription();
        if (resource instanceof ValueSet vs) return vs.getDescription();
        if (resource instanceof ConceptMap cm) return cm.getDescription();
        if (resource instanceof NamingSystem ns) return ns.getDescription();
        if (resource instanceof StructureDefinition sd) return sd.getDescription();
        if (resource instanceof ImplementationGuide ig) return ig.getDescription();
        return null;
    }

    private String extractPublisher(Resource resource) {
        if (resource instanceof CodeSystem cs) return cs.getPublisher();
        if (resource instanceof ValueSet vs) return vs.getPublisher();
        if (resource instanceof ConceptMap cm) return cm.getPublisher();
        if (resource instanceof NamingSystem ns) return ns.getPublisher();
        if (resource instanceof StructureDefinition sd) return sd.getPublisher();
        if (resource instanceof ImplementationGuide ig) return ig.getPublisher();
        return null;
    }

    /**
     * Find the index of a column by name (case-insensitive).
     *
     * @return the column index, or -1 if not found
     */
    private int findColumnIndex(String[] header, String columnName) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Inner data class
    // ------------------------------------------------------------------

    /**
     * Result of an import operation.
     *
     * @param total   the total number of resources or rows processed
     * @param created the number of artifacts successfully created
     * @param skipped the number of resources skipped (e.g. duplicates, invalid data)
     * @param errors  a list of error messages encountered during import
     */
    public record ImportResult(int total, int created, int skipped, List<String> errors) {}
}
