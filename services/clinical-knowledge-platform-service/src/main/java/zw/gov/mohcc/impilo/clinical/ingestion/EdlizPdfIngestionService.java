package zw.gov.mohcc.impilo.clinical.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ExtractionJobEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceDocumentEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ExtractionJobRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceDocumentRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceSectionRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;

/**
 * Loads EDLIZ PDF text (per page) into {@code source_sections} with {@code section_path} prefix {@code pdf/}.
 * Curated seed rows (other {@code section_path} values) are preserved unless {@code replace_pdf_sections} is used
 * to drop prior PDF-derived rows only.
 */
@Service
public class EdlizPdfIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EdlizPdfIngestionService.class);
    private static final String PDF_PATH_PREFIX = "pdf/";

    private final PdfTextExtractor pdfTextExtractor;
    private final SourceSectionRepository sectionRepository;
    private final SourceDocumentRepository documentRepository;
    private final ExtractionJobRepository extractionJobRepository;
    private final ObjectMapper objectMapper;

    private final String defaultReferencePdfPath;
    private final String expectedSha256;
    private final String workingDirectory;
    private final int maxChunkChars;
    private final int chunkOverlapChars;

    public EdlizPdfIngestionService(
            PdfTextExtractor pdfTextExtractor,
            SourceSectionRepository sectionRepository,
            SourceDocumentRepository documentRepository,
            ExtractionJobRepository extractionJobRepository,
            ObjectMapper objectMapper,
            @Value("${impilo.clinical.edliz.reference-pdf-path}") String defaultReferencePdfPath,
            @Value("${impilo.clinical.edliz.reference-sha256}") String expectedSha256,
            @Value("${impilo.clinical.ingestion.working-directory:}") String workingDirectory,
            @Value("${impilo.clinical.ingestion.max-chunk-chars:12000}") int maxChunkChars,
            @Value("${impilo.clinical.ingestion.chunk-overlap-chars:400}") int chunkOverlapChars) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.sectionRepository = sectionRepository;
        this.documentRepository = documentRepository;
        this.extractionJobRepository = extractionJobRepository;
        this.objectMapper = objectMapper;
        this.defaultReferencePdfPath = defaultReferencePdfPath;
        this.expectedSha256 = expectedSha256 == null ? "" : expectedSha256.trim();
        this.workingDirectory = workingDirectory == null ? "" : workingDirectory.trim();
        this.maxChunkChars = Math.max(maxChunkChars, 500);
        this.chunkOverlapChars = Math.max(chunkOverlapChars, 0);
    }

    @Transactional
    public Map<String, Object> ingestPdfSections(
            UUID documentId,
            String pdfPathOverride,
            boolean replacePdfSections,
            boolean verifySha256) {

        SourceDocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "source document not found"));

        Path pdfPath = resolvePdfPath(pdfPathOverride != null && !pdfPathOverride.isBlank()
                ? pdfPathOverride
                : defaultReferencePdfPath);

        if (!replacePdfSections
                && sectionRepository.countByDocumentIdAndSectionPathStartingWith(documentId, PDF_PATH_PREFIX) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PDF-derived sections already exist for this document; re-run with replace_pdf_sections=true or use a different document.");
        }

        try {
            if (verifySha256 && !expectedSha256.isEmpty()) {
                verifyFileSha256(pdfPath, expectedSha256);
            }

            if (replacePdfSections) {
                int removed = sectionRepository.deletePdfDerivedSections(documentId);
                log.info("Removed {} prior pdf/* sections for document {}", removed, documentId);
            }

            List<PdfTextExtractor.PageText> pages = pdfTextExtractor.extractPages(pdfPath);
            List<SourceSectionEntity> toSave = new ArrayList<>();
            int nonBlankPages = 0;
            for (PdfTextExtractor.PageText pt : pages) {
                List<String> chunks = TextChunker.chunk(pt.text(), maxChunkChars, chunkOverlapChars);
                if (chunks.isEmpty()) {
                    continue;
                }
                nonBlankPages++;
                for (int c = 0; c < chunks.size(); c++) {
                    String chunk = chunks.get(c);
                    if (chunk.isBlank()) {
                        continue;
                    }
                    SourceSectionEntity row = new SourceSectionEntity();
                    row.setDocumentId(documentId);
                    row.setPageNumber(pt.pageNumber());
                    String title = "EDLIZ — page " + pt.pageNumber();
                    if (chunks.size() > 1) {
                        title += " (part " + (c + 1) + "/" + chunks.size() + ")";
                    }
                    row.setSectionTitle(title);
                    row.setSectionPath(PDF_PATH_PREFIX + "p" + pt.pageNumber() + "c" + c);
                    row.setRawText(chunk);
                    toSave.add(row);
                }
            }
            sectionRepository.saveAll(toSave);

            document.setIngestionStatus("PDF_TEXT_INDEXED");
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);

            ExtractionJobEntity job = new ExtractionJobEntity();
            job.setDocumentId(documentId);
            job.setJobType("PDF_PAGE_EXTRACT");
            job.setStatus("COMPLETED");
            job.setProposedItemsJson(objectMapper.valueToTree(Map.of(
                    "pdf_path", pdfPath.toString(),
                    "pages_total", pages.size(),
                    "non_blank_pages", nonBlankPages,
                    "sections_inserted", toSave.size(),
                    "replace_pdf_sections", replacePdfSections,
                    "max_chunk_chars", maxChunkChars
            )));
            job.setCompletedAt(Instant.now());
            extractionJobRepository.save(job);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("document_id", documentId.toString());
            data.put("extraction_job_id", job.getId().toString());
            data.put("pdf_path", pdfPath.toString());
            data.put("pages_total", pages.size());
            data.put("sections_inserted", toSave.size());
            data.put("ingestion_status", document.getIngestionStatus());
            return data;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF ingestion failed for document {}: {}", documentId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF ingestion failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> ingestionSummary(UUID documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "source document not found"));
        long pdfSections = sectionRepository.countByDocumentIdAndSectionPathStartingWith(documentId, PDF_PATH_PREFIX);
        long allSections = sectionRepository.countByDocumentId(documentId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("document_id", documentId.toString());
        m.put("pdf_derived_section_count", pdfSections);
        m.put("total_section_count", allSections);
        return m;
    }

    private Path resolvePdfPath(String relativeOrAbsolute) {
        Path raw = Paths.get(relativeOrAbsolute);
        Path resolved = raw.isAbsolute()
                ? raw.normalize()
                : baseDir().resolve(relativeOrAbsolute).normalize();
        if (!Files.isRegularFile(resolved)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF file not found: " + resolved + " (check impilo.clinical.ingestion.working-directory and path)");
        }
        return resolved;
    }

    private Path baseDir() {
        if (workingDirectory.isEmpty()) {
            return Paths.get("").toAbsolutePath();
        }
        return Paths.get(workingDirectory).toAbsolutePath().normalize();
    }

    private static void verifyFileSha256(Path file, String expectedHex) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        String hex = HexFormat.of().formatHex(md.digest());
        if (!hex.equalsIgnoreCase(expectedHex.replace(" ", ""))) {
            throw new IllegalStateException("SHA-256 does not match configured impilo.clinical.edliz.reference-sha256");
        }
    }
}
