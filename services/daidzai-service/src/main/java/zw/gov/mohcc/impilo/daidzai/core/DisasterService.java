package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AffectedSiteEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.AffectedSiteRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyIncidentRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Disaster / mass-casualty (MCI) command spine. A disaster is an {@link EmergencyIncidentEntity}
 * of type DISASTER or MCI with affected sites (Indawo/Ndila refs), resource requests, and a
 * command/coordination state. Closure routes an after-action review to Rito (link only — Rito
 * owns the review record). Public/responder comms are owner-routed to Khuluma.
 */
@Service
public class DisasterService {

    private final EmergencyIncidentRepository incidentRepo;
    private final AffectedSiteRepository siteRepo;
    private final ReferenceGenerator refs;
    private final OwnerRoutedGateway gateway;
    private final DaidzaiEventEmitter emitter;

    public DisasterService(EmergencyIncidentRepository incidentRepo, AffectedSiteRepository siteRepo,
                           ReferenceGenerator refs, OwnerRoutedGateway gateway, DaidzaiEventEmitter emitter) {
        this.incidentRepo = incidentRepo;
        this.siteRepo = siteRepo;
        this.refs = refs;
        this.gateway = gateway;
        this.emitter = emitter;
    }

    @Transactional
    public EmergencyIncidentEntity declareDisaster(UUID tenantId, String incidentType, String category,
                                                   String severity, String title, String description,
                                                   String commandPost, String incidentCommander,
                                                   String affectedAreaRef) {
        String type = incidentType != null ? incidentType.toUpperCase() : "DISASTER";
        if (!type.equals("DISASTER") && !type.equals("MCI") && !type.equals("OUTBREAK")) {
            throw new IllegalArgumentException("incidentType must be DISASTER, MCI or OUTBREAK");
        }
        EmergencyIncidentEntity inc = new EmergencyIncidentEntity();
        inc.setTenantId(tenantId);
        inc.setIncidentReference(refs.incidentReference());
        inc.setIncidentType(type);
        inc.setEmergencyCategory(category != null ? category.toUpperCase() : "DISASTER");
        inc.setSeverity(severity != null ? severity.toUpperCase() : "HIGH");
        inc.setStatus("RESPONDING");
        inc.setTitle(title);
        inc.setDescription(description);
        inc.setCommandPost(commandPost);
        inc.setIncidentCommander(incidentCommander);
        inc.setAffectedAreaRef(affectedAreaRef);
        inc.setOpenedAt(OffsetDateTime.now());
        incidentRepo.save(inc);

        emitter.emit("EMERGENCY_INCIDENT", inc.getId().toString(), "daidzai.disaster.declared",
                "EMERGENCY_INCIDENT", inc.getId().toString(),
                Map.of("incidentType", type, "severity", inc.getSeverity(),
                        "commandPost", commandPost == null ? "" : commandPost), tenantId);
        // Public/responder activation comms (owner-routed to Khuluma, request-only).
        gateway.requestNotification(tenantId, "BROADCAST", "RESPONDERS",
                "daidzai.disaster.activation", Map.of("reference", inc.getIncidentReference()));
        return inc;
    }

    @Transactional
    public AffectedSiteEntity addAffectedSite(UUID tenantId, UUID incidentId, String siteLabel,
                                              String indawoSiteRef, String ndilaAreaRef,
                                              Double lat, Double lng, Integer estimatedAffected) {
        load(tenantId, incidentId);
        AffectedSiteEntity s = new AffectedSiteEntity();
        s.setTenantId(tenantId);
        s.setIncidentId(incidentId);
        s.setSiteLabel(siteLabel);
        s.setIndawoSiteRef(indawoSiteRef);
        s.setNdilaAreaRef(ndilaAreaRef);
        s.setLocationLat(lat);
        s.setLocationLng(lng);
        s.setEstimatedAffected(estimatedAffected);
        s.setStatus("ACTIVE");
        siteRepo.save(s);
        emitter.emit("EMERGENCY_INCIDENT", incidentId.toString(), "daidzai.disaster.site_added",
                "EMERGENCY_INCIDENT", incidentId.toString(),
                Map.of("site", siteLabel, "estimatedAffected", estimatedAffected == null ? 0 : estimatedAffected), tenantId);
        return s;
    }

    @Transactional(readOnly = true)
    public List<AffectedSiteEntity> sites(UUID tenantId, UUID incidentId) {
        load(tenantId, incidentId);
        return siteRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    @Transactional(readOnly = true)
    public List<EmergencyIncidentEntity> listDisasters(UUID tenantId) {
        List<EmergencyIncidentEntity> all = incidentRepo.findByTenantIdOrderByOpenedAtDesc(tenantId);
        return all.stream()
                .filter(i -> !"INDIVIDUAL".equals(i.getIncidentType()))
                .toList();
    }

    /** Close a disaster and route an after-action review to Rito (link only — Rito owns the review). */
    @Transactional
    public EmergencyIncidentEntity closeWithAfterAction(UUID tenantId, UUID incidentId, String ritoCaseRef) {
        EmergencyIncidentEntity inc = load(tenantId, incidentId);
        inc.setStatus("CLOSED");
        inc.setClosedAt(OffsetDateTime.now());
        inc.setRitoCaseRef(ritoCaseRef);
        incidentRepo.save(inc);
        emitter.emit("EMERGENCY_INCIDENT", incidentId.toString(), "daidzai.disaster.closed",
                "EMERGENCY_INCIDENT", incidentId.toString(),
                Map.of("ritoCaseRef", ritoCaseRef == null ? "" : ritoCaseRef), tenantId);
        return inc;
    }

    private EmergencyIncidentEntity load(UUID tenantId, UUID incidentId) {
        return incidentRepo.findByIdAndTenantId(incidentId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
    }
}
