package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeSpecialtyEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeSpecialtyRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeSpecialtyEntity.ROLE_LEAD;
import static zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeSpecialtyEntity.ROLE_SHARED;

/**
 * Surgical demonstration 4 — shared-specialty care (V011).
 *
 * <p>A retroperitoneal sarcoma resection is surgical oncology, vascular and urology in one
 * operation. Until V011 the episode could name one of them. This service owns the set, and owns
 * the one invariant that matters: {@code surgical_episode.specialty} continues to mean the LEAD
 * specialty, and is written in the same transaction as the LEAD row, so the column existing
 * readers rely on can never drift from the join table.</p>
 */
@Service
public class EpisodeSpecialtyService {

    private final SurgicalEpisodeRepository episodeRepository;
    private final SurgicalEpisodeSpecialtyRepository specialtyRepository;

    public EpisodeSpecialtyService(SurgicalEpisodeRepository episodeRepository,
                                   SurgicalEpisodeSpecialtyRepository specialtyRepository) {
        this.episodeRepository = episodeRepository;
        this.specialtyRepository = specialtyRepository;
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    private SurgicalEpisodeEntity requireEpisode(UUID episodeId) {
        return episodeRepository.findByIdAndTenantId(episodeId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));
    }

    private String requireKnownSpecialty(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            throw new SurgeryDomainException("SPECIALTY_REQUIRED", 400, "specialty is required");
        }
        String normalised = specialty.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SpecialtyContentService.SPECIALTIES.contains(normalised)) {
            throw new SurgeryDomainException("INVALID_SPECIALTY", 400,
                    "specialty must be one of " + SpecialtyContentService.SPECIALTIES);
        }
        return normalised;
    }

    /**
     * Record the lead specialty when the episode is opened, so the column and the join table
     * agree from the episode's first moment rather than from whenever a second team is added.
     */
    @Transactional
    public void recordLeadAtOpen(SurgicalEpisodeEntity episode) {
        if (episode.getSpecialty() == null || episode.getSpecialty().isBlank()) {
            return;
        }
        SurgicalEpisodeSpecialtyEntity lead = new SurgicalEpisodeSpecialtyEntity();
        lead.setTenantId(episode.getTenantId());
        lead.setEpisodeId(episode.getId());
        lead.setSpecialty(episode.getSpecialty());
        lead.setRole(ROLE_LEAD);
        lead.setAddedBy(currentActor());
        specialtyRepository.save(lead);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSpecialties(UUID episodeId) {
        requireEpisode(episodeId);
        return specialtyRepository.findByEpisodeIdOrderByRoleAscSpecialtyAsc(episodeId)
                .stream().map(this::row).toList();
    }

    /**
     * Add a team to the case. A second LEAD is refused rather than silently accepted: two teams
     * that both believe they own the episode is the exact failure shared-specialty care exists to
     * prevent, and handing the lead over is a different act with its own method.
     */
    @Transactional
    public Map<String, Object> addSpecialty(UUID episodeId, String specialty, String role, String contribution) {
        SurgicalEpisodeEntity episode = requireEpisode(episodeId);
        String normalised = requireKnownSpecialty(specialty);
        String normalisedRole = role == null || role.isBlank()
                ? ROLE_SHARED : role.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ROLE_LEAD.equals(normalisedRole) && !ROLE_SHARED.equals(normalisedRole)) {
            throw new SurgeryDomainException("INVALID_SPECIALTY_ROLE", 400,
                    "role must be LEAD or SHARED");
        }

        specialtyRepository.findByEpisodeIdAndSpecialty(episodeId, normalised).ifPresent(existing -> {
            throw new SurgeryDomainException("SPECIALTY_ALREADY_ON_EPISODE", 409,
                    normalised + " is already on this episode as " + existing.getRole()
                            + " — adding it twice is a duplicate, not a second team");
        });

        if (ROLE_LEAD.equals(normalisedRole)) {
            specialtyRepository.findByEpisodeIdAndRole(episodeId, ROLE_LEAD).ifPresent(lead -> {
                throw new SurgeryDomainException("LEAD_SPECIALTY_ALREADY_SET", 409,
                        "This episode is already led by " + lead.getSpecialty()
                                + " — transfer the lead rather than adding a second one");
            });
        }

        SurgicalEpisodeSpecialtyEntity row = new SurgicalEpisodeSpecialtyEntity();
        row.setTenantId(currentTenant());
        row.setEpisodeId(episodeId);
        row.setSpecialty(normalised);
        row.setRole(normalisedRole);
        row.setContribution(contribution);
        row.setAddedBy(currentActor());
        row = specialtyRepository.save(row);

        if (ROLE_LEAD.equals(normalisedRole)) {
            syncLeadColumn(episode, normalised);
        }
        return row(row);
    }

    /**
     * Hand the lead to another team — general surgery opens, vascular takes over. A real clinical
     * event, not a correction: the outgoing lead stays on the case as SHARED, because it operated.
     */
    @Transactional
    public List<Map<String, Object>> transferLead(UUID episodeId, String toSpecialty, String contribution) {
        SurgicalEpisodeEntity episode = requireEpisode(episodeId);
        String normalised = requireKnownSpecialty(toSpecialty);

        SurgicalEpisodeSpecialtyEntity currentLead = specialtyRepository
                .findByEpisodeIdAndRole(episodeId, ROLE_LEAD).orElse(null);
        if (currentLead != null && currentLead.getSpecialty().equals(normalised)) {
            throw new SurgeryDomainException("LEAD_UNCHANGED", 409,
                    normalised + " already leads this episode");
        }
        if (currentLead != null) {
            // Demote first: the partial unique index permits exactly one LEAD at a time, so the
            // outgoing lead must step down within this transaction before the incoming one exists.
            currentLead.setRole(ROLE_SHARED);
            specialtyRepository.saveAndFlush(currentLead);
        }

        SurgicalEpisodeSpecialtyEntity incoming = specialtyRepository
                .findByEpisodeIdAndSpecialty(episodeId, normalised)
                .orElseGet(() -> {
                    SurgicalEpisodeSpecialtyEntity fresh = new SurgicalEpisodeSpecialtyEntity();
                    fresh.setTenantId(currentTenant());
                    fresh.setEpisodeId(episodeId);
                    fresh.setSpecialty(normalised);
                    fresh.setAddedBy(currentActor());
                    return fresh;
                });
        incoming.setRole(ROLE_LEAD);
        if (contribution != null && !contribution.isBlank()) {
            incoming.setContribution(contribution);
        }
        specialtyRepository.save(incoming);

        syncLeadColumn(episode, normalised);
        return listSpecialties(episodeId);
    }

    /**
     * Remove a team that turned out not to be needed. The lead cannot be removed — an episode
     * with no team answering for it is not a state this service will produce.
     */
    @Transactional
    public List<Map<String, Object>> removeSpecialty(UUID episodeId, String specialty) {
        requireEpisode(episodeId);
        String normalised = requireKnownSpecialty(specialty);
        SurgicalEpisodeSpecialtyEntity row = specialtyRepository
                .findByEpisodeIdAndSpecialty(episodeId, normalised)
                .orElseThrow(() -> new SurgeryDomainException("SPECIALTY_NOT_ON_EPISODE", 404,
                        normalised + " is not on this episode"));
        if (ROLE_LEAD.equals(row.getRole())) {
            throw new SurgeryDomainException("CANNOT_REMOVE_LEAD_SPECIALTY", 409,
                    "The lead specialty cannot be removed — transfer the lead to another team first");
        }
        specialtyRepository.delete(row);
        return listSpecialties(episodeId);
    }

    /**
     * The one invariant. {@code surgical_episode.specialty} keeps its original meaning, so every
     * reader written before V011 still gets a correct answer; it is written here, in the same
     * transaction as the LEAD row, so it cannot drift from it.
     */
    private void syncLeadColumn(SurgicalEpisodeEntity episode, String leadSpecialty) {
        episode.setSpecialty(leadSpecialty);
        episodeRepository.save(episode);
    }

    private Map<String, Object> row(SurgicalEpisodeSpecialtyEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId() != null ? e.getId().toString() : null);
        m.put("specialty", e.getSpecialty());
        m.put("role", e.getRole());
        m.put("contribution", e.getContribution());
        m.put("addedBy", e.getAddedBy());
        m.put("addedAt", e.getAddedAt() != null ? e.getAddedAt().toString() : null);
        return m;
    }
}
