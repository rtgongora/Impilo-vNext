package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

/**
 * Read-only inventory over the one-way WGV mirror rows
 * ({@code source = WGV_MIRROR}). Exposed so workforce-governance can pull the
 * current mirror contents and diff them against its local active
 * {@code wgv_organisation} rows (the criterion-1 reconciliation report).
 *
 * <p>Phase-2a evidence tooling: purely additive and read-only. It never mutates
 * the registry, has no coupling to the mirror producer flag, and honestly
 * reports an empty page when nothing has been mirrored yet.
 */
@Service
public class WgvMirrorInventoryService {

    private final OrganizationRepository organizationRepository;

    public WgvMirrorInventoryService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public OrgRegistryDtos.MirrorInventoryPage inventory(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 500);
        Page<OrganizationEntity> result = organizationRepository.findBySourceOrderByCreatedAtAsc(
                WgvMirrorService.SOURCE_WGV_MIRROR, PageRequest.of(safePage, safeSize));
        var items = result.getContent().stream()
                .map(o -> new OrgRegistryDtos.MirrorInventoryItem(
                        o.getId(),
                        o.getSourceRef(),
                        o.getCode(),
                        o.getLegalName(),
                        o.getStatus(),
                        o.getVerificationStatus() != null ? o.getVerificationStatus().name() : null))
                .toList();
        return new OrgRegistryDtos.MirrorInventoryPage(
                safePage, safeSize, result.getTotalElements(), result.getTotalPages(), items);
    }
}
