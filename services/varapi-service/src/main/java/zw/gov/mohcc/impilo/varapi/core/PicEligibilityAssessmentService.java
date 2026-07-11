package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCertificateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilAffiliationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilLicenceRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEligibilitySnapshotEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.DisciplinaryActionRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilLicenceRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderEligibilitySnapshotRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Practitioner-in-Charge eligibility assessment.
 *
 * Unifies the FRAGMENTED credential truth held across VARAPI —
 * provider identity/registry status, primary council, council registration
 * records (with legacy affiliation fallback), practising certificates,
 * licences, disciplinary actions and council licence-record restrictions —
 * into one per-axis, point-in-time ({@code asOf}) assessment.
 *
 * Every assessment persists an immutable {@link ProviderEligibilitySnapshotEntity}
 * so the facility-side nomination decision references audited evidence, and
 * publishes a {@code varapi.provider.eligibility.assessed} outbox event.
 *
 * Result semantics:
 *   INELIGIBLE  — any blocking axis (identity unresolved, no valid registration,
 *                 no valid practising certificate, suspended/revoked credential,
 *                 blocking restriction).
 *   CONDITIONAL — only conditions-level restrictions apply; eligible=false but the
 *                 facility-side human decides with the conditions in view.
 *   ELIGIBLE    — all axes pass; eligible=true.
 */
