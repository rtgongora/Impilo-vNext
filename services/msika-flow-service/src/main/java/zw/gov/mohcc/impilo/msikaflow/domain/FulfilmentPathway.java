package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * OF-B17 — the patient's fulfilment-pathway election for a marketplace
 * request. NULL is treated as {@link #PICKUP} everywhere (fail-closed: no
 * delivery task is ever created without an explicit DELIVERY election plus
 * the §11.8 delivery-minimum details).
 */
public enum FulfilmentPathway {
    PICKUP,
    DELIVERY
}
