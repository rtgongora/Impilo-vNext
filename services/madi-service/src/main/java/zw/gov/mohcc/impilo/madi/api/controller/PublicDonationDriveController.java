package zw.gov.mohcc.impilo.madi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.madi.core.DonationDriveService;
import zw.gov.mohcc.impilo.madi.domain.DriveStatus;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonationDriveEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public (R0, anonymous) blood-donation-drive discovery lane — gateway public-lane ADR
 * {@code Public*Controller} rule. Lets an anonymous citizen see which donation drives are currently
 * PUBLISHED and open for participation, WITHOUT signing in.
 *
 * <p><b>Disclosure-limited by construction.</b> The response is an allow-listed, PII-free view built
 * from a fresh {@link LinkedHashMap} per drive. Only public-safe descriptive fields are mapped —
 * title, description, driveType, locationRef, jurisdiction, startAt, endAt, capacity. Internal
 * identifiers and operational metadata (id, driveId, tenantId, facilityId, ndilaSiteRef, publishedBy,
 * createdBy, and any audit timestamps) live on the entity but are deliberately NOT mapped here.
 * Adding a field is a deliberate disclosure decision.</p>
 *
 * <p>Drives are care-plane, so the read is scoped to the fixed care-plane tenant. A public read must
 * never fail loudly: any error is logged at warn and yields an empty list, never a 500.</p>
 */
@RestController
@RequestMapping("/v1/public/madi/drives")
public class PublicDonationDriveController {

    private static final Logger log = LoggerFactory.getLogger(PublicDonationDriveController.class);

    /** Care-plane tenant — donation drives are care-plane. */
    private static final UUID CARE_PLANE_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final DonationDriveService driveService;

    public PublicDonationDriveController(DonationDriveService driveService) {
        this.driveService = driveService;
    }

    @GetMapping("")
    public List<Map<String, Object>> list() {
        try {
            return driveService.list(CARE_PLANE_TENANT).stream()
                    // Live public appeals = PUBLISHED (announced) AND OPEN (actively taking
                    // donors — the service flips PUBLISHED→OPEN on first registration).
                    .filter(d -> DriveStatus.PUBLISHED.name().equals(d.getStatus())
                            || DriveStatus.OPEN.name().equals(d.getStatus()))
                    .map(PublicDonationDriveController::toPublicView)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Public donation-drive read failed; returning empty list", ex);
            return List.of();
        }
    }

    /**
     * Allow-listed, PII-free public view. Only descriptive, public-safe fields are mapped — never
     * internal identifiers, operational refs, or audit metadata. Dates are rendered null-safe via
     * {@code toString()}.
     */
    private static Map<String, Object> toPublicView(DonationDriveEntity drive) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", drive.getTitle());
        view.put("description", drive.getDescription());
        view.put("driveType", drive.getDriveType());
        view.put("locationRef", drive.getLocationRef());
        view.put("jurisdiction", drive.getJurisdiction());
        view.put("startAt", drive.getStartAt() != null ? drive.getStartAt().toString() : null);
        view.put("endAt", drive.getEndAt() != null ? drive.getEndAt().toString() : null);
        view.put("capacity", drive.getCapacity());
        return view;
    }
}
