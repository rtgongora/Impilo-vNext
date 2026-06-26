package zw.gov.mohcc.impilo.costa.domain.enums;

/** How a reconciled emergency-deferred charge is ultimately closed out. */
public enum SettlementRoute {
    /** Patient (or guarantor) settles the charge directly. */
    SETTLE,
    /** Charge is packed into an insurance/scheme claim (MUSheX). */
    CLAIM,
    /** Charge is waived (exemption / hardship / policy). */
    WAIVER
}
