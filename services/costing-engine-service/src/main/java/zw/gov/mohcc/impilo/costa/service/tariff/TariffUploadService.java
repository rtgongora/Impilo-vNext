package zw.gov.mohcc.impilo.costa.service.tariff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaTariffListEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaTariffListItemEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffUploadBatchEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffUploadRowEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.CostaTariffListItemRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.CostaTariffListRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.TariffLibraryRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.TariffUploadBatchRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.TariffUploadRowRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TariffUploadService {

    private static final String GLOBAL_LIBRARY = "IMPILO_GLOBAL";

    private final TariffUploadBatchRepository batchRepository;
    private final TariffUploadRowRepository rowRepository;
    private final TariffLibraryRepository tariffLibraryRepository;
    private final CostaTariffListRepository costaTariffListRepository;
    private final CostaTariffListItemRepository costaTariffListItemRepository;
    private final ObjectMapper objectMapper;

    public TariffUploadService(TariffUploadBatchRepository batchRepository,
                               TariffUploadRowRepository rowRepository,
                               TariffLibraryRepository tariffLibraryRepository,
                               CostaTariffListRepository costaTariffListRepository,
                               CostaTariffListItemRepository costaTariffListItemRepository,
                               ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.tariffLibraryRepository = tariffLibraryRepository;
        this.costaTariffListRepository = costaTariffListRepository;
        this.costaTariffListItemRepository = costaTariffListItemRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TariffUploadBatchEntity ingest(UUID tenantId, JsonNode body, String uploadedBy) throws IOException {
        String fileName = requiredText(body, "file_name");
        String proposedName = requiredText(body, "proposed_tariff_name");
        String proposedCurrency = text(body, "proposed_currency");
        if (proposedCurrency == null) {
            proposedCurrency = "USD";
        }
        String proposedCountry = text(body, "proposed_country");
        String ownerOrg = text(body, "owner_organisation");

        byte[] rawBytes;
        if (body.hasNonNull("file_base64")) {
            rawBytes = Base64.getDecoder().decode(body.get("file_base64").asText().trim());
        } else if (body.hasNonNull("raw_text")) {
            rawBytes = body.get("raw_text").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Provide file_base64 or raw_text");
        }

        String fileType = inferFileType(fileName, body);
        List<Map<String, String>> parsed = TariffUploadFileParser.parse(fileType, rawBytes, objectMapper);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("No data rows found in upload");
        }

        TariffUploadBatchEntity batch = new TariffUploadBatchEntity();
        batch.setTenantId(tenantId);
        batch.setFileName(fileName);
        batch.setFileType(fileType);
        batch.setUploadedBy(uploadedBy);
        batch.setOwnerOrganisation(ownerOrg);
        batch.setProposedTariffName(proposedName);
        batch.setProposedCountry(proposedCountry);
        batch.setProposedCurrency(proposedCurrency);
        batch.setUploadStatus("RECEIVED");
        batch.setValidationStatus("unvalidated");
        batch.setRawBytes(rawBytes);
        if (fileType.contains("json") || fileType.contains("csv")) {
            batch.setRawText(new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8));
        }
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("detected_row_count", parsed.size());
        batch.setMetadata(meta.toString());

        String mappingJson = objectMapper.writeValueAsString(guessMapping(parsed.get(0)));
        batch.setColumnMapping(mappingJson);
        batch = batchRepository.save(batch);

        rowRepository.deleteByUploadBatchId(batch.getUploadBatchId());
        int rowNum = 1;
        for (Map<String, String> row : parsed) {
            TariffUploadRowEntity r = new TariffUploadRowEntity();
            r.setUploadBatchId(batch.getUploadBatchId());
            r.setRowNumber(rowNum++);
            r.setRawPayload(objectMapper.writeValueAsString(row));
            r.setSeverity("unknown");
            r.setMessages("[]");
            rowRepository.save(r);
        }
        batch.setRowsTotal(parsed.size());
        batch.setRowsValid(0);
        batch.setRowsWithWarnings(0);
        batch.setRowsWithErrors(0);
        return batchRepository.save(batch);
    }

    private static String inferFileType(String fileName, JsonNode body) {
        if (body.hasNonNull("file_type")) {
            return body.get("file_type").asText().trim();
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".csv")) {
            return "csv";
        }
        if (lower.endsWith(".xlsx")) {
            return "xlsx";
        }
        if (lower.endsWith(".xls")) {
            return "xls";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        return "csv";
    }

    /**
     * Canonical tariff fields → source column names from the first row of the file.
     */
    private Map<String, String> guessMapping(Map<String, String> sampleRow) {
        Map<String, String> lowerKeyToOriginal = new HashMap<>();
        for (String k : sampleRow.keySet()) {
            lowerKeyToOriginal.put(k.toLowerCase().replace(' ', '_'), k);
        }
        Map<String, String> m = new LinkedHashMap<>();
        putSynonym(m, "item_code", lowerKeyToOriginal, List.of("item_code", "code", "service_code", "tariff_code"));
        putSynonym(m, "item_name", lowerKeyToOriginal, List.of("item_name", "name", "description", "service_name"));
        putSynonym(m, "base_price", lowerKeyToOriginal, List.of("base_price", "price", "amount", "unit_price"));
        putSynonym(m, "service_domain", lowerKeyToOriginal, List.of("service_domain", "domain", "clinical_domain"));
        return m;
    }

    private void putSynonym(Map<String, String> mapping, String canonical,
                            Map<String, String> lowerKeyToOriginal, List<String> synonyms) {
        for (String syn : synonyms) {
            if (lowerKeyToOriginal.containsKey(syn)) {
                mapping.put(canonical, lowerKeyToOriginal.get(syn));
                return;
            }
        }
    }

    @Transactional(readOnly = true)
    public TariffUploadBatchEntity requireBatch(UUID tenantId, UUID batchId) {
        return batchRepository.findByUploadBatchIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Upload batch not found: " + batchId));
    }

    @Transactional
    public TariffUploadBatchEntity setColumnMapping(UUID tenantId, UUID batchId, JsonNode body) {
        TariffUploadBatchEntity batch = requireBatch(tenantId, batchId);
        if ("IMPORTED".equalsIgnoreCase(batch.getUploadStatus())) {
            throw new IllegalStateException("Batch already imported");
        }
        JsonNode mapNode = body.has("column_mapping") ? body.get("column_mapping") : body;
        if (!mapNode.isObject()) {
            throw new IllegalArgumentException("column_mapping must be a JSON object");
        }
        try {
            batch.setColumnMapping(objectMapper.writeValueAsString(mapNode));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid column_mapping");
        }
        batch.setValidationStatus("unvalidated");
        return batchRepository.save(batch);
    }

    @Transactional
    public TariffUploadBatchEntity validate(UUID tenantId, UUID batchId) {
        TariffUploadBatchEntity batch = requireBatch(tenantId, batchId);
        if ("IMPORTED".equalsIgnoreCase(batch.getUploadStatus())) {
            throw new IllegalStateException("Batch already imported");
        }
        Map<String, String> colMap = readMapping(batch.getColumnMapping());
        List<TariffUploadRowEntity> rows = rowRepository.findByUploadBatchIdOrderByRowNumberAsc(batchId);
        int valid = 0;
        int warn = 0;
        int err = 0;
        Set<String> codesSeen = new HashSet<>();

        for (TariffUploadRowEntity row : rows) {
            ArrayNode msgs = objectMapper.createArrayNode();
            try {
                JsonNode raw = objectMapper.readTree(row.getRawPayload());
                ObjectNode normalized = applyMapping(raw, colMap);

                String code = nodeText(normalized, "item_code");
                String name = nodeText(normalized, "item_name");
                String priceStr = nodeText(normalized, "base_price");
                List<String> rowErrs = new ArrayList<>();
                if (code == null || code.isBlank()) {
                    rowErrs.add("Missing item_code");
                }
                if (name == null || name.isBlank()) {
                    rowErrs.add("Missing item_name");
                }
                BigDecimal price = null;
                if (priceStr != null && !priceStr.isBlank()) {
                    try {
                        price = new BigDecimal(priceStr);
                        if (price.compareTo(BigDecimal.ZERO) < 0) {
                            rowErrs.add("base_price must be >= 0");
                        }
                    } catch (NumberFormatException ex) {
                        rowErrs.add("Invalid base_price");
                    }
                } else {
                    rowErrs.add("Missing base_price");
                }
                if (code != null && !code.isBlank()) {
                    String key = code.trim().toLowerCase();
                    if (!codesSeen.add(key)) {
                        rowErrs.add("Duplicate item_code in this upload");
                    }
                }

                boolean hasServiceDomain = nodeText(normalized, "service_domain") != null;
                if (rowErrs.isEmpty() && !hasServiceDomain) {
                    msgs.add("Optional service_domain is empty");
                }

                for (String e : rowErrs) {
                    msgs.add(e);
                }

                row.setNormalizedPayload(normalized.toString());
                if (!rowErrs.isEmpty()) {
                    row.setSeverity("error");
                    err++;
                } else if (!hasServiceDomain) {
                    row.setSeverity("warning");
                    warn++;
                    valid++;
                } else {
                    row.setSeverity("ok");
                    valid++;
                }
                row.setMessages(msgs.toString());
                rowRepository.save(row);
            } catch (Exception e) {
                row.setSeverity("error");
                msgs.add("Row parse failed: " + e.getMessage());
                row.setMessages(msgs.toString());
                err++;
                rowRepository.save(row);
            }
        }

        batch.setRowsValid(valid);
        batch.setRowsWithWarnings(warn);
        batch.setRowsWithErrors(err);
        if (err > 0) {
            batch.setValidationStatus("invalid");
        } else if (warn > 0) {
            batch.setValidationStatus("valid_with_warnings");
        } else {
            batch.setValidationStatus("valid");
        }
        return batchRepository.save(batch);
    }

    private Map<String, String> readMapping(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            Map<String, String> m = new LinkedHashMap<>();
            n.fields().forEachRemaining(e -> {
                if (e.getValue() != null && !e.getValue().isNull()) {
                    m.put(e.getKey(), e.getValue().asText());
                }
            });
            return m;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid stored column_mapping");
        }
    }

    private ObjectNode applyMapping(JsonNode rawRow, Map<String, String> colMap) {
        ObjectNode n = objectMapper.createObjectNode();
        for (Map.Entry<String, String> e : colMap.entrySet()) {
            String canonical = e.getKey();
            String src = e.getValue();
            if (src == null) {
                continue;
            }
            JsonNode v = rawRow.get(src);
            if (v != null && !v.isNull()) {
                n.put(canonical, v.asText());
            }
        }
        return n;
    }

    @Transactional
    public TariffUploadBatchEntity submit(UUID tenantId, UUID batchId) {
        TariffUploadBatchEntity batch = requireBatch(tenantId, batchId);
        if (batch.getRowsWithErrors() > 0 || "invalid".equals(batch.getValidationStatus())) {
            throw new IllegalStateException("Cannot submit: validation has errors");
        }
        if (!"valid".equals(batch.getValidationStatus()) && !"valid_with_warnings".equals(batch.getValidationStatus())) {
            throw new IllegalStateException("Run validation before submit");
        }
        batch.setUploadStatus("SUBMITTED");
        return batchRepository.save(batch);
    }

    @Transactional
    public Map<String, Object> approveImport(UUID tenantId, UUID batchId, JsonNode body) {
        TariffUploadBatchEntity batch = requireBatch(tenantId, batchId);
        if (!"SUBMITTED".equalsIgnoreCase(batch.getUploadStatus())) {
            throw new IllegalStateException("Batch must be in SUBMITTED status");
        }
        boolean force = body != null && body.path("force").asBoolean(false);
        if (!force && batch.getRowsWithErrors() > 0) {
            throw new IllegalStateException("Cannot import batch with row errors unless force=true");
        }

        long libraryId = tariffLibraryRepository.findByCode(GLOBAL_LIBRARY)
                .orElseThrow(() -> new IllegalStateException("Tariff library " + GLOBAL_LIBRARY + " not seeded"))
                .getId();

        CostaTariffListEntity list = new CostaTariffListEntity();
        list.setTenantId(tenantId);
        list.setLibraryId(libraryId);
        String ext = "UPL-" + batch.getUploadBatchId().toString().replace("-", "");
        list.setExternalCode(ext.length() > 120 ? ext.substring(0, 120) : ext);
        list.setName(batch.getProposedTariffName());
        list.setDescription("Imported from upload batch " + batch.getUploadBatchId());
        list.setTariffFamily("UPLOAD");
        list.setTariffType("SERVICE");
        list.setPriceBasis("UNIT");
        list.setOfficialStatus("tenant_upload");
        list.setValidationStatus("validated");
        list.setReferenceOnly(true);
        list.setApprovedForBilling(false);
        list.setCurrency(batch.getProposedCurrency());
        list.setEffectiveFrom(LocalDate.now());
        try {
            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("upload_batch_id", batch.getUploadBatchId().toString());
            meta.put("source_file", batch.getFileName());
            if (batch.getProposedCountry() != null) {
                meta.put("proposed_country", batch.getProposedCountry());
            }
            list.setMetadata(meta.toString());
        } catch (Exception e) {
            throw new IllegalStateException("metadata", e);
        }
        list = costaTariffListRepository.save(list);

        List<TariffUploadRowEntity> rows = rowRepository.findByUploadBatchIdOrderByRowNumberAsc(batchId);
        for (TariffUploadRowEntity row : rows) {
            if (!"ok".equals(row.getSeverity()) && !"warning".equals(row.getSeverity())) {
                if (!force) {
                    throw new IllegalStateException("Row " + row.getRowNumber() + " is not importable");
                }
                continue;
            }
            try {
                JsonNode norm = objectMapper.readTree(row.getNormalizedPayload());
                String code = nodeRequiredText(norm, "item_code");
                String name = nodeRequiredText(norm, "item_name");
                BigDecimal price = new BigDecimal(nodeRequiredText(norm, "base_price"));
                CostaTariffListItemEntity it = new CostaTariffListItemEntity();
                it.setTariffListId(list.getId());
                it.setItemCode(code.trim());
                it.setItemName(name.trim());
                it.setServiceDomain(nodeText(norm, "service_domain"));
                it.setBasePrice(price);
                it.setCurrency(batch.getProposedCurrency());
                ObjectNode im = objectMapper.createObjectNode();
                im.put("upload_batch_id", batch.getUploadBatchId().toString());
                im.put("row_number", row.getRowNumber());
                it.setMetadata(im.toString());
                costaTariffListItemRepository.save(it);
            } catch (Exception e) {
                if (!force) {
                    throw new IllegalStateException("Failed on row " + row.getRowNumber() + ": " + e.getMessage());
                }
            }
        }

        batch.setUploadStatus("IMPORTED");
        batch.setCompletedAt(java.time.OffsetDateTime.now());
        batchRepository.save(batch);

        return Map.of(
                "tariff_list_id", list.getId(),
                "external_code", list.getExternalCode(),
                "upload_batch_id", batch.getUploadBatchId().toString()
        );
    }

    @Transactional(readOnly = true)
    public List<TariffUploadRowEntity> listRows(UUID tenantId, UUID batchId) {
        requireBatch(tenantId, batchId);
        return rowRepository.findByUploadBatchIdOrderByRowNumberAsc(batchId);
    }

    private static String nodeText(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field)) {
            return null;
        }
        String v = n.get(field).asText();
        return v.isBlank() ? null : v;
    }

    private static String nodeRequiredText(JsonNode n, String field) {
        String v = nodeText(n, field);
        if (v == null) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return v;
    }

    private static String text(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field)) {
            return null;
        }
        String v = body.get(field).asText();
        return v.isBlank() ? null : v;
    }

    private static String requiredText(JsonNode body, String field) {
        String v = text(body, field);
        if (v == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return v;
    }
}