@Service
public class PicEligibilityAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(PicEligibilityAssessmentService.class);

    private static final String DEFAULT_PURPOSE = "PIC_NOMINATION";
    private static final Set<String> VERIFIED_REGISTRY_STATUSES =
            Set.of("REGISTERED_ACTIVE", "ACTIVE", "VERIFIED");
    private static final Set<String> BLOCKING_LIFECYCLE_STATUSES =
            Set.of("SUSPENDED", "RESTRICTED", "REMOVED");

    public record AssessmentRequest(
            String providerPublicId,
            String facilityRef,
            String facilityUnitRef,
            String purpose,
            LocalDate asOf) {}

    public record AssessmentResponse(
            UUID snapshotId,
            String providerPublicId,
            UUID impiloHealthId,
            String displayName,
            String profession,
            String cadre,
            String councilCode,
            String councilName,
            LocalDate asOf,
            Instant assessedAt,
            boolean eligible,
            String result,
            Map<String, Object> axes,
            List<String> reasons) {}

    private final ProviderRepository providerRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRecordRepository;
    private final ProviderCouncilAffiliationRepository councilAffiliationRepository;
    private final CertificateService certificateService;
    private final LicenseRepository licenseRepository;
    private final DisciplinaryActionRepository disciplinaryActionRepository;
    private final ProviderCouncilLicenceRecordRepository councilLicenceRecordRepository;
    private final ProviderEligibilitySnapshotRepository snapshotRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PicEligibilityAssessmentService(
            ProviderRepository providerRepository,
            ProviderCouncilRegistrationRecordRepository registrationRecordRepository,
            ProviderCouncilAffiliationRepository councilAffiliationRepository,
            CertificateService certificateService,
            LicenseRepository licenseRepository,
            DisciplinaryActionRepository disciplinaryActionRepository,
            ProviderCouncilLicenceRecordRepository councilLicenceRecordRepository,
            ProviderEligibilitySnapshotRepository snapshotRepository,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.registrationRecordRepository = registrationRecordRepository;
        this.councilAffiliationRepository = councilAffiliationRepository;
        this.certificateService = certificateService;
        this.licenseRepository = licenseRepository;
        this.disciplinaryActionRepository = disciplinaryActionRepository;
        this.councilLicenceRecordRepository = councilLicenceRecordRepository;
        this.snapshotRepository = snapshotRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssessmentResponse assess(AssessmentRequest request) {
        if (request == null || request.providerPublicId() == null || request.providerPublicId().isBlank()) {
            throw new IllegalArgumentException("providerPublicId is required");
        }
        TrustContext ctx = TrustContextHolder.get();
        UUID tenantId = ctx != null ? ctx.tenantId() : null;
        LocalDate asOf = request.asOf() != null ? request.asOf() : LocalDate.now();
        String purpose = (request.purpose() == null || request.purpose().isBlank())
                ? DEFAULT_PURPOSE : request.purpose();

        ProviderEntity provider = resolveProvider(request.providerPublicId(), tenantId);

        List<String> reasons = new ArrayList<>();
        Map<String, Object> axes = new LinkedHashMap<>();
        boolean blocking = false;
        boolean conditional = false;

        // ── Axis 1: identity ─────────────────────────────────────────────────
        Map<String, Object> identityAxis = new LinkedHashMap<>();
        boolean healthIdPresent = provider.getImpiloHealthId() != null;
        String registryStatus = provider.getRegistryStatus();
        String lifecycleStatus = provider.getLifecycleStatus();
        boolean registryVerified = registryStatus != null
                && VERIFIED_REGISTRY_STATUSES.contains(registryStatus.trim().toUpperCase(Locale.ROOT));
        boolean lifecycleBlocked = lifecycleStatus != null
                && BLOCKING_LIFECYCLE_STATUSES.contains(lifecycleStatus.trim().toUpperCase(Locale.ROOT));
        identityAxis.put("impiloHealthIdPresent", healthIdPresent);
        identityAxis.put("registryStatus", registryStatus);
        identityAxis.put("registryVerified", registryVerified);
        identityAxis.put("lifecycleStatus", lifecycleStatus);
        if (!healthIdPresent) {
            blocking = true;
            identityAxis.put("status", "FAIL");
            reasons.add("Identity unresolved: provider has no Impilo Health ID anchor.");
        } else if (lifecycleBlocked) {
            blocking = true;
            identityAxis.put("status", "FAIL");
            reasons.add("Provider lifecycle status is " + lifecycleStatus
                    + " — suspended, restricted or removed providers cannot be nominated.");
        } else if (!registryVerified) {
            identityAxis.put("status", "WARN");
            reasons.add("Registry status is " + (registryStatus == null ? "unset" : registryStatus)
                    + " — provider registry entry is not in a verified active state.");
        } else {
            identityAxis.put("status", "PASS");
        }
        axes.put("identity", identityAxis);

        // ── Axis 2: council ──────────────────────────────────────────────────
        Map<String, Object> councilAxis = new LinkedHashMap<>();
        CouncilEntity council = provider.getPrimaryCouncil();
        String councilCode = council != null ? council.getCouncilCode() : null;
        String councilName = council != null ? council.getName() : null;
        councilAxis.put("councilCode", councilCode);
        councilAxis.put("councilName", councilName);
        if (councilCode == null || councilCode.isBlank() || councilName == null || councilName.isBlank()) {
            councilAxis.put("status", "WARN");
            reasons.add("No primary council recorded for provider.");
        } else {
            councilAxis.put("status", "PASS");
        }
        axes.put("council", councilAxis);

        // ── Axis 3: registration (records, legacy affiliation fallback) ─────
        Map<String, Object> registrationAxis = new LinkedHashMap<>();
        List<ProviderCouncilRegistrationRecordEntity> registrationRecords = tenantId != null
                ? registrationRecordRepository.findByTenantIdAndProvider_Id(tenantId, provider.getId())
                : List.of();
        Optional<ProviderCouncilRegistrationRecordEntity> latestRecord = registrationRecords.stream()
                .max(Comparator
                        .comparing(ProviderCouncilRegistrationRecordEntity::getRegistrationDate,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ProviderCouncilRegistrationRecordEntity::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())));
        boolean registrationValid = false;
        if (latestRecord.isPresent()) {
            ProviderCouncilRegistrationRecordEntity rec = latestRecord.get();
            registrationValid = "REGISTERED".equalsIgnoreCase(rec.getStatus())
                    && (rec.getExpiryDate() == null || !rec.getExpiryDate().isBefore(asOf));
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("source", "provider_council_registration_records");
            evidence.put("id", rec.getId());
            evidence.put("status", rec.getStatus());
            evidence.put("registrationNumber", rec.getRegistrationNumber());
            evidence.put("registrationCategory", rec.getRegistrationCategory());
            evidence.put("expiryDate", rec.getExpiryDate() != null ? rec.getExpiryDate().toString() : null);
            evidence.put("updatedAt", rec.getUpdatedAt() != null ? rec.getUpdatedAt().toString() : null);
            registrationAxis.put("evidence", evidence);
            if (!registrationValid) {
                reasons.add("Latest council registration record is not valid at " + asOf
                        + " (status=" + rec.getStatus()
                        + ", expiryDate=" + rec.getExpiryDate() + ").");
            }
        } else {
            // Legacy fallback: provider_council_affiliations.
            Optional<ProviderCouncilAffiliationEntity> affiliation =
                    councilAffiliationRepository.findByProviderId(provider.getId()).stream()
                            .filter(ProviderCouncilAffiliationEntity::isActive)
                            .max(Comparator.comparing(ProviderCouncilAffiliationEntity::getId,
                                    Comparator.nullsFirst(Comparator.naturalOrder())));
            if (affiliation.isPresent()) {
                ProviderCouncilAffiliationEntity aff = affiliation.get();
                registrationValid = true;
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("source", "provider_council_affiliations");
                evidence.put("id", aff.getId());
                evidence.put("status", aff.getStatus());
                evidence.put("registrationNumber", aff.getRegistrationNumber());
                evidence.put("updatedAt", aff.getUpdatedAt() != null ? aff.getUpdatedAt().toString() : null);
                registrationAxis.put("evidence", evidence);
            } else {
                reasons.add("No valid council registration found (no registration record and no active legacy affiliation).");
            }
        }
        if (!registrationValid) {
            blocking = true;
        }
        registrationAxis.put("status", registrationValid ? "PASS" : "FAIL");
        axes.put("registration", registrationAxis);

        // ── Axis 4: practising certificate ───────────────────────────────────
        Map<String, Object> certificateAxis = new LinkedHashMap<>();
        Optional<ProviderCertificateEntity> certOpt =
                certificateService.getActiveCertificate(provider.getId(), "PRACTISING_CERTIFICATE");
        boolean certificateValid = false;
        if (certOpt.isPresent()) {
            ProviderCertificateEntity cert = certOpt.get();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("source", "provider_certificates");
            evidence.put("id", cert.getId());
            evidence.put("status", cert.getStatus());
            evidence.put("certificateNumber", cert.getCertificateNumber());
            evidence.put("issueDate", cert.getIssueDate() != null ? cert.getIssueDate().toString() : null);
            evidence.put("expiryDate", cert.getExpiryDate() != null ? cert.getExpiryDate().toString() : null);
            certificateAxis.put("evidence", evidence);
            if (cert.getSuspensionDate() != null) {
                reasons.add("Practising certificate is marked suspended (suspensionDate="
                        + cert.getSuspensionDate() + ").");
            } else if (cert.getRevocationDate() != null) {
                reasons.add("Practising certificate is marked revoked (revocationDate="
                        + cert.getRevocationDate() + ").");
            } else if (cert.getExpiryDate() != null && cert.getExpiryDate().isBefore(asOf)) {
                reasons.add("Practising certificate expired on " + cert.getExpiryDate()
                        + " (assessment as of " + asOf + ").");
            } else {
                certificateValid = true;
            }
        } else {
            reasons.add("No active practising certificate on record.");
        }
        if (!certificateValid) {
            blocking = true;
        }
        certificateAxis.put("status", certificateValid ? "PASS" : "FAIL");
        axes.put("practisingCertificate", certificateAxis);

        // ── Axis 5: licence ──────────────────────────────────────────────────
        Map<String, Object> licenceAxis = new LinkedHashMap<>();
        List<LicenseEntity> licences = licenseRepository.findByProviderId(provider.getId());
        List<LicenseEntity> suspendedLicences = licences.stream()
                .filter(l -> "SUSPENDED".equalsIgnoreCase(l.getStatus()))
                .toList();
        Optional<LicenseEntity> validLicence = licences.stream()
                .filter(l -> "ACTIVE".equalsIgnoreCase(l.getStatus()))
                .filter(l -> l.getValidFrom() == null || !l.getValidFrom().isAfter(asOf))
                .filter(l -> l.getValidTo() == null || !l.getValidTo().isBefore(asOf))
                .max(Comparator.comparing(LicenseEntity::getValidTo,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        if (!suspendedLicences.isEmpty()) {
            blocking = true;
            licenceAxis.put("status", "FAIL");
            reasons.add("Provider has a SUSPENDED licence (licenseId="
                    + suspendedLicences.get(0).getId() + ") — suspended credentials are blocking.");
        } else if (validLicence.isPresent()) {
            LicenseEntity lic = validLicence.get();
            licenceAxis.put("status", "PASS");
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("source", "licenses");
            evidence.put("id", lic.getId());
            evidence.put("licenseType", lic.getLicenseType());
            evidence.put("status", lic.getStatus());
            evidence.put("validFrom", lic.getValidFrom() != null ? lic.getValidFrom().toString() : null);
            evidence.put("validTo", lic.getValidTo() != null ? lic.getValidTo().toString() : null);
            licenceAxis.put("evidence", evidence);
        } else {
            boolean anyRevoked = licences.stream().anyMatch(l -> "REVOKED".equalsIgnoreCase(l.getStatus()));
            if (anyRevoked) {
                blocking = true;
                licenceAxis.put("status", "FAIL");
                reasons.add("Provider licence is REVOKED and no active licence is valid at " + asOf + ".");
            } else {
                licenceAxis.put("status", "WARN");
                reasons.add("No active licence valid at " + asOf + ".");
            }
        }
        axes.put("licence", licenceAxis);

        // ── Axis 6: restrictions ─────────────────────────────────────────────
        Map<String, Object> restrictionsAxis = new LinkedHashMap<>();
        List<Map<String, Object>> restrictionItems = new ArrayList<>();
        boolean restrictionsBlocking = false;

        // (a) Active disciplinary actions at asOf.
        for (DisciplinaryActionEntity action : disciplinaryActionRepository.findByProviderId(provider.getId())) {
            String status = action.getStatus();
            boolean activeStatus = "ACTIVE".equalsIgnoreCase(status) || "OPEN".equalsIgnoreCase(status);
            boolean effectiveAtAsOf = action.getExpiryDate() == null || !action.getExpiryDate().isBefore(asOf);
            if (!activeStatus || !effectiveAtAsOf) {
                continue;
            }
            String type = action.getActionType() != null
                    ? action.getActionType().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
            boolean itemBlocking = "SUSPENSION".equals(type) || "REVOCATION".equals(type);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", "disciplinary_actions");
            item.put("id", action.getId());
            item.put("type", type);
            item.put("effect", itemBlocking ? "BLOCKING" : "CONDITIONAL");
            item.put("detail", action.getDescription());
            item.put("expiryDate", action.getExpiryDate() != null ? action.getExpiryDate().toString() : null);
            restrictionItems.add(item);
            if (itemBlocking) {
                restrictionsBlocking = true;
                reasons.add("Active disciplinary " + type + " in force at " + asOf
                        + (action.getDescription() != null ? ": " + action.getDescription() : "."));
            } else {
                conditional = true;
                reasons.add("Active disciplinary condition in force at " + asOf
                        + (action.getDescription() != null ? ": " + action.getDescription() : "."));
            }
        }

        // (b) Conditions text on licences valid at asOf.
        for (LicenseEntity lic : licences) {
            if (!"ACTIVE".equalsIgnoreCase(lic.getStatus())) continue;
            if (lic.getValidFrom() != null && lic.getValidFrom().isAfter(asOf)) continue;
            if (lic.getValidTo() != null && lic.getValidTo().isBefore(asOf)) continue;
            if (lic.getConditions() == null || lic.getConditions().isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", "licenses");
            item.put("id", lic.getId());
            item.put("type", "LICENCE_CONDITIONS");
            item.put("effect", "CONDITIONAL");
            item.put("detail", lic.getConditions());
            restrictionItems.add(item);
            conditional = true;
            reasons.add("Licence carries practice conditions: " + lic.getConditions());
        }

        // (c) Restriction summaries on council licence records.
        List<ProviderCouncilLicenceRecordEntity> councilLicenceRecords = tenantId != null
                ? councilLicenceRecordRepository.findByTenantIdAndProvider_Id(tenantId, provider.getId())
                : List.of();
        for (ProviderCouncilLicenceRecordEntity rec : councilLicenceRecords) {
            if (rec.getRestrictionSummary() == null || rec.getRestrictionSummary().isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", "provider_council_licence_records");
            item.put("id", rec.getId());
            item.put("type", "COUNCIL_RESTRICTION");
            item.put("effect", "CONDITIONAL");
            item.put("detail", rec.getRestrictionSummary());
            restrictionItems.add(item);
            conditional = true;
            reasons.add("Council licence record carries restrictions: " + rec.getRestrictionSummary());
        }

        if (restrictionsBlocking) {
            blocking = true;
        }
        restrictionsAxis.put("status", restrictionsBlocking ? "BLOCKING"
                : (restrictionItems.isEmpty() ? "NONE" : "CONDITIONS"));
        restrictionsAxis.put("items", restrictionItems);
        axes.put("restrictions", restrictionsAxis);

        // ── Verdict ──────────────────────────────────────────────────────────
        String result = blocking ? "INELIGIBLE" : (conditional ? "CONDITIONAL" : "ELIGIBLE");
        boolean eligible = "ELIGIBLE".equals(result);

        // ── Persist snapshot ─────────────────────────────────────────────────
        ProviderEligibilitySnapshotEntity snapshot = new ProviderEligibilitySnapshotEntity();
        snapshot.setSnapshotId(UUID.randomUUID());
        snapshot.setTenantId(tenantId);
        snapshot.setProviderId(provider.getId());
        snapshot.setProviderPublicId(provider.getProviderPublicId());
        snapshot.setImpiloHealthId(provider.getImpiloHealthId());
        snapshot.setFacilityRef(request.facilityRef());
        snapshot.setFacilityUnitRef(request.facilityUnitRef());
        snapshot.setPurpose(purpose);
        snapshot.setAsOf(asOf);
        snapshot.setAssessedAt(Instant.now());
        snapshot.setEligible(eligible);
        snapshot.setResult(result);
        snapshot.setAxes(axes);
        snapshot.setReasons(List.copyOf(reasons));
        snapshot.setRequestedBy(ctx != null ? ctx.actorId() : null);
        snapshot = snapshotRepository.save(snapshot);

        publishAssessedEvent(snapshot, tenantId, request.facilityRef(), purpose);

        log.info("PIC eligibility assessed: providerPublicId={}, asOf={}, result={}, snapshotId={}",
                provider.getProviderPublicId(), asOf, result, snapshot.getSnapshotId());

        return new AssessmentResponse(
                snapshot.getSnapshotId(),
                provider.getProviderPublicId(),
                provider.getImpiloHealthId(),
                displayName(provider),
                provider.getProfession(),
                provider.getCadre(),
                councilCode,
                councilName,
                asOf,
                snapshot.getAssessedAt(),
                eligible,
                result,
                axes,
                List.copyOf(reasons));
    }

    @Transactional(readOnly = true)
    public ProviderEligibilitySnapshotEntity getSnapshot(UUID snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Eligibility snapshot not found: " + snapshotId));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ProviderEntity resolveProvider(String providerPublicId, UUID tenantId) {
        Optional<ProviderEntity> provider = tenantId != null
                ? providerRepository.findByProviderPublicIdAndTenantId(providerPublicId, tenantId)
                : providerRepository.findByProviderPublicId(providerPublicId);
        return provider.orElseThrow(() -> new IllegalArgumentException(
                "Provider not found: " + providerPublicId));
    }

    private void publishAssessedEvent(ProviderEligibilitySnapshotEntity snapshot,
                                      UUID tenantId, String facilityRef, String purpose) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshotId", snapshot.getSnapshotId().toString());
        payload.put("providerPublicId", snapshot.getProviderPublicId());
        payload.put("result", snapshot.getResult());
        payload.put("facilityRef", facilityRef);
        payload.put("purpose", purpose);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CREDENTIAL");
        event.setAggregateId(snapshot.getProviderPublicId());
        event.setEventType("varapi.provider.eligibility.assessed");
        event.setPayload(toJson(payload));
        event.setTenantId(tenantId != null ? tenantId.toString() : null);
        event.setPodId("national-spine");
        event.setIdempotencyKey("varapi:eligibility:" + snapshot.getSnapshotId());
        outboxRepository.save(event);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Eligibility event payload serialization failed", e);
        }
    }

    private static String displayName(ProviderEntity provider) {
        String joined = Stream.of(provider.getTitle(), provider.getGivenName(), provider.getFamilyName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return joined.isBlank() ? null : joined;
    }
}
