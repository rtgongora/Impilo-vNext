package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.api.dto.AddWardRoundEntryRequest;
import zw.gov.mohcc.impilo.inpatient.api.dto.StartWardRoundRequest;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardRoundEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardRoundEntryEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRoundEntryRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRoundRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class WardRoundService {

    /** Tenant for the current request from the trust context (was a hardcoded shared default). */
    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private final WardRoundRepository wardRoundRepository;
    private final WardRoundEntryRepository wardRoundEntryRepository;
    private final AdmissionRepository admissionRepository;

    public WardRoundService(WardRoundRepository wardRoundRepository,
                            WardRoundEntryRepository wardRoundEntryRepository,
                            AdmissionRepository admissionRepository) {
        this.wardRoundRepository = wardRoundRepository;
        this.wardRoundEntryRepository = wardRoundEntryRepository;
        this.admissionRepository = admissionRepository;
    }

    @Transactional
    public WardRoundEntity startRound(StartWardRoundRequest request) {
        UUID admissionRef = request.admissionRef();
        if (admissionRef == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "admission_ref is required");
        }
        admissionRepository.findByAdmissionRef(admissionRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));

        WardRoundEntity round = new WardRoundEntity();
        round.setTenantId(currentTenant());
        round.setAdmissionRef(admissionRef);
        round.setWardId(request.wardId());
        round.setRoundType(request.roundType() != null ? request.roundType() : "MORNING");
        round.setLedBy(request.ledBy());
        round.setNotes(request.notes());
        return wardRoundRepository.save(round);
    }

    @Transactional
    public WardRoundEntryEntity addEntry(UUID roundId, AddWardRoundEntryRequest request) {
        wardRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ward round not found"));

        WardRoundEntryEntity entry = new WardRoundEntryEntity();
        entry.setRoundId(roundId);
        entry.setAssessment(request.assessment());
        entry.setPlan(request.plan());
        entry.setNewOrders(request.newOrders());
        entry.setEscalation(request.escalation());
        entry.setReviewedBy(request.reviewedBy());
        return wardRoundEntryRepository.save(entry);
    }

    public List<WardRoundEntity> listByAdmission(UUID admissionRef) {
        return wardRoundRepository.findByAdmissionRefOrderByStartedAtDesc(admissionRef);
    }

    public List<WardRoundEntity> listByWard(UUID wardId) {
        return wardRoundRepository.findByWardIdOrderByStartedAtDesc(wardId);
    }
}
