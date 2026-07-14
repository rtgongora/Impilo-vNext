package zw.gov.mohcc.impilo.costa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.costa.config.CostaProperties;
import zw.gov.mohcc.impilo.costa.domain.entity.EncounterEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ExemptionCategory;
import zw.gov.mohcc.impilo.costa.integration.ProviderRecognitionClient;

/**
 * Encounter-creation enrichment: tags a new encounter with the
 * {@link ExemptionCategory#HEALTH_WORKER} billing category when the patient
 * is a recognised health worker AND tenant policy allows it.
 *
 * <p>Guard rails:</p>
 * <ul>
 *   <li>OFF by default — {@code costa.recognition.enabled=false}; nothing
 *       happens until a deployment explicitly enables it (and the DB
 *       exemption rule is separately flipped to ACTIVE).</li>
 *   <li>Never overwrites an existing exemption category (INDIGENT etc. keep
 *       precedence — this enrichment only fills a blank).</li>
 *   <li>NEVER throws: recognition lookups fail open to no-discount so
 *       billing ingest cannot stall on a registry outage.</li>
 *   <li>The person key used is the encounter's patient identifier
 *       (patientCpid, which carries the Health ID UUID on the journeys that
 *       feed COSTA); non-UUID legacy CPIDs simply resolve to not-recognised
 *       upstream (generic negative), i.e. no discount.</li>
 * </ul>
 */
@Service
public class HealthWorkerExemptionEnrichment {

    private static final Logger log = LoggerFactory.getLogger(HealthWorkerExemptionEnrichment.class);

    private final ProviderRecognitionClient recognitionClient;
    private final CostaProperties properties;

    public HealthWorkerExemptionEnrichment(ProviderRecognitionClient recognitionClient,
                                           CostaProperties properties) {
        this.recognitionClient = recognitionClient;
        this.properties = properties;
    }

    /** Enrich in place before the encounter is first persisted. Never throws. */
    public void enrich(EncounterEntity encounter) {
        try {
            if (encounter == null || !properties.getRecognition().isEnabled()) {
                return;
            }
            if (encounter.getExemptionCategory() != null
                    && !encounter.getExemptionCategory().isBlank()) {
                return; // existing category always wins
            }
            ProviderRecognitionClient.Recognition recognition =
                    recognitionClient.resolve(encounter.getTenantId(), encounter.getPatientCpid());
            if (!recognition.recognised()) {
                return;
            }
            if (!properties.getRecognition().getAllowedClasses()
                    .contains(recognition.recognitionClass())) {
                log.debug("Recognition class {} not allowed by tenant policy; no enrichment",
                        recognition.recognitionClass());
                return;
            }
            encounter.setExemptionCategory(ExemptionCategory.HEALTH_WORKER.name());
            log.info("Encounter {} tagged HEALTH_WORKER (class={}) — discount applies only if the governed rule is ACTIVE",
                    encounter.getEncounterId(), recognition.recognitionClass());
        } catch (Exception e) {
            // Fail open to no-discount; billing must never block on recognition.
            log.warn("Health-worker enrichment skipped (fail-open): {}", e.getMessage());
        }
    }
}
