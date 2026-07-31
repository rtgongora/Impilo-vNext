package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormExtractedResourceEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormExtractedResourceRepository;

import java.util.List;
import java.util.Map;

/**
 * Writes FAILED extraction provenance in a transaction of its own.
 *
 * <p>When an extraction item marks its surrounding transaction rollback-only (for example
 * {@code DuplicateProblemException} from {@code ProblemService.add}), any provenance row written
 * into that same transaction is discarded at commit. The clinician's form response survives only
 * if the failure audit trail is committed separately — otherwise the cockpit never sees why an
 * item failed, and the form looks like it never extracted.</p>
 */
@Component
public class FormExtractionFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(FormExtractionFailureRecorder.class);

    private final FormExtractedResourceRepository extractedRepository;
    private final ObjectMapper objectMapper;

    public FormExtractionFailureRecorder(FormExtractedResourceRepository extractedRepository,
                                         ObjectMapper objectMapper) {
        this.extractedRepository = extractedRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(FormResponseEntity r, Map<String, Object> m, String linkId,
                              String resourceType, String reason) {
        FormExtractedResourceEntity e = new FormExtractedResourceEntity();
        e.setTenantId(r.getTenantId());
        e.setResponseId(r.getResponseId());
        e.setFormSchemaVersionId(r.getFormSchemaVersionId());
        e.setResourceType(resourceType == null || resourceType.isBlank() ? "UNKNOWN" : resourceType);
        String route = m == null ? null : (m.get("routeTarget") == null ? null : m.get("routeTarget").toString());
        e.setRouteTarget(route == null ? "" : route.trim().toUpperCase());
        e.setSourceLinkIds(toJson(List.of(linkId)));
        e.setStatus("FAILED");
        e.setAttempts(1);
        e.setFailureReason(reason);
        extractedRepository.save(e);
        log.warn("pct.form.extraction.item.failed id={} linkId={} type={}: {}",
                r.getResponseId(), linkId, resourceType, reason);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "[]";
        }
    }
}
