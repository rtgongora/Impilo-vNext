package zw.gov.mohcc.impilo.inventory.api.dto;

/** Request to record that an implanted device has been removed, with no replacement. */
public record RemoveImplantRequest(String removalReason) {}
