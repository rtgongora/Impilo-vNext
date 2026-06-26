package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Full safety report view with nested products, events and attachments. */
public record ReportResponse(
        UUID id,
        String referenceCode,
        String reportType,
        String reporterKind,
        String sourceContext,
        String status,
        String reporterName,
        String reporterRole,
        String reporterFacilityId,
        String reporterPhone,
        String reporterEmail,
        String subjectCpid,
        boolean subjectIsSelf,
        String subjectInitials,
        String subjectSex,
        LocalDate subjectDob,
        Integer subjectAgeValue,
        String subjectAgeUnit,
        BigDecimal subjectWeightKg,
        String encounterId,
        String narrative,
        LocalDate onsetDate,
        LocalDate reportDate,
        boolean serious,
        List<String> seriousnessReasons,
        String seriousnessOtherText,
        String formPackKey,
        String formPackVersion,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        UUID caseId,
        String caseReference,
        List<ProductView> products,
        List<EventView> events,
        List<AttachmentView> attachments
) {
    public record ProductView(
            UUID id, String productRole, String productKind, String productId, String productName,
            String manufacturer, String batchNumber, String lotNumber, LocalDate expiryDate,
            String dose, String doseUnit, String route, String frequency, String indication,
            LocalDate therapyStartDate, LocalDate therapyEndDate, String dechallenge, String rechallenge,
            String doseNumber, LocalDate vaccinationDate, String vaccinationSite) {}

    public record EventView(
            UUID id, String term, String meddraCode, String meddraPt,
            LocalDate onsetDate, LocalDate endDate, String severity, String outcome) {}

    public record AttachmentView(
            UUID id, String documentObjectId, String filename, String mimeType,
            String description, boolean consentObtained) {}
}
