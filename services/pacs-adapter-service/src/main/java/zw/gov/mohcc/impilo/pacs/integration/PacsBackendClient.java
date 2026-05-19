package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Abstraction boundary for PACS backend providers (embedded Orthanc, external PACS/VNA, hybrid follow-on).
 */
public interface PacsBackendClient {

    String backendProvider();

    String backendMode();

    Map<String, Object> capabilities();

    Map<String, Object> systemStatus();

    List<String> findStudyIdsByStudyInstanceUid(String studyInstanceUid);

    JsonNode getStudy(String backendStudyId);

    JsonNode getSeries(String backendSeriesId);

    JsonNode getInstanceSimplifiedTags(String backendInstanceId);

    default boolean supportsHierarchySync() {
        return true;
    }
}
