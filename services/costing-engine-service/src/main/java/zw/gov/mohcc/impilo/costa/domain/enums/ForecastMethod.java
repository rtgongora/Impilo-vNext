package zw.gov.mohcc.impilo.costa.domain.enums;

/** Transparent forecasting method — every projection exposes its drivers. No ML. */
public enum ForecastMethod {
    LINEAR_YTD,
    BURN_RATE,
    SEASONAL_NAIVE
}
