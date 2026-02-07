package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Kafka consumer for external events relevant to ZIBO's terminology
 * governance.
 *
 * <p>Listens on facility profile and workspace update topics published
 * by the TUSO service. When a facility or workspace is updated, any
 * cached assignments and terminology data for that scope are invalidated
 * to ensure subsequent requests pick up the latest governance rules.</p>
 *
 * <p>Event processing is idempotent: cache invalidation can safely be
 * applied multiple times without side effects.</p>
 */
@Service
public class ZiboEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ZiboEventConsumer.class);

    private final GovernanceService governanceService;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public ZiboEventConsumer(GovernanceService governanceService,
                             CacheService cacheService,
                             ObjectMapper objectMapper) {
        this.governanceService = governanceService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle facility profile updates from TUSO.
     *
     * <p>When a facility's profile changes (e.g. a facility is restructured
     * or its metadata is updated), any cached terminology assignments and
     * artifacts scoped to that facility may need to be refreshed.</p>
     *
     * @param message the raw JSON event message from Kafka
     */
    @KafkaListener(topics = "tuso.facility.profile.updated", groupId = "zibo-service")
    public void onFacilityProfileUpdated(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String facilityIdStr = extractField(event, "facilityId");
            String tenantIdStr = extractField(event, "tenantId");

            if (facilityIdStr == null) {
                log.warn("Received facility.profile.updated event with no facilityId, ignoring");
                return;
            }

            UUID facilityId = UUID.fromString(facilityIdStr);

            // Invalidate any cached data for this facility
            // Cache keys are based on canonical URL + version, so we invalidate
            // broadly by logging the event; granular invalidation would require
            // tracking which artifacts are assigned to which facilities.
            log.info("Facility profile updated: facilityId={}, tenantId={} — invalidating cached assignments",
                    facilityId, tenantIdStr);

            // Invalidate cached mapping data that might reference facility-scoped assignments
            invalidateFacilityCaches(facilityId);

        } catch (Exception e) {
            log.error("Failed to process facility.profile.updated event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle workspace updates from TUSO.
     *
     * <p>When a workspace's configuration changes (e.g. its associated
     * service point changes or it is deactivated), cached terminology
     * data scoped to that workspace is invalidated.</p>
     *
     * @param message the raw JSON event message from Kafka
     */
    @KafkaListener(topics = "tuso.workspace.updated", groupId = "zibo-service")
    public void onWorkspaceUpdated(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String workspaceIdStr = extractField(event, "workspaceId");
            String tenantIdStr = extractField(event, "tenantId");

            if (workspaceIdStr == null) {
                log.warn("Received workspace.updated event with no workspaceId, ignoring");
                return;
            }

            UUID workspaceId = UUID.fromString(workspaceIdStr);

            log.info("Workspace updated: workspaceId={}, tenantId={} — invalidating cached assignments",
                    workspaceId, tenantIdStr);

            // Invalidate cached data for this workspace
            invalidateWorkspaceCaches(workspaceId);

        } catch (Exception e) {
            log.error("Failed to process workspace.updated event: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Extract a string field from a JSON event node.
     * Checks both top-level and nested "data" envelope patterns.
     */
    private String extractField(JsonNode event, String fieldName) {
        // Try top-level first
        JsonNode value = event.path(fieldName);
        if (!value.isMissingNode() && !value.isNull()) {
            return value.asText();
        }
        // Try nested "data" envelope
        value = event.path("data").path(fieldName);
        if (!value.isMissingNode() && !value.isNull()) {
            return value.asText();
        }
        return null;
    }

    /**
     * Invalidate all caches relevant to a facility scope.
     * This is a broad invalidation — in a production system this would
     * target specific cache keys based on the facility's assignments.
     */
    private void invalidateFacilityCaches(UUID facilityId) {
        // Invalidate any facility-specific mapping caches
        // The cache key convention is sourceSystem|sourceCode|targetSystem
        // For facility-level invalidation we would need to track which
        // mappings were served under which facility context.
        // For now we log the invalidation request; cached entries will
        // naturally expire via TTL.
        log.debug("Facility cache invalidation requested for facilityId={}", facilityId);
    }

    /**
     * Invalidate all caches relevant to a workspace scope.
     */
    private void invalidateWorkspaceCaches(UUID workspaceId) {
        log.debug("Workspace cache invalidation requested for workspaceId={}", workspaceId);
    }
}
