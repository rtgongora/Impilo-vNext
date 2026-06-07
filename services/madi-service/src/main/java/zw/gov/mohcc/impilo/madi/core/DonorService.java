package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.DonorStatus;
import zw.gov.mohcc.impilo.madi.domain.ScreeningResult;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import zw.gov.mohcc.impilo.madi.domain.PreScreeningOutcome;

@Service
public class DonorService {

    private final DonorProfileRepository donorProfileRepository;
    private final DonorEligibilityScreeningRepository screeningRepository;
    private final DonorDeferralRepository deferralRepository;
    private final DonorCommunicationPreferenceRepository preferenceRepository;
    private final DonorFeedbackRepository feedbackRepository;
    private final DonorPreScreeningRepository preScreeningRepository;
    private final MadiEventEmitter eventEmitter;

    public DonorService(DonorProfileRepository donorProfileRepository,
                        DonorEligibilityScreeningRepository screeningRepository,
                        DonorDeferralRepository deferralRepository,
                        DonorCommunicationPreferenceRepository preferenceRepository,
                        DonorFeedbackRepository feedbackRepository,
                        DonorPreScreeningRepository preScreeningRepository,
                        MadiEventEmitter eventEmitter) {
        this.donorProfileRepository = donorProfileRepository;
        this.screeningRepository = screeningRepository;
        this.deferralRepository = deferralRepository;
        this.preferenceRepository = preferenceRepository;
        this.feedbackRepository = feedbackRepository;
        this.preScreeningRepository = preScreeningRepository;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public DonorProfileEntity register(UUID tenantId, String personCpid, String bloodGroup,
                                       String rhFactor, UUID facilityId, String registeredBy) {
        donorProfileRepository.findByPersonCpidAndTenantId(personCpid, tenantId).ifPresent(d -> {
            throw new IllegalStateException("Donor already registered for person");
        });
        DonorProfileEntity donor = new DonorProfileEntity();
        donor.setTenantId(tenantId);
        donor.setPersonCpid(personCpid);
        donor.setBloodGroup(bloodGroup);
        donor.setRhFactor(rhFactor);
        donor.setFacilityId(facilityId);
        donor.setRegisteredBy(registeredBy);
        donor.setStatus(DonorStatus.REGISTERED.name());
        donor.setUpdatedAt(OffsetDateTime.now());
        DonorProfileEntity saved = donorProfileRepository.save(donor);
        eventEmitter.emit("DONOR", saved.getDonorId().toString(), "DONOR_REGISTERED",
                "DONOR", saved.getDonorId().toString(),
                Map.of("personCpid", personCpid, "bloodGroup", bloodGroup), tenantId);
        return saved;
    }

    @Transactional
    public DonorEligibilityScreeningEntity screen(UUID tenantId, UUID donorId, UUID driveId,
                                                  ScreeningResult result, String notes, String screenedBy,
                                                  UUID facilityId) {
        DonorProfileEntity donor = requireDonor(tenantId, donorId);
        DonorEligibilityScreeningEntity screening = new DonorEligibilityScreeningEntity();
        screening.setTenantId(tenantId);
        screening.setDonorId(donorId);
        screening.setDriveId(driveId);
        screening.setResult(result.name());
        screening.setScreeningNotes(notes);
        screening.setScreenedBy(screenedBy);
        screening.setFacilityId(facilityId);
        screening.setScreenedAt(OffsetDateTime.now());
        screening.setStatus("COMPLETED");
        DonorEligibilityScreeningEntity saved = screeningRepository.save(screening);
        if (result == ScreeningResult.PASS) {
            donor.setStatus(DonorStatus.ACTIVE.name());
        } else if (result == ScreeningResult.DEFERRED) {
            donor.setStatus(DonorStatus.DEFERRED.name());
        } else {
            donor.setStatus(DonorStatus.INELIGIBLE.name());
        }
        donor.setUpdatedAt(OffsetDateTime.now());
        donorProfileRepository.save(donor);
        eventEmitter.emit("DONOR", donorId.toString(), "DONOR_SCREENED", "DONOR", donorId.toString(),
                Map.of("result", result.name(), "driveId", driveId != null ? driveId.toString() : ""), tenantId);
        return saved;
    }

    @Transactional
    public DonorDeferralEntity defer(UUID tenantId, UUID donorId, String reasonCode,
                                     LocalDate deferredUntil, String notes, String deferredBy, UUID facilityId) {
        DonorProfileEntity donor = requireDonor(tenantId, donorId);
        DonorDeferralEntity deferral = new DonorDeferralEntity();
        deferral.setTenantId(tenantId);
        deferral.setDonorId(donorId);
        deferral.setReasonCode(reasonCode);
        deferral.setDeferredUntil(deferredUntil);
        deferral.setNotes(notes);
        deferral.setDeferredBy(deferredBy);
        deferral.setFacilityId(facilityId);
        deferral.setStatus("ACTIVE");
        deferral.setUpdatedAt(OffsetDateTime.now());
        donor.setStatus(DonorStatus.DEFERRED.name());
        donor.setUpdatedAt(OffsetDateTime.now());
        donorProfileRepository.save(donor);
        DonorDeferralEntity saved = deferralRepository.save(deferral);
        eventEmitter.emit("DONOR", donorId.toString(), "DONOR_DEFERRED", "DONOR", donorId.toString(),
                Map.of("reasonCode", reasonCode), tenantId);
        return saved;
    }

    @Transactional
    public DonorCommunicationPreferenceEntity updatePreferences(UUID tenantId, UUID donorId,
                                                              String channel, boolean enabled, String locale,
                                                              String updatedBy, UUID facilityId) {
        requireDonor(tenantId, donorId);
        DonorCommunicationPreferenceEntity pref = preferenceRepository
                .findByTenantIdAndDonorIdAndChannel(tenantId, donorId, channel)
                .orElseGet(DonorCommunicationPreferenceEntity::new);
        if (pref.getPreferenceId() == null) {
            pref.setTenantId(tenantId);
            pref.setDonorId(donorId);
            pref.setChannel(channel);
        }
        pref.setEnabled(enabled);
        pref.setLocale(locale);
        pref.setUpdatedBy(updatedBy);
        pref.setFacilityId(facilityId);
        pref.setUpdatedAt(OffsetDateTime.now());
        return preferenceRepository.save(pref);
    }

    @Transactional
    public DonorFeedbackEntity submitFeedback(UUID tenantId, UUID donorId, UUID driveId,
                                              Integer rating, String comments, UUID facilityId) {
        requireDonor(tenantId, donorId);
        DonorFeedbackEntity feedback = new DonorFeedbackEntity();
        feedback.setTenantId(tenantId);
        feedback.setDonorId(donorId);
        feedback.setDriveId(driveId);
        feedback.setRating(rating);
        feedback.setComments(comments);
        feedback.setFacilityId(facilityId);
        feedback.setStatus("SUBMITTED");
        feedback.setSubmittedAt(OffsetDateTime.now());
        return feedbackRepository.save(feedback);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> donorHistory(UUID tenantId, UUID donorId) {
        requireDonor(tenantId, donorId);
        List<DonorEligibilityScreeningEntity> screenings = screeningRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().filter(s -> donorId.equals(s.getDonorId())).toList();
        List<DonorDeferralEntity> deferrals = deferralRepository.findByDonorIdAndTenantIdAndStatus(donorId, tenantId, "ACTIVE");
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("donorId", donorId);
        history.put("screenings", screenings.size());
        history.put("activeDeferrals", deferrals.size());
        history.put("lastScreening", screenings.isEmpty() ? null : screenings.getFirst().getResult());
        return history;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> nextEligibility(UUID tenantId, UUID donorId) {
        DonorProfileEntity donor = requireDonor(tenantId, donorId);
        List<DonorDeferralEntity> active = deferralRepository.findByDonorIdAndTenantIdAndStatus(donorId, tenantId, "ACTIVE");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("donorId", donorId);
        result.put("status", donor.getStatus());
        if (!active.isEmpty() && active.getFirst().getDeferredUntil() != null) {
            result.put("eligibleAfter", active.getFirst().getDeferredUntil().toString());
            result.put("eligible", LocalDate.now().isAfter(active.getFirst().getDeferredUntil()));
        } else {
            result.put("eligible", DonorStatus.ACTIVE.name().equals(donor.getStatus()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<DonorProfileEntity> findByPersonCpid(UUID tenantId, String personCpid) {
        return donorProfileRepository.findByPersonCpidAndTenantId(personCpid, tenantId);
    }

    @Transactional
    public DonorPreScreeningEntity submitPreScreening(UUID tenantId, UUID donorId, Map<String, Object> answers,
                                                      UUID facilityId) {
        requireDonor(tenantId, donorId);
        Map<String, Object> normalizedAnswers = answers != null ? new LinkedHashMap<>(answers) : Map.of();
        PreScreeningOutcome outcome = evaluatePreScreeningOutcome(normalizedAnswers);
        DonorPreScreeningEntity screening = new DonorPreScreeningEntity();
        screening.setTenantId(tenantId);
        screening.setDonorId(donorId);
        screening.setAnswersJson(normalizedAnswers);
        screening.setOutcome(outcome.name());
        screening.setSafeMessage(safeMessageFor(outcome));
        screening.setFacilityId(facilityId);
        DonorPreScreeningEntity saved = preScreeningRepository.save(screening);
        eventEmitter.emit("DONOR", donorId.toString(), "DONOR_PRE_SCREENED", "DONOR", donorId.toString(),
                Map.of("outcome", outcome.name()), tenantId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<DonorPreScreeningEntity> getLatestPreScreening(UUID tenantId, UUID donorId) {
        requireDonor(tenantId, donorId);
        return preScreeningRepository.findFirstByTenantIdAndDonorIdOrderByCreatedAtDesc(tenantId, donorId);
    }

    static PreScreeningOutcome evaluatePreScreeningOutcome(Map<String, Object> answers) {
        if (isAffirmative(answers.get("pregnant_or_recent_birth"))) {
            return PreScreeningOutcome.CONTACT_STAFF;
        }
        if (isAffirmative(answers.get("recent_illness"))
                || isAffirmative(answers.get("on_antibiotics"))
                || isAffirmative(answers.get("low_hemoglobin_symptoms"))
                || isAffirmative(answers.get("recent_travel_malaria"))
                || isAffirmative(answers.get("recent_tattoo"))) {
            return PreScreeningOutcome.DEFER_SAFELY;
        }
        return PreScreeningOutcome.PROCEED_TO_DRIVE;
    }

    private static boolean isAffirmative(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized);
    }

    private static String safeMessageFor(PreScreeningOutcome outcome) {
        return switch (outcome) {
            case PROCEED_TO_DRIVE -> "You may proceed to the donation drive check-in. Staff will confirm eligibility on site.";
            case DEFER_SAFELY -> "Based on your answers, please defer donating today and contact the drive team if you need guidance.";
            case CONTACT_STAFF -> "Please speak with donation drive staff before continuing. This is not a clinical diagnosis.";
        };
    }

    private DonorProfileEntity requireDonor(UUID tenantId, UUID donorId) {
        return donorProfileRepository.findByDonorIdAndTenantId(donorId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Donor not found"));
    }
}
