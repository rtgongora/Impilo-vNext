package zw.gov.mohcc.impilo.patientsafety.core;

import zw.gov.mohcc.impilo.patientsafety.api.dto.ReportResponse;
import zw.gov.mohcc.impilo.patientsafety.api.dto.ReportSummary;
import zw.gov.mohcc.impilo.patientsafety.domain.AttachmentRefEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.ProductExposureEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyEventEntity;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyReportEntity;

import java.util.List;
import java.util.UUID;

/** Stateless assembly of report DTOs from entities + children (shared by report and case services). */
public final class ReportAssembler {

    private ReportAssembler() {}

    public static ReportSummary summary(SafetyReportEntity r,
                                        String primaryProduct, String primaryReaction) {
        return new ReportSummary(
                r.getId(), r.getReferenceCode(), r.getReportType(), r.getReporterKind(), r.getStatus(),
                r.getSubjectCpid(), r.getSubjectInitials(), r.isSerious(),
                primaryProduct, primaryReaction, r.getOnsetDate(), r.getCreatedAt(), r.getSubmittedAt());
    }

    public static ReportResponse response(SafetyReportEntity r,
                                          List<ProductExposureEntity> products,
                                          List<SafetyEventEntity> events,
                                          List<AttachmentRefEntity> attachments,
                                          UUID caseId, String caseReference) {
        List<ReportResponse.ProductView> productViews = products.stream()
                .map(p -> new ReportResponse.ProductView(
                        p.getId(), p.getProductRole(), p.getProductKind(), p.getProductId(), p.getProductName(),
                        p.getManufacturer(), p.getBatchNumber(), p.getLotNumber(), p.getExpiryDate(),
                        p.getDose(), p.getDoseUnit(), p.getRoute(), p.getFrequency(), p.getIndication(),
                        p.getTherapyStartDate(), p.getTherapyEndDate(), p.getDechallenge(), p.getRechallenge(),
                        p.getDoseNumber(), p.getVaccinationDate(), p.getVaccinationSite()))
                .toList();
        List<ReportResponse.EventView> eventViews = events.stream()
                .map(e -> new ReportResponse.EventView(
                        e.getId(), e.getTerm(), e.getMeddraCode(), e.getMeddraPt(),
                        e.getOnsetDate(), e.getEndDate(), e.getSeverity(), e.getOutcome()))
                .toList();
        List<ReportResponse.AttachmentView> attachmentViews = attachments.stream()
                .map(a -> new ReportResponse.AttachmentView(
                        a.getId(), a.getDocumentObjectId(), a.getFilename(), a.getMimeType(),
                        a.getDescription(), a.isConsentObtained()))
                .toList();
        return new ReportResponse(
                r.getId(), r.getReferenceCode(), r.getReportType(), r.getReporterKind(), r.getSourceContext(),
                r.getStatus(), r.getReporterName(), r.getReporterRole(), r.getReporterFacilityId(),
                r.getReporterPhone(), r.getReporterEmail(), r.getSubjectCpid(), r.isSubjectIsSelf(),
                r.getSubjectInitials(), r.getSubjectSex(), r.getSubjectDob(), r.getSubjectAgeValue(),
                r.getSubjectAgeUnit(), r.getSubjectWeightKg(), r.getEncounterId(), r.getNarrative(),
                r.getOnsetDate(), r.getReportDate(), r.isSerious(),
                JsonUtil.readStringList(r.getSeriousnessReasons()), r.getSeriousnessOtherText(),
                r.getFormPackKey(), r.getFormPackVersion(), r.getCreatedAt(), r.getSubmittedAt(),
                caseId, caseReference, productViews, eventViews, attachmentViews);
    }

    public static String primaryProductName(List<ProductExposureEntity> products) {
        return products.stream()
                .filter(p -> "SUSPECT".equalsIgnoreCase(p.getProductRole()))
                .map(ProductExposureEntity::getProductName)
                .findFirst()
                .orElseGet(() -> products.isEmpty() ? null : products.get(0).getProductName());
    }

    public static String primaryReactionTerm(List<SafetyEventEntity> events) {
        return events.isEmpty() ? null : events.get(0).getTerm();
    }
}
