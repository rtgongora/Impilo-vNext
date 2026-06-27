package zw.gov.mohcc.impilo.oros.domain;

/**
 * Where a diagnostic order is routed for fulfilment (spec §4 routing details).
 *
 * <p>Internal destinations resolve to TUSO department / service-point contexts; external
 * destinations resolve to VARAPI providers or facilities. {@code PATIENT_CARRIED} indicates the
 * patient physically carries a printable/QR order to a fulfilling site.</p>
 */
public enum RouteDestinationType {
    INTERNAL_DEPARTMENT,
    INTERNAL_SERVICE_POINT,
    EXTERNAL_PROVIDER,
    EXTERNAL_FACILITY,
    PATIENT_CARRIED
}
