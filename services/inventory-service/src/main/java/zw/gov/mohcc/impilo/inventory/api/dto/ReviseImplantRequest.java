package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Request to replace an implanted device with a new one — the revision half of pipeline §14. */
public record ReviseImplantRequest(
        @NotNull @Valid RecordImplantRequest newImplant,
        String revisionReason
) {}
