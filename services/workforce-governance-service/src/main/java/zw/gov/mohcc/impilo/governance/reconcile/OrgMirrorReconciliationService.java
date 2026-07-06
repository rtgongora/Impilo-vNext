package zw.gov.mohcc.impilo.governance.reconcile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import zw.gov.mohcc.impilo.governance.mirror.MirrorInventoryDtos;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;
import zw.gov.mohcc.impilo.governance.mirror.OrgRegistryMirrorClient;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;

/**
 * Builds the criterion-1 reconciliation report: pulls the org-registry WGV
 * mirror inventory and diffs it against local ACTIVE {@code wgv_organisation}
 * rows.
 *
 * <p>Read-only and additive. It changes nothing on either side and is safe to
 * run at any point during the phase-2b soak. It never crashes on an empty
 * mirror — that legitimately yields 0% completeness with every active row
 * listed as missing.
 */
@Service
public class OrgMirrorReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OrgMirrorReconciliationService.class);

    private final OrganisationRepository organisationRepository;
    private final OrgRegistryMirrorClient mirrorClient;
    private final OrgMirrorProperties properties;

    public OrgMirrorReconciliationService(OrganisationRepository organisationRepository,
                                          OrgRegistryMirrorClient mirrorClient,
                                          OrgMirrorProperties properties) {
        this.organisationRepository = organisationRepository;
        this.mirrorClient = mirrorClient;
        this.properties = properties;
    }

    public OrgMirrorReconciliationReport reconcile(UUID tenantId, UUID correlationId) {
        // 1) Pull the whole mirror inventory, keyed by source_ref (= wgv org id).
        Map<String, MirrorInventoryDtos.InventoryRow> mirror = new HashMap<>();
        for (MirrorInventoryDtos.InventoryRow row : mirrorClient.fetchInventory(tenantId, correlationId)) {
            if (row.sourceRef() != null) {
                mirror.put(row.sourceRef(), row);
            }
        }

        // 2) Sweep local ACTIVE wgv organisations and diff.
        long wgvActiveTotal = 0;
        long mirroredCount = 0;
        List<String> missingSourceRefs = new ArrayList<>();
        List<OrgMirrorReconciliationReport.DriftRow> driftRows = new ArrayList<>();

        int pageSize = Math.max(1, properties.getBackfillPageSize());
        int pageIndex = 0;
        Page<OrganisationEntity> page;
        do {
            page = organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(PageRequest.of(pageIndex, pageSize));
            for (OrganisationEntity org : page.getContent()) {
                wgvActiveTotal++;
                String sourceRef = org.getId().toString();
                MirrorInventoryDtos.InventoryRow mirrored = mirror.get(sourceRef);
                if (mirrored == null) {
                    missingSourceRefs.add(sourceRef);
                    continue;
                }
                mirroredCount++;
                List<String> driftFields = drift(org, mirrored);
                if (!driftFields.isEmpty()) {
                    driftRows.add(new OrgMirrorReconciliationReport.DriftRow(sourceRef, driftFields));
                }
            }
            pageIndex++;
        } while (page.hasNext());

        double completenessPct = wgvActiveTotal == 0
                ? 100.0
                : Math.round((mirroredCount * 100.0 / wgvActiveTotal) * 100.0) / 100.0;

        log.info("Org-mirror reconciliation: wgvActiveTotal={} mirroredCount={} missing={} drift={} completenessPct={}",
                wgvActiveTotal, mirroredCount, missingSourceRefs.size(), driftRows.size(), completenessPct);

        return new OrgMirrorReconciliationReport(
                wgvActiveTotal, mirroredCount, missingSourceRefs, driftRows, completenessPct);
    }

    /** Which of code / legalName / status differ between the authoritative wgv row and its mirror. */
    private static List<String> drift(OrganisationEntity org, MirrorInventoryDtos.InventoryRow mirrored) {
        List<String> fields = new ArrayList<>(3);
        if (!eqTrimmed(org.getOrganisationCode(), mirrored.code())) {
            fields.add("code");
        }
        if (!eqTrimmed(org.getLegalName(), mirrored.legalName())) {
            fields.add("legalName");
        }
        if (!eqStatus(org.getStatus(), mirrored.status())) {
            fields.add("status");
        }
        return fields;
    }

    private static boolean eqTrimmed(String a, String b) {
        String ta = a == null ? null : a.trim();
        String tb = b == null ? null : b.trim();
        return ta == null ? tb == null : ta.equals(tb);
    }

    /** The mirror normalises status to upper-case (WgvMirrorService.mapStatus), so compare normalised. */
    private static boolean eqStatus(String wgv, String mirror) {
        String nw = wgv == null || wgv.isBlank() ? "ACTIVE" : wgv.trim().toUpperCase();
        String nm = mirror == null || mirror.isBlank() ? null : mirror.trim().toUpperCase();
        return nw.equals(nm);
    }
}
