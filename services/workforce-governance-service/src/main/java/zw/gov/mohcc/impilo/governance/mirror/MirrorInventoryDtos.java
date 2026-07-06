package zw.gov.mohcc.impilo.governance.mirror;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Client-side wire shapes for the org-registry read-only mirror inventory
 * ({@code GET /internal/v1/org-registry/mirror/wgv/inventory}). Mirrors
 * {@code OrgRegistryDtos.MirrorInventoryItem / MirrorInventoryPage} on the
 * receiver — additive, read-only, used only by the phase-2a reconciliation
 * report and the (dormant) dual-key backfill.
 */
public final class MirrorInventoryDtos {

    private MirrorInventoryDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InventoryRow(
            UUID orgRegistryId,
            String sourceRef,
            String code,
            String legalName,
            String status,
            String verificationStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InventoryPage(
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<InventoryRow> items) {
    }
}
