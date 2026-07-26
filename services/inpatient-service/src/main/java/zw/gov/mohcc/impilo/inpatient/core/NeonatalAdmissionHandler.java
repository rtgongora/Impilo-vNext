package zw.gov.mohcc.impilo.inpatient.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Admits a newborn in their own right when they are handed over to neonatal care.
 *
 * <p>A baby routed to a neonatal unit is an inpatient, but until now no admission existed
 * under the baby's CPID — the theatre episode belongs to the mother. Every subsequent
 * clinical write about the baby (APGAR, observations, medication administration) is checked
 * against an active care context for that subject, so without this admission those writes
 * are refused. The gap is recorded in
 * docs/security/clinical-write-subject-relationship-control.md; this closes it.</p>
 *
 * <p>Only destinations that genuinely mean inpatient neonatal care create an admission.
 * A baby rooming in with their mother on the postnatal ward is not separately admitted:
 * that would create a bed occupancy and a bed-day that does not exist, and the decision of
 * whether rooming-in should ever be modelled as its own admission is a service-configuration
 * question, not something to assume here.</p>
 */
@Component
public class NeonatalAdmissionHandler {

    private static final Logger log = LoggerFactory.getLogger(NeonatalAdmissionHandler.class);

    /** Destinations that mean the baby is an inpatient under neonatal care. */
    private static final List<String> INPATIENT_DESTINATIONS = List.of("NEONATAL", "NICU", "SCBU");

    private final AdmissionRepository admissionRepository;

    public NeonatalAdmissionHandler(AdmissionRepository admissionRepository) {
        this.admissionRepository = admissionRepository;
    }

    public static boolean requiresAdmission(String destination) {
        return destination != null
                && INPATIENT_DESTINATIONS.contains(destination.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Ensure the neonate has an active admission of their own.
     *
     * @return the admission, or empty when the destination does not warrant one
     */
    @Transactional
    public Optional<AdmissionEntity> admitOnHandover(UUID tenantId, String babyCpid, UUID facilityId,
                                                     String destination, UUID motherEpisodeId) {
        if (!requiresAdmission(destination)) {
            return Optional.empty();
        }
        if (tenantId == null || babyCpid == null || babyCpid.isBlank()) {
            log.warn("Neonatal handover to {} could not be admitted: tenant or baby CPID missing", destination);
            return Optional.empty();
        }

        Optional<AdmissionEntity> active = admissionRepository
                .findBySubjectCpidAndStatus(babyCpid, "ADMITTED")
                .stream()
                .filter(a -> tenantId.equals(a.getTenantId()))
                .findFirst();
        if (active.isPresent()) {
            // A repeated handover, or a baby already admitted through another route.
            return active;
        }

        AdmissionEntity admission = new AdmissionEntity();
        admission.setAdmissionRef(UUID.randomUUID());
        admission.setTenantId(tenantId);
        admission.setSubjectCpid(babyCpid);
        admission.setFacilityId(facilityId);
        // The neonate has no encounter of their own in this service; derive a deterministic
        // reference from the baby's CPID so repeated handovers resolve to the same value.
        admission.setEncounterId(UUID.nameUUIDFromBytes(("neonatal-handover:" + babyCpid).getBytes()));
        admission.setAdmittingDiagnosis("Neonatal care following delivery");
        admission.setAdmissionType("EMERGENCY");
        admission.setStatus("ADMITTED");
        admission.setAdmittedAt(OffsetDateTime.now());
        admission.setCreatedAt(OffsetDateTime.now());
        admission = admissionRepository.save(admission);

        log.info("Neonate admitted on handover: admissionRef={} baby={} destination={} motherEpisode={}",
                admission.getAdmissionRef(), babyCpid, destination, motherEpisodeId);
        return Optional.of(admission);
    }
}
