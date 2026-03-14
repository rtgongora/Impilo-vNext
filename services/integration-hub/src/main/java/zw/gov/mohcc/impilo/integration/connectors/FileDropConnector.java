package zw.gov.mohcc.impilo.integration.connectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Component
public class FileDropConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(FileDropConnector.class);

    private final Path dropDirectory;

    public FileDropConnector(
            @Value("${impilo.integration.file-drop.directory:/tmp/impilo-filedrop}") String directory) {
        this.dropDirectory = Path.of(directory);
    }

    @Override
    public ConnectorType type() {
        return ConnectorType.FILE_DROP;
    }

    @Override
    public ConnectorResult execute(ConnectorRequest request) {
        try {
            Files.createDirectories(dropDirectory);

            String timestamp = String.valueOf(Instant.now().toEpochMilli());
            String routeId = request.routeId() != null ? request.routeId() : "unknown";
            String dispatchId = request.headers() != null
                    ? request.headers().getOrDefault("X-Dispatch-ID", "unknown")
                    : "unknown";

            String fileName = routeId + "_" + dispatchId + "_" + timestamp + ".json";
            Path filePath = dropDirectory.resolve(fileName);

            String content = request.body() != null ? request.body() : "";
            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            log.info("FileDropConnector wrote file: {}", filePath);

            return ConnectorResult.success(201, "File written: " + filePath);
        } catch (IOException e) {
            log.error("FileDropConnector error: route={}, error={}", request.routeId(), e.getMessage(), e);
            return ConnectorResult.error("File drop failed: " + e.getMessage());
        }
    }
}
