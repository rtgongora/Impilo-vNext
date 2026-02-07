package zw.gov.mohcc.impilo.cardprint.service.spooler;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.cardprint.config.CardPrintProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * File-based spooler that writes rendered PDFs to a local filesystem directory.
 * Activated when {@code card-print.spooler.type=FILE}.
 */
@Service
@ConditionalOnProperty(name = "card-print.spooler.type", havingValue = "FILE", matchIfMissing = true)
public class FileSpoolerService implements SpoolerService {

    private static final Logger log = LoggerFactory.getLogger(FileSpoolerService.class);

    private final CardPrintProperties properties;
    private Path outputDir;

    public FileSpoolerService(CardPrintProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        this.outputDir = Paths.get(properties.getSpooler().getOutputDir());
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
            log.info("Created card print output directory: {}", outputDir);
        }
        log.info("File spooler initialized with output directory: {}", outputDir);
    }

    @Override
    public void spool(UUID jobId, String fileName, byte[] pdfData) throws IOException {
        Path jobDir = outputDir.resolve(jobId.toString());
        if (!Files.exists(jobDir)) {
            Files.createDirectories(jobDir);
        }

        Path filePath = jobDir.resolve(fileName);
        Files.write(filePath, pdfData);
        log.info("Spooled PDF for job {} to {} ({} bytes)", jobId, filePath, pdfData.length);
    }

    @Override
    public byte[] retrieve(UUID jobId, String fileName) throws IOException {
        Path filePath = outputDir.resolve(jobId.toString()).resolve(fileName);
        if (!Files.exists(filePath)) {
            log.warn("PDF not found for job {} at {}", jobId, filePath);
            return null;
        }
        return Files.readAllBytes(filePath);
    }
}
