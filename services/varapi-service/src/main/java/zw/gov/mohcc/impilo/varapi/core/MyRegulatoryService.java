package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.*;
import zw.gov.mohcc.impilo.varapi.persistence.repository.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "My Regulatory Affairs" (ROM-W3): the AUTHENTICATED person's own regulatory standing across
 * every council they are registered with — registrations, register entries, good standing and
 * restrictions. Keyed on the person's Health-ID from the trust context, NOT a {@code providerId}
 * query param (fixing the admin-plane defect where the only self-service page was
 * {@code ?providerId=}-keyed and therefore not self-service at all).
 */
@Service
public class MyRegulatoryService {

    private final ProviderRepository providerRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final ProviderGoodStandingRepository goodStandingRepository;
    private final RegisterEntryRestrictionRepository restrictionRepository;
    private final ProviderApplicationRepository applicationRepository;
    private final ProviderDisciplinaryCaseRepository disciplinaryRepository;

    public MyRegulatoryService(ProviderRepository providerRepository,
                               ProviderCouncilRegistrationRecordRepository registrationRepository,
                               ProviderGoodStandingRepository goodStandingRepository,
                               RegisterEntryRestrictionRepository restrictionRepository,
                               ProviderApplicationRepository applicationRepository,
                               ProviderDisciplinaryCaseRepository disciplinaryRepository) {
        this.providerRepository = providerRepository;
        this.registrationRepository = registrationRepository;
        this.goodStandingRepository = goodStandingRepository;
        this.restrictionRepository = restrictionRepository;
        this.applicationRepository = applicationRepository;
        this.disciplinaryRepository = disciplinaryRepository;
    }

    public record Restriction(String type, String description, String status,
                              LocalDate validFrom, LocalDate validTo) {}

    public record CouncilRegistration(String councilCode, String councilName, String registrationNumber,
                                      String registrationCategory, String status, String standing,
                                      LocalDate registrationDate, LocalDate expiryDate,
                                      List<Restriction> restrictions) {}

    public record MyRegulatorySummary(boolean linked, String providerPublicId,
                                      List<CouncilRegistration> councils) {
        static MyRegulatorySummary unlinked() {
            return new MyRegulatorySummary(false, null, List.of());
        }
    }

    public record MyApplication(Long id, String applicationType, String workflowState,
                                String councilCode, String submittedAt) {}

    /** The signed-in person's own applications (ROM-U). */
    @Transactional(readOnly = true)
    public List<MyApplication> applicationsForPerson(UUID tenantId, String personHealthId) {
        UUID healthId = parseUuid(personHealthId);
        if (healthId == null) {
            return List.of();
        }
        ProviderEntity provider = providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId).orElse(null);
        if (provider == null) {
            return List.of();
        }
        return applicationRepository.findByTenantIdAndProviderId(tenantId, provider.getId()).stream()
                .map(a -> new MyApplication(a.getId(), a.getApplicationType(), a.getWorkflowState(),
                        a.getCouncil() != null ? a.getCouncil().getCouncilCode() : null,
                        a.getSubmittedAt() != null ? a.getSubmittedAt().toString() : null))
                .toList();
    }

    public record MyComplaint(Long id, String caseStage, String status, String summary,
                              boolean respondentNotified, String openedDate) {}

    /** Complaints/disciplinary matters involving the signed-in person (ROM-U3, respondent view). */
    @Transactional(readOnly = true)
    public List<MyComplaint> complaintsForPerson(UUID tenantId, String personHealthId) {
        UUID healthId = parseUuid(personHealthId);
        if (healthId == null) {
            return List.of();
        }
        ProviderEntity provider = providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId).orElse(null);
        if (provider == null) {
            return List.of();
        }
        return disciplinaryRepository.findByProviderId(provider.getId()).stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(c -> new MyComplaint(c.getId(), c.getCaseStage(), c.getStatus(), c.getSummary(),
                        Boolean.TRUE.equals(c.getRespondentNotified()),
                        c.getOpenedDate() != null ? c.getOpenedDate().toString() : null))
                .toList();
    }

    /** Resolve the person (Health-ID) → their provider → their regulatory affairs. */
    @Transactional(readOnly = true)
    public MyRegulatorySummary summaryForPerson(UUID tenantId, String personHealthId) {
        UUID healthId = parseUuid(personHealthId);
        if (healthId == null) {
            return MyRegulatorySummary.unlinked();
        }
        ProviderEntity provider = providerRepository.findByTenantIdAndImpiloHealthId(tenantId, healthId).orElse(null);
        if (provider == null) {
            return MyRegulatorySummary.unlinked();
        }

        List<ProviderCouncilRegistrationRecordEntity> records =
                registrationRepository.findByTenantIdAndProvider_Id(tenantId, provider.getId());

        Map<Long, String> standingByCouncil = goodStandingRepository
                .findByTenantIdAndProviderId(tenantId, provider.getId()).stream()
                .collect(Collectors.toMap(ProviderGoodStandingEntity::getCouncilId,
                        ProviderGoodStandingEntity::getStanding, (a, b) -> a));

        Map<Long, List<Restriction>> restrictionsByRecord = new HashMap<>();
        List<Long> recordIds = records.stream().map(ProviderCouncilRegistrationRecordEntity::getId).toList();
        if (!recordIds.isEmpty()) {
            for (RegisterEntryRestrictionEntity r : restrictionRepository
                    .findByTenantIdAndRegistrationRecordIdInAndStatus(tenantId, recordIds, "ACTIVE")) {
                restrictionsByRecord.computeIfAbsent(r.getRegistrationRecordId(), k -> new ArrayList<>())
                        .add(new Restriction(r.getRestrictionType(), r.getDescription(), r.getStatus(),
                                r.getValidFrom(), r.getValidTo()));
            }
        }

        List<CouncilRegistration> councils = records.stream().map(rec -> {
            CouncilEntity c = rec.getCouncil();
            return new CouncilRegistration(
                    c.getCouncilCode(), c.getName(), rec.getRegistrationNumber(), rec.getRegistrationCategory(),
                    rec.getStatus(), standingByCouncil.getOrDefault(c.getId(), "GOOD"),
                    rec.getRegistrationDate(), rec.getExpiryDate(),
                    restrictionsByRecord.getOrDefault(rec.getId(), List.of()));
        }).toList();

        return new MyRegulatorySummary(true, provider.getProviderPublicId(), councils);
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
