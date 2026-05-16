package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Minimal external PACS provider stub for neutral runtime wiring.
 * Concrete external PACS/VNA connectors are intentionally follow-on work.
 */
@Component
@ConditionalOnProperty(name = "impilo.pacs.backend.provider", havingValue = "EXTERNAL")
public class ExternalPacsClient implements PacsBackendClient {

    private final String backendMode;
    private final String externalBaseUrl;
    private final boolean dicomwebEnabled;
    private final boolean dimseEnabled;

    public ExternalPacsClient(
            @Value("${impilo.pacs.backend.mode:EMBEDDED}") String backendMode,
            @Value("${impilo.pacs.backend.external-base-url:}") String externalBaseUrl,
            @Value("${impilo.pacs.backend.external-dicomweb-enabled:true}") boolean dicomwebEnabled,
            @Value("${impilo.pacs.backend.external-dimse-enabled:false}") boolean dimseEnabled) {
        this.backendMode = backendMode;
        this.externalBaseUrl = externalBaseUrl;
        this.dicomwebEnabled = dicomwebEnabled;
        this.dimseEnabled = dimseEnabled;
    }

    @Override
    public String backendProvider() {
        return "EXTERNAL";
    }

    @Override
    public String backendMode() {
        return backendMode;
    }

    @Override
    public Map<String, Object> capabilities() {
        return Map.of(
                "provider", backendProvider(),
                "mode", backendMode(),
                "studyQuery", true,
                "studyRetrieve", true,
                "dicomweb", dicomwebEnabled,
                "dimse", dimseEnabled,
                "hierarchySync", false
        );
    }

    @Override
    public Map<String, Object> systemStatus() {
        return Map.of(
                "reachable", false,
                "provider", backendProvider(),
                "mode", backendMode(),
                "baseUrl", externalBaseUrl,
                "status", "CONFIGURED_EXTERNAL_PROVIDER",
                "note", "External PACS connector contract exists but runtime connector is not implemented in this service."
        );
    }

    @Override
    public java.util.List<String> findStudyIdsByStudyInstanceUid(String studyInstanceUid) {
        return Collections.emptyList();
    }

    @Override
    public JsonNode getStudy(String backendStudyId) {
        throw unsupported();
    }

    @Override
    public JsonNode getSeries(String backendSeriesId) {
        throw unsupported();
    }

    @Override
    public JsonNode getInstanceSimplifiedTags(String backendInstanceId) {
        throw unsupported();
    }

    @Override
    public boolean supportsHierarchySync() {
        return false;
    }

    private IllegalStateException unsupported() {
        return new IllegalStateException(
                "External PACS provider selected but connector operations are not implemented in this run.");
    }
}
