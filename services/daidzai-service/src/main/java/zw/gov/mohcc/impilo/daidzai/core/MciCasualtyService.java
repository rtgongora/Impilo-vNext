package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.MciCasualtyEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyIncidentRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.MciCasualtyRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Individual casualty tracking within an MCI incident (V202, W11) — genuinely absent before this
 * table; {@link EmergencyIncidentEntity} models exactly one subject per row.
 */
@Service
public class MciCasualtyService {

    /**
     * Surge threshold, an ENGINEERING_SEED like every other Zimbabwe time/count target in this pack
     * (R9's own standing disclosure) — no MoHCC-ratified MCI surge threshold was found. Chosen as a
     * round, clinically-plausible number (an incident with 10+ casualties genuinely strains a single
     * facility's ED), not derived from a national policy.
     */
    public static final int SURGE_THRESHOLD = 10;

    private static final Set<String> SIEVE_CATEGORIES =
            Set.of("UNASSESSED", "IMMEDIATE", "URGENT", "DELAYED", "EXPECTANT", "DECEASED");
    private static final Set<String> STATUSES =
            Set.of("ON_SCENE", "TRANSPORTED", "AT_FACILITY", "DECEASED_CONFIRMED");

    private final MciCasualtyRepository casualtyRepo;
    private final EmergencyIncidentRepository incidentRepo;
    private final DaidzaiEventEmitter emitter;

    public MciCasualtyService(MciCasualtyRepository casualtyRepo, EmergencyIncidentRepository incidentRepo,
                              DaidzaiEventEmitter emitter) {
        this.casualtyRepo = casualtyRepo;
        this.incidentRepo = incidentRepo;
        this.emitter = emitter;
    }

    /**
     * Tag a casualty. Idempotent on the scene-assigned {@code casualtyReference} — retagging the same
     * physical tag number updates the same record rather than creating a duplicate.
     */
    @Transactional
    public MciCasualtyEntity tagCasualty(UUID tenantId, UUID incidentId, String casualtyReference,
                                         String sieveCategory, UUID siteId, String taggedBy) {
        EmergencyIncidentEntity incident = loadIncident(tenantId, incidentId);
        if (casualtyReference == null || casualtyReference.isBlank()) {
            throw new IllegalArgumentException("casualtyReference is required");
        }
        if (taggedBy == null || taggedBy.isBlank()) {
            throw new IllegalArgumentException("taggedBy is required");
        }
        String category = sieveCategory != null ? sieveCategory.toUpperCase() : "UNASSESSED";
        if (!SIEVE_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("sieveCategory must be one of " + SIEVE_CATEGORIES);
        }

        MciCasualtyEntity casualty = casualtyRepo.findByTenantIdAndIncidentIdOrderByTaggedAtAsc(tenantId, incidentId)
                .stream().filter(c -> casualtyReference.equals(c.getCasualtyReference())).findFirst()
                .orElseGet(MciCasualtyEntity::new);
        boolean isNew = casualty.getId() == null;
        // Counted BEFORE the save, not after — a post-save COUNT inside the same transaction risks
        // an auto-flush of the pending INSERT ahead of the query, which would double-count the row
        // this call is about to add. Computing "count so far" first and adding exactly one for a
        // genuinely new casualty is deterministic regardless of JPA flush timing.
        long countBeforeThisTag = isNew ? casualtyRepo.countByTenantIdAndIncidentId(tenantId, incidentId) : 0;
        casualty.setTenantId(tenantId);
        casualty.setIncidentId(incidentId);
        casualty.setSiteId(siteId);
        casualty.setCasualtyReference(casualtyReference);
        casualty.setSieveCategory(category);
        casualty.setTaggedBy(taggedBy);
        casualty = casualtyRepo.save(casualty);

        if (isNew) {
            checkSurge(tenantId, incident, countBeforeThisTag + 1);
        }

        emitter.emit("EMERGENCY_INCIDENT", incidentId.toString(), "daidzai.mci.casualty_tagged",
                "MCI_CASUALTY", casualty.getId().toString(),
                Map.of("casualtyReference", casualtyReference, "sieveCategory", category), tenantId);
        return casualty;
    }

    @Transactional
    public MciCasualtyEntity updateStatus(UUID tenantId, UUID casualtyId, String status, UUID destinationFacilityId) {
        MciCasualtyEntity casualty = casualtyRepo.findByTenantIdAndId(tenantId, casualtyId)
                .orElseThrow(() -> new NoSuchElementException("Casualty not found: " + casualtyId));
        String s = status != null ? status.toUpperCase() : null;
        if (s == null || !STATUSES.contains(s)) {
            throw new IllegalArgumentException("status must be one of " + STATUSES);
        }
        casualty.setStatus(s);
        if (destinationFacilityId != null) casualty.setDestinationFacilityId(destinationFacilityId);
        casualty = casualtyRepo.save(casualty);
        emitter.emit("EMERGENCY_INCIDENT", casualty.getIncidentId().toString(), "daidzai.mci.casualty_status_changed",
                "MCI_CASUALTY", casualty.getId().toString(), Map.of("status", s), tenantId);
        return casualty;
    }

    @Transactional(readOnly = true)
    public List<MciCasualtyEntity> listForIncident(UUID tenantId, UUID incidentId) {
        loadIncident(tenantId, incidentId);
        return casualtyRepo.findByTenantIdAndIncidentIdOrderByTaggedAtAsc(tenantId, incidentId);
    }

    /** Called by PCT's bulk-mint pass to read the not-yet-minted list. */
    @Transactional(readOnly = true)
    public List<MciCasualtyEntity> listUnminted(UUID tenantId, UUID incidentId) {
        loadIncident(tenantId, incidentId);
        return casualtyRepo.findUnminted(tenantId, incidentId);
    }

    /** Called by PCT's bulk-mint pass to write the minted episode id back onto the casualty. */
    @Transactional
    public MciCasualtyEntity linkEpisode(UUID tenantId, UUID casualtyId, UUID emergencyEpisodeId) {
        MciCasualtyEntity casualty = casualtyRepo.findByTenantIdAndId(tenantId, casualtyId)
                .orElseThrow(() -> new NoSuchElementException("Casualty not found: " + casualtyId));
        if (casualty.getEmergencyEpisodeId() != null && !casualty.getEmergencyEpisodeId().equals(emergencyEpisodeId)) {
            throw new IllegalStateException(
                    "Casualty " + casualtyId + " is already linked to a different emergency episode");
        }
        casualty.setEmergencyEpisodeId(emergencyEpisodeId);
        return casualtyRepo.save(casualty);
    }

    /** Stamped once, the first time the count crosses SURGE_THRESHOLD — never cleared. */
    private void checkSurge(UUID tenantId, EmergencyIncidentEntity incident, long countIncludingThisTag) {
        if (incident.getSurgeDeclaredAt() != null) return; // already declared — a fact, not a live status
        long count = countIncludingThisTag;
        if (count >= SURGE_THRESHOLD) {
            incident.setSurgeDeclaredAt(OffsetDateTime.now());
            incident.setSurgeCasualtyCount((int) count);
            incidentRepo.save(incident);
            emitter.emit("EMERGENCY_INCIDENT", incident.getId().toString(), "daidzai.mci.surge_declared",
                    "EMERGENCY_INCIDENT", incident.getId().toString(),
                    Map.of("casualtyCount", count, "threshold", SURGE_THRESHOLD), tenantId);
        }
    }

    private EmergencyIncidentEntity loadIncident(UUID tenantId, UUID incidentId) {
        return incidentRepo.findByIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
    }
}
