package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * Request DTO for authoring / signing-out a histopathology report (spec §11).
 *
 * <p>Used for both the DRAFT capture ({@code POST /orders/{orderId}/histopath}) and the pathologist
 * sign-out ({@code POST /orders/{orderId}/histopath/sign-out}). All fields are optional so a draft
 * can be built incrementally; sign-out may re-supply any section plus the critical flag and the
 * SHR/report fields ({@code recommendations}, {@code docIds}, {@code ziboResultCodes}).</p>
 *
 * <p>{@code ancillaryTests} and {@code docIds} are free-form JSON (serialized by the controller).</p>
 */
public record HistopathReportRequest(
        String specimenRef,
        String grossDescription,
        String microscopicDescription,
        String diagnosis,
        Object ancillaryTests,
        String reportingPathologist,
        String recommendations,
        Object docIds,
        String ziboResultCodes,
        Boolean critical,
        String criticalReason
) {}
