package zw.gov.mohcc.impilo.mushex.domain.enums;

public enum AdapterType {
    MOBILE_MONEY,
    BANK_TRANSFER,
    CARD_GATEWAY,
    /** Internal Mushe-wallet rail — the estate's own money movement (payouts, wallet pay). */
    WALLET,
    SANDBOX
}
