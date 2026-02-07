package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.msika.api.dto.CreateItemRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ImportSourceRequest;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.*;
import zw.gov.mohcc.impilo.msika.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ExternalSourceRepository sourceRepository;
    private final ExternalMappingRepository mappingRepository;
    private final ImportJobRepository jobRepository;
    private final CatalogItemRepository itemRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ChangeLogService changeLogService;
    private final ObjectMapper objectMapper;

    public ImportService(ExternalSourceRepository sourceRepository,
                         ExternalMappingRepository mappingRepository,
                         ImportJobRepository jobRepository,
                         CatalogItemRepository itemRepository,
                         ProductDetailRepository productDetailRepository,
                         ChangeLogService changeLogService,
                         ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.mappingRepository = mappingRepository;
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.productDetailRepository = productDetailRepository;
        this.changeLogService = changeLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExternalSourceEntity createSource(ImportSourceRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ExternalSourceEntity source = new ExternalSourceEntity();
        source.setSourceId(UlidGenerator.generate());
        source.setTenantId(ctx.tenantId());
        source.setName(request.name());
        source.setMode(request.mode());
        try {
            source.setConfigEncrypted(objectMapper.writeValueAsString(request.config() != null ? request.config() : "{}"));
        } catch (Exception e) {
            source.setConfigEncrypted("{}");
        }
        source = sourceRepository.save(source);
        changeLogService.record("CREATE", "SOURCE", source.getSourceId(), request);
        return source;
    }

    @Transactional
    public ImportJobEntity importCsv(MultipartFile file, String catalogId) {
        TrustContext ctx = TrustContextHolder.require();

        String jobId = UlidGenerator.generate();
        ImportJobEntity job = new ImportJobEntity();
        job.setJobId(jobId);
        job.setTenantId(ctx.tenantId());
        job.setCatalogId(catalogId);
        job.setStatus("VALIDATING");
        job.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");
        job = jobRepository.save(job);

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] header = reader.readNext();
            if (header == null) {
                job.setStatus("FAILED");
                job.setErrorRows(0);
                jobRepository.save(job);
                return job;
            }

            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                colIndex.put(header[i].trim().toLowerCase(), i);
            }

            int total = 0, valid = 0, duplicates = 0, errors = 0, imported = 0;
            List<Map<String, String>> errorRows = new ArrayList<>();

            String[] row;
            while ((row = reader.readNext()) != null) {
                total++;
                try {
                    String code = getColumn(row, colIndex, "canonical_code");
                    String name = getColumn(row, colIndex, "display_name");
                    String kind = getColumn(row, colIndex, "kind");

                    if (code == null || code.isBlank() || name == null || name.isBlank()) {
                        errors++;
                        errorRows.add(Map.of("row", String.valueOf(total), "error", "Missing required fields"));
                        continue;
                    }

                    // Dedup check
                    Optional<CatalogItemEntity> existing = itemRepository.findByCatalogIdAndCanonicalCode(catalogId, code);
                    String barcode = getColumn(row, colIndex, "barcode");
                    if (barcode != null && !barcode.isBlank()) {
                        Optional<ProductDetailEntity> barcodeMatch = productDetailRepository.findByBarcode(barcode);
                        if (barcodeMatch.isPresent()) {
                            duplicates++;
                            continue;
                        }
                    }
                    if (existing.isPresent()) {
                        duplicates++;
                        continue;
                    }

                    // Create item
                    String itemId = UlidGenerator.generate();
                    CatalogItemEntity item = new CatalogItemEntity();
                    item.setItemId(itemId);
                    item.setCatalogId(catalogId);
                    item.setKind(kind != null ? kind : "PRODUCT");
                    item.setCanonicalCode(code);
                    item.setDisplayName(name);
                    item.setDescription(getColumn(row, colIndex, "description"));
                    item.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "CSV_IMPORT");

                    String tagsStr = getColumn(row, colIndex, "tags");
                    if (tagsStr != null) item.setTags(tagsStr.split(";"));

                    itemRepository.save(item);

                    // Product details if applicable
                    if ("PRODUCT".equals(item.getKind())) {
                        ProductDetailEntity pd = new ProductDetailEntity();
                        pd.setItemId(itemId);
                        pd.setForm(getColumn(row, colIndex, "form"));
                        pd.setStrength(getColumn(row, colIndex, "strength"));
                        pd.setRoute(getColumn(row, colIndex, "route"));
                        pd.setUom(getColumn(row, colIndex, "uom"));
                        pd.setBarcode(barcode);
                        String packSizeStr = getColumn(row, colIndex, "pack_size");
                        if (packSizeStr != null) {
                            try { pd.setPackSize(Integer.parseInt(packSizeStr)); } catch (NumberFormatException ignored) {}
                        }
                        pd.setManufacturer(getColumn(row, colIndex, "manufacturer"));
                        pd.setAtcCode(getColumn(row, colIndex, "atc_code"));
                        productDetailRepository.save(pd);
                    }

                    imported++;
                    valid++;
                } catch (Exception e) {
                    errors++;
                    errorRows.add(Map.of("row", String.valueOf(total), "error", e.getMessage()));
                }
            }

            job.setStatus("COMPLETED");
            job.setTotalRows(total);
            job.setValidRows(valid);
            job.setDuplicateRows(duplicates);
            job.setErrorRows(errors);
            job.setImportedRows(imported);
            job.setCompletedAt(OffsetDateTime.now());

            try {
                job.setStats(objectMapper.writeValueAsString(Map.of(
                        "total", total, "valid", valid, "duplicates", duplicates,
                        "errors", errors, "imported", imported, "errorDetails", errorRows
                )));
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.error("CSV import failed for job {}: {}", jobId, e.getMessage());
            job.setStatus("FAILED");
            try {
                job.setStats(objectMapper.writeValueAsString(Map.of("error", e.getMessage())));
            } catch (Exception ignored) {}
        }

        job = jobRepository.save(job);
        changeLogService.record("CREATE", "IMPORT_JOB", jobId, job);
        return job;
    }

    @Transactional
    public ImportJobEntity runSourceImport(String sourceId) {
        TrustContext ctx = TrustContextHolder.require();
        ExternalSourceEntity source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        String jobId = UlidGenerator.generate();
        ImportJobEntity job = new ImportJobEntity();
        job.setJobId(jobId);
        job.setSourceId(sourceId);
        job.setTenantId(ctx.tenantId());
        job.setStatus("PENDING");
        job.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");
        job = jobRepository.save(job);

        source.setLastRunAt(OffsetDateTime.now());
        sourceRepository.save(source);

        log.info("Queued import job {} for source {} (mode={})", jobId, sourceId, source.getMode());
        return job;
    }

    public ImportJobEntity getJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
    }

    private String getColumn(String[] row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null || idx >= row.length) return null;
        String val = row[idx].trim();
        return val.isEmpty() ? null : val;
    }
}
