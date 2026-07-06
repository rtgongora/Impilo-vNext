package zw.gov.mohcc.impilo.governance.orgkey;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.gov.mohcc.impilo.governance.mirror.MirrorInventoryDtos;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;
import zw.gov.mohcc.impilo.governance.mirror.OrgRegistryMirrorClient;
import zw.gov.mohcc.impilo.governance.persistence.FacilityOrganisationLinkEntity;
import zw.gov.mohcc.impilo.governance.persistence.FacilityOrganisationLinkRepository;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentEntity;
import zw.gov.mohcc.impilo.governance.persistence.HscEmploymentRepository;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationMembershipEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationMembershipRepository;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationUnitEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationUnitRepository;
import zw.gov.mohcc.impilo.governance.persistence.SiteOrganisationLinkEntity;
import zw.gov.mohcc.impilo.governance.persistence.SiteOrganisationLinkRepository;

/**
 * Callable-only phase-2c dual-key backfill. Given a POPULATED mirror, it sets
 * {@code org_registry_org_id} on each FK-carrier row from the org-registry id
 * resolved via {@code source_ref} (= the wgv organisation id the row references).
 *
 * <p>This is dormant scaffolding: it is NOT run automatically and is gated
 * behind {@code impilo.org-mirror.enabled} (no-op + {@code enabled=false} when
 * off). It only ever WRITES the additive, nullable {@code org_registry_org_id}
 * column — it never touches the authoritative {@code organisation_id} FK, so it
 * is reversible (the column can be nulled again) until phase-2c commits the flip.
 * With an empty mirror it maps nothing and reports zero updates.
 */
@Service
public class OrgKeyBackfillService {

    private static final Logger log = LoggerFactory.getLogger(OrgKeyBackfillService.class);

    private final OrgMirrorProperties properties;
    private final OrgRegistryMirrorClient mirrorClient;
    private final HscEmploymentRepository hscEmploymentRepository;
    private final FacilityOrganisationLinkRepository facilityOrganisationLinkRepository;
    private final SiteOrganisationLinkRepository siteOrganisationLinkRepository;
    private final OrganisationUnitRepository organisationUnitRepository;
    private final OrganisationMembershipRepository organisationMembershipRepository;

    public OrgKeyBackfillService(OrgMirrorProperties properties,
                                 OrgRegistryMirrorClient mirrorClient,
                                 HscEmploymentRepository hscEmploymentRepository,
                                 FacilityOrganisationLinkRepository facilityOrganisationLinkRepository,
                                 SiteOrganisationLinkRepository siteOrganisationLinkRepository,
                                 OrganisationUnitRepository organisationUnitRepository,
                                 OrganisationMembershipRepository organisationMembershipRepository) {
        this.properties = properties;
        this.mirrorClient = mirrorClient;
        this.hscEmploymentRepository = hscEmploymentRepository;
        this.facilityOrganisationLinkRepository = facilityOrganisationLinkRepository;
        this.siteOrganisationLinkRepository = siteOrganisationLinkRepository;
        this.organisationUnitRepository = organisationUnitRepository;
        this.organisationMembershipRepository = organisationMembershipRepository;
    }

    /**
     * @param enabled       whether the mirror was enabled (false ⇒ no-op)
     * @param mappedRefs    distinct source_refs available in the mirror inventory
     * @param updatedByTable count of rows whose dual-key was set, per FK-carrier table
     */
    public record OrgKeyBackfillResult(boolean enabled, int mappedRefs, Map<String, Long> updatedByTable) {
    }

    @Transactional
    public OrgKeyBackfillResult backfill(UUID tenantId, UUID correlationId) {
        if (!properties.isEnabled()) {
            log.info("Org-key backfill requested but mirror is disabled (impilo.org-mirror.enabled=false) — no-op");
            return new OrgKeyBackfillResult(false, 0, Map.of());
        }

        // Build source_ref -> org-registry id from the mirror inventory.
        Map<String, UUID> byRef = new HashMap<>();
        for (MirrorInventoryDtos.InventoryRow row : mirrorClient.fetchInventory(tenantId, correlationId)) {
            if (row.sourceRef() != null && row.orgRegistryId() != null) {
                byRef.put(row.sourceRef(), row.orgRegistryId());
            }
        }

        Map<String, Long> updated = new LinkedHashMap<>();
        updated.put("wgv_hsc_employment", apply(
                hscEmploymentRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenantId), byRef,
                HscEmploymentEntity::getEmployerOrganisationId, HscEmploymentEntity::setOrgRegistryOrgId,
                hscEmploymentRepository::saveAll));
        updated.put("wgv_facility_organisation_link", apply(
                facilityOrganisationLinkRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenantId), byRef,
                FacilityOrganisationLinkEntity::getOrganisationId, FacilityOrganisationLinkEntity::setOrgRegistryOrgId,
                facilityOrganisationLinkRepository::saveAll));
        updated.put("wgv_site_organisation_link", apply(
                siteOrganisationLinkRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenantId), byRef,
                SiteOrganisationLinkEntity::getOrganisationId, SiteOrganisationLinkEntity::setOrgRegistryOrgId,
                siteOrganisationLinkRepository::saveAll));
        updated.put("wgv_organisation_unit", apply(
                organisationUnitRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenantId), byRef,
                OrganisationUnitEntity::getOrganisationId, OrganisationUnitEntity::setOrgRegistryOrgId,
                organisationUnitRepository::saveAll));
        updated.put("wgv_organisation_membership", apply(
                organisationMembershipRepository.findByTenantIdAndOrgRegistryOrgIdIsNull(tenantId), byRef,
                OrganisationMembershipEntity::getOrganisationId, OrganisationMembershipEntity::setOrgRegistryOrgId,
                organisationMembershipRepository::saveAll));

        log.info("Org-key backfill complete: mappedRefs={} updated={}", byRef.size(), updated);
        return new OrgKeyBackfillResult(true, byRef.size(), updated);
    }

    private <E> long apply(List<E> rows,
                           Map<String, UUID> byRef,
                           Function<E, UUID> wgvOrgRef,
                           BiConsumer<E, UUID> setKey,
                           java.util.function.Consumer<List<E>> saveAll) {
        long count = 0;
        List<E> dirty = new java.util.ArrayList<>();
        for (E row : rows) {
            UUID ref = wgvOrgRef.apply(row);
            if (ref == null) {
                continue;
            }
            UUID orgRegistryId = byRef.get(ref.toString());
            if (orgRegistryId != null) {
                setKey.accept(row, orgRegistryId);
                dirty.add(row);
                count++;
            }
        }
        if (!dirty.isEmpty()) {
            saveAll.accept(dirty);
        }
        return count;
    }
}
