package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.ConsentPreferenceEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ConsentPreferenceRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wellness consent preferences (Simba-owned). Read by {@link WellnessAccessGuard} for cross-person,
 * caregiver/provider/programme involvement and sensitive-category gating. Defaults are
 * privacy-preserving (only provider_involvement defaults on, matching a care-team default scope).
 */
@Service
public class ConsentPreferenceService {

    private final ConsentPreferenceRepository repository;
    private final SimbaEventEmitter events;
    private final ObjectMapper objectMapper;

    public ConsentPreferenceService(ConsentPreferenceRepository repository,
                                    SimbaEventEmitter events,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /** Returns the person's stored consent, or a transient default (never persisted here). */
    @Transactional(readOnly = true)
    public ConsentPreferenceEntity getOrDefault(UUID tenantId, String personCpid) {
        return repository.findByTenantIdAndPersonCpid(tenantId, personCpid)
                .orElseGet(() -> {
                    ConsentPreferenceEntity def = new ConsentPreferenceEntity();
                    def.setTenantId(tenantId);
                    def.setPersonCpid(personCpid);
                    return def;
                });
    }

    /** Parsed sensitive-category set the person has consented to share. */
    @Transactional(readOnly = true)
    public Set<String> consentedSensitiveCategories(UUID tenantId, String personCpid) {
        ConsentPreferenceEntity pref = getOrDefault(tenantId, personCpid);
        return parseCategories(pref.getSensitiveCategories());
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseCategories(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, List.class);
            return Set.copyOf(list.stream().map(s -> s == null ? "" : s.toUpperCase()).toList());
        } catch (Exception e) {
            return Set.of();
        }
    }

    @Transactional
    public ConsentPreferenceEntity upsert(UUID tenantId,
                                          String personCpid,
                                          Boolean caregiver,
                                          Boolean provider,
                                          Boolean programme,
                                          List<String> sensitiveCategories,
                                          String dataSharingScope,
                                          String updatedBy) {
        ConsentPreferenceEntity pref = repository.findByTenantIdAndPersonCpid(tenantId, personCpid)
                .orElseGet(() -> {
                    ConsentPreferenceEntity fresh = new ConsentPreferenceEntity();
                    fresh.setTenantId(tenantId);
                    fresh.setPersonCpid(personCpid);
                    return fresh;
                });
        if (caregiver != null) pref.setCaregiverInvolvement(caregiver);
        if (provider != null) pref.setProviderInvolvement(provider);
        if (programme != null) pref.setProgrammeInvolvement(programme);
        if (sensitiveCategories != null) {
            try {
                pref.setSensitiveCategories(objectMapper.writeValueAsString(
                        sensitiveCategories.stream().map(String::toUpperCase).toList()));
            } catch (Exception ignored) {
                // keep previous value on serialization failure
            }
        }
        if (dataSharingScope != null && !dataSharingScope.isBlank()) {
            pref.setDataSharingScope(dataSharingScope);
        }
        pref.setUpdatedBy(updatedBy);
        ConsentPreferenceEntity saved = repository.save(pref);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("person_cpid", personCpid);
        payload.put("provider_involvement", saved.isProviderInvolvement());
        payload.put("caregiver_involvement", saved.isCaregiverInvolvement());
        payload.put("programme_involvement", saved.isProgrammeInvolvement());
        payload.put("data_sharing_scope", saved.getDataSharingScope());
        events.emit(tenantId, "WELLNESS_CONSENT", saved.getConsentId().toString(),
                "impilo.simba.wellness.consent.updated.v1", personCpid,
                "simba:consent:" + saved.getConsentId() + ":" + saved.getUpdatedAt(), payload);
        return saved;
    }
}
